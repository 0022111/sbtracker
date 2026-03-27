package com.sbtracker

import com.sbtracker.data.DeviceStatus
import kotlin.math.max

/**
 * Lightweight hit record produced during detection before the parent session
 * row exists.
 */
data class PendingHit(
    val startTimeMs: Long,
    val durationMs:  Long,
    val peakTempC:   Int
)

/**
 * Pure, stateless hit detector that operates on a pre-collected list of
 * [DeviceStatus] rows.
 *
 * Synchronized with SessionTracker v1.0: uses Pure Interactivity Model.
 */
object HitDetector {

    /**
     * Detect all hits within the given list of status rows.
     *
     * @param statuses Rows ordered ascending by [DeviceStatus.timestampMs].
     * @return List of [PendingHit] records in chronological order.
     */
    fun detect(statuses: List<DeviceStatus>): List<PendingHit> {
        val result = mutableListOf<PendingHit>()

        var hitInProgress    = false
        var lastBaselineTemp = 0
        var hitStartTimeMs   = 0L
        var hitPeakTempC     = 0
        var prevShutdownSecs = -1

        fun endHit(endTimeMs: Long) {
            val durationMs = endTimeMs - hitStartTimeMs
            if (durationMs >= 2000) { // Enforce 2s min
                result.add(PendingHit(hitStartTimeMs, durationMs, hitPeakTempC))
            }
            hitInProgress  = false
            hitStartTimeMs = 0L
        }

        for (s in statuses) {
            val isHeaterOn = s.heaterMode > 0

            if (!isHeaterOn) {
                if (hitInProgress) endHit(s.timestampMs)
                prevShutdownSecs = -1
                continue
            }

            // V1.0 COMBINED MODEL: Trigger on timer reset.
            val timerResetTrigger = prevShutdownSecs != -1 && s.autoShutdownSeconds > prevShutdownSecs + 2

            if (timerResetTrigger) {
                if (!hitInProgress) {
                    hitInProgress  = true
                    hitStartTimeMs = s.timestampMs
                    hitPeakTempC   = s.currentTempC
                    lastBaselineTemp = s.currentTempC
                }
            }

            if (hitInProgress) {
                hitPeakTempC = max(hitPeakTempC, s.currentTempC)
                val isTimerTickingDown = prevShutdownSecs != -1 && s.autoShutdownSeconds < prevShutdownSecs
                
                // Hit ends if timer ticks down AND temperature has recovered
                val tempRecovered = lastBaselineTemp > 0 && (lastBaselineTemp - s.currentTempC) < 1.0
                
                if (!timerResetTrigger && isTimerTickingDown && tempRecovered) {
                    endHit(s.timestampMs)
                }
            }

            prevShutdownSecs = s.autoShutdownSeconds
        }

        if (hitInProgress && statuses.isNotEmpty()) {
            endHit(statuses.last().timestampMs)
        }

        return result
    }
}
