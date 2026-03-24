package com.sbtracker

import com.sbtracker.data.DeviceStatus
import kotlin.math.max

/**
 * Lightweight hit record produced during detection before the parent session
 * row exists.  [MainViewModel] converts these to [com.sbtracker.data.Hit] rows
 * once it has the session's Room-assigned ID.
 */
data class PendingHit(
    val startTimeMs: Long,
    val durationMs:  Long,
    val peakTempC:   Int
)

/**
 * Pure, stateless hit detector that operates on a pre-collected list of
 * [DeviceStatus] rows rather than on a live stream.
 *
 * Because it takes a [List] rather than individual samples, the same logic
 * can be run retroactively against the raw device_status log at any time,
 * allowing hit data to be re-derived whenever the detection algorithm improves.
 * This is the key property that closes the one gap in the raw-log architecture:
 * hits are no longer frozen at the moment they were first observed.
 *
 * The detection logic mirrors [SessionTracker] exactly.  It uses
 * [DeviceStatus.timestampMs] in place of [System.currentTimeMillis] for all
 * hit boundary timestamps.
 */
object HitDetector {


    /**
     * Detect all hits within the given list of status rows.
     *
     * @param statuses Rows ordered ascending by [DeviceStatus.timestampMs].
     *                 Should span the session window (startTimeMs..endTimeMs).
     * @return List of [PendingHit] records in chronological order.
     */
    fun detect(statuses: List<DeviceStatus>): List<PendingHit> {
        val result = mutableListOf<PendingHit>()

        var hitInProgress    = false
        var hitStartTimeMs   = 0L
        var hitPeakTempC     = 0
        var prevShutdownSecs = -1
        var lastBaselineTemp = 0

        fun endHit(endTimeMs: Long) {
            val durationMs = endTimeMs - hitStartTimeMs
            if (durationMs > 0) {
                result.add(PendingHit(hitStartTimeMs, durationMs, hitPeakTempC))
            }
            hitInProgress  = false
            hitStartTimeMs = 0L
        }

        for (s in statuses) {
            val isHeaterOn = s.heaterMode > 0

            if (!isHeaterOn) {
                // Heater went off — close any open hit and reset per-session state.
                if (hitInProgress) endHit(s.timestampMs)
                prevShutdownSecs = -1
                lastBaselineTemp = 0
                continue
            }

            val timerResetTrigger = s.setpointReached &&
                    prevShutdownSecs != -1 &&
                    s.autoShutdownSeconds > prevShutdownSecs + 2

            val tempDipTrigger = s.setpointReached &&
                    lastBaselineTemp > 0 &&
                    (lastBaselineTemp - s.currentTempC) >= BleConstants.TEMP_DIP_THRESHOLD_C

            if (timerResetTrigger || tempDipTrigger) {
                if (!hitInProgress) {
                    hitInProgress  = true
                    hitStartTimeMs = s.timestampMs
                    hitPeakTempC   = s.currentTempC
                }
            }

            if (hitInProgress) {
                hitPeakTempC = max(hitPeakTempC, s.currentTempC)
                // End the hit once neither trigger is firing and the timer has started counting down.
                if (!timerResetTrigger && !tempDipTrigger &&
                    prevShutdownSecs != -1 && s.autoShutdownSeconds < prevShutdownSecs) {
                    endHit(s.timestampMs)
                }
            } else if (s.setpointReached) {
                // Track rising baseline only when not mid-hit.
                if (lastBaselineTemp == 0 || s.currentTempC >= lastBaselineTemp) {
                    lastBaselineTemp = s.currentTempC
                }
            }

            prevShutdownSecs = s.autoShutdownSeconds
        }

        // Close any hit still open at the end of the window.
        if (hitInProgress && statuses.isNotEmpty()) {
            endHit(statuses.last().timestampMs)
        }

        return result
    }
}
