package com.sbtracker.analytics

import com.sbtracker.data.Hit
import com.sbtracker.HitDetector
import com.sbtracker.data.AppDatabase
import com.sbtracker.data.ChargeCycle
import com.sbtracker.data.Session
import com.sbtracker.data.SessionMetadata
import com.sbtracker.data.SessionSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

/**
 * Single source of truth for all analytics computation in SBTracker.
 *
 * Responsibilities:
 *  1. Compute [SessionSummary] from the raw DB log with parallel queries.
 *  2. Cache those summaries so repeated emissions of the same session list
 *     are essentially free — only new or invalidated sessions hit the DB.
 *  3. Derive all aggregate analytics ([HistoryStats], [UsageInsights],
 *     [BatteryInsights], [PersonalRecords], [DailyStats]) from a list of
 *     already-computed summaries, keeping the computation pure and testable.
 *
 * Construction: pass the [AppDatabase] singleton. No Android context needed.
 *
 * Cache invalidation:
 *  - Call [invalidateSession] when a session's associated data changes
 *    (e.g., hits are re-detected after a BLE reconnect).
 *  - Call [clearCache] on a full history wipe.
 *
 * Thread safety: the cache is a [ConcurrentHashMap] so reads and writes from
 * multiple coroutines are safe. All DB-bound work is dispatched on [Dispatchers.IO].
 */
class AnalyticsRepository(private val db: AppDatabase) {

    // ── Session summary cache ─────────────────────────────────────────────────

    /**
     * In-memory cache of computed [SessionSummary] objects keyed by session ID.
     *
     * A summary is stable once written: hits are inserted once per session and
     * device_status rows are append-only, so there is no risk of a cached entry
     * becoming stale during normal operation. Explicit invalidation is provided
     * for the edge cases (hit re-detection, history clear).
     */
    private val cache = ConcurrentHashMap<Long, SessionSummary>()

    /** Force re-computation of a single session on the next access. */
    fun invalidateSession(sessionId: Long) {
        cache.remove(sessionId)
    }

    /** Drop the entire cache (call after a user-initiated history clear). */
    fun clearCache() {
        cache.clear()
    }

    /** Number of entries currently in the cache — exposed for diagnostics. */
    val cacheSize: Int get() = cache.size

    // ── Data retention ────────────────────────────────────────────────────────

    /**
     * Delete device_status rows older than [retentionDays] days.
     * Pass [Int.MAX_VALUE] (or use the "Never" sentinel) to skip pruning.
     */
    suspend fun pruneOldData(retentionDays: Int) {
        if (retentionDays == Int.MAX_VALUE) return
        val thresholdMs = System.currentTimeMillis() - retentionDays * 86_400_000L
        withContext(Dispatchers.IO) {
            db.deviceStatusDao().deleteRowsOlderThan(thresholdMs)
        }
    }

    // ── Session summary computation ───────────────────────────────────────────

