package com.sbtracker.data

/**
 * Session boundaries plus all stats that can be derived from the raw log.
 *
 * This is NOT a Room entity.  It is assembled on demand by querying
 * [DeviceStatusDao], [HitDao], and [ExtendedDataDao] for the session's time
 * window.  Because it is computed — not stored — improving any algorithm
 * (hit detection, temperature averaging, etc.) and recomputing summaries will
 * retroactively update every session in history.
 *
 * Construction is handled by [com.sbtracker.analytics.AnalyticsRepository.getSessionSummary].
 */
data class SessionSummary(
    val session:            Session,

    // ── Hit stats (from the hits table) ──────────────────────────────────────
    val hitCount:           Int,
    val totalHitDurationMs: Long,

    // ── Battery (from device_status boundary readings) ────────────────────────
    val startBattery:       Int,
    val endBattery:         Int,

    // ── Temperature (from device_status aggregates over the session window) ──
    val avgTempC:           Int,
    val peakTempC:          Int,

    // ── Timing (from device_status) ───────────────────────────────────────────
    /** Time from heater-on to setpoint first reached, in ms.  0 if no setpoint data. */
    val heatUpTimeMs:       Long,

    // ── Device wear (from extended_data boundary readings) ────────────────────
    /** Delta heaterRuntimeMinutes across the session.  0 if extended_data unavailable. */
    val heaterWearMinutes:  Int,

    // ── User metadata ─────────────────────────────────────────────────────────
    val rating:             Int?    = null,
    val notes:              String? = null
) {
    // Convenience delegation so call-sites can use summary.* directly
    val id:             Long    get() = session.id
    val deviceAddress:  String  get() = session.deviceAddress
    val serialNumber:   String? get() = session.serialNumber
    val startTimeMs:    Long    get() = session.startTimeMs
    val endTimeMs:      Long    get() = session.endTimeMs
    val durationMs:     Long    get() = session.durationMs
    val batteryConsumed: Int    get() = (startBattery - endBattery).coerceAtLeast(0)
}
