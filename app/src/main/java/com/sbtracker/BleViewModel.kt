package com.sbtracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.sbtracker.analytics.AnalyticsRepository
import com.sbtracker.data.AppDatabase
import com.sbtracker.data.UserPreferencesRepository
import com.sbtracker.data.DeviceInfo
import com.sbtracker.data.DeviceStatus
import com.sbtracker.data.ExtendedData
import com.sbtracker.data.Hit
import com.sbtracker.data.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Owns the BLE connection lifecycle, data pipeline (byte parsing → DB storage → session tracking),
 * and device management (known devices, active device).
 */
@HiltViewModel
class BleViewModel @Inject constructor(
    private val bleManager: BleManager,
    private val db: AppDatabase,
    private val analyticsRepo: AnalyticsRepository,
    private val prefsRepo: UserPreferencesRepository,
    application: Application
) : AndroidViewModel(application) {

    val connectionState: StateFlow<BleManager.ConnectionState> = bleManager.connectionState

    private val _latestStatus = MutableStateFlow<DeviceStatus?>(null)
    val latestStatus: StateFlow<DeviceStatus?> = _latestStatus.asStateFlow()

    private val _latestExtended = MutableStateFlow<ExtendedData?>(null)
    val latestExtended: StateFlow<ExtendedData?> = _latestExtended.asStateFlow()

    private val _latestInfo = MutableStateFlow<DeviceInfo?>(null)
    val latestInfo: StateFlow<DeviceInfo?> = _latestInfo.asStateFlow()

    private val _displaySettings = MutableStateFlow<DisplaySettings?>(null)
    val displaySettings: StateFlow<DisplaySettings?> = _displaySettings.asStateFlow()

    private val _firmwareVersion = MutableStateFlow<String?>(null)
    val firmwareVersion: StateFlow<String?> = _firmwareVersion.asStateFlow()

    private val _sessionStats = MutableStateFlow(SessionTracker.SessionStats())
    val sessionStats: StateFlow<SessionTracker.SessionStats> = _sessionStats.asStateFlow()

    private val _targetTemp = MutableStateFlow(180)
    val targetTemp: StateFlow<Int> = _targetTemp.asStateFlow()

    val scannedDevices = bleManager.scannedDevices

    // ── Device persistence ──

    data class SavedDevice(
        val serialNumber: String,
        val deviceAddress: String,
        val deviceType: String
    )

    data class DeviceBatterySnapshot(
        val device: SavedDevice,
        val lastBattery: Int?,
        val lastSeenMs: Long?
    )

    private val _activeDevice = MutableStateFlow<SavedDevice?>(null)
    val activeDevice: StateFlow<SavedDevice?> = _activeDevice.asStateFlow()

    private val _knownDevices = MutableStateFlow<List<SavedDevice>>(emptyList())
    val knownDevices: StateFlow<List<SavedDevice>> = _knownDevices.asStateFlow()

    private val _knownDeviceBatteries = MutableStateFlow<List<DeviceBatterySnapshot>>(emptyList())
    val knownDeviceBatteries: StateFlow<List<DeviceBatterySnapshot>> = _knownDeviceBatteries.asStateFlow()

    // ── Internal state ──

    private val trackers = mutableMapOf<String, SessionTracker>()
    private val lastSavedChargeState = mutableMapOf<String, SessionTracker.ChargeState?>()

    private val devicePrefs by lazy {
        getApplication<Application>().getSharedPreferences("known_devices_v1", Context.MODE_PRIVATE)
    }
    private val chargeStatePrefs by lazy {
        getApplication<Application>().getSharedPreferences("charge_state_v1", Context.MODE_PRIVATE)
    }

    // Temperature unit — synced from device status, persisted locally
    val isCelsius: StateFlow<Boolean> = prefsRepo.userPreferencesFlow
        .map { it.isCelsius }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Alerts state
    val phoneAlertsEnabled: StateFlow<Boolean> = prefsRepo.userPreferencesFlow
        .map { it.phoneAlertsEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private var lastSetpointReached = false
    private var lastCharge80Reached = false
    private var isAppInForeground = false
    private var statusTick = 0
    private var hasSyncedInitialTemp = false

    // Dim-on-charge state
    val dimOnChargeEnabled: StateFlow<Boolean> = prefsRepo.userPreferencesFlow
        .map { it.dimOnChargeEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private var preDimBrightness = -1
    private var wasCharging = false

    private val notificationManager = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val CHANNEL_ID = "device_alerts"

    init {
        viewModelScope.launch {
            prefsRepo.userPreferencesFlow.collect { prefs ->
                preDimBrightness = prefs.preDimBrightness
            }
        }

        _activeDevice.value = loadLastDevice()
        _knownDevices.value = loadAllKnownDevices()

        viewModelScope.launch { refreshKnownDeviceBatteries() }

        createNotificationChannel()
        setupLifecycleObserver()

        // ── BLE data pipeline ──

        viewModelScope.launch {
            bleManager.statusBytes.collect { (bytes, address) ->
                val info = _latestInfo.value
                val deviceType = info?.deviceType ?: ""
                val status = BlePacket.parseStatus(bytes, address, deviceType) ?: return@collect
                _latestStatus.value = status

                if (!hasSyncedInitialTemp) {
                    _targetTemp.value = status.targetTempC
                    hasSyncedInitialTemp = true
                }

                checkAlerts(status)
                checkChargeDim(status)

                if (status.isCelsius != isCelsius.value) {
                    viewModelScope.launch {
                        prefsRepo.updateIsCelsius(status.isCelsius)
                    }
                }

                statusTick++
                val shouldLog = (status.heaterMode > 0) || (statusTick % 60 == 0)
                if (shouldLog) {
                    db.deviceStatusDao().insert(status)
                }

                val serial = info?.serialNumber
                val trackerKey = serial ?: address
                val currentRuntime = _latestExtended.value?.heaterRuntimeMinutes ?: 0
                val tracker = trackerFor(trackerKey, address)
                tracker.markReconnected(status.batteryLevel)
                val result = tracker.update(status, bytes, serial, currentRuntime)

                _sessionStats.value = result.stats
                result.completedSession?.let { session ->
                    val sessionId = db.sessionDao().insert(session)
                    withContext(Dispatchers.IO) {
                        val statuses = db.deviceStatusDao().getStatusForRange(
                            session.deviceAddress, session.startTimeMs, session.endTimeMs
                        )
                        val hits = HitDetector.detect(statuses).map { ph ->
                            Hit(
                                sessionId = sessionId,
                                deviceAddress = session.deviceAddress,
                                startTimeMs = ph.startTimeMs,
                                durationMs = ph.durationMs,
                                peakTempC = ph.peakTempC
                            )
                        }
                        if (hits.isNotEmpty()) db.hitDao().insertAll(hits)

                        val startBat = db.deviceStatusDao().getBatteryAtStart(session.deviceAddress, session.startTimeMs, session.endTimeMs)
                        val endBat = db.deviceStatusDao().getBatteryAtEnd(session.deviceAddress, session.endTimeMs)
                        if (startBat != null && endBat != null) {
                            val drain = (startBat - endBat).coerceAtLeast(0)
                            tracker.recordSessionDrain(drain)
                        }
                    }
                }
                result.completedCharge?.let { db.chargeCycleDao().insert(it) }

                val chargeState = tracker.getChargeState()
                if (serial != null && chargeState != lastSavedChargeState[serial]) {
                    saveChargeState(serial, chargeState)
                    lastSavedChargeState[serial] = chargeState
                }
            }
        }

        viewModelScope.launch {
            bleManager.extendedBytes.collect { (bytes, address) ->
                val extended = BlePacket.parseExtended(bytes, address) ?: return@collect

                if (_latestExtended.value == null) {
                    withContext(Dispatchers.IO) {
                        try {
                            val dbExtended = db.extendedDataDao().getByAddress(address)
                            if (dbExtended != null) {
                                val gapMinutes = extended.heaterRuntimeMinutes - dbExtended.heaterRuntimeMinutes
                                if (gapMinutes > 0) {
                                    val lastStatus = db.deviceStatusDao().getLatestForAddress(address)
                                    val currentStatus = _latestStatus.value

                                    val startBattery = lastStatus?.batteryLevel ?: currentStatus?.batteryLevel ?: 50
                                    val endBattery = currentStatus?.batteryLevel ?: startBattery
                                    val serial = _latestInfo.value?.serialNumber ?: db.deviceInfoDao().getByAddress(address)?.serialNumber

                                    val endTimeMs = System.currentTimeMillis()
                                    val startTimeMs = endTimeMs - (gapMinutes * 60_000L)

                                    val existingId = db.sessionDao().findExistingSessionNear(address, startTimeMs)
                                    if (existingId != null) return@withContext

                                    val sessionId = db.sessionDao().insert(
                                        Session(
                                            deviceAddress = address,
                                            serialNumber = serial,
                                            startTimeMs = startTimeMs,
                                            endTimeMs = endTimeMs
                                        )
                                    )

                                    val baseStatus = currentStatus ?: lastStatus
                                    if (baseStatus != null) {
                                        db.deviceStatusDao().insert(baseStatus.copy(
                                            id = 0, timestampMs = startTimeMs, batteryLevel = startBattery, heaterMode = 1
                                        ))
                                        db.deviceStatusDao().insert(baseStatus.copy(
                                            id = 0, timestampMs = endTimeMs, batteryLevel = endBattery, heaterMode = 0
                                        ))
                                    }

                                    analyticsRepo.invalidateSession(sessionId)
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("BleViewModel", "Offline gap detection failed", e)
                        }
                    }
                }

                _latestExtended.value = extended
                db.extendedDataDao().upsert(extended)
            }
        }

        viewModelScope.launch {
            bleManager.identityBytes.collect { (bytes, address) ->
                val info = BlePacket.parseIdentity(bytes, address) ?: return@collect
                _latestInfo.value = info
                db.deviceInfoDao().upsert(info)
                updateKnownDevice(info)
            }
        }

        viewModelScope.launch {
            bleManager.displaySettingsBytes.collect { bytes ->
                BlePacket.parseDisplaySettings(bytes)?.let { _displaySettings.value = it }
            }
        }

        viewModelScope.launch {
            bleManager.firmwareBytes.collect { bytes ->
                BlePacket.parseFirmware(bytes)?.let { _firmwareVersion.value = it }
            }
        }

        viewModelScope.launch {
            bleManager.connectionState.collect { state ->
                if (state == BleManager.ConnectionState.Disconnected) {
                    hasSyncedInitialTemp = false
                    val serial = _latestInfo.value?.serialNumber
                    if (serial != null) {
                        trackers[serial]?.let { tracker ->
                            tracker.markDisconnected()
                            tracker.reset()
                        }
                    }
                    _sessionStats.value = SessionTracker.SessionStats()
                    _latestStatus.value = null
                    _latestExtended.value = null
                    _latestInfo.value = null
                    _displaySettings.value = null
                    _firmwareVersion.value = null
                    lastSetpointReached = false
                    lastCharge80Reached = false
                }
            }
        }
    }

    // ── BLE actions ──

    fun startScan() = bleManager.startScan()
    fun disconnect() = bleManager.disconnect()
    fun connectToDevice(device: android.bluetooth.BluetoothDevice) = bleManager.connectToDevice(device)

    /** Send a raw BLE write packet. Used by SessionViewModel for device controls. */
    fun sendWrite(packet: ByteArray) = bleManager.sendWrite(packet)
    fun findDevice() = bleManager.findDevice()
    fun factoryReset() = bleManager.sendWrite(BlePacket.buildFactoryReset())

    // ── Temperature ──

    fun setTargetTemp(tempC: Int) {
        _targetTemp.value = tempC.coerceIn(40, 230)
    }

    // ── Unit toggle (syncs device + local pref) ──

    fun toggleUnit() {
        val s = _latestStatus.value ?: return
        bleManager.sendWrite(BlePacket.buildSetUnit(s.isCelsius))
        val newCelsius = !s.isCelsius
        viewModelScope.launch {
            prefsRepo.updateIsCelsius(newCelsius)
        }
    }

    // ── Display settings local update ──

    fun updateDisplaySettingsLocally(brightness: Int) {
        _displaySettings.value?.let { ds ->
            _displaySettings.value = ds.copy(brightness = brightness)
        }
    }

    fun updateVibrationLocally(level: Int) {
        _displaySettings.value?.let { ds ->
            _displaySettings.value = ds.copy(vibrationLevel = level)
        }
    }

    // ── Alerts ──

    fun togglePhoneAlerts() {
        viewModelScope.launch {
            prefsRepo.updatePhoneAlerts(!phoneAlertsEnabled.value)
        }
    }

    private fun checkAlerts(s: DeviceStatus) {
        if (!phoneAlertsEnabled.value) return
        if (s.heaterMode > 0) {
            if (s.setpointReached && !lastSetpointReached) {
                triggerAlert("Device Ready", "Target temperature reached!")
            }
            lastSetpointReached = s.setpointReached
        } else {
            lastSetpointReached = false
        }
        if (s.isCharging) {
            val reached80 = s.batteryLevel >= 80
            if (reached80 && !lastCharge80Reached) {
                triggerAlert("Charging Progress", "Battery has reached 80%.")
            }
            lastCharge80Reached = reached80
        } else {
            lastCharge80Reached = false
        }
    }

    private fun triggerAlert(title: String, message: String) {
        vibratePhone()
        if (!isAppInForeground) {
            showNotification(title, message)
        }
    }

    private fun vibratePhone() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getApplication<Application>().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getApplication<Application>().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(500)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Device Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Vibrations and notifications for temperature and charging events"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(title: String, message: String) {
        val intent = Intent(getApplication(), MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(getApplication(), 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(getApplication(), CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun setupLifecycleObserver() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { isAppInForeground = true }
            override fun onStop(owner: LifecycleOwner) { isAppInForeground = false }
        })
    }

    // ── Dim-on-charge ──

    fun toggleDimOnCharge() {
        val next = !dimOnChargeEnabled.value
        viewModelScope.launch {
            prefsRepo.updateDimOnCharge(next)
        }
        if (next && _latestStatus.value?.isCharging == true) {
            val current = _displaySettings.value?.brightness ?: -1
            if (current > 1) {
                preDimBrightness = current
                viewModelScope.launch {
                    prefsRepo.updatePreDimBrightness(current)
                }
                applyBrightnessAndRefresh(1)
            }
        } else if (!next && _latestStatus.value?.isCharging == true) {
            if (preDimBrightness > 1) {
                applyBrightnessAndRefresh(preDimBrightness)
                preDimBrightness = -1
                viewModelScope.launch {
                    prefsRepo.updatePreDimBrightness(-1)
                }
            }
        }
    }

    private fun checkChargeDim(s: DeviceStatus) {
        if (!dimOnChargeEnabled.value) {
            wasCharging = s.isCharging
            return
        }
        if (s.isCharging && !wasCharging) {
            val current = _displaySettings.value?.brightness ?: -1
            if (current > 1) {
                preDimBrightness = current
                viewModelScope.launch {
                    prefsRepo.updatePreDimBrightness(current)
                }
                applyBrightnessAndRefresh(1)
            }
        } else if (!s.isCharging && wasCharging) {
            if (preDimBrightness > 1) {
                applyBrightnessAndRefresh(preDimBrightness)
                preDimBrightness = -1
                viewModelScope.launch {
                    prefsRepo.updatePreDimBrightness(-1)
                }
            }
        }
        wasCharging = s.isCharging
    }

    private fun applyBrightnessAndRefresh(level: Int) {
        bleManager.sendWrite(BlePacket.buildSetBrightness(level.coerceIn(1, 9)))
        _displaySettings.value?.let { ds ->
            _displaySettings.value = ds.copy(brightness = level)
        }
    }

    /** Handle manual brightness override during dim-on-charge state. */
    fun onManualBrightnessChange(level: Int) {
        if (dimOnChargeEnabled.value && _latestStatus.value?.isCharging == true) {
            preDimBrightness = level
            viewModelScope.launch {
                prefsRepo.updatePreDimBrightness(level)
            }
        }
    }

    // ── Device persistence ──

    private fun updateKnownDevice(info: DeviceInfo) {
        val serials = devicePrefs.getStringSet("known_serials", mutableSetOf())!!.toMutableSet()
        serials.add(info.serialNumber)
        devicePrefs.edit()
            .putStringSet("known_serials", serials)
            .putString("dev_${info.serialNumber}_addr", info.deviceAddress)
            .putString("dev_${info.serialNumber}_type", info.deviceType)
            .putString("last_serial", info.serialNumber)
            .apply()
        _activeDevice.value = SavedDevice(info.serialNumber, info.deviceAddress, info.deviceType)
        _knownDevices.value = loadAllKnownDevices()
    }

    private fun loadLastDevice(): SavedDevice? {
        val serial = devicePrefs.getString("last_serial", null) ?: return null
        val address = devicePrefs.getString("dev_${serial}_addr", null) ?: return null
        val type = devicePrefs.getString("dev_${serial}_type", "") ?: ""
        return SavedDevice(serial, address, type)
    }

    private fun loadAllKnownDevices(): List<SavedDevice> {
        val serials = devicePrefs.getStringSet("known_serials", emptySet())!!
        return serials.mapNotNull { serial ->
            val address = devicePrefs.getString("dev_${serial}_addr", null) ?: return@mapNotNull null
            val type = devicePrefs.getString("dev_${serial}_type", "") ?: ""
            SavedDevice(serial, address, type)
        }.sortedBy { it.serialNumber }
    }

    suspend fun refreshKnownDeviceBatteries() {
        val devices = _knownDevices.value
        if (devices.isEmpty()) return
        _knownDeviceBatteries.value = withContext(Dispatchers.IO) {
            devices.map { device ->
                val latest = db.deviceStatusDao().getLatestForAddress(device.deviceAddress)
                DeviceBatterySnapshot(
                    device = device,
                    lastBattery = latest?.batteryLevel,
                    lastSeenMs = latest?.timestampMs
                )
            }
        }
    }

    // ── Session tracker helpers ──

    private fun trackerFor(key: String, address: String): SessionTracker =
        trackers.getOrPut(key) {
            SessionTracker().apply {
                loadChargeState(key)?.let { restoreChargeState(it) }
                viewModelScope.launch {
                    val recentSessions = db.sessionDao().getRecentSessions(key, address, 50)
                    val recentCycles = db.chargeCycleDao().getRecentCycles(key, address, 20)
                    val drains = recentSessions.mapNotNull { s ->
                        val start = db.deviceStatusDao().getBatteryAtStart(s.deviceAddress, s.startTimeMs, s.endTimeMs)
                        val end = db.deviceStatusDao().getBatteryAtEnd(s.deviceAddress, s.endTimeMs)
                        if (start != null && end != null) (start - end).coerceAtLeast(0) else null
                    }
                    setHistoricalData(drains = drains, rates = recentCycles.map { it.avgRatePctPerMin })
                }
            }
        }

    private fun saveChargeState(serial: String, state: SessionTracker.ChargeState?) {
        val ed = chargeStatePrefs.edit()
        if (state == null) {
            ed.remove("${serial}_active")
            ed.remove("${serial}_startMs")
            ed.remove("${serial}_startBat")
            ed.remove("${serial}_endPendMs")
            ed.remove("${serial}_endBat")
            ed.remove("${serial}_voltLim")
            ed.remove("${serial}_curOpt")
        } else {
            ed.putBoolean("${serial}_active", true)
            ed.putLong("${serial}_startMs", state.chargeStartMs)
            ed.putInt("${serial}_startBat", state.chargeStartBattery)
            ed.putLong("${serial}_endPendMs", state.chargeEndPendingMs)
            ed.putInt("${serial}_endBat", state.chargeEndBattery)
            ed.putBoolean("${serial}_voltLim", state.startChargeVoltageLimit)
            ed.putBoolean("${serial}_curOpt", state.startChargeCurrentOpt)
        }
        ed.apply()
    }

    private fun loadChargeState(serial: String): SessionTracker.ChargeState? {
        if (!chargeStatePrefs.getBoolean("${serial}_active", false)) return null
        return SessionTracker.ChargeState(
            chargeStartMs = chargeStatePrefs.getLong("${serial}_startMs", 0L),
            chargeStartBattery = chargeStatePrefs.getInt("${serial}_startBat", 0),
            chargeEndPendingMs = chargeStatePrefs.getLong("${serial}_endPendMs", -1L),
            chargeEndBattery = chargeStatePrefs.getInt("${serial}_endBat", 0),
            startChargeVoltageLimit = chargeStatePrefs.getBoolean("${serial}_voltLim", false),
            startChargeCurrentOpt = chargeStatePrefs.getBoolean("${serial}_curOpt", false)
        )
    }

    // ── Test device helpers ──

    companion object {
        const val TEST_DEVICE_ADDRESS = "AA:BB:CC:DD:EE:FF"
        const val TEST_DEVICE_SERIAL = "TEST00001"
        const val TEST_DEVICE_TYPE = "Crafty+"
    }

    fun injectTestDevice() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.deviceInfoDao().upsert(
                    DeviceInfo(
                        deviceAddress = TEST_DEVICE_ADDRESS,
                        lastSeenMs = System.currentTimeMillis(),
                        serialNumber = TEST_DEVICE_SERIAL,
                        colorIndex = 2,
                        deviceType = TEST_DEVICE_TYPE
                    )
                )
                db.extendedDataDao().upsert(
                    ExtendedData(
                        deviceAddress = TEST_DEVICE_ADDRESS,
                        lastUpdatedMs = System.currentTimeMillis(),
                        heaterRuntimeMinutes = 420,
                        batteryChargingTimeMinutes = 180
                    )
                )

                val now = System.currentTimeMillis()
                val sessionDefs = listOf(
                    now - 2 * 3600_000L to (4 * 60_000L),
                    now - 8 * 3600_000L to (6 * 60_000L),
                    now - 26 * 3600_000L to (3 * 60_000L),
                    now - 50 * 3600_000L to (5 * 60_000L),
                    now - 60 * 3600_000L to (8 * 60_000L)
                )

                for ((startMs, durationMs) in sessionDefs) {
                    val endMs = startMs + durationMs
                    if (db.sessionDao().findExistingSessionNear(TEST_DEVICE_ADDRESS, startMs) != null) continue

                    val baseBattery = (40..90).random()
                    val drain = (3..12).random()
                    val targetTemp = listOf(175, 180, 185, 190, 195).random()
                    fun makeStatus(ts: Long, temp: Int, battery: Int, mode: Int, setpoint: Boolean) =
                        DeviceStatus(
                            timestampMs = ts, deviceAddress = TEST_DEVICE_ADDRESS,
                            deviceType = TEST_DEVICE_TYPE, currentTempC = temp,
                            targetTempC = targetTemp, boostOffsetC = 0, superBoostOffsetC = 0,
                            batteryLevel = battery, heaterMode = mode, isCharging = false,
                            setpointReached = setpoint, autoShutdownSeconds = 120,
                            isCelsius = true, vibrationEnabled = true,
                            chargeCurrentOptimization = false, chargeVoltageLimit = false,
                            permanentBluetooth = true, boostVisualization = false
                        )

                    db.deviceStatusDao().insert(makeStatus(startMs, 25, baseBattery, 1, false))
                    db.deviceStatusDao().insert(makeStatus(startMs + 30_000L, targetTemp, baseBattery - 1, 1, true))
                    db.deviceStatusDao().insert(makeStatus(endMs, targetTemp, baseBattery - drain, 0, false))

                    val sessionId = db.sessionDao().insert(
                        Session(
                            deviceAddress = TEST_DEVICE_ADDRESS,
                            serialNumber = TEST_DEVICE_SERIAL,
                            startTimeMs = startMs,
                            endTimeMs = endMs
                        )
                    )

                    val hitCount = (1..4).random()
                    val hitSpacing = durationMs / (hitCount + 1)
                    for (h in 1..hitCount) {
                        db.hitDao().insert(
                            Hit(
                                sessionId = sessionId,
                                deviceAddress = TEST_DEVICE_ADDRESS,
                                startTimeMs = startMs + h * hitSpacing,
                                durationMs = (2000L..6000L).random(),
                                peakTempC = targetTemp + (0..5).random()
                            )
                        )
                    }
                }
            }

            val serials = devicePrefs.getStringSet("known_serials", mutableSetOf())!!.toMutableSet()
            serials.add(TEST_DEVICE_SERIAL)
            devicePrefs.edit()
                .putStringSet("known_serials", serials)
                .putString("dev_${TEST_DEVICE_SERIAL}_addr", TEST_DEVICE_ADDRESS)
                .putString("dev_${TEST_DEVICE_SERIAL}_type", TEST_DEVICE_TYPE)
                .apply()
            _knownDevices.value = loadAllKnownDevices()
            analyticsRepo.clearCache()
            refreshKnownDeviceBatteries()
        }
    }

    fun removeTestDevice() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.hitDao().clearAll(TEST_DEVICE_ADDRESS)
                db.sessionDao().clearHistory(TEST_DEVICE_SERIAL, TEST_DEVICE_ADDRESS)
                db.chargeCycleDao().clearAll(TEST_DEVICE_ADDRESS)
                db.deviceStatusDao().clearAll(TEST_DEVICE_ADDRESS)
                db.extendedDataDao().clearAll(TEST_DEVICE_ADDRESS)
                db.deviceInfoDao().clearAll(TEST_DEVICE_ADDRESS)
            }
            val serials = devicePrefs.getStringSet("known_serials", mutableSetOf())!!.toMutableSet()
            serials.remove(TEST_DEVICE_SERIAL)
            devicePrefs.edit()
                .putStringSet("known_serials", serials)
                .remove("dev_${TEST_DEVICE_SERIAL}_addr")
                .remove("dev_${TEST_DEVICE_SERIAL}_type")
                .apply()
            _knownDevices.value = loadAllKnownDevices()
            analyticsRepo.clearCache()
            refreshKnownDeviceBatteries()
        }
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.cleanup()
    }
}