    /**
     * Return the [SessionSummary] for [session], serving from cache when available.
     *
     * All eight DB queries are fired in parallel so a cache miss costs one
     * round-trip equivalent rather than eight sequential ones.
     *
     * Must be called from a coroutine; dispatches IO internally.
     */
    suspend fun getSessionSummary(session: Session): SessionSummary {
        cache[session.id]?.let { return it }

        return withContext(Dispatchers.IO) {
            coroutineScope {
                val hitStatsD       = async { db.hitDao().getHitStatsForSession(session.id) }
                val startBatD       = async { db.deviceStatusDao().getBatteryAtStart(session.deviceAddress, session.startTimeMs, session.endTimeMs) ?: 0 }
                val endBatD         = async { db.deviceStatusDao().getBatteryAtEnd(session.deviceAddress, session.startTimeMs, session.endTimeMs) ?: 0 }
                val avgTempD        = async { db.deviceStatusDao().getAvgTempForRange(session.deviceAddress, session.startTimeMs, session.endTimeMs)?.toInt() ?: 0 }
                val peakTempD       = async { db.deviceStatusDao().getPeakTempForRange(session.deviceAddress, session.startTimeMs, session.endTimeMs) ?: 0 }
                val firstSetpointD  = async { db.deviceStatusDao().getFirstSetpointReachedMs(session.deviceAddress, session.startTimeMs, session.endTimeMs) }
                // ExtendedData is now a singleton — historical snapshots are no longer available.
                // Heater wear per-session is computed in-memory by the SessionTracker at session end;
                // for older sessions without that data the delta will be 0.
                val startRuntimeD   = async { db.extendedDataDao().getHeaterRuntime(session.deviceAddress) ?: 0 }
                val endRuntimeD     = async { db.extendedDataDao().getHeaterRuntime(session.deviceAddress) ?: 0 }
                val metadataD       = async { db.sessionMetadataDao().getMetadataForSession(session.id) }

                val hitStats       = hitStatsD.await()
                val startBattery   = startBatD.await()
                val endBattery     = endBatD.await()
                val avgTempC       = avgTempD.await()
                val peakTempC      = peakTempD.await()
                val firstSetpointMs = firstSetpointD.await()
                val heatUpTimeMs   = if (firstSetpointMs != null) firstSetpointMs - session.startTimeMs else 0L
                val startRuntime   = startRuntimeD.await()
                val endRuntime     = endRuntimeD.await()
                val metadata       = metadataD.await()

                SessionSummary(
                    session           = session,
                    hitCount          = hitStats.hitCount,
                    totalHitDurationMs = hitStats.totalDurationMs,
                    startBattery      = startBattery,
                    endBattery        = endBattery,
                    avgTempC          = avgTempC,
                    peakTempC         = peakTempC,
                    heatUpTimeMs      = heatUpTimeMs,
                    heaterWearMinutes = (endRuntime - startRuntime).coerceAtLeast(0),
                    rating            = metadata?.rating,
                    notes             = metadata?.notes
                ).also { cache[session.id] = it }
            }
        }
    }

    /**
     * Batch version of [getSessionSummary].
     *
     * Fires one coroutine per session so cached hits return instantly while
     * uncached ones execute their DB queries concurrently. Preserves input order.
     */
    suspend fun getSessionSummaries(sessions: List<Session>): List<SessionSummary> =
        coroutineScope {
            sessions.map { session ->
                async(Dispatchers.IO) { getSessionSummary(session) }
            }.map { it.await() }
        }

    // ── History aggregate stats ───────────────────────────────────────────────

