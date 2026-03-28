package com.sbtracker

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sbtracker.data.AppDatabase
import com.sbtracker.data.BackupRepository
import com.sbtracker.data.ProgramRepository
import com.sbtracker.data.RestoreRepository
import com.sbtracker.data.UserPreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/**
 * Foreground service to keep the BLE connection alive and handle background tasks.
 */
@AndroidEntryPoint
class BleService : Service() {

    @Inject lateinit var bleManager: BleManager
    @Inject lateinit var programRepository: ProgramRepository
    @Inject lateinit var db: AppDatabase
    @Inject lateinit var analyticsRepository: com.sbtracker.analytics.AnalyticsRepository
    @Inject lateinit var backupRepo: BackupRepository
    @Inject lateinit var restoreRepo: RestoreRepository
    @Inject lateinit var prefsRepo: UserPreferencesRepository

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    private var bleViewModel: BleViewModel? = null
    private var wsServer: VaporizerWebSocketServer? = null
    private val notificationId = 101
    private val analyticsFlow = MutableStateFlow<JSONObject?>(null)
    private var telemetryJob: Job? = null

    inner class LocalBinder : Binder() {
        fun getService(): BleService = this@BleService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAllChannels(this)
        startForeground(notificationId, createNotification())

        wsServer = VaporizerWebSocketServer(8080).apply {
            isReuseAddr = true
            commandListener = object : VaporizerWebSocketServer.CommandListener {
                override fun onCommand(json: String) {
                    handleWebCommand(json)
                }
            }
            start()
        }
    }

