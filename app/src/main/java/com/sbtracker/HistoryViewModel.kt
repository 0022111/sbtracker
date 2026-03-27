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
import com.sbtracker.util.StreakUtils
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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSessionSort(sort: SessionSort) { _sessionSort.value = sort }
    fun setSessionFilter(filter: String?) { _sessionFilter.value = filter }
    fun setSearchQuery(query: String) { _searchQuery.value = query }

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

    /**
     * Maps each session ID in the current history to its [SessionMetadata].
     * Capsule weight fallback: when [SessionMetadata.capsuleWeightGrams] is 0, the global
     * [UserPreferences.capsuleWeightGrams] preference is substituted so adapters always
     * receive a usable weight value.
     */
    val sessionMetadataMap: StateFlow<Map<Long, SessionMetadata>> =
        combine(sessionSummaries, prefsRepo.userPreferencesFlow) { summaries, prefs ->
            val ids = summaries.map { it.id }
            if (ids.isEmpty()) return@combine emptyMap()
            val rawList = withContext(Dispatchers.IO) {
                db.sessionMetadataDao().getMetadataForSessions(ids)
            }
            rawList.associate { meta ->
                val effectiveWeight = if (meta.capsuleWeightGrams == 0f)
                    prefs.capsuleWeightGrams
                else
                    meta.capsuleWeightGrams
                meta.sessionId to meta.copy(capsuleWeightGrams = effectiveWeight)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

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
    
    val avgDrainPerMinute: StateFlow<Float> = deviceSessionSummaries
        .map { summaries -> analyticsRepo.computeAvgDrainPerMinute(summaries) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

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

    val filteredSessionHistory: StateFlow<List<HistoryItem>> =
        combine(sessionHistory, _searchQuery) { items, query ->
            if (query.isBlank()) items
            else {
                val q = query.trim().lowercase()
                items.filter { item ->
                    when (item) {
                        is HistoryItem.SessionItem -> {
                            val s = item.summary
                            val sdf = java.text.SimpleDateFormat("MMM dd HH:mm", java.util.Locale.getDefault())
                            val dateStr = sdf.format(java.util.Date(s.startTimeMs)).lowercase()
                            dateStr.contains(q) ||
                            (s.serialNumber?.lowercase()?.contains(q) == true) ||
                            (s.notes?.lowercase()?.contains(q) == true)
                        }
                        is HistoryItem.ChargeItem -> true
                    }
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

    // ── Streak & Break ──

    val currentStreak: StateFlow<Int> = allSessionSummaries
        .map { StreakUtils.currentStreak(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val longestStreak: StateFlow<Int> = allSessionSummaries
        .map { StreakUtils.longestStreak(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val daysSinceLastSession: StateFlow<Int> = allSessionSummaries
        .map { StreakUtils.daysSinceLastSession(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1)

    val breakGoalDays: StateFlow<Int> = prefsRepo.userPreferencesFlow
        .map { it.breakGoalDays }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 7)

    val breakProgress: StateFlow<Float> =
        combine(allSessionSummaries, breakGoalDays) { summaries, goal ->
            StreakUtils.breakProgress(summaries, goal)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

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

    // ── Extracted usage pattern signals ──

    val currentStreakDaysFlow: StateFlow<Int> = usageInsights
        .map { it.currentStreakDays }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val longestStreakDaysFlow: StateFlow<Int> = usageInsights
        .map { it.longestStreakDays }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val peakTimeOfDayLabel: StateFlow<String> = usageInsights
        .map { insights ->
            when (insights.peakTimeOfDay) {
                0 -> "Night (12am–6am)"
                1 -> "Morning (6am–12pm)"
                2 -> "Afternoon (12pm–6pm)"
                3 -> "Evening (6pm–12am)"
                else -> "No data"
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "No data")

    val busiestDayOfWeekLabel: StateFlow<String> = usageInsights
        .map { insights ->
            val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
            if (insights.busiestDayOfWeek in 0..6) days[insights.busiestDayOfWeek] else "No data"
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "No data")

    val avgHitsPerMinuteFlow: StateFlow<Float> = usageInsights
        .map { it.avgHitsPerMinute }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val totalDaysActiveFlow: StateFlow<Int> = usageInsights
        .map { it.totalDaysActive }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // ── Extracted personal record signals ──

    val longestSessionDurationMs: StateFlow<Long?> = combine(allSessionSummaries) { summaries ->
        summaries.maxByOrNull { it.durationMs }?.durationMs
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val mostHitsInSession: StateFlow<Int> = combine(allSessionSummaries) { summaries ->
        summaries.maxOfOrNull { it.hitCount } ?: 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val favoriteTempCelsius: StateFlow<Int?> = historyStats
        .map { stats ->
            stats.favoriteTempsCelsius.firstOrNull()?.first
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val peakSessionsInADay: StateFlow<Int> = historyStats
        .map { it.peakSessionsInADay }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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
            db.sessionMetadataDao().clearAllForDevice(addr)   // must precede sessions delete
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
                append("ID,Device,Date,Duration(ms),Hits,TotalHitMs,StartBat,EndBat,BatConsumed,AvgTempC,PeakTempC,HeatUpMs,HeaterWearMin,Rating,Notes\n")
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
                    append("${s.heaterWearMinutes},")
                    append("${s.rating ?: ""},")
                    val notesEscaped = s.notes?.replace("\"", "\"\"") ?: ""
                    append("\"$notesEscaped\"\n")
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
        analyticsRepo.rebuildSessionHistory(db)
    }

    // ── Helpers ──

    private fun loadLastDevice(): BleViewModel.SavedDevice? {
        val serial = devicePrefs.getString("last_serial", null) ?: return null
        val address = devicePrefs.getString("dev_${serial}_addr", null) ?: return null
        val type = devicePrefs.getString("dev_${serial}_type", "") ?: ""
        return BleViewModel.SavedDevice(serial, address, type)
    }
}
