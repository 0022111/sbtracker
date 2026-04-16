package com.sbtracker.core

import com.sbtracker.data.DeviceStatus
import com.sbtracker.data.Session

/**
 * Stats derived from a session's slice of the god log. Pure function.
 *
 * All numbers shown on a session detail screen flow from here: we re-derive
 * them every time. No migrations when the algorithm improves.
 */
data class Summary(
    val session:        Session,
    val hitCount:       Int,
    val peakTempC:      Int,
    val avgTempC:       Int,
    val batteryDrain:   Int,
    val heatUpSeconds:  Long,
) {
    val durationMs: Long get() = session.endTimeMs - session.startTimeMs
}

object Summaries {

    fun of(session: Session, window: List<DeviceStatus>): Summary {
        val hits = Hits.detect(window)

        val heaterOn   = window.filter { it.heaterMode > 0 }
        val avgTemp    = if (heaterOn.isNotEmpty()) heaterOn.sumOf { it.currentTempC } / heaterOn.size else 0
        val peakTemp   = window.maxOfOrNull { it.currentTempC } ?: 0

        val startBattery = window.firstOrNull()?.batteryLevel ?: 0
        val endBattery   = window.lastOrNull()?.batteryLevel ?: startBattery
        val drain        = (startBattery - endBattery).coerceAtLeast(0)

        val firstReady = window.firstOrNull { it.setpointReached }?.timestampMs
        val heatUp     = if (firstReady != null) (firstReady - session.startTimeMs) / 1000 else 0L

        return Summary(
            session       = session,
            hitCount      = hits.size,
            peakTempC     = peakTemp,
            avgTempC      = avgTemp,
            batteryDrain  = drain,
            heatUpSeconds = heatUp,
        )
    }
}
