package com.sbtracker.util

import com.sbtracker.data.SessionSummary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object StreakUtils {

    private fun dayOf(ms: Long): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(ms))

    /**
     * Consecutive days with ≥1 session ending today.
     * Allows yesterday as the starting day (grace period so streak doesn't break at midnight).
     */
    fun currentStreak(summaries: List<SessionSummary>): Int {
        if (summaries.isEmpty()) return 0
        val days = summaries.map { dayOf(it.startTimeMs) }.toSet()
        val cal = Calendar.getInstance()
        val todayStr = dayOf(cal.timeInMillis)
        if (!days.contains(todayStr)) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
            if (!days.contains(dayOf(cal.timeInMillis))) return 0
        }
        var streak = 0
        while (days.contains(dayOf(cal.timeInMillis))) {
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }

    /** All-time best consecutive days with ≥1 session. */
    fun longestStreak(summaries: List<SessionSummary>): Int {
        if (summaries.isEmpty()) return 0
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
        val days = summaries.map { dayOf(it.startTimeMs) }.toSortedSet()
        var longest = 0
        var current = 0
        var prevMs: Long? = null
        for (day in days) {
            val ms = fmt.parse(day)!!.time
            if (prevMs == null) {
                current = 1
            } else {
                val prevCal = Calendar.getInstance().apply { timeInMillis = prevMs!! }
                prevCal.add(Calendar.DAY_OF_YEAR, 1)
                current = if (dayOf(prevCal.timeInMillis) == day) current + 1 else 1
            }
            if (current > longest) longest = current
            prevMs = ms
        }
        return longest
    }

    /**
     * Days since the most recent session.
     * Returns 0 if there was a session today, 1 if yesterday, etc.
     * Returns -1 if no sessions exist.
     */
    fun daysSinceLastSession(summaries: List<SessionSummary>): Int {
        if (summaries.isEmpty()) return -1
        val lastMs = summaries.maxOf { it.startTimeMs }
        val diffMs = System.currentTimeMillis() - lastMs
        return (diffMs / (1000L * 60 * 60 * 24)).toInt().coerceAtLeast(0)
    }

    /** Progress 0.0–1.0 toward a tolerance break goal of [goalDays] days without a session. */
    fun breakProgress(summaries: List<SessionSummary>, goalDays: Int): Float {
        if (goalDays <= 0) return 0f
        val days = daysSinceLastSession(summaries)
        if (days < 0) return 0f
        return (days.toFloat() / goalDays).coerceIn(0f, 1f)
    }
}
