package com.sbtracker.core

import com.sbtracker.data.DeviceStatus

/**
 * A charging window derived from the log.
 */
data class Charge(
    val startMs:      Long,
    val endMs:        Long,
    val startBattery: Int,
    val endBattery:   Int,
) {
    val durationMs:       Long  get() = endMs - startMs
    val gain:             Int   get() = (endBattery - startBattery).coerceAtLeast(0)
    val ratePctPerMin:    Float get() = if (durationMs > 0) gain / (durationMs / 60_000f) else 0f
}

/**
 * Pure charge-cycle derivation — mirror of [Sessions.derive] for charging windows.
 * A charge is a contiguous stretch where `isCharging = true`, allowing brief
 * dropouts of up to [END_GRACE_MS].
 */
object Charges {

    private const val END_GRACE_MS = 60_000L
    private const val MIN_DURATION_MS = 60_000L

    fun derive(log: List<DeviceStatus>): List<Charge> {
        if (log.isEmpty()) return emptyList()
        val out = mutableListOf<Charge>()

        var startMs:      Long = -1
        var startBattery: Int  = 0
        var lastOnMs:     Long = -1
        var lastBattery:  Int  = 0

        for (s in log) {
            if (s.isCharging) {
                if (startMs < 0) {
                    startMs      = s.timestampMs
                    startBattery = s.batteryLevel
                }
                lastOnMs    = s.timestampMs
                lastBattery = s.batteryLevel
            } else if (startMs >= 0 && s.timestampMs - lastOnMs >= END_GRACE_MS) {
                if (lastOnMs - startMs >= MIN_DURATION_MS) {
                    out += Charge(startMs, lastOnMs, startBattery, lastBattery)
                }
                startMs = -1
            }
        }
        if (startMs >= 0 && lastOnMs - startMs >= MIN_DURATION_MS) {
            out += Charge(startMs, lastOnMs, startBattery, lastBattery)
        }
        return out
    }

    /**
     * If the device is currently charging, estimate minutes to reach [targetPct].
     * Returns null when the rate can't be established yet.
     */
    fun etaMinutes(log: List<DeviceStatus>, nowPct: Int, targetPct: Int): Int? {
        if (targetPct <= nowPct) return 0
        val charging = log.asReversed().takeWhile { it.isCharging }.reversed()
        if (charging.size < 2) return null
        val first = charging.first()
        val last  = charging.last()
        val durMin = (last.timestampMs - first.timestampMs) / 60_000f
        val gain   = (last.batteryLevel - first.batteryLevel).toFloat()
        if (durMin < 1f || gain <= 0f) return null
        val rate = gain / durMin
        return ((targetPct - nowPct) / rate).toInt().coerceAtLeast(0)
    }
}
