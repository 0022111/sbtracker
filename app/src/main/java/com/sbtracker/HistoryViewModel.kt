package com.sbtracker

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sbtracker.analytics.AnalyticsRepository
import com.sbtracker.analytics.DailyStats
import com.sbtracker.analytics.HistoryStats
import com.sbtracker.analytics.IntakeStats
import com.sbtracker.analytics.ProfileStats
import com.sbtracker.analytics.UsageInsights
import com.sbtracker.data.AppDatabase
import com.sbtracker.data.UserPreferencesRepository
import com.sbtracker.data.ChargeCycle
import com.sbtracker.data.DeviceStatus
import com.sbtracker.data.Hit
import com.sbtracker.data.Session
import com.sbtracker.data.SessionMetadata
import com.sbtracker.data.SessionSummary
import dagger.hilt.android.lifecycle.HiltViewModel
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
import javax.inject.Inject

/**
 * Owns session history list, filtering, sorting, analytics queries, graphs, and CSV export.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val db: AppDatabase,
    val analyticsRepo: AnalyticsRepository,
    private val prefsRepo: UserPreferencesRepository,
    application: Application
) : AndroidViewModel(application) {

    // ── Sort / Filter ──

    enum class SessionSort { DATE, HITS, DURATION, DRAIN, TEMP }
    enum class GraphPeriod { DAY, WEEK }

    private val _sessionSort = MutableStateFlow(SessionSort.DATE)
    val sessionSort: StateFlow<SessionSort> = _sessionSort.asStateFlow()

    private val _sessionFilter = MutableStateFlow<String?>(null)
    val sessionFilter: StateFlow<String?> = _sessionFilter.asStateFlow()

    fun setSessionSort(sort: SessionSort) { _sessionSort.value = sort }
    fun setSessionFilter(filter: String?) { _sessionFilter.value = filter }

    // Active device — loaded from SharedPrefs so we don't depend on BleViewModel
    private val devicePrefs by lazy {
        getApplication<Application>().getSharedPreferences("known_devices_v1", android.content.Context.MODE_PRIVATE)
    }

    private val _activeDevice = MutableStateFlow<BleViewModel.SavedDevice?>(null)
    val activeDevice: StateFlow<BleViewModel.SavedDevice?> = _activeDevice.asStateFlow()

    val dayStartHour: StateFlow<Int> = prefsRepo.userPreferencesFlow
        .map { it.dayStartHour }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 4)

    private val _exportUri = MutableSharedFlow<Uri>(extraBufferCapacity = 1)
    val exportUri = _exportUri.asSharedFlow()

    init {
        _activeDevice.value = loadLastDevice()
    }

    /** Called by fragments when activeDevice changes in BleViewModel. */
    fun updateActiveDevice(device: BleViewModel.SavedDevice?) {
        _activeDevice.value = device
    }

    fun updateDayStartHour(hour: Int) {
        viewModelScope.launch {
            prefsRepo.updateDayStartHour(hour)
        }
    }

    // ── Raw queries ──

    @OptIn(ExperimentalCoroutinesApi::class)
    val rawSessionHistory: StateFlow<List<Session>> =
        combine(_activeDevice, _sessionFilter) { device, filter ->
            device to filter
        }.flatMapLatest { (device, filter) ->
            when {
                filter == "all" -> db.sessionDao().observeAllSessions()
                filter != null -> db.sessionDao().observeSessions(filter, filter)
                device != null -> db.sessionDao().observeSessions(device.serialNumber, device.deviceAddress)
                else -> db.sessionDao().observeAllSessions()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val rawChargeHistory: StateFlow<List<ChargeCycle>> =
        combine(_activeDevice, _sessionFilter) { device, filter ->
            device to filter
        }.flatMapLatest { (device, filter) ->
            when {
                filter == "all" -> db.chargeCycleDao().observeAllCycles()
                filter != null -> db.chargeCycleDao().observeCycles(filter, filter)
                device != null -> db.chargeCycleDao().observeCycles(device.serialNumber, device.deviceAddress)
                else -> db.chargeCycleDao().observeAllCycles()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Session summaries ──

    val sessionSummaries: StateFlow<List<SessionSummary>> = rawSessionHistory
        .map { sessions -> analyticsRepo.getSessionSummaries(sessions) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allSessionSummaries: StateFlow<List<SessionSummary>> =
        db.sessionDao().observeAllSessions()
            .map { sessions -> analyticsRepo.getSessionSummaries(sessions) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lastSession: StateFlow<SessionSummary?> =
        allSessionSummaries.map { it.firstOrNull() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
        combine(deviceSessionSummaries, MutableStateFlow(180)) { summaries, target ->
            val ms = analyticsRepo.computeEstimatedHeatUpTime(target, summaries)
            if (ms != null) ms / 1000 else null
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Create a heat-up estimate flow that reacts to a specific target temp. */
    fun estimatedHeatUpTimeSecs(targetTemp: StateFlow<Int>): StateFlow<Long?> =
        combine(deviceSessionSummaries, targetTemp) { summaries, target ->
            val ms = analyticsRepo.computeEstimatedHeatUpTime(target, summaries)
            if (ms != null) ms / 1000 else null
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Create a heat-up estimate flow that incorporates time-proximity and device temperature context. */
    fun estimatedHeatUpTimeSecsWithContext(
        targetTemp: StateFlow<Int>,
        bleViewModel: BleViewModel
    ): StateFlow<Long?> =
        combine(
            deviceSessionSummaries,
            targetTemp,
            bleViewModel.latestStatus
        ) { summaries, target, status ->
            // Calculate time since last session
            val recentSession = summaries.lastOrNull()
            val timeSinceLast = if (recentSession != null) {
                System.currentTimeMillis() - recentSession.endTimeMs
            } else null

            // Call enhanced estimation with time and temperature parameters
            val ms = analyticsRepo.computeEstimatedHeatUpTime(
                targetTempC = target,
                summaries = summaries,
                timeSinceLastSessionMs = timeSinceLast,
                currentDeviceTempC = status?.currentTempC
            )
            if (ms != null) ms / 1000 else null
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ── Combined history list ──

    val sessionHistory: StateFlow<List<HistoryItem>> =
        combine(sessionSummaries, rawChargeHistory, _sessionSort) { summaries, charges, sort ->
            val items = mutableListOf<HistoryItem>()
            summaries.mapTo(items) { HistoryItem.SessionItem(it) }
            charges.mapTo(items) { HistoryItem.ChargeItem(it) }
            when (sort) {
                SessionSort.DATE -> items.sortedByDescending { it.startTimeMs }
                SessionSort.HITS -> items.sortedByDescending {
                    if (it is HistoryItem.SessionItem) it.summary.hitCount else -1
                }
                SessionSort.DURATION -> items.sortedByDescending {
                    when (it) {
                        is HistoryItem.SessionItem -> it.summary.durationMs
                        is HistoryItem.ChargeItem -> it.cycle.durationMs
                    }
                }
                SessionSort.DRAIN -> items.sortedByDescending {
                    when (it) {
                        is HistoryItem.SessionItem -> it.summary.batteryConsumed
                        is HistoryItem.ChargeItem -> it.cycle.batteryGained
                    }
                }
                SessionSort.TEMP -> items.sortedByDescending {
                    if (it is HistoryItem.SessionItem) it.summary.peakTempC else -1
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Today summaries ──

    val todaySummaries: StateFlow<List<SessionSummary>> =
        combine(allSessionSummaries, dayStartHour) { summaries, startHour ->
            val c = java.util.Calendar.getInstance()
            if (c.get(java.util.Calendar.HOUR_OF_DAY) < startHour) c.add(java.util.Calendar.DAY_OF_YEAR, -1)
            c.set(java.util.Calendar.HOUR_OF_DAY, startHour)
            c.set(java.util.Calendar.MINUTE, 0)
            c.set(java.util.Calendar.SECOND, 0)
            c.set(java.util.Calendar.MILLISECOND, 0)
            val dayStartMs = c.timeInMillis
            summaries.filter { it.startTimeMs >= dayStartMs }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Analytics ──

    val profileStats: StateFlow<ProfileStats> =
        combine(deviceSessionSummaries, MutableStateFlow(0)) { summaries, runtime ->
            analyticsRepo.computeProfileStats(summaries, runtime)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileStats())

    /** Create profileStats that reacts to latestExtended runtime. */
    fun profileStats(heaterRuntime: StateFlow<Int>): StateFlow<ProfileStats> =
        combine(deviceSessionSummaries, heaterRuntime) { summaries, runtime ->
            analyticsRepo.computeProfileStats(summaries, runtime)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileStats())

    val historyStats: StateFlow<HistoryStats> =
        combine(sessionSummaries, dayStartHour) { summaries, startHour ->
            analyticsRepo.computeHistoryStats(summaries, startHour)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryStats())

    val usageInsights: StateFlow<UsageInsights> =
        combine(deviceSessionSummaries, dayStartHour) { summaries, startHour ->
            analyticsRepo.computeUsageInsights(summaries, startHour)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UsageInsights())

    val dailyStats: StateFlow<List<DailyStats>> =
        combine(deviceSessionSummaries, dayStartHour) { summaries, startHour ->
            analyticsRepo.computeDailyStats(summaries, startHour)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Graph ──

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

    val graphWindowStartMs: StateFlow<Long> = combine(_graphPeriod, dayStartHour) { period, startHour ->
        computeGraphWindowStart(period, startHour)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, computeGraphWindowStart(GraphPeriod.DAY, 4))

    @OptIn(ExperimentalCoroutinesApi::class)
    val graphStatuses: StateFlow<List<DeviceStatus>> =
        combine(_activeDevice, _graphPeriod, dayStartHour) { device, period, startHour ->
            Triple(device, period, startHour)
        }.flatMapLatest { (device, period, startHour) ->
            if (device == null) flowOf(emptyList())
            else db.deviceStatusDao().observeStatusForRange(
                device.deviceAddress,
                computeGraphWindowStart(period, startHour),
                System.currentTimeMillis() + 60_000L
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val recentStatuses: StateFlow<List<DeviceStatus>> = _activeDevice.flatMapLatest { device ->
        if (device != null)
            db.deviceStatusDao().observeRecentStatus(device.deviceAddress, 120)
        else
            flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Intake stats ──

    private val _intakeStats = MutableStateFlow(IntakeStats())
    val intakeStats: StateFlow<IntakeStats> = _intakeStats.asStateFlow()

    fun refreshIntakeStats(capsuleWeightGrams: Float, defaultIsCapsule: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val device = _activeDevice.value ?: return@withContext
                val serial = device.serialNumber.ifEmpty { null }
                val address = device.deviceAddress
                val sessions = db.sessionDao().getRecentSessions(serial ?: "", address, Int.MAX_VALUE)
                if (sessions.isEmpty()) return@withContext
                val summaries = sessions.mapNotNull { session ->
                    analyticsRepo.getSessionSummary(session)
                }
                val ids = summaries.map { it.id }
                val metaList = db.sessionMetadataDao().getMetadataForSessions(ids)
                val metaMap = metaList.associateBy { it.sessionId }
                val stats = analyticsRepo.computeIntakeStats(
                    summaries, metaMap, capsuleWeightGrams, defaultIsCapsule
                )
                _intakeStats.value = stats
            }
        }
    }

    // ── Session operations ──

    fun deleteSession(session: Session) {
        viewModelScope.launch {
            analyticsRepo.invalidateSession(session.id)
            db.hitDao().deleteHitsForSession(session.id)
            db.sessionDao().delete(session)
        }
    }

    fun reprocessHits(session: Session) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val statuses = db.deviceStatusDao().getStatusForRange(
                    session.deviceAddress, session.startTimeMs, session.endTimeMs
                )
                val newHits = HitDetector.detect(statuses).map { ph ->
                    Hit(
                        sessionId = session.id,
                        deviceAddress = session.deviceAddress,
                        startTimeMs = ph.startTimeMs,
                        durationMs = ph.durationMs,
                        peakTempC = ph.peakTempC
                    )
                }
                db.hitDao().deleteHitsForSession(session.id)
                if (newHits.isNotEmpty()) db.hitDao().insertAll(newHits)
                analyticsRepo.invalidateSession(session.id)
            }
        }
    }

    fun clearSessionHistory(device: BleViewModel.SavedDevice) {
        viewModelScope.launch {
            val addr = device.deviceAddress
            val serial = device.serialNumber
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

    // ── Session rebuild ──

    suspend fun rebuildSessionHistoryFromLogs() {
        withContext(Dispatchers.IO) {
            backcompileSessionsFromLogs(force = true)
        }
    }

    private suspend fun backcompileSessionsFromLogs(force: Boolean) {
        val addresses = db.deviceStatusDao().getAllDeviceAddresses()
        if (addresses.isEmpty()) return

        for (address in addresses) {
            val statuses = db.deviceStatusDao().getAllForAddress(address)
            if (statuses.isEmpty()) continue

            val serial = runCatching { db.deviceInfoDao().getByAddress(address)?.serialNumber }.getOrNull()

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

            if (force && inSession && lastOnMs > startMs) {
                commit(lastOnMs)
            }
        }

        analyticsRepo.clearCache()
    }

    // ── Helpers ──

    private fun loadLastDevice(): BleViewModel.SavedDevice? {
        val serial = devicePrefs.getString("last_serial", null) ?: return null
        val address = devicePrefs.getString("dev_${serial}_addr", null) ?: return null
        val type = devicePrefs.getString("dev_${serial}_type", "") ?: ""
        return BleViewModel.SavedDevice(serial, address, type)
    }
}