    /**
     * Compute [HistoryStats] from an arbitrary list of summaries.
     *
     * Pure function — no DB access. Pass the filter-aware summary list from
     * the ViewModel so filter changes are automatically reflected.
     */
    fun computeHistoryStats(
        summaries: List<SessionSummary>,
        dayStartHour: Int = 4
    ): HistoryStats {
        if (summaries.isEmpty()) return HistoryStats()

        val avgDuration    = summaries.sumOf { it.durationMs } / summaries.size / 1000
        val sortedDurSec   = summaries.map { it.durationMs / 1000 }.sorted()
        val medianDur      = if (sortedDurSec.size % 2 == 0)
            (sortedDurSec[sortedDurSec.size / 2 - 1] + sortedDurSec[sortedDurSec.size / 2]) / 2
        else
            sortedDurSec[sortedDurSec.size / 2]

        val totalHits    = summaries.sumOf { it.hitCount }
        val avgHits      = totalHits.toFloat() / summaries.size
        val totalHitMs   = summaries.sumOf { it.totalHitDurationMs }
        val avgHitDur    = if (totalHits > 0) (totalHitMs / 1000f) / totalHits else 0f
        val avgDrain     = summaries.sumOf { it.batteryConsumed }.toFloat() / summaries.size

        // Top-3 peak-temp buckets (rounded to nearest 5°C)
        val tempCounts = summaries
            .filter { it.peakTempC > 0 }
            .map { ((it.peakTempC + 2) / 5) * 5 }
            .groupingBy { it }.eachCount()
        val topTemps   = tempCounts.entries.sortedByDescending { it.value }.take(3)
            .map { it.key to it.value }

        val heatUpSessions = summaries.filter { it.heatUpTimeMs > 0 }
        val avgHeatUp      = if (heatUpSessions.isNotEmpty())
            heatUpSessions.sumOf { it.heatUpTimeMs } / heatUpSessions.size / 1000 else 0L

        val productiveSessions = summaries.filter { it.hitCount > 0 }
        val productiveCount    = productiveSessions.size
        val warmupOnlyCount    = summaries.count { it.hitCount == 0 && it.batteryConsumed > 0 }
        val productivePct      = (productiveCount * 100f) / summaries.size

        val bestEfficiencyTemp = summaries
            .filter { it.hitCount > 0 && it.batteryConsumed > 0 && it.peakTempC > 0 }
            .groupBy { ((it.peakTempC + 2) / 5) * 5 }
            .mapValues { (_, bucketSessions) ->
                val totalHits = bucketSessions.sumOf { it.hitCount }
                val totalDrain = bucketSessions.sumOf { it.batteryConsumed }
                val count = bucketSessions.size
                val ratio = if (totalDrain > 0) totalHits.toFloat() / totalDrain else 0f
                Triple(ratio, count, bucketSessions.sumOf { it.hitCount })
            }
            .filterValues { (_, count, totalHitsInBucket) -> count >= 2 && totalHitsInBucket >= 8 }
            .maxWithOrNull(
                compareBy<Map.Entry<Int, Triple<Float, Int, Int>>> { it.value.first }
                    .thenBy { it.value.second }
                    .thenBy { it.value.third }
            )
            ?.key

        val lowYieldTemp = summaries
            .filter { it.hitCount == 0 && it.batteryConsumed > 0 && it.peakTempC > 0 }
            .groupingBy { ((it.peakTempC + 2) / 5) * 5 }
            .eachCount()
            .filterValues { it >= 2 }
            .maxByOrNull { it.value }
            ?.key

        // Per-day session rates
        val nowMs       = System.currentTimeMillis()
        val in7d        = summaries.count { it.startTimeMs >= nowMs - 7L  * 24 * 3_600_000L }
        val in30d       = summaries.count { it.startTimeMs >= nowMs - 30L * 24 * 3_600_000L }
        val sessPerDay7d  = in7d  / 7f
        val sessPerDay30d = in30d / 30f

        // Peak sessions in a single calendar day (respects custom day-start hour)
        val perDay = summaries.groupingBy { epochDay(it.startTimeMs, dayStartHour) }.eachCount()
        val peakDay = perDay.values.maxOrNull() ?: 0

        return HistoryStats(
            sessionCount              = summaries.size,
            avgSessionDurationSec     = avgDuration,
            medianSessionDurationSec  = medianDur,
            avgHitsPerSession         = avgHits,
            avgHitDurationSec         = avgHitDur,
            avgBatteryDrainPct        = avgDrain,
            favoriteTempsCelsius      = topTemps,
            avgHeatUpTimeSec          = avgHeatUp,
            sessionsPerDay7d          = sessPerDay7d,
            sessionsPerDay30d         = sessPerDay30d,
            peakSessionsInADay        = peakDay,
            productiveSessionCount    = productiveCount,
            warmupOnlySessionCount    = warmupOnlyCount,
            productiveSessionPct      = productivePct,
            bestEfficiencyTempC       = bestEfficiencyTemp,
            lowYieldTemp              = lowYieldTemp
        )
    }

    /**
     * Compute the average battery drain percentage per minute of heater-on time across [summaries].
     * Excludes sessions with no battery data or zero duration to prevent noise.
     */
    fun computeAvgDrainPerMinute(summaries: List<SessionSummary>): Float {
        val sessionsWithDrain = summaries.filter { it.batteryConsumed > 0 && it.durationMs > 0 }
        if (sessionsWithDrain.isEmpty()) return 0f

        val totalDrain = sessionsWithDrain.sumOf { it.batteryConsumed }
        val totalMinutes = sessionsWithDrain.sumOf { it.durationMs } / 60_000.0
        return if (totalMinutes > 0) (totalDrain / totalMinutes).toFloat() else 0f
    }

    // ── Pre-session estimates ─────────────────────────────────────────────────