    private fun handleWebCommand(json: String) {
        try {
            val root = JSONObject(json)
            val command = root.optString("command")
            val value = root.opt("value")

            serviceScope.launch {
                val vm = bleViewModel ?: return@launch
                when (command) {
                    "setTemp" -> {
                        val temp = (value as? Number)?.toInt() ?: return@launch
                        programJob?.cancel()
                        vm.sendWrite(BlePacket.buildStatusWrite(BleConstants.WRITE_TEMPERATURE, tempC = temp))
                    }
                    "setHeater" -> {
                        val on = (value as? Boolean) ?: return@launch
                        vm.sendWrite(BlePacket.buildSetHeater(on))
                    }
                    "setBoostDelta" -> {
                        val boost = (value as? Number)?.toInt() ?: return@launch
                        vm.sendWrite(BlePacket.buildStatusWrite(BleConstants.WRITE_BOOST, boostC = boost))
                    }
                    "setSuperBoostDelta" -> {
                        val boost = (value as? Number)?.toInt() ?: return@launch
                        vm.sendWrite(BlePacket.buildStatusWrite(BleConstants.WRITE_SUPERBOOST, superBoostC = boost))
                    }
                    "setBoostPulse" -> {
                        val on = (value as? Boolean) ?: return@launch
                        val mode = if (on) 2 else 1
                        vm.sendWrite(BlePacket.buildStatusWrite(BleConstants.WRITE_HEATER_STATE, mode = mode))
                    }
                    "setSuperBoostPulse" -> {
                        val on = (value as? Boolean) ?: return@launch
                        val mode = if (on) 3 else 1
                        vm.sendWrite(BlePacket.buildStatusWrite(BleConstants.WRITE_HEATER_STATE, mode = mode))
                    }
                    "setBrightness" -> {
                        val level = (value as? Number)?.toInt() ?: return@launch
                        vm.sendWrite(BlePacket.buildSetBrightness(level))
                        vm.updateDisplaySettingsLocally(level)
                    }
                    "setVibrationLevel" -> {
                        val level = (value as? Number)?.toInt() ?: return@launch
                        vm.sendWrite(BlePacket.buildSetVibrationLevel(level))
                        vm.updateVibrationLocally(level)
                    }
                    "setVibration" -> {
                        val enabled = (value as? Boolean) ?: return@launch
                        vm.sendWrite(BlePacket.buildSetBoostVisualization(enabled, vm.latestStatus.value?.deviceType ?: ""))
                    }
                    "toggleUnit" -> vm.toggleUnit()
                    "setChargeCurrentOptimization" -> {
                        val enabled = (value as? Boolean) ?: return@launch
                        vm.sendWrite(BlePacket.buildSetChargeCurrentOpt(enabled))
                    }
                    "setChargeVoltageLimit" -> {
                        val enabled = (value as? Boolean) ?: return@launch
                        vm.sendWrite(BlePacket.buildSetChargeVoltageLimit(enabled))
                    }
                    "togglePermanentBle" -> {
                        val enabled = (value as? Boolean) ?: return@launch
                        vm.sendWrite(BlePacket.buildSetPermanentBle(enabled))
                    }
                    "setCapsuleWeight" -> {
                        val weight = (value as? Number)?.toFloat() ?: return@launch
                        prefsRepo.updateCapsuleWeight(weight)
                    }
                    "setDefaultIsCapsule" -> {
                        val isCapsule = (value as? Boolean) ?: return@launch
                        prefsRepo.updateDefaultIsCapsule(isCapsule)
                    }
                    "injectTestDevice" -> vm.injectTestDevice()
                    "removeTestDevice" -> vm.removeTestDevice()
                    "rebuildHistory" -> {
                        analyticsRepository.rebuildSessionHistory(db)
                    }
                    "clearHistory" -> {
                        val addr = vm.activeDevice.value?.deviceAddress ?: ""
                        analyticsRepository.clearCache()
                        db.sessionMetadataDao().clearAllForDevice(addr)
                        db.sessionDao().clearHistory("", addr)
                        db.deviceStatusDao().clearAll(addr)
                        db.hitDao().clearAll(addr)
                        db.chargeCycleDao().clearAll(addr)
                        db.extendedDataDao().clearAll(addr)
                    }
                    "backupDatabase" -> backupRepo.createBackup()
                    "restoreDatabase" -> vm.requestRestorePicker()
                    "setSessionNote" -> {
                        val sessionObj = value as? JSONObject ?: return@launch
                        val id = sessionObj.optLong("id")
                        val notes = sessionObj.optString("notes")
                        val existing = db.sessionMetadataDao().getMetadataForSession(id) 
                            ?: com.sbtracker.data.SessionMetadata(sessionId = id)
                        db.sessionMetadataDao().insertOrUpdate(existing.copy(notes = notes))
                        analyticsRepository.invalidateSession(id)
                    }
                    "setSessionRating" -> {
                        val sessionObj = value as? JSONObject ?: return@launch
                        val id = sessionObj.optLong("id")
                        val rating = sessionObj.optInt("rating")
                        val existing = db.sessionMetadataDao().getMetadataForSession(id) 
                            ?: com.sbtracker.data.SessionMetadata(sessionId = id)
                        db.sessionMetadataDao().insertOrUpdate(existing.copy(rating = rating))
                        analyticsRepository.invalidateSession(id)
                    }
                    "vibrate" -> {
                        val vibrator = getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            vibrator?.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator?.vibrate(50)
                        }
                    }
                    "startScan" -> vm.startScan()
                    "setProgram" -> {
                        val id = (value as? Number)?.toLong() ?: return@launch
                        val program = programRepository.getById(id) ?: return@launch
                        programJob?.cancel()
                        programJob = serviceScope.launch {
                            vm.sendWrite(BlePacket.buildStatusWrite(
                                BleConstants.WRITE_TEMPERATURE or BleConstants.WRITE_HEATER_STATE,
                                tempC = program.targetTempC, mode = 1
                            ))
                            val stepsArray = JSONArray(program.boostStepsJson)
                            val startTime = System.currentTimeMillis()
                            for (i in 0 until stepsArray.length()) {
                                val stepObj = stepsArray.getJSONObject(i)
                                val offsetSec = stepObj.optInt("offsetSec", 0)
                                val boostC = stepObj.optInt("boostC", 0)
                                val delayMs = (startTime + offsetSec * 1000L) - System.currentTimeMillis()
                                if (delayMs > 0L) kotlinx.coroutines.delay(delayMs)
                                vm.sendWrite(BlePacket.buildStatusWrite(BleConstants.WRITE_BOOST, boostC = boostC.coerceAtLeast(0)))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BleService", "Error parsing web command", e)
        }
    }

    private var programJob: Job? = null

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun initialize(vm: BleViewModel) {
        telemetryJob?.cancel()
        telemetryJob = serviceScope.launch {
            this@BleService.bleViewModel = vm

            // 1. Analytics Background Task (Slow Flow - debounced)
            launch {
                combine(
                    db.sessionDao().observeAllSessions(),
                    db.chargeCycleDao().observeAllCycles(),
                    prefsRepo.userPreferencesFlow
                ) { sessions, charges, prefs ->
                    val summaries = analyticsRepository.getSessionSummaries(sessions)
                    val insights = analyticsRepository.computeBatteryInsights(summaries, charges, prefs.dayStartHour)
                    val records = analyticsRepository.computePersonalRecords(summaries)
                    val usage = analyticsRepository.computeUsageInsights(summaries, prefs.dayStartHour)
                    val daily = analyticsRepository.computeDailyStats(summaries, prefs.dayStartHour)
                    val history = analyticsRepository.computeHistoryStats(summaries, prefs.dayStartHour)
                    val metadata = db.sessionMetadataDao().getMetadataForSessions(sessions.map { it.id }).associateBy { it.sessionId }
                    val intake = analyticsRepository.computeIntakeStats(summaries, metadata, prefs.capsuleWeightGrams, prefs.defaultIsCapsule)

                    val fullJson = JSONObject(TelemetryMapper.toJson(
                        null, BleManager.ConnectionState.Disconnected, SessionTracker.SessionStats(), null, null, 
                        insights, records, usage, daily, history, null, intake
                    ))
                    val prefsObj = JSONObject()
                    prefsObj.put("dayStartHour", prefs.dayStartHour)
                    prefsObj.put("retentionDays", prefs.retentionDays)
                    prefsObj.put("capsuleWeightGrams", prefs.capsuleWeightGrams)
                    prefsObj.put("defaultIsCapsule", prefs.defaultIsCapsule)
                    fullJson.put("prefs", prefsObj)
                    fullJson
                }.debounce(2000L).collectLatest { json ->
                    analyticsFlow.value = json
                }
            }

            // 2. High-Speed Telemetry Stream
            launch {
                val flows = listOf(
                    vm.latestStatus,
                    vm.connectionState,
                    vm.sessionStats,
                    vm.displaySettings,
                    vm.firmwareVersion,
                    vm.latestExtended,
                    analyticsFlow
                )
                combine(flows) { args ->
                    val status = args[0] as? com.sbtracker.data.DeviceStatus
                    val state = args[1] as? BleManager.ConnectionState ?: BleManager.ConnectionState.Disconnected
                    val stats = args[2] as? SessionTracker.SessionStats ?: SessionTracker.SessionStats()
                    val display = args[3] as? DisplaySettings
                    val fw = args[4] as? String
                    val extended = args[5] as? com.sbtracker.data.ExtendedData
                    val analytics = args[6] as? JSONObject

                    // Use TelemetryMapper for the core JSON
                    val jsonStr = TelemetryMapper.toJson(
                        status, state, stats, display, fw, null, null, null, null, null, extended, null
                    )
                    val root = JSONObject(jsonStr)

                    // Merge slow-moving analytics fields from analyticsFlow
                    analytics?.let { a ->
                        val keys = a.keys()
                        while (keys.hasNext()) {
                            val key = keys.next() as String
                            if (!root.has(key)) root.put(key, a.get(key))
                        }
                    }

                    // Add dynamic heat-up estimation
                    if (status != null && status.currentTempC < status.targetTempC) {
                        val remainingC = status.targetTempC - status.currentTempC
                        if (remainingC > 0) {
                            val summaries = analyticsRepository.getSessionSummaries(db.sessionDao().getAllSessionsSync())
                            val estMs = analyticsRepository.computeEstimatedHeatUpTime(status.targetTempC, summaries)
                            if (estMs != null) {
                                root.put("estHeatUpMs", estMs)
                                val statsObj = root.optJSONObject("stats") ?: JSONObject().also { root.put("stats", it) }
                                statsObj.put("estHeatUpMs", estMs)
                            }
                        }
                    }

                    updateNotification(status, state, stats)
                    root
                }.collect { json ->
                    wsServer?.broadcastTelemetry(json.toString())
                }
            }

            // 3. Session Programs Stream
            launch {
                combine(
                    programRepository.programs,
                    db.sessionDao().observeAllSessions()
                ) { programs, sessions -> programs to sessions }.collectLatest { (programs, sessions) ->
                    val summaries = analyticsRepository.getSessionSummaries(sessions)
                    val avgDrain = analyticsRepository.computeAvgDrainPerMinute(summaries)
                    val array = JSONArray()
                    programs.forEach { p ->
                        val obj = JSONObject()
                        obj.put("id", p.id); obj.put("name", p.name); obj.put("targetTempC", p.targetTempC)
                        obj.put("boostStepsJson", p.boostStepsJson); obj.put("isDefault", p.isDefault)
                        val durationSec = try {
                            val arr = JSONArray(p.boostStepsJson)
                            if (arr.length() > 0) arr.getJSONObject(arr.length() - 1).getInt("offsetSec") + 60 else 180
                        } catch (e: Exception) { 180 }
                        val estDrain = ( (durationSec / 60.0) * avgDrain ).toInt()
                        obj.put("estDurationSec", durationSec); obj.put("estDrainPct", estDrain)
                        array.put(obj)
                    }
                    val root = JSONObject(); root.put("type", "programs"); root.put("data", array)
                    wsServer?.broadcastTelemetry(root.toString())
                }
            }

            // 4. History Stream
            launch {
                db.sessionDao().observeAllSessions().collectLatest { sessions ->
                    val summaries = analyticsRepository.getSessionSummaries(sessions)
                    wsServer?.broadcastTelemetry(TelemetryMapper.historyToJson(summaries))
                }
            }

            // 5. Charge History Stream
            launch {
                db.chargeCycleDao().observeAllCycles().collectLatest { charges ->
                    wsServer?.broadcastTelemetry(TelemetryMapper.chargesToJson(charges))
                }
            }
        }
    }

    private fun createNotification(
        statusText: String = "Monitoring device...",
        contentText: String = "Background tracking active",
        actions: List<NotificationCompat.Action> = emptyList()
    ): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NotificationChannels.STATUS)
            .setContentTitle(statusText).setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply { actions.forEach { addAction(it) } }.build()
    }

    private fun buildActionIntent(action: String, targetTemp: Int = 0, heaterMode: Int = 0): PendingIntent {
        val reqCode = when (action) {
            NotificationActionReceiver.ACTION_TEMP_UP   -> NotificationActionReceiver.REQ_TEMP_UP
            NotificationActionReceiver.ACTION_TEMP_DOWN -> NotificationActionReceiver.REQ_TEMP_DOWN
            NotificationActionReceiver.ACTION_HEATER_ON -> NotificationActionReceiver.REQ_HEATER_ON
            else                                        -> NotificationActionReceiver.REQ_HEATER_OFF
        }
        val intent = Intent(this, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(NotificationActionReceiver.EXTRA_TARGET_TEMP, targetTemp)
            putExtra(NotificationActionReceiver.EXTRA_HEATER_MODE, heaterMode)
        }
        return PendingIntent.getBroadcast(this, reqCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    // Dedup key: only call manager.notify when visible content changes.
    // The elapsed timer changes every second otherwise, causing a notify storm.
    private var lastNotifKey: String = ""

    private fun updateNotification(status: com.sbtracker.data.DeviceStatus?, state: BleManager.ConnectionState, stats: SessionTracker.SessionStats) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        var notifActions: List<NotificationCompat.Action> = emptyList()
        val (title, content) = when {
            state is BleManager.ConnectionState.Connected && status != null -> {
                if (status.heaterMode > 0) {
                    notifActions = listOf(
                        NotificationCompat.Action(0, "▲ Temp", buildActionIntent(NotificationActionReceiver.ACTION_TEMP_UP, status.targetTempC, status.heaterMode)),
                        NotificationCompat.Action(0, "▼ Temp", buildActionIntent(NotificationActionReceiver.ACTION_TEMP_DOWN, status.targetTempC, status.heaterMode)),
                        NotificationCompat.Action(0, "Stop", buildActionIntent(NotificationActionReceiver.ACTION_HEATER_OFF))
                    )
                    // Round elapsed to nearest 30s so notify() cadence is max 2/min during session
                    val elapsedRounded = (stats.durationSeconds / 30) * 30
                    val drain = if (stats.batteryDrain > 0) " • −${stats.batteryDrain}%" else ""
                    "Session — ${status.currentTempC}°C" to "${status.batteryLevel}% batt$drain • ${elapsedRounded / 60}m"
                } else if (status.isCharging) {
                    val eta = stats.chargeEtaMinutes?.let { " • ~${it}m" } ?: ""
                    "Charging" to "${status.batteryLevel}%$eta"
                } else {
                    notifActions = listOf(NotificationCompat.Action(0, "Ignite", buildActionIntent(NotificationActionReceiver.ACTION_HEATER_ON, status.targetTempC)))
                    "Ready" to "${status.batteryLevel}% • ${status.targetTempC}°C set"
                }
            }
            state is BleManager.ConnectionState.Reconnecting -> "Reconnecting…" to "Waiting for device"
            else -> "Disconnected" to "Tap to reconnect"
        }

        // Only rebuild and post notification if something the user can see has changed
        val key = "$title|$content|${notifActions.size}"
        if (key == lastNotifKey) return
        lastNotifKey = key
        manager.notify(notificationId, createNotification(title, content, notifActions))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (bleManager.connectionState.value is BleManager.ConnectionState.Disconnected) {
            val prefs = getSharedPreferences("known_devices_v1", MODE_PRIVATE)
            val lastSerial = prefs.getString("last_serial", null)
            if (lastSerial != null) {
                val address = prefs.getString("dev_${lastSerial}_addr", null)
                if (!address.isNullOrBlank()) bleManager.reconnectToAddress(address)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
