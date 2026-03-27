package com.sbtracker.analytics

import com.sbtracker.data.SessionSummary

// ─────────────────────────────────────────────────────────────────────────────
// Analytics data models
//
// All analytics types live here so that AnalyticsRepository, MainViewModel,
// and any future feature (TrendsViewModel, widgets, etc.) share the same
// definitions without coupling to each other.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Aggregate stats derived from a filtered list of [SessionSummary] objects.
 * Drives the "History Stats" card on the history screen.
 */
data class HistoryStats(
    val sessionCount: Int = 0,
    val avgSessionDurationSec: Long = 0,
    /** Median session duration in seconds (more robust than mean for skewed data). */
    val medianSessionDurationSec: Long = 0,
    val avgHitsPerSession: Float = 0f,
    val avgHitDurationSec: Float = 0f,
    val avgBatteryDrainPct: Float = 0f,
    /** Top-3 peak-temp buckets (rounded to nearest 5°C) with session counts, descending. */
    val favoriteTempsCelsius: List<Pair<Int, Int>> = emptyList(),
    /** Average time from heater-on to setpoint reached, in seconds. 0 if no data. */
    val avgHeatUpTimeSec: Long = 0,
    /** Sessions per day averaged over the last 7 days of data. */
    val sessionsPerDay7d: Float = 0f,
    /** Sessions per day averaged over the last 30 days of data. */
    val sessionsPerDay30d: Float = 0f,
    /** Maximum sessions recorded in a single day. */
    val peakSessionsInADay: Int = 0,
    /** Sessions with at least one detected hit. */
    val productiveSessionCount: Int = 0,
    /** Sessions that consumed battery but never registered a hit. */
    val warmupOnlySessionCount: Int = 0,
    /** Productive sessions as a share of total history. */
    val productiveSessionPct: Float = 0f,
    /** Peak-temp bucket with the best average hits-per-battery-spent ratio. */
    val bestEfficiencyTempC: Int? = null,
    /** Peak-temp bucket with the most repeated warmup-only sessions. */
    val lowYieldTemp: Int? = null
)

/**
 * Behavioral patterns and trends computed from the device's full session history.
 * Not filter-aware — always reflects the active device.
 */
data class UsageInsights(
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val sessionsThisWeek: Int = 0,
    val sessionsLastWeek: Int = 0,
    val hitsThisWeek: Int = 0,
    val hitsLastWeek: Int = 0,
    /** 0=Night(0–5am), 1=Morning(6–11am), 2=Afternoon(12–5pm), 3=Evening(6–11pm); -1 = no data. */
    val peakTimeOfDay: Int = -1,
    val sessionsByTimeOfDay: List<Int> = List(4) { 0 },
    /** 0=Mon…6=Sun; -1 = no data. */
    val busiestDayOfWeek: Int = -1,
    val sessionsByDayOfWeek: List<Int> = List(7) { 0 },
    /** Average hits per minute of session time across all sessions. */
    val avgHitsPerMinute: Float = 0f,
    /** Average sessions per active day (total sessions / distinct days with at least one session). */
    val avgSessionsPerDay: Float = 0f,
    /** Total distinct days with at least one session. */
    val totalDaysActive: Int = 0
)

/**
 * Battery-focused analytics derived from sessions and charge cycles.
 */
data class BatteryInsights(
    /** Lifetime average battery consumed per session (%). */
    val allTimeAvgDrain: Float = 0f,
    /** Average drain across the last 5 sessions. */
    val recentAvgDrain: Float = 0f,
    /** Change in avg drain: last-5 minus prev-5. +ve = drain increasing (battery aging). */
    val drainTrend: Float = 0f,
    /** Population std deviation of per-session drain values (%). */
    val drainStdDev: Float = 0f,
    /** Median battery consumed per session (%). More robust than mean for skewed distributions. */
    val medianDrain: Float = 0f,
    /** Average sessions completed per charge cycle. */
    val sessionsPerChargeCycle: Float = 0f,
    /** Average duration of a charge cycle, in minutes. */
    val avgChargeDurationMin: Int = 0,
    /** Average percentage gained per charge cycle. */
    val avgBatteryGainedPct: Float = 0f,
    /** Maximum sessions completed in a row without an intervening charge. */
    val longestRunSessions: Int = 0,
    /** How depleted the battery gets on average before a charge begins (100 − avgChargeStartPct). */
    val avgDepthOfDischarge: Float = 0f,
    /**
     * Average days a charge cycle lasts at the user's current session pace.
     * Null when there is insufficient data.
     */
    val avgDaysPerChargeCycle: Float? = null
)

/**
 * All-time best stats across all sessions for the active device.
 * Each nullable field is null when there is insufficient data (e.g. no sessions with hits).
 */
data class PersonalRecords(
    /** Session with the most hits ever recorded. */
    val mostHitsSession: SessionSummary? = null,
    /** Session with the longest duration. */
    val longestSession: SessionSummary? = null,
    /** Session with the lowest battery drain (most efficient use of a charge). */
    val mostEfficientSession: SessionSummary? = null,
    /** Session with the highest peak temperature. */
    val hottestSession: SessionSummary? = null,
    /** Session with the fastest heat-up time (lowest non-zero heatUpTimeMs). */
    val fastestHeatUpSession: SessionSummary? = null,
    /** Raw best values — useful for progress bars / comparisons. */
    val maxHitsEver: Int = 0,
    val maxDurationMs: Long = 0,
    val minDrainPct: Int = 0,
    val maxPeakTempC: Int = 0,
    val fastestHeatUpMs: Long = 0
)

/**
 * Aggregated usage stats for a single "day" window (respects the custom day-start hour).
 * Used for rendering time-series charts (bar charts, trend lines).
 */
data class DailyStats(
    /** Epoch ms of the start of this day window (adjusted for custom day-start hour). */
    val dayStartMs: Long,
    val sessionCount: Int,
    val totalHits: Int,
    val totalDurationMs: Long,
    val avgBatteryDrainPct: Float,
    /** Average peak temp (°C) across sessions that have temp data. 0 if none. */
    val avgPeakTempC: Int
)

/**
 * Lifetime profile totals for the profile card.
 */
data class ProfileStats(
    val totalSessions: Int = 0,
    val totalHits: Int = 0,
    val lifetimeHeaterMinutes: Int = 0
)

/**
 * Dosage and intake analytics derived from session metadata.
 * Uses a global default capsule weight; per-session overrides are respected
 * when [SessionMetadata.capsuleWeightGrams] is non-zero.
 */
data class IntakeStats(
    /** Total grams consumed across all capsule sessions (all time). */
    val totalGramsAllTime: Float = 0f,
    /** Total grams consumed in the last 7 days. */
    val totalGramsThisWeek: Float = 0f,
    /** Total grams consumed in the last 30 days. */
    val totalGramsThisMonth: Float = 0f,
    /** Number of sessions marked as capsule type. */
    val capsuleSessionCount: Int = 0,
    /** Number of sessions marked as free-pack type. */
    val freePackSessionCount: Int = 0,
    /** Average grams per capsule session (0 if no capsule sessions). */
    val avgGramsPerSession: Float = 0f,
    /** Grams per day averaged over the last 7 days. */
    val gramsPerDay7d: Float = 0f,
    /** Grams per day averaged over the last 30 days. */
    val gramsPerDay30d: Float = 0f
)
