package com.sbtracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.sbtracker.data.AppDatabase
import com.sbtracker.data.ChargeCycle
import com.sbtracker.data.DeviceInfo
import com.sbtracker.data.DeviceStatus
import com.sbtracker.data.ExtendedData
import com.sbtracker.data.Hit
import com.sbtracker.data.Session
import com.sbtracker.data.SessionSummary
import com.sbtracker.analytics.AnalyticsRepository
import com.sbtracker.analytics.BatteryInsights
import com.sbtracker.analytics.DailyStats
import com.sbtracker.analytics.HistoryStats
import com.sbtracker.analytics.PersonalRecords
import com.sbtracker.analytics.ProfileStats
import com.sbtracker.analytics.UsageInsights
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bridges [BleManager] (BLE layer) with the database, session analytics, and UI.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val bleManager: BleManager = BleManager(application)
    private val db = AppDatabase.getInstance(application)

    /** Analytics engine — owns all stat computation and the session summary cache. */
    val analyticsRepo = AnalyticsRepository(db)

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

    private val _exportUri = MutableSharedFlow<Uri>(extraBufferCapacity = 1)
    val exportUri = _exportUri.asSharedFlow()

    private val trackers = mutableMapOf<String, SessionTracker>()
    private val lastSavedChargeState = mutableMapOf<String, SessionTracker.ChargeState?>()

    private val appPrefs by lazy {
        getApplication<Application>().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    }

    private val _phoneAlertsEnabled = MutableStateFlow(true)
    val phoneAlertsEnabled: StateFlow<Boolean> = _phoneAlertsEnabled.asStateFlow()

    private val _dimOnChargeEnabled = MutableStateFlow(false)
    val dimOnChargeEnabled: StateFlow<Boolean> = _dimOnChargeEnabled.asStateFlow()

    // Temperature unit — true = Celsius, false = Fahrenheit.
    // Sourced from the device's own setting; persisted so history is readable offline.
    private val _isCelsius = MutableStateFlow(true)
    val isCelsius: StateFlow<Boolean> = _isCelsius.asStateFlow()

    /** Hour of day (0–23) at which a new "session day" begins. Persisted in app prefs. */
    private val _dayStartHour = MutableStateFlow(4)
    val dayStartHour: StateFlow<Int> = _dayStartHour.asStateFlow()

    fun setDayStartHour(hour: Int) {
        val next = hour.coerceIn(0, 23)
        _dayStartHour.value = next
        appPrefs.edit().putInt("day_start_hour", next).apply()
    }

    // Alerts State
    private var lastSetpointReached = false
    private var lastCharge80Reached = false
    private var isAppInForeground = false
    private var statusTick = 0
    private var wasCharging = false
    private var preDimBrightness = -1
    private var hasSyncedInitialTemp = false

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

    private val devicePrefs by lazy {
        getApplication<Application>().getSharedPreferences("known_devices_v1", Context.MODE_PRIVATE)
    }

    private val _activeDevice = MutableStateFlow<SavedDevice?>(null)
    val activeDevice: StateFlow<SavedDevice?> = _activeDevice.asStateFlow()

    private val _knownDevices = MutableStateFlow<List<SavedDevice>>(emptyList())
    val knownDevices: StateFlow<List<SavedDevice>> = _knownDevices.asStateFlow()

    private val _knownDeviceBatteries = MutableStateFlow<List<DeviceBatterySnapshot>>(emptyList())
    val knownDeviceBatteries: StateFlow<List<DeviceBatterySnapshot>> = _knownDeviceBatteries.asStateFlow()

    private val notificationManager = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val CHANNEL_ID = "device_alerts"

    init {
        _phoneAlertsEnabled.value = appPrefs.getBoolean("phone_alerts", true)
        _dimOnChargeEnabled.value = appPrefs.getBoolean("dim_on_charge", false)
        _isCelsius.value          = appPrefs.getBoolean("is_celsius", true)
        _dayStartHour.value       = appPrefs.getInt("day_start_hour", 4)

        // Restore last-known device so history is accessible before connecting
        _activeDevice.value = loadLastDevice()
        _knownDevices.value = loadAllKnownDevices()

        // Load per-device battery snapshots for the landing page
        viewModelScope.launch { refreshKnownDeviceBatteries() }

        createNotificationChannel()
        setupLifecycleObserver()

        viewModelScope.launch {
            bleManager.statusBytes.collect { (bytes, address) ->
                val info       = _latestInfo.value
                val deviceType = info?.deviceType ?: ""
                val status     = BlePacket.parseStatus(bytes, address, deviceType) ?: return@collect
                _latestStatus.value = status
                
                // One-time sync from device on connection
                if (!hasSyncedInitialTemp) {
                    _targetTemp.value = status.targetTempC
                    hasSyncedInitialTemp = true
                }

                checkAlerts(status)
                checkChargeDim(status)

                // Keep local unit preference in sync with the device setting
                if (status.isCelsius != _isCelsius.value) {
                    _isCelsius.value = status.isCelsius
                    appPrefs.edit().putBoolean("is_celsius", status.isCelsius).apply()
                }

                // Log to DB regardless of identity — graph queries only use deviceAddress.
                // Must NOT be gated behind the serial check below or status rows go missing
                // during the first tick after every connection / reconnect.
                statusTick++
                val shouldLog = (status.heaterMode > 0) || (statusTick % 60 == 0) // ~every 30s when idle
                if (shouldLog) {
                    db.deviceStatusDao().insert(status)
                }

                // Identity frames can arrive late (or not at all on some firmwares).
                // Use deviceAddress as a stable fallback key so sessions still save and history works offline.
                val serial = info?.serialNumber
                val trackerKey = serial ?: address
                val currentRuntime = _latestExtended.value?.heaterRuntimeMinutes ?: 0
                val tracker = trackerFor(trackerKey, address)
                tracker.markReconnected(status.batteryLevel)
                val result  = tracker.update(status, bytes, serial, currentRuntime)

                _sessionStats.value = result.stats
                result.completedSession?.let { session ->
                    val sessionId = db.sessionDao().insert(session)
                    withContext(Dispatchers.IO) {
                        val statuses = db.deviceStatusDao().getStatusForRange(
                            session.deviceAddress, session.startTimeMs, session.endTimeMs
                        )
                        // Re-detect hits from the raw device_status log.
                        val hits = HitDetector.detect(statuses).map { ph ->
                            Hit(
                                sessionId     = sessionId,
                                deviceAddress = session.deviceAddress,
                                startTimeMs   = ph.startTimeMs,
                                durationMs    = ph.durationMs,
                                peakTempC     = ph.peakTempC
                            )
                        }
                        if (hits.isNotEmpty()) db.hitDao().insertAll(hits)

                        // Feed the real battery drain back into the tracker so that
                        // sessionsRemaining / sessionsToCritical update immediately for
                        // the next session rather than staying frozen on startup history.
                        val startBat = db.deviceStatusDao().getBatteryAtStart(session.deviceAddress, session.startTimeMs, session.endTimeMs)
                        val endBat   = db.deviceStatusDao().getBatteryAtEnd(session.deviceAddress, session.endTimeMs)
                        if (startBat != null && endBat != null) {
                            val drain = (startBat - endBat).coerceAtLeast(0)
                            tracker.recordSessionDrain(drain)
                        }
                    }
                }
                result.completedCharge?.let  { db.chargeCycleDao().insert(it) }

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
                
                // Offline gap detection: if this is the first ExtendedData received this session,
                // check if the heaterRuntime jumped since we last saw the device.
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
                                    
                                    // Insert synthetic bounded logs so that AnalyticsRepository can compute duration and drain
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
                            // Ignore
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
                if (state is BleManager.ConnectionState.Connected) {
                    // Nothing to prune — raw log is retained until user clears history.
                }
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

        // If the user has a device_status log but no sessions (e.g. after a schema reset),
        // reconstruct sessions from the raw log so history stays usable.
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { backcompileSessionsFromLogsIfNeeded() }
            }
        }
    }

    suspend fun rebuildSessionHistoryFromLogs() {
        withContext(Dispatchers.IO) {
            backcompileSessionsFromLogs(force = true)
        }
    }

    private suspend fun backcompileSessionsFromLogsIfNeeded() {
        val existing = db.sessionDao().countAll()
        if (existing > 0) return
        // Only bother if we have any status log at all.
        val addresses = db.deviceStatusDao().getAllDeviceAddresses()
        if (addresses.isEmpty()) return
        backcompileSessionsFromLogs(force = false)
    }

    private suspend fun backcompileSessionsFromLogs(force: Boolean) {
        val addresses = db.deviceStatusDao().getAllDeviceAddresses()
        if (addresses.isEmpty()) return

        for (address in addresses) {
            val statuses = db.deviceStatusDao().getAllForAddress(address)
            if (statuses.isEmpty()) continue

            val serial = runCatching { db.deviceInfoDao().getByAddress(address)?.serialNumber }.getOrNull()

            // Reconstruct using the same invariants as SessionTracker.
            val minDurationMs = 30_000L
            val endGraceMs = 8_000L

            var inSession = false
            var startMs = 0L
            var endPendingMs = -1L
            var lastOnMs = 0L

            suspend fun commit(endMs: Long) {
                val duration = endMs - startMs
                if (duration < minDurationMs) return

                val existingId = db.sessionDao().findExistingSessionNear(address, startMs)
                if (existingId != null) return

                val sessionId = db.sessionDao().insert(
                    Session(
                        deviceAddress = address,
                        serialNumber = serial,
                        startTimeMs = startMs,
                        endTimeMs = endMs
                    )
                )

                // Populate hits table for this reconstructed session (idempotent insert guarded above).
                val window = db.deviceStatusDao().getStatusForRange(address, startMs, endMs)
                val hits = HitDetector.detect(window).map { ph ->
                    Hit(
                        sessionId = sessionId,
                        deviceAddress = address,
                        startTimeMs = ph.startTimeMs,
                        durationMs = ph.durationMs,
                        peakTempC = ph.peakTempC
                    )
                }
                if (hits.isNotEmpty()) db.hitDao().insertAll(hits)
            }

            for (s in statuses) {
                val heaterOn = s.heaterMode > 0

                if (!inSession && heaterOn) {
                    inSession = true
                    startMs = s.timestampMs
                    lastOnMs = s.timestampMs
                    endPendingMs = -1L
                    continue
                }

                if (!inSession) continue

                if (heaterOn) {
                    lastOnMs = s.timestampMs
                    endPendingMs = -1L
                } else {
                    if (endPendingMs == -1L) endPendingMs = s.timestampMs
                    // Close when we've observed at least the grace window of "off" time.
                    if (s.timestampMs - endPendingMs >= endGraceMs) {
                        val endMs = endPendingMs
                        commit(endMs)
                        inSession = false
                        startMs = 0L
                        endPendingMs = -1L
                        lastOnMs = 0L
                    }
                }
            }

            // If log ends while session active, optionally close it at last "on" timestamp.
            // Only do this when forced (user-invoked rebuild) to avoid creating partial sessions
            // during live use where the session may still be running.
            if (force && inSession && lastOnMs > startMs) {
                commit(lastOnMs)
            }
        }

        analyticsRepo.clearCache()
    }

    private fun checkAlerts(s: DeviceStatus) {
        if (!_phoneAlertsEnabled.value) return

        // 1. Temp Ready
        if (s.heaterMode > 0) {
            if (s.setpointReached && !lastSetpointReached) {
                triggerAlert("Device Ready", "Target temperature reached!")
            }
            lastSetpointReached = s.setpointReached
        } else {
            lastSetpointReached = false
        }

        // 2. Charge 80%
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

    fun togglePhoneAlerts() {
        val next = !_phoneAlertsEnabled.value
        _phoneAlertsEnabled.value = next
        appPrefs.edit().putBoolean("phone_alerts", next).apply()
    }

    fun toggleDimOnCharge() {
        val next = !_dimOnChargeEnabled.value
        _dimOnChargeEnabled.value = next
        appPrefs.edit().putBoolean("dim_on_charge", next).apply()
        // If enabling while already charging, dim immediately
        if (next && _latestStatus.value?.isCharging == true) {
            val current = _displaySettings.value?.brightness ?: -1
            if (current > 1) {
                preDimBrightness = current
                applyBrightnessAndRefresh(1)
            }
        }
    }

    private fun checkChargeDim(s: DeviceStatus) {
        if (!_dimOnChargeEnabled.value) {
            wasCharging = s.isCharging
            return
        }

        if (s.isCharging && !wasCharging) {
            // Charge started — save current brightness and dim to 1
            val current = _displaySettings.value?.brightness ?: -1
            if (current > 1) {
                preDimBrightness = current
                applyBrightnessAndRefresh(1)
            }
        } else if (!s.isCharging && wasCharging) {
            // Charge ended — restore previous brightness
            if (preDimBrightness > 1) {
                applyBrightnessAndRefresh(preDimBrightness)
                preDimBrightness = -1
            }
        }
        wasCharging = s.isCharging
    }

    private fun applyBrightnessAndRefresh(level: Int) {
        setBrightness(level)
        // Update local state immediately so slider reflects the change
        _displaySettings.value?.let { ds ->
            _displaySettings.value = ds.copy(brightness = level)
        }
    }

    private val chargeStatePrefs by lazy {
        getApplication<Application>().getSharedPreferences("charge_state_v1", Context.MODE_PRIVATE)
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
            ed.putBoolean("${serial}_active",    true)
            ed.putLong   ("${serial}_startMs",   state.chargeStartMs)
            ed.putInt    ("${serial}_startBat",  state.chargeStartBattery)
            ed.putLong   ("${serial}_endPendMs", state.chargeEndPendingMs)
            ed.putInt    ("${serial}_endBat",    state.chargeEndBattery)
            ed.putBoolean("${serial}_voltLim",   state.startChargeVoltageLimit)
            ed.putBoolean("${serial}_curOpt",    state.startChargeCurrentOpt)
        }
        ed.apply()
    }

    private fun loadChargeState(serial: String): SessionTracker.ChargeState? {
        if (!chargeStatePrefs.getBoolean("${serial}_active", false)) return null
        return SessionTracker.ChargeState(
            chargeStartMs           = chargeStatePrefs.getLong   ("${serial}_startMs",   0L),
            chargeStartBattery      = chargeStatePrefs.getInt    ("${serial}_startBat",  0),
            chargeEndPendingMs      = chargeStatePrefs.getLong   ("${serial}_endPendMs", -1L),
            chargeEndBattery        = chargeStatePrefs.getInt    ("${serial}_endBat",    0),
            startChargeVoltageLimit = chargeStatePrefs.getBoolean("${serial}_voltLim",   false),
            startChargeCurrentOpt  = chargeStatePrefs.getBoolean("${serial}_curOpt",    false)
        )
    }

    private fun trackerFor(key: String, address: String): SessionTracker =
        trackers.getOrPut(key) {
            SessionTracker().apply {
                loadChargeState(key)?.let { restoreChargeState(it) }
                viewModelScope.launch {
                    val recentSessions = db.sessionDao().getRecentSessions(key, address, 50)
                    val recentCycles   = db.chargeCycleDao().getRecentCycles(key, address, 20)
                    // Battery drain per session is now derived from device_status boundary readings.
                    val drains = recentSessions.mapNotNull { s ->
                        val start = db.deviceStatusDao().getBatteryAtStart(s.deviceAddress, s.startTimeMs, s.endTimeMs)
                        val end   = db.deviceStatusDao().getBatteryAtEnd(s.deviceAddress, s.endTimeMs)
                        if (start != null && end != null) (start - end).coerceAtLeast(0) else null
                    }
                    setHistoricalData(drains = drains, rates = recentCycles.map { it.avgRatePctPerMin })
                }
            }
        }

    // ── Device persistence helpers ──

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

    /** Refresh per-device last-known battery readings from the DB. */
    private suspend fun refreshKnownDeviceBatteries() {
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

    // ── History sort / filter ──

    enum class SessionSort { DATE, HITS, DURATION, DRAIN, TEMP }

    private val _sessionSort = MutableStateFlow(SessionSort.DATE)
    val sessionSort: StateFlow<SessionSort> = _sessionSort.asStateFlow()

    /** null = active device, "all" = all devices, otherwise a specific serialNumber */
    private val _sessionFilter = MutableStateFlow<String?>(null)
    val sessionFilter: StateFlow<String?> = _sessionFilter.asStateFlow()

    fun setSessionSort(sort: SessionSort) { _sessionSort.value = sort }
    fun setSessionFilter(filter: String?) { _sessionFilter.value = filter }

    @OptIn(ExperimentalCoroutinesApi::class)
    val rawSessionHistory: StateFlow<List<Session>> =
        combine(_activeDevice, _sessionFilter) { device, filter ->
            device to filter
        }.flatMapLatest { (device, filter) ->
            when {
                filter == "all" -> db.sessionDao().observeAllSessions()
                filter != null  -> db.sessionDao().observeSessions(filter, filter)
                device != null  -> db.sessionDao().observeSessions(device.serialNumber, device.deviceAddress)
                else            -> db.sessionDao().observeAllSessions()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val rawChargeHistory: StateFlow<List<ChargeCycle>> =
        combine(_activeDevice, _sessionFilter) { device, filter ->
            device to filter
        }.flatMapLatest { (device, filter) ->
            when {
                filter == "all" -> db.chargeCycleDao().observeAllCycles()
                filter != null  -> db.chargeCycleDao().observeCycles(filter, filter)
                device != null  -> db.chargeCycleDao().observeCycles(device.serialNumber, device.deviceAddress)
                else            -> db.chargeCycleDao().observeAllCycles()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Session summary computation ───────────────────────────────────────────
    // Delegated entirely to AnalyticsRepository, which caches results so that
    // repeated Flow emissions for unchanged sessions are free (cache hit).

    /**
     * Filter-aware list of session summaries — drives the history list and the historyStats card.
     * Served from [analyticsRepo]'s cache after the first computation per session.
     */
    val sessionSummaries: StateFlow<List<SessionSummary>> = rawSessionHistory
        .map { sessions -> analyticsRepo.getSessionSummaries(sessions) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * All sessions across all devices — used for cross-device "today" and "last session".
     * Not filter-aware; always reflects the complete history.
     */
    private val allSessionSummaries: StateFlow<List<SessionSummary>> =
        db.sessionDao().observeAllSessions()
            .map { sessions -> analyticsRepo.getSessionSummaries(sessions) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Most recent session across ALL devices — drives the "Last Activity" card on the landing page.
     * Always chronological, independent of any device filter.
     */
    val lastSession: StateFlow<SessionSummary?> =
        allSessionSummaries.map { it.firstOrNull() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Active-device summaries (not filter-aware) — used for insights and profile so they
     * always reflect the connected device regardless of the history filter.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val deviceSessionSummaries: StateFlow<List<SessionSummary>> =
        _activeDevice.flatMapLatest { device ->
            val sessionsFlow = if (device != null)
                db.sessionDao().observeSessions(device.serialNumber, device.deviceAddress)
            else
                db.sessionDao().observeAllSessions()
            sessionsFlow.map { sessions -> analyticsRepo.getSessionSummaries(sessions) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val estimatedHeatUpTimeSecs: StateFlow<Long?> =
        combine(deviceSessionSummaries, targetTemp) { summaries, target ->
            val ms = analyticsRepo.computeEstimatedHeatUpTime(target, summaries)
            if (ms != null) ms / 1000 else null
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val sessionHistory: StateFlow<List<HistoryItem>> =
        combine(sessionSummaries, rawChargeHistory, _sessionSort) { summaries, charges, sort ->
            val items = mutableListOf<HistoryItem>()
            summaries.mapTo(items) { HistoryItem.SessionItem(it) }
            charges.mapTo(items)   { HistoryItem.ChargeItem(it) }
            when (sort) {
                SessionSort.DATE     -> items.sortedByDescending { it.startTimeMs }
                SessionSort.HITS     -> items.sortedByDescending {
                    if (it is HistoryItem.SessionItem) it.summary.hitCount else -1
                }
                SessionSort.DURATION -> items.sortedByDescending {
                    when (it) {
                        is HistoryItem.SessionItem -> it.summary.durationMs
                        is HistoryItem.ChargeItem  -> it.cycle.durationMs
                    }
                }
                SessionSort.DRAIN    -> items.sortedByDescending {
                    when (it) {
                        is HistoryItem.SessionItem -> it.summary.batteryConsumed
                        is HistoryItem.ChargeItem  -> it.cycle.batteryGained
                    }
                }
                SessionSort.TEMP     -> items.sortedByDescending {
                    if (it is HistoryItem.SessionItem) it.summary.peakTempC else -1
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Sessions across ALL devices that fall within the current "day window"
     * (from [_dayStartHour] today until now).  Drives the TODAY card on the landing page.
     * Uses allSessionSummaries so every known device contributes to the count.
     */
    val todaySummaries: StateFlow<List<SessionSummary>> =
        combine(allSessionSummaries, _dayStartHour) { summaries, startHour ->
            // Fully-qualify to avoid any accidental Calendar shadowing/import differences.
            val c = java.util.Calendar.getInstance()
            // If the clock hasn't yet passed the day-start hour, the window began yesterday.
            if (c.get(java.util.Calendar.HOUR_OF_DAY) < startHour) c.add(java.util.Calendar.DAY_OF_YEAR, -1)
            c.set(java.util.Calendar.HOUR_OF_DAY, startHour)
            c.set(java.util.Calendar.MINUTE, 0)
            c.set(java.util.Calendar.SECOND, 0)
            c.set(java.util.Calendar.MILLISECOND, 0)
            val dayStartMs = c.timeInMillis
            summaries.filter { it.startTimeMs >= dayStartMs }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val profileStats: StateFlow<ProfileStats> =
        combine(deviceSessionSummaries, latestExtended) { summaries, extended ->
            analyticsRepo.computeProfileStats(summaries, extended?.heaterRuntimeMinutes ?: 0)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileStats())

    // ── History aggregate stats (derived from the current filter's summaries) ─

    val historyStats: StateFlow<HistoryStats> =
        combine(sessionSummaries, _dayStartHour) { summaries, startHour ->
            analyticsRepo.computeHistoryStats(summaries, startHour)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryStats())

    // ── Usage insights — streaks, weekly comparison, time-of-day patterns ─────

    val usageInsights: StateFlow<UsageInsights> =
        combine(deviceSessionSummaries, _dayStartHour) { summaries, startHour ->
            analyticsRepo.computeUsageInsights(summaries, startHour)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UsageInsights())

    // ── Battery insights — drain trend, charge patterns ───────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    private val deviceChargeHistory: StateFlow<List<ChargeCycle>> =
        _activeDevice.flatMapLatest { device ->
            if (device != null)
                db.chargeCycleDao().observeCycles(device.serialNumber, device.deviceAddress)
            else
                db.chargeCycleDao().observeAllCycles()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val batteryInsights: StateFlow<BatteryInsights> =
        combine(deviceSessionSummaries, deviceChargeHistory, _dayStartHour) { summaries, charges, startHour ->
            analyticsRepo.computeBatteryInsights(summaries, charges, startHour)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BatteryInsights())

    // ── Personal records and daily time-series ────────────────────────────────

    /** All-time best session stats for the active device. */
    val personalRecords: StateFlow<PersonalRecords> =
        deviceSessionSummaries.map { summaries ->
            analyticsRepo.computePersonalRecords(summaries)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PersonalRecords())

    /** Per-day aggregates ordered chronologically — suitable for trend charts. */
    val dailyStats: StateFlow<List<DailyStats>> =
        combine(deviceSessionSummaries, _dayStartHour) { summaries, startHour ->
            analyticsRepo.computeDailyStats(summaries, startHour)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val recentStatuses: StateFlow<List<DeviceStatus>> = _activeDevice.flatMapLatest { device ->
        if (device != null)
            db.deviceStatusDao().observeRecentStatus(device.deviceAddress, 120)
        else
            flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── History Graph Period ──────────────────────────────────────────────────

    enum class GraphPeriod { DAY, WEEK }

    private val _graphPeriod = MutableStateFlow(GraphPeriod.DAY)
    val graphPeriod: StateFlow<GraphPeriod> = _graphPeriod.asStateFlow()

    fun setGraphPeriod(p: GraphPeriod) { _graphPeriod.value = p }



    private fun computeGraphWindowStart(period: GraphPeriod, startHour: Int): Long {
        return if (period == GraphPeriod.DAY) {
            val c = java.util.Calendar.getInstance()
            if (c.get(java.util.Calendar.HOUR_OF_DAY) < startHour) c.add(java.util.Calendar.DAY_OF_YEAR, -1)
            c.set(java.util.Calendar.HOUR_OF_DAY, startHour)
            c.set(java.util.Calendar.MINUTE, 0)
            c.set(java.util.Calendar.SECOND, 0)
            c.set(java.util.Calendar.MILLISECOND, 0)
            c.timeInMillis
        } else {
            System.currentTimeMillis() - 7L * 24 * 3600_000L
        }
    }

    val graphWindowStartMs: StateFlow<Long> = combine(_graphPeriod, _dayStartHour) { period, startHour ->
        computeGraphWindowStart(period, startHour)
    }.stateIn(viewModelScope, SharingStarted.Eagerly,
        computeGraphWindowStart(GraphPeriod.DAY, 4))

    @OptIn(ExperimentalCoroutinesApi::class)
    val graphStatuses: StateFlow<List<DeviceStatus>> =
        combine(_activeDevice, _graphPeriod, _dayStartHour) { device, period, startHour ->
            Triple(device, period, startHour)
        }.flatMapLatest { (device, period, startHour) ->
            if (device == null) flowOf(emptyList())
            else db.deviceStatusDao().observeStatusForRange(
                device.deviceAddress,
                computeGraphWindowStart(period, startHour),
                System.currentTimeMillis() + 60_000L
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scannedDevices = bleManager.scannedDevices

    fun startScan()  = bleManager.startScan()
    fun disconnect() = bleManager.disconnect()
    fun connectToDevice(device: android.bluetooth.BluetoothDevice) = bleManager.connectToDevice(device)

    fun startSession() {
        setTempAndHeaterMode(_targetTemp.value, 1)
    }

    fun setHeater(on: Boolean) = bleManager.sendWrite(BlePacket.buildSetHeater(on))
    
    fun setHeaterMode(mode: Int) {
        val currentTarget = _targetTemp.value
        bleManager.sendWrite(BlePacket.buildStatusWrite(BleConstants.WRITE_HEATER_STATE or BleConstants.WRITE_TEMPERATURE, tempC = currentTarget, mode = mode))
    }
    
    fun setTemp(tempC: Int) {
        val clamped = tempC.coerceIn(40, 230)
        _targetTemp.value = clamped

        val status = _latestStatus.value
        // Always bundle mode to ensure setpoint is accepted while active
        val mask = BleConstants.WRITE_TEMPERATURE or BleConstants.WRITE_HEATER_STATE
        val mode = status?.heaterMode ?: 0
        bleManager.sendWrite(BlePacket.buildStatusWrite(mask, tempC = clamped, mode = mode))
    }

    private fun setTempAndHeaterMode(tempC: Int, mode: Int) {
        _targetTemp.value = tempC.coerceIn(40, 230)
        bleManager.sendWrite(BlePacket.buildStatusWrite(BleConstants.WRITE_TEMPERATURE or BleConstants.WRITE_HEATER_STATE, tempC = _targetTemp.value, mode = mode))
    }

    fun adjustTemp(deltaC: Int) {
        setTemp(_targetTemp.value + deltaC)
    }

    // Boost/superboost offsets are raw °C deltas stored in a single byte (0–255).
    // No arbitrary upper cap — the device firmware enforces its own maximum.
    fun setBoost(offsetC: Int) = bleManager.sendWrite(BlePacket.buildStatusWrite(BleConstants.WRITE_BOOST, boostC = offsetC.coerceAtLeast(0)))
    fun adjustBoost(deltaC: Int) {
        val current = _latestStatus.value?.boostOffsetC ?: return
        setBoost(current + deltaC)
    }

    fun setSuperBoost(offsetC: Int) = bleManager.sendWrite(BlePacket.buildStatusWrite(BleConstants.WRITE_SUPERBOOST, superBoostC = offsetC.coerceAtLeast(0)))
    fun adjustSuperBoost(deltaC: Int) {
        val current = _latestStatus.value?.superBoostOffsetC ?: return
        setSuperBoost(current + deltaC)
    }

    // Auto-shutdown is a uint16 (0–65535 seconds). No artificial device-level floor/ceiling —
    // the device enforces its own valid range.
    fun setAutoShutdown(seconds: Int) = bleManager.sendWrite(BlePacket.buildStatusWrite(BleConstants.WRITE_AUTO_SHUTDOWN, shutdownSec = seconds.coerceAtLeast(0)))

    // UI helper: cycles tap-to-increase through 1–60 minutes (60s steps). Once over
    // 3600s it wraps back to 60s so the user always has a usable cycle in settings.
    fun adjustAutoShutdown(deltaSecs: Int) {
        val current = _latestStatus.value?.autoShutdownSeconds ?: return
        var next = current + deltaSecs
        if (next > 3600) next = 60
        setAutoShutdown(next)
    }

    /**
     * Toggles the device haptic vibration level via CMD_BRIGHTNESS_VIBRATION byte 5.
     * This is the actual motor vibration, distinct from the boost-visualization LED flag.
     * Optimistically updates local state so the switch reflects immediately.
     */
    fun toggleVibrationLevel() {
        val current = _displaySettings.value?.vibrationLevel ?: return
        val next = if (current > 0) 0 else 1
        bleManager.sendWrite(BlePacket.buildSetVibrationLevel(next))
        _displaySettings.value = _displaySettings.value?.copy(vibrationLevel = next)
    }

    /**
     * Toggles the Boost & Superboost LED visualization flag (bit 0x40 in CMD_STATUS byte 14).
     * Veazy inverts the bit; for all other devices the raw bit reflects the enabled state.
     */
    fun toggleBoostVisualization() {
        val s = _latestStatus.value ?: return
        bleManager.sendWrite(BlePacket.buildSetBoostVisualization(!s.boostVisualization, s.deviceType))
    }

    fun toggleUnit() {
        val s = _latestStatus.value ?: return
        bleManager.sendWrite(BlePacket.buildSetUnit(s.isCelsius))
        // Flip locally so all displays react immediately, before next status poll confirms it
        val newCelsius = !s.isCelsius
        _isCelsius.value = newCelsius
        appPrefs.edit().putBoolean("is_celsius", newCelsius).apply()
    }

    fun toggleChargeCurrentOpt() {
        val s = _latestStatus.value ?: return
        bleManager.sendWrite(BlePacket.buildSetChargeCurrentOpt(!s.chargeCurrentOptimization))
    }

    fun toggleChargeVoltageLimit() {
        val s = _latestStatus.value ?: return
        bleManager.sendWrite(BlePacket.buildSetChargeVoltageLimit(!s.chargeVoltageLimit))
    }

    fun togglePermanentBle() {
        val s = _latestStatus.value ?: return
        bleManager.sendWrite(BlePacket.buildSetPermanentBle(!s.permanentBluetooth))
    }

    fun toggleBoostTimeout() {
        val current = _displaySettings.value?.boostTimeout ?: return
        // Per reference: 0 = permanent boost (no timeout), 1 = boost times out
        val next = if (current > 0) 0 else 1
        setBoostTimeout(next)
    }

    fun setBrightness(level: Int) = bleManager.sendWrite(BlePacket.buildSetBrightness(level.coerceIn(1, 9)))

    fun setVibrationLevel(level: Int) = bleManager.sendWrite(BlePacket.buildSetVibrationLevel(level.coerceIn(0, 100)))

    fun setBoostTimeout(seconds: Int) = bleManager.sendWrite(BlePacket.buildSetBoostTimeout(seconds.coerceIn(0, 255)))

    fun findDevice() = bleManager.findDevice()

    /** Factory reset: CMD 0x01, mask 0x80, byte14=0x04 (BIT_SETTINGS_FACTORY_RESET), byte15=0x04 */
    fun factoryReset() = bleManager.sendWrite(BlePacket.buildFactoryReset())

    fun deleteSession(session: Session) {
        viewModelScope.launch {
            analyticsRepo.invalidateSession(session.id)
            db.hitDao().deleteHitsForSession(session.id)
            db.sessionDao().delete(session)
        }
    }

    /**
     * Re-run [HitDetector] against the raw device_status log for [session] and
     * replace any previously stored hits with the new results.
     *
     * Call this after updating the detection algorithm to retroactively correct
     * a specific session's hit data without requiring a full history wipe.
     */
    fun reprocessHits(session: Session) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val statuses = db.deviceStatusDao().getStatusForRange(
                    session.deviceAddress, session.startTimeMs, session.endTimeMs
                )
                val newHits = HitDetector.detect(statuses).map { ph ->
                    Hit(
                        sessionId     = session.id,
                        deviceAddress = session.deviceAddress,
                        startTimeMs   = ph.startTimeMs,
                        durationMs    = ph.durationMs,
                        peakTempC     = ph.peakTempC
                    )
                }
                db.hitDao().deleteHitsForSession(session.id)
                if (newHits.isNotEmpty()) db.hitDao().insertAll(newHits)
                // Invalidate the cache so the next summary read reflects the new hits.
                analyticsRepo.invalidateSession(session.id)
            }
        }
    }

    /**
     * Clear all stored history for the active device: sessions, hits, device_status
     * telemetry, extended_data, and device_info.  Raw log and derived tables are
     * cleared together so the data layer stays consistent.
     */
    fun clearSessionHistory() {
        val device = _activeDevice.value ?: return
        viewModelScope.launch {
            val addr   = device.deviceAddress
            val serial = device.serialNumber
            // Clear all six tables in a consistent order: derived first, raw last.
            db.hitDao().clearAll(addr)
            db.sessionDao().clearHistory(serial, addr)
            db.chargeCycleDao().clearAll(addr)
            db.deviceStatusDao().clearAll(addr)
            db.extendedDataDao().clearAll(addr)
            db.deviceInfoDao().clearAll(addr)
            analyticsRepo.clearCache()
        }
    }

    suspend fun getStatusForSession(session: Session): List<DeviceStatus> {
        return db.deviceStatusDao().getStatusForRange(session.deviceAddress, session.startTimeMs, session.endTimeMs)
    }

    fun exportHistoryCsv() {
        viewModelScope.launch {
            val summaries = withContext(Dispatchers.IO) {
                analyticsRepo.getSessionSummaries(db.sessionDao().getAllSessionsSync())
            }
            val csv = buildString {
                append("ID,Device,Date,Duration(ms),Hits,TotalHitMs,StartBat,EndBat,BatConsumed,AvgTempC,PeakTempC,HeatUpMs,HeaterWearMin\n")
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                summaries.forEach { s ->
                    append("${s.id},")
                    append("${s.serialNumber ?: s.deviceAddress},")
                    append("${sdf.format(Date(s.startTimeMs))},")
                    append("${s.durationMs},")
                    append("${s.hitCount},")
                    append("${s.totalHitDurationMs},")
                    append("${s.startBattery},")
                    append("${s.endBattery},")
                    append("${s.batteryConsumed},")
                    append("${s.avgTempC},")
                    append("${s.peakTempC},")
                    append("${s.heatUpTimeMs},")
                    append("${s.heaterWearMinutes}\n")
                }
            }

            withContext(Dispatchers.IO) {
                val file = File(getApplication<Application>().cacheDir, "sb_history_export.csv")
                file.writeText(csv)
                val uri = FileProvider.getUriForFile(getApplication(), "${getApplication<Application>().packageName}.provider", file)
                _exportUri.emit(uri)
            }
        }
    }

    // ── Synthetic test device (development only) ─────────────────────────────

    companion object {
        const val TEST_DEVICE_ADDRESS = "AA:BB:CC:DD:EE:FF"
        const val TEST_DEVICE_SERIAL  = "TEST00001"
        const val TEST_DEVICE_TYPE    = "Crafty+"
    }

    /**
     * Inject a synthetic "Crafty+" device with sessions, hits, and status data.
     * Exercises the entire multi-device pipeline without a second physical device.
     */
    fun injectTestDevice() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // 1. Device identity
                db.deviceInfoDao().upsert(
                    DeviceInfo(
                        deviceAddress = TEST_DEVICE_ADDRESS,
                        lastSeenMs = System.currentTimeMillis(),
                        serialNumber = TEST_DEVICE_SERIAL,
                        colorIndex = 2,
                        deviceType = TEST_DEVICE_TYPE
                    )
                )

                // 2. Extended data
                db.extendedDataDao().upsert(
                    ExtendedData(
                        deviceAddress = TEST_DEVICE_ADDRESS,
                        lastUpdatedMs = System.currentTimeMillis(),
                        heaterRuntimeMinutes = 420,
                        batteryChargingTimeMinutes = 180
                    )
                )

                // 3. Generate 5 sessions spread over the past 3 days
                val now = System.currentTimeMillis()
                val sessionDefs = listOf(
                    now - 2 * 3600_000L to (4 * 60_000L),   // 2 hours ago, 4 min
                    now - 8 * 3600_000L to (6 * 60_000L),   // 8 hours ago, 6 min
                    now - 26 * 3600_000L to (3 * 60_000L),  // yesterday, 3 min
                    now - 50 * 3600_000L to (5 * 60_000L),  // 2 days ago, 5 min
                    now - 60 * 3600_000L to (8 * 60_000L)   // 2.5 days ago, 8 min
                )

                for ((startMs, durationMs) in sessionDefs) {
                    val endMs = startMs + durationMs

                    // Check for existing
                    if (db.sessionDao().findExistingSessionNear(TEST_DEVICE_ADDRESS, startMs) != null) continue

                    // Insert boundary status rows
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

                    // Insert hits
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

            // Register in known devices prefs
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

    /** Remove the synthetic test device and all its data. */
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
