package com.sbtracker

import android.app.Application
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
import com.sbtracker.data.SessionMetadata
import com.sbtracker.data.ActiveProgramHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
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
    private val activeProgramHolder: ActiveProgramHolder,
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

    // ── Restore Trigger ──
    private val _triggerRestorePicker = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val triggerRestorePicker = _triggerRestorePicker.asSharedFlow()

    fun requestRestorePicker() {
        _triggerRestorePicker.tryEmit(Unit)
    }

    // ── Internal state ──

    private val trackers = mutableMapOf<String, SessionTracker>()
    private val lastSavedChargeState = mutableMapOf<String, SessionTracker.ChargeState?>()
    private val lastPersistedStatus = mutableMapOf<String, DeviceStatus>()

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

    val alertTempReady: StateFlow<Boolean> = prefsRepo.userPreferencesFlow
        .map { it.alertTempReady }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val alertCharge80: StateFlow<Boolean> = prefsRepo.userPreferencesFlow
        .map { it.alertCharge80 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val alertSessionEnd: StateFlow<Boolean> = prefsRepo.userPreferencesFlow
        .map { it.alertSessionEnd }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private var lastSetpointReached = false
    private var lastCharge80Reached = false
    private var lastHeaterOn = false
    private var heaterSessionStartMs = 0L
    private var isAppInForeground = false
    private var hasSyncedInitialTemp = false

    // Dim-on-charge state
    val dimOnChargeEnabled: StateFlow<Boolean> = prefsRepo.userPreferencesFlow
        .map { it.dimOnChargeEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private var preDimBrightness = -1
    private var wasCharging = false

    private val notificationManager = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        viewModelScope.launch {
            prefsRepo.userPreferencesFlow.collect { prefs ->
                preDimBrightness = prefs.preDimBrightness
            }
        }

        _activeDevice.value = loadLastDevice()
        _knownDevices.value = loadAllKnownDevices()

        viewModelScope.launch { refreshKnownDeviceBatteries() }

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

                if (shouldPersistStatus(status)) {
                    db.deviceStatusDao().insert(status)
                    lastPersistedStatus[address] = status
                }

                val serial = info?.serialNumber
                val currentRuntime = _latestExtended.value?.heaterRuntimeMinutes ?: 0
                val tracker = trackerFor(address)
                tracker.markReconnected()
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

                        // Save Program Metadata (T-056)
                        val appliedProgram = activeProgramHolder.consume()
                        db.sessionMetadataDao().insertOrUpdate(
                            SessionMetadata(
                                sessionId = sessionId,
                                appliedProgramId = appliedProgram?.id
                            )
                        )

                        val startBat = db.deviceStatusDao().getBatteryAtStart(session.deviceAddress, session.startTimeMs, session.endTimeMs)
                        val endBat = db.deviceStatusDao().getBatteryAtEnd(session.deviceAddress, session.startTimeMs, session.endTimeMs)
                        if (startBat != null && endBat != null) {
                            val drain = (startBat - endBat).coerceAtLeast(0)
                            tracker.recordSessionDrain(drain)
                        }
                    }
                }
                result.completedCharge?.let { db.chargeCycleDao().insert(it) }

                val chargeState = tracker.getChargeState()
                if (chargeState != lastSavedChargeState[address]) {
                    saveChargeState(address, chargeState)
                    lastSavedChargeState[address] = chargeState
                }
            }
        }

        viewModelScope.launch {
            bleManager.extendedBytes.collect { (bytes, address) ->
                val extended = BlePacket.parseExtended(bytes, address) ?: return@collect

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
                    val address = _latestStatus.value?.deviceAddress ?: _activeDevice.value?.deviceAddress
                    if (address != null) {
                        trackers[address]?.markDisconnected()
                    }
                    _sessionStats.value = SessionTracker.SessionStats()
                    _latestStatus.value = null
                    _latestExtended.value = null
                    _latestInfo.value = null
                    _displaySettings.value = null
                    _firmwareVersion.value = null
                    lastSetpointReached = false
                    lastCharge80Reached = false
                    lastHeaterOn = false
                    heaterSessionStartMs = 0L
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
        val heaterOn = s.heaterMode > 0
        if (heaterOn) {
            if (!lastHeaterOn) heaterSessionStartMs = System.currentTimeMillis()
            if (s.setpointReached && !lastSetpointReached && alertTempReady.value) {
                triggerAlert("Device Ready", "Target temperature reached!", NOTIF_ID_TEMP_READY)
            }
            lastSetpointReached = s.setpointReached
        } else {
            if (lastHeaterOn) {
                val sessionDurationMs = System.currentTimeMillis() - heaterSessionStartMs
                if (sessionDurationMs >= 60_000L && alertSessionEnd.value) {
                    triggerAlert("Session Complete", "Your session has ended.", NOTIF_ID_SESSION_END)
                }
            }
            lastSetpointReached = false
        }
        lastHeaterOn = heaterOn
        if (s.isCharging) {
            val reached80 = s.batteryLevel >= 80
            if (reached80 && !lastCharge80Reached && alertCharge80.value) {
                triggerAlert("Charging Progress", "Battery has reached 80%.", NOTIF_ID_CHARGE_80)
            }
            lastCharge80Reached = reached80
        } else {
            lastCharge80Reached = false
        }
    }

    private fun triggerAlert(title: String, message: String, notifId: Int) {
        vibratePhone()
        showNotification(title, message, notifId)
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

    private fun showNotification(title: String, message: String, notifId: Int) {
        if (!NotificationPermissionHelper.isGranted(getApplication())) {
            return
        }
        val ctx = getApplication<Application>()
        val tapIntent = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        fun dismissPendingIntent(reqCode: Int) = PendingIntent.getBroadcast(
            ctx, reqCode,
            Intent(ctx, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_DISMISS_ALERT
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notifId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(ctx, NotificationChannels.ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tapIntent)

        when (notifId) {
            NOTIF_ID_TEMP_READY -> {
                val timerIntent = PendingIntent.getBroadcast(
                    ctx, NotificationActionReceiver.REQ_START_TIMER,
                    Intent(ctx, NotificationActionReceiver::class.java).apply {
                        action = NotificationActionReceiver.ACTION_START_TIMER
                        putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notifId)
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                builder.addAction(0, "Start Timer", timerIntent)
                builder.addAction(0, "Dismiss", dismissPendingIntent(NotificationActionReceiver.REQ_DISMISS_ALERT_TEMP))
            }
            NOTIF_ID_CHARGE_80 -> {
                val disconnectIntent = PendingIntent.getBroadcast(
                    ctx, NotificationActionReceiver.REQ_DISCONNECT_CHARGE,
                    Intent(ctx, NotificationActionReceiver::class.java).apply {
                        action = NotificationActionReceiver.ACTION_DISCONNECT_CHARGE
                        putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notifId)
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                builder.addAction(0, "Disconnect", disconnectIntent)
                builder.addAction(0, "Dismiss", dismissPendingIntent(NotificationActionReceiver.REQ_DISMISS_ALERT_CHARGE))
            }
            NOTIF_ID_SESSION_END -> {
                builder.addAction(0, "Dismiss", dismissPendingIntent(NotificationActionReceiver.REQ_DISMISS_ALERT_SESSION))
            }
        }

        notificationManager.notify(notifId, builder.build())
    }

    companion object {
        private const val NOTIF_ID_TEMP_READY  = 210
        private const val NOTIF_ID_CHARGE_80   = 211
        private const val NOTIF_ID_SESSION_END = 212

        const val TEST_DEVICE_ADDRESS = "AA:BB:CC:DD:EE:FF"
        const val TEST_DEVICE_SERIAL  = "TEST00001"
        const val TEST_DEVICE_TYPE    = "Crafty+"
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

    private fun trackerFor(address: String): SessionTracker =
        trackers.getOrPut(address) {
            SessionTracker().apply {
                loadChargeState(address)?.let { restoreChargeState(it) }
                viewModelScope.launch {
                    val recentSessions = db.sessionDao().getRecentSessions(address, address, 50)
                    val recentCycles = db.chargeCycleDao().getRecentCycles(address, address, 20)
                    val drains = recentSessions.mapNotNull { s ->
                        val start = db.deviceStatusDao().getBatteryAtStart(s.deviceAddress, s.startTimeMs, s.endTimeMs)
                        val end = db.deviceStatusDao().getBatteryAtEnd(s.deviceAddress, s.startTimeMs, s.endTimeMs)
                        if (start != null && end != null) (start - end).coerceAtLeast(0) else null
                    }
                    setHistoricalData(drains = drains, rates = recentCycles.map { it.avgRatePctPerMin })
                }
            }
        }

    private fun saveChargeState(address: String, state: SessionTracker.ChargeState?) {
        val ed = chargeStatePrefs.edit()
        if (state == null) {
            ed.remove("${address}_active")
            ed.remove("${address}_startMs")
            ed.remove("${address}_startBat")
            ed.remove("${address}_endPendMs")
            ed.remove("${address}_endBat")
            ed.remove("${address}_voltLim")
            ed.remove("${address}_curOpt")
        } else {
            ed.putBoolean("${address}_active", true)
            ed.putLong("${address}_startMs", state.chargeStartMs)
            ed.putInt("${address}_startBat", state.chargeStartBattery)
            ed.putLong("${address}_endPendMs", state.chargeEndPendingMs)
            ed.putInt("${address}_endBat", state.chargeEndBattery)
            ed.putBoolean("${address}_voltLim", state.startChargeVoltageLimit)
            ed.putBoolean("${address}_curOpt", state.startChargeCurrentOpt)
        }
        ed.apply()
    }

    private fun loadChargeState(address: String): SessionTracker.ChargeState? {
        if (!chargeStatePrefs.getBoolean("${address}_active", false)) return null
        return SessionTracker.ChargeState(
            chargeStartMs = chargeStatePrefs.getLong("${address}_startMs", 0L),
            chargeStartBattery = chargeStatePrefs.getInt("${address}_startBat", 0),
            chargeEndPendingMs = chargeStatePrefs.getLong("${address}_endPendMs", -1L),
            chargeEndBattery = chargeStatePrefs.getInt("${address}_endBat", 0),
            startChargeVoltageLimit = chargeStatePrefs.getBoolean("${address}_voltLim", false),
            startChargeCurrentOpt = chargeStatePrefs.getBoolean("${address}_curOpt", false)
        )
    }

    private fun shouldPersistStatus(status: DeviceStatus): Boolean {
        val previous = lastPersistedStatus[status.deviceAddress] ?: return true

        if (status.heaterMode > 0 || previous.heaterMode > 0) return true

        if (status.isCharging || previous.isCharging) {
            val chargeBoundaryChanged =
                status.isCharging != previous.isCharging ||
                    status.batteryLevel != previous.batteryLevel ||
                    status.chargeCurrentOptimization != previous.chargeCurrentOptimization ||
                    status.chargeVoltageLimit != previous.chargeVoltageLimit
            return chargeBoundaryChanged || (status.timestampMs - previous.timestampMs) >= 15_000L
        }

        val meaningfulIdleChange =
            status.heaterMode != previous.heaterMode ||
                status.isCharging != previous.isCharging ||
                status.setpointReached != previous.setpointReached ||
                status.targetTempC != previous.targetTempC ||
                status.boostOffsetC != previous.boostOffsetC ||
                status.superBoostOffsetC != previous.superBoostOffsetC ||
                status.batteryLevel != previous.batteryLevel ||
                abs(status.autoShutdownSeconds - previous.autoShutdownSeconds) > 2

        return meaningfulIdleChange || (status.timestampMs - previous.timestampMs) >= 30_000L
    }

    // ── Test device helpers ──

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
