package com.sbtracker.core

import com.sbtracker.data.DeviceStatus
import com.sbtracker.data.Session

/**
 * Pure session derivation. Given the god log for a device, return the list of
 * sessions. No state, no Android, no DB — feed in the log, get sessions out.
 *
 * A session is a contiguous window where heater was on, allowing brief dips
 * up to [END_GRACE_MS]. Sessions shorter than [MIN_DURATION_SEC] are dropped.
 */
object Sessions {

    private const val MIN_DURATION_SEC = 30L
    private const val END_GRACE_MS     = 8_000L

    fun derive(log: List<DeviceStatus>): List<Session> {
        if (log.isEmpty()) return emptyList()
        val out = mutableListOf<Session>()

        var startMs: Long = -1
        var lastOnMs: Long = -1
        val address = log.first().deviceAddress

        for (s in log) {
            val on = s.heaterMode > 0
            if (on) {
                if (startMs < 0) startMs = s.timestampMs
                lastOnMs = s.timestampMs
            } else if (startMs >= 0 && s.timestampMs - lastOnMs >= END_GRACE_MS) {
                emit(out, address, startMs, lastOnMs)
                startMs = -1
            }
        }
        if (startMs >= 0) emit(out, address, startMs, lastOnMs)
        return out
    }

    private fun emit(out: MutableList<Session>, address: String, start: Long, end: Long) {
        if ((end - start) / 1000 >= MIN_DURATION_SEC) {
            out += Session(
                deviceAddress = address,
                serialNumber  = null,
                startTimeMs   = start,
                endTimeMs     = end,
            )
        }
    }
}
