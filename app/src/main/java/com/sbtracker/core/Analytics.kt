package com.sbtracker.core

import com.sbtracker.data.Session

/**
 * Aggregate stats across a collection of sessions.
 *
 * Pure functions. Feed in sessions + summaries, get numbers out. No caching,
 * no ordering, no Android. Call sites decide which slice of history to pass in.
 */
data class Totals(
    val sessions:      Int,
    val hits:          Int,
    val durationMs:    Long,
    val avgHits:       Float,
    val avgDurationMs: Long,
    val avgPeakC:      Int,
)

object Analytics {

    fun totals(summaries: List<Summary>): Totals {
        if (summaries.isEmpty()) return Totals(0, 0, 0, 0f, 0, 0)
        val hits        = summaries.sumOf { it.hitCount }
        val duration    = summaries.sumOf { it.durationMs }
        val avgPeak     = summaries.sumOf { it.peakTempC } / summaries.size
        return Totals(
            sessions      = summaries.size,
            hits          = hits,
            durationMs    = duration,
            avgHits       = hits.toFloat() / summaries.size,
            avgDurationMs = duration / summaries.size,
            avgPeakC      = avgPeak,
        )
    }

    /** Sessions whose start falls in the last [windowMs] ms from [nowMs]. */
    fun recent(sessions: List<Session>, nowMs: Long, windowMs: Long): List<Session> =
        sessions.filter { nowMs - it.startTimeMs <= windowMs }
}