    /**
     * Estimates the time to initial heat based on recent historical sessions with a similar target temperature.
     * Incorporates weighting for time since last session and adjustments for current device temperature.
     * We use `peakTempC` as a proxy for the target temperature.
     *
     * @param targetTempC The setpoint temperature the device is heating towards.
     * @param summaries The list of available historical session summaries.
     * @param timeSinceLastSessionMs Time in ms since the previous session ended.
     * @param currentDeviceTempC The current temperature of the device (optional).
     */
    fun computeEstimatedHeatUpTime(
        targetTempC: Int, 
        summaries: List<SessionSummary>,
        timeSinceLastSessionMs: Long? = null,
        currentDeviceTempC: Int? = null
    ): Long? {
        // Filter for sessions with valid heat-up data and similar target temp (+/- 10C)
        val similarSessions = summaries.filter { 
            it.heatUpTimeMs > 0 && Math.abs(it.peakTempC - targetTempC) <= 10 
        }
        if (similarSessions.isEmpty()) return null
        
        // 1. Calculate time-based speed factor from the current state
        // Weight values 1.2, 1.0, 0.9 are speed factors (higher = faster heat-up = less time)
        val timeFactor = if (timeSinceLastSessionMs != null) {
            when {
                timeSinceLastSessionMs <= 5 * 60 * 1000 -> 1.2    // back-to-back boost
                timeSinceLastSessionMs <= 30 * 60 * 1000 -> 1.0   // baseline
                else -> 0.9                                       // extra cooling time
            }
        } else 1.0

        // 2. Select recent similar sessions, preferring those that started at similar temperatures
        val selectedSessions = if (currentDeviceTempC != null && currentDeviceTempC > 0) {
            // Since SessionSummary doesn't store start temp, we estimate it from the previous session's peak
            // if it happened within 10 minutes; otherwise we assume ambient (25C).
            val allSorted = summaries.sortedBy { it.startTimeMs }
            val startTempMap = mutableMapOf<Long, Int>()
            var lastS: SessionSummary? = null
            for (s in allSorted) {
                val estStart = if (lastS != null && (s.startTimeMs - lastS.endTimeMs) < 10 * 60 * 1000) {
                    lastS.peakTempC
                } else 25
                startTempMap[s.id] = estStart
                lastS = s
            }

            val preferred = similarSessions.filter { 
                val start = startTempMap[it.id] ?: 25
                Math.abs(start - currentDeviceTempC) <= 15
            }

            if (preferred.isNotEmpty()) {
                // Merge and prioritize preferred sessions, while keeping recency in the take(5)
                (preferred.sortedByDescending { it.startTimeMs } + similarSessions.sortedByDescending { it.startTimeMs })
                    .distinctBy { it.id }
                    .take(5)
            } else {
                similarSessions.sortedByDescending { it.startTimeMs }.take(5)
            }
        } else {
            similarSessions.sortedByDescending { it.startTimeMs }.take(5)
        }

        if (selectedSessions.isEmpty()) return null
        
        // 3. Compute baseline average and apply state-based weighting
        // "Apply this weight to each session's heat-up time before averaging"
        val weightedSum = selectedSessions.sumOf { (it.heatUpTimeMs / timeFactor).toLong() }
        var resultMs = weightedSum / selectedSessions.size

        // 4. Apply temperature-proximity adjustment (boost)
        // Reduce the estimated time by 10% per 20°C of temperature delta already "covered" above ambient.
        if (currentDeviceTempC != null && currentDeviceTempC > 0) {
            val ambient = 25.0
            val deltaCovered = (currentDeviceTempC.toDouble() - ambient).coerceAtLeast(0.0)
            val reductionSteps = deltaCovered / 20.0
            val reductionFactor = (1.0 - (reductionSteps * 0.10)).coerceIn(0.1, 1.0)
            resultMs = (resultMs * reductionFactor).toLong()
        }

        return resultMs
    }

    // ── Usage insights ────────────────────────────────────────────────────────

