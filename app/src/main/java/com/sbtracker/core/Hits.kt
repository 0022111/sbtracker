package com.sbtracker.core

import com.sbtracker.data.DeviceStatus
import kotlin.math.max

/**
 * Detected hit within a session.
 */
data class Hit(
    val startMs:   Long,
    val durationMs: Long,
    val peakTempC: Int,
)

/**
 * Hit detection: a hit is an inhalation event. We infer it from the device's
 * auto-shutdown timer — every inhalation resets the timer, so a jump in
 * [autoShutdownSeconds] (while the heater is on) marks the start of a hit.
 * The hit ends when the timer starts ticking down again.
 *
 * Pure function: same input log → same hits.
 */
object Hits {

    private const val MIN_HIT_MS:       Long = 2_000
    private const val TIMER_RESET_GRACE: Int  = 2

    fun detect(log: List<DeviceStatus>): List<Hit> {
        val out = mutableListOf<Hit>()

        var inHit       = false
        var startMs     = 0L
        var peakC       = 0
        var prevTimer   = -1
        var baselineC   = 0

        fun close(endMs: Long) {
            if (inHit && endMs - startMs >= MIN_HIT_MS) {
                out += Hit(startMs, endMs - startMs, peakC)
            }
            inHit = false
        }

        for (s in log) {
            if (s.heaterMode == 0) {
                close(s.timestampMs)
                prevTimer = -1
                continue
            }

            val reset = prevTimer >= 0 && s.autoShutdownSeconds > prevTimer + TIMER_RESET_GRACE
            if (reset && !inHit) {
                inHit     = true
                startMs   = s.timestampMs
                peakC     = s.currentTempC
                baselineC = s.currentTempC
            }
            if (inHit) {
                peakC = max(peakC, s.currentTempC)
                val ticking  = prevTimer >= 0 && s.autoShutdownSeconds < prevTimer
                val recovered = baselineC > 0 && (baselineC - s.currentTempC) < 1
                if (!reset && ticking && recovered) close(s.timestampMs)
            }
            prevTimer = s.autoShutdownSeconds
        }
        if (inHit) close(log.last().timestampMs)
        return out
    }
}