    /**
     * Compute behavioral patterns and trends from [summaries].
     *
     * Pure function — no DB access. Always reflects the active device's full
     * history (not filter-aware by design, so streak/pattern data is complete).
     */
    fun computeUsageInsights(
        summaries: List<SessionSummary>,
        dayStartHour: Int = 4
    ): UsageInsights {
        if (summaries.isEmpty()) return UsageInsights()

        val activeDays = summaries.map { epochDay(it.startTimeMs, dayStartHour) }.toSortedSet()

        // Current streak: consecutive days ending today (or yesterday if today has no session yet)
        val todayEpoch = epochDay(System.currentTimeMillis(), dayStartHour)
        var current = 0; var d = todayEpoch
        while (activeDays.contains(d)) { current++; d-- }

        // Longest streak ever
        val dayList = activeDays.toList()
        var longest = if (dayList.isNotEmpty()) 1 else 0
        var run = 1
        for (i in 1 until dayList.size) {
            if (dayList[i] - dayList[i - 1] == 1L) { run++; if (run > longest) longest = run }
            else run = 1
        }

        // Rolling weekly comparison
        val now    = System.currentTimeMillis()
        val weekMs = 7L * 24 * 3_600_000L
        val thisWeek = summaries.filter { it.startTimeMs >= now - weekMs }
        val lastWeek = summaries.filter { it.startTimeMs in (now - 2 * weekMs) until (now - weekMs) }

        // Time-of-day buckets (Night/Morning/Afternoon/Evening)
        val tod = MutableList(4) { 0 }
        for (s in summaries) {
            val h = Calendar.getInstance().apply { timeInMillis = s.startTimeMs }.get(Calendar.HOUR_OF_DAY)
            tod[when { h < 6 -> 0; h < 12 -> 1; h < 18 -> 2; else -> 3 }]++
        }
        val peakTod = tod.indices.maxByOrNull { tod[it] } ?: -1

        // Day-of-week buckets (0=Mon … 6=Sun)
        val dow = MutableList(7) { 0 }
        for (s in summaries) {
            val raw = Calendar.getInstance().apply { timeInMillis = s.startTimeMs }.get(Calendar.DAY_OF_WEEK)
            dow[(raw - Calendar.MONDAY + 7) % 7]++
        }
        val busiestDow = dow.indices.maxByOrNull { dow[it] } ?: -1

        // Efficiency: average hits per minute of heater-on time
        val totalHits  = summaries.sumOf { it.hitCount }
        val totalMin   = summaries.sumOf { it.durationMs } / 60_000.0
        val hitsPerMin = if (totalMin > 0) (totalHits / totalMin).toFloat() else 0f

        val totalDaysActive = activeDays.size
        val avgSessPerDay   = if (totalDaysActive > 0) summaries.size.toFloat() / totalDaysActive else 0f

        return UsageInsights(
            currentStreakDays    = current,
            longestStreakDays    = longest,
            sessionsThisWeek    = thisWeek.size,
            sessionsLastWeek    = lastWeek.size,
            hitsThisWeek        = thisWeek.sumOf { it.hitCount },
            hitsLastWeek        = lastWeek.sumOf { it.hitCount },
            peakTimeOfDay       = peakTod,
            sessionsByTimeOfDay = tod,
            busiestDayOfWeek    = busiestDow,
            sessionsByDayOfWeek = dow,
            avgHitsPerMinute    = hitsPerMin,
            avgSessionsPerDay   = avgSessPerDay,
            totalDaysActive     = totalDaysActive
        )
    }

    // ── Battery insights ──────────────────────────────────────────────────────

    /**
     * Compute battery-focused analytics from sessions and charge cycles.
     * Pure function — no DB access.
     */
    fun computeBatteryInsights(
        summaries: List<SessionSummary>,
        charges: List<ChargeCycle>,
        dayStartHour: Int = 4
    ): BatteryInsights {
        val drains      = summaries.map { it.batteryConsumed }.filter { it > 0 }
        val allTimeAvg  = if (drains.isNotEmpty()) drains.average().toFloat() else 0f
        val recent5     = drains.takeLast(5)
        val recentAvg   = if (recent5.isNotEmpty()) recent5.average().toFloat() else 0f

        val trend = if (drains.size >= 10) {
            drains.takeLast(5).average().toFloat() - drains.takeLast(10).take(5).average().toFloat()
        } else 0f

        val drainSd = if (drains.size >= 2) {
            val mean     = drains.average()
            val variance = drains.sumOf { (it - mean) * (it - mean) } / drains.size
            sqrt(variance).toFloat()
        } else 0f

        val medianDrain = if (drains.isNotEmpty()) {
            val sorted = drains.sorted()
            if (sorted.size % 2 == 0)
                (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2f
            else
                sorted[sorted.size / 2].toFloat()
        } else 0f

        val completedCharges = charges.filter { it.batteryGained > 0 }

        // Sessions per charge cycle: bracket sessions between charge end times
        val sessPerCycle = if (completedCharges.size >= 2) {
            val sortedCharges = completedCharges.sortedBy { it.endTimeMs }
            val windows = sortedCharges.zipWithNext { a, b -> a.endTimeMs to b.endTimeMs }
            val sessInWindows = windows.map { (start, end) ->
                summaries.count { it.startTimeMs in start until end }
            }.filter { it > 0 }
            if (sessInWindows.isNotEmpty()) sessInWindows.average().toFloat() else 0f
        } else 0f

        val avgChargeDurMin = if (completedCharges.isNotEmpty())
            (completedCharges.sumOf { it.durationMs } / completedCharges.size / 60_000L).toInt()
        else 0

        val avgGained = if (completedCharges.isNotEmpty())
            completedCharges.sumOf { it.batteryGained }.toFloat() / completedCharges.size
        else 0f

        // Longest run: max sessions between consecutive charges
        val longestRun = if (completedCharges.isEmpty()) {
            summaries.size
        } else {
            val sortedCharges = completedCharges.sortedBy { it.endTimeMs }
            val boundaries    = listOf(Long.MIN_VALUE) + sortedCharges.map { it.endTimeMs }
            boundaries.zipWithNext { start, end ->
                summaries.count { it.startTimeMs in start until end }
            }.maxOrNull() ?: 0
        }

        val avgDepth = if (completedCharges.isNotEmpty())
            completedCharges.sumOf { 100 - it.startBattery }.toFloat() / completedCharges.size
        else 0f

        // Projected days per cycle at current pace
        val avgDaysPerCycle: Float? = if (sessPerCycle > 0 && drains.isNotEmpty()) {
            val avgDrainPerSess = drains.average().toFloat()
            val sessUntilEmpty  = if (avgDrainPerSess > 0) 100f / avgDrainPerSess else null
            val now             = System.currentTimeMillis()
            val weekMs          = 7L * 24 * 3_600_000L
            val recentSessCount = summaries.count { it.startTimeMs >= now - weekMs }
            val sessPerDay      = recentSessCount / 7f
            if (sessUntilEmpty != null && sessPerDay > 0) sessUntilEmpty / sessPerDay else null
        } else null

        return BatteryInsights(
            allTimeAvgDrain       = allTimeAvg,
            recentAvgDrain        = recentAvg,
            drainTrend            = trend,
            drainStdDev           = drainSd,
            medianDrain           = medianDrain,
            sessionsPerChargeCycle = sessPerCycle,
            avgChargeDurationMin  = avgChargeDurMin,
            avgBatteryGainedPct   = avgGained,
            longestRunSessions    = longestRun,
            avgDepthOfDischarge   = avgDepth,
            avgDaysPerChargeCycle = avgDaysPerCycle
        )
    }

    // ── Personal records ──────────────────────────────────────────────────────

    /**
     * Compute all-time personal bests across [summaries].
     * Pure function — no DB access.
     */
    fun computePersonalRecords(summaries: List<SessionSummary>): PersonalRecords {
        if (summaries.isEmpty()) return PersonalRecords()

        val mostHits      = summaries.maxByOrNull { it.hitCount }
        val longest       = summaries.maxByOrNull { it.durationMs }
        val mostEfficient = summaries.filter { it.batteryConsumed > 0 && it.hitCount > 0 }
            .minByOrNull { it.batteryConsumed.toFloat() / it.hitCount }
        val hottest       = summaries.filter { it.peakTempC > 0 }.maxByOrNull { it.peakTempC }
        val fastestHeatUp = summaries.filter { it.heatUpTimeMs > 0 }.minByOrNull { it.heatUpTimeMs }

        return PersonalRecords(
            mostHitsSession      = mostHits,
            longestSession       = longest,
            mostEfficientSession = mostEfficient,
            hottestSession       = hottest,
            fastestHeatUpSession = fastestHeatUp,
            maxHitsEver          = mostHits?.hitCount ?: 0,
            maxDurationMs        = longest?.durationMs ?: 0,
            minDrainPct          = mostEfficient?.batteryConsumed ?: 0,
            maxPeakTempC         = hottest?.peakTempC ?: 0,
            fastestHeatUpMs      = fastestHeatUp?.heatUpTimeMs ?: 0
        )
    }

    // ── Daily time-series aggregation ─────────────────────────────────────────

    /**
     * Aggregate [summaries] into per-day buckets suitable for charting.
     *
     * Returns a list ordered chronologically (oldest first) covering only days
     * that have at least one session — there are no empty-day filler entries.
     * UI code that needs to fill gaps for a continuous x-axis can do so by
     * iterating over the returned list and inserting zeroed entries where needed.
     *
     * Pure function — no DB access.
     */
    fun computeDailyStats(
        summaries: List<SessionSummary>,
        dayStartHour: Int = 4
    ): List<DailyStats> {
        if (summaries.isEmpty()) return emptyList()

        // Group sessions by their epoch-day key
        data class Accumulator(
            val sessions: MutableList<SessionSummary> = mutableListOf()
        )

        val grouped = LinkedHashMap<Long, Accumulator>()
        for (s in summaries.sortedBy { it.startTimeMs }) {
            val key = epochDay(s.startTimeMs, dayStartHour)
            grouped.getOrPut(key) { Accumulator() }.sessions.add(s)
        }

        return grouped.map { (epochKey, acc) ->
            val sessions      = acc.sessions
            val totalHits     = sessions.sumOf { it.hitCount }
            val totalDurMs    = sessions.sumOf { it.durationMs }
            val totalDrain    = sessions.sumOf { it.batteryConsumed }
            val avgDrain      = if (sessions.isNotEmpty()) totalDrain.toFloat() / sessions.size else 0f
            val tempsWithData = sessions.filter { it.peakTempC > 0 }
            val avgPeakTemp   = if (tempsWithData.isNotEmpty())
                tempsWithData.sumOf { it.peakTempC } / tempsWithData.size else 0

            DailyStats(
                dayStartMs          = epochKey * 24 * 3_600_000L,
                sessionCount        = sessions.size,
                totalHits           = totalHits,
                totalDurationMs     = totalDurMs,
                avgBatteryDrainPct  = avgDrain,
                avgPeakTempC        = avgPeakTemp
            )
        }
    }

    // ── Profile totals ────────────────────────────────────────────────────────

    /**
     * Lightweight lifetime totals for the profile card.
     * Pure function — no DB access.
     */
    fun computeProfileStats(
        summaries: List<SessionSummary>,
        lifetimeHeaterMinutes: Int
    ): ProfileStats = ProfileStats(
        totalSessions         = summaries.size,
        totalHits             = summaries.sumOf { it.hitCount },
        lifetimeHeaterMinutes = lifetimeHeaterMinutes
    )

    // ── Intake / dosage analytics ─────────────────────────────────────────────

    /**
     * Compute intake statistics from session summaries and their metadata.
     *
     * NOTE: B-013 — Sessions created before F-018 (Health & Dosage) lack SessionMetadata rows.
     * These sessions default to free-pack (isCapsule=false) unless metadata was backfilled.
     * Users with early history will see understated intake totals for old sessions.
     * Solution: Provide UI to manually review and correct pre-F-018 sessions.
     *
     * @param summaries All session summaries for the active device (or filtered set).
     * @param metadataMap Map of sessionId → SessionMetadata for those sessions.
     * @param defaultWeightGrams Global fallback capsule weight when per-session weight is 0.
     */
    fun computeIntakeStats(
        summaries: List<SessionSummary>,
        metadataMap: Map<Long, SessionMetadata>,
        defaultWeightGrams: Float,
        defaultIsCapsule: Boolean = false
    ): IntakeStats {
        if (summaries.isEmpty()) return IntakeStats()

        val nowMs = System.currentTimeMillis()
        val weekAgoMs  = nowMs - 7L  * 24 * 60 * 60 * 1000
        val monthAgoMs = nowMs - 30L * 24 * 60 * 60 * 1000

        var totalAll   = 0f
        var totalWeek  = 0f
        var totalMonth = 0f
        var capsuleCount   = 0
        var freePackCount  = 0

        for (summary in summaries) {
            val meta = metadataMap[summary.id]
            val isCapsule = meta?.isCapsule ?: defaultIsCapsule

            if (isCapsule) {
                val weight = meta?.capsuleWeightGrams?.takeIf { it > 0f } ?: defaultWeightGrams
                capsuleCount++
                totalAll += weight
                if (summary.startTimeMs >= weekAgoMs)  totalWeek  += weight
                if (summary.startTimeMs >= monthAgoMs) totalMonth += weight
            } else {
                freePackCount++
            }
        }

        return IntakeStats(
            totalGramsAllTime      = totalAll,
            totalGramsThisWeek     = totalWeek,
            totalGramsThisMonth    = totalMonth,
            capsuleSessionCount    = capsuleCount,
            freePackSessionCount   = freePackCount,
            avgGramsPerSession     = if (capsuleCount > 0) totalAll / capsuleCount else 0f,
            gramsPerDay7d          = totalWeek  / 7f,
            gramsPerDay30d         = totalMonth / 30f
        )
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Map a timestamp to its "session epoch-day" integer, adjusted so that days
     * begin at [dayStartHour] rather than midnight.
     *
     * Two timestamps that fall in the same logical day share the same return value,
     * even if they straddle a calendar midnight.
     */
    private fun epochDay(ms: Long, dayStartHour: Int): Long {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        if (c.get(Calendar.HOUR_OF_DAY) < dayStartHour) c.add(Calendar.DAY_OF_YEAR, -1)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis / (24 * 3_600_000L)
    }
    
    suspend fun rebuildSessionHistory(db: AppDatabase) {
        withContext(Dispatchers.IO) {
            val addresses = db.deviceStatusDao().getAllDeviceAddresses()
            if (addresses.isEmpty()) return@withContext

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

                if (inSession && lastOnMs > startMs) {
                    commit(lastOnMs)
                }
            }

            clearCache()
        }
    }

    suspend fun rebuildChargeHistory(db: AppDatabase) {
        withContext(Dispatchers.IO) {
            val addresses = db.deviceStatusDao().getAllDeviceAddresses()
            if (addresses.isEmpty()) return@withContext

            val chargeEndGraceMs = 60_000L
            val minChargeDurationMs = 60_000L

            for (address in addresses) {
                val statuses = db.deviceStatusDao().getAllForAddress(address)
                if (statuses.isEmpty()) continue

                val serial = runCatching { db.deviceInfoDao().getByAddress(address)?.serialNumber }.getOrNull()

                var charging = false
                var chargeStartMs = 0L
                var chargeStartBattery = 0
                var chargeEndPendingMs = -1L
                var chargeEndBattery = 0
                var startChargeVoltageLimit = false
                var startChargeCurrentOpt = false

                suspend fun commit(endMs: Long, endBattery: Int) {
                    val durationMs = endMs - chargeStartMs
                    val batteryGained = (endBattery - chargeStartBattery).coerceAtLeast(0)
                    if (durationMs < minChargeDurationMs || batteryGained <= 0) return
                    if (db.chargeCycleDao().findExistingCycleNear(address, chargeStartMs) != null) return

                    val avgRate = batteryGained / (durationMs / 60_000f)
                    db.chargeCycleDao().insert(
                        ChargeCycle(
                            deviceAddress = address,
                            serialNumber = serial,
                            startTimeMs = chargeStartMs,
                            endTimeMs = endMs,
                            startBattery = chargeStartBattery,
                            endBattery = endBattery,
                            avgRatePctPerMin = avgRate,
                            chargeVoltageLimit = startChargeVoltageLimit,
                            chargeCurrentOptimization = startChargeCurrentOpt
                        )
                    )
                }

                for (status in statuses) {
                    if (!charging && status.isCharging) {
                        charging = true
                        chargeStartMs = status.timestampMs
                        chargeStartBattery = status.batteryLevel
                        chargeEndPendingMs = -1L
                        chargeEndBattery = status.batteryLevel
                        startChargeVoltageLimit = status.chargeVoltageLimit
                        startChargeCurrentOpt = status.chargeCurrentOptimization
                        continue
                    }

                    if (!charging) continue

                    if (status.isCharging) {
                        chargeEndPendingMs = -1L
                        chargeEndBattery = status.batteryLevel
                    } else {
                        if (chargeEndPendingMs == -1L) {
                            chargeEndPendingMs = status.timestampMs
                            chargeEndBattery = status.batteryLevel
                        }

                        if (status.timestampMs - chargeEndPendingMs >= chargeEndGraceMs) {
                            commit(chargeEndPendingMs, chargeEndBattery)
                            charging = false
                            chargeStartMs = 0L
                            chargeStartBattery = 0
                            chargeEndPendingMs = -1L
                            chargeEndBattery = 0
                        }
                    }
                }

                if (charging && chargeEndBattery > chargeStartBattery) {
                    commit(statuses.last().timestampMs, chargeEndBattery)
                }
            }
        }
    }
}
