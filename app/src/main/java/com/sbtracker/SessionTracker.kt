package com.sbtracker

import com.sbtracker.data.ChargeCycle
import com.sbtracker.data.DeviceStatus
import com.sbtracker.data.Session
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Per-device session and charge state machine.
 */
class SessionTracker {

    companion object {
        private const val MIN_SESSION_DURATION_SEC  = 30
        private const val SESSION_END_GRACE_MS      = 8_000L
        private const val CHARGE_END_GRACE_MS       = 60_000L

        private const val DRAIN_HISTORY_SIZE  = 50
        private const val RATE_HISTORY_SIZE   = 20
        private const val RATE_WINDOW_SIZE    = 60

        private const val MIN_DRAIN_SAMPLES_FOR_CONFIDENCE = 10
        private const val CRITICAL_BATTERY_LEVEL = 15
    }

    enum class State { IDLE, ACTIVE }

    data class SessionStats(
        val state:               State   = State.IDLE,
        val hitCount:            Int     = 0,
        val durationSeconds:     Long    = 0L,
        val heatingDurationSec:  Long    = 0L,
        val readyDurationSec:    Long    = 0L,
        val totalHitDurationSec: Long    = 0L,
        val isHitActive:         Boolean = false,
        val currentHitDurationSec: Long  = 0L,
        val avgHitDurationSec:   Float   = 0f,
        val batteryDrain:        Int     = 0,
        val startingBattery:     Int     = 0,
        val drainRatePctPerMin:  Float   = 0f,
        val avgTempC:            Int     = 0,
        val peakTempC:           Int     = 0,
        val heatUpTimeSecs:      Long    = 0L,
        val currentBattery:      Int     = 0,
        val sessionsRemaining:   Int     = 0,
        val sessionsToCritical:  Int     = 0,
        val avgDrainPctPerSession: Float = 0f,
        val drainSampleCount:    Int     = 0,
        val drainEstimateReliable: Boolean = false,
        val sessionsRemainingLow:  Int   = 0,
        val sessionsRemainingHigh: Int   = 0,
        val chargeEtaMinutes:    Int?    = null,
        val chargeEta80Minutes:  Int?    = null,
        val chargeRatePctPerMin: Float   = 0f,
        val debugHex:            String  = "",
        val tempTimeMap:         Map<Int, Long> = emptyMap()
    )

    data class UpdateResult(
        val stats: SessionStats,
        val completedSession: Session? = null,
        val completedCharge:  ChargeCycle? = null
    )

    data class ChargeState(
        val chargeStartMs:          Long,
        val chargeStartBattery:     Int,
        val chargeEndPendingMs:     Long,
        val chargeEndBattery:       Int,
        val startChargeVoltageLimit: Boolean,
        val startChargeCurrentOpt:  Boolean
    )

    private var state = State.IDLE

    private var sessionStartMs = 0L
    private var startBattery   = 0
    private var hitCount       = 0
    private var peakTempC      = 0
    private var heatUpStartMs  = 0L
    private var heatUpEndMs    = 0L

    private var heatingMs      = 0L
    private var readyMs        = 0L
    
    private var tempAccum   = 0L
    private var tempSamples = 0
    
    private var prevShutdownSecs = -1
    private var prevTargetTempC  = 0
    private var prevBoostOffsets = 0
    private var sessionEndPendingMs = -1L

    private var hitInProgress = false
    private var hitStartTimeMs = 0L
    private var totalHitDurationMs = 0L
    private var hitPeakTempC = 0
    private var lastBaselineTemp = 0

    private val tempTimeMap = mutableMapOf<Int, Long>()
    private var lastUpdateMs = 0L

    private var startHeaterRuntime = 0
    private var currentHeaterRuntime = 0

    private var isCharging = false
    private var chargeStartMs = 0L
    private var chargeStartBattery = 0
    private var chargeEndPendingMs = -1L
    private var chargeEndBattery = 0
    private var startChargeVoltageLimit = false
    private var startChargeCurrentOpt   = false

    private val drainHistory = ArrayDeque<Int>(DRAIN_HISTORY_SIZE)
    private val rateHistory  = ArrayDeque<Float>(RATE_HISTORY_SIZE)
    private val chargeRateWindow = ArrayDeque<Pair<Long, Int>>()

    fun getChargeState(): ChargeState? {
        if (!isCharging) return null
        return ChargeState(chargeStartMs, chargeStartBattery, chargeEndPendingMs, chargeEndBattery, startChargeVoltageLimit, startChargeCurrentOpt)
    }

    fun restoreChargeState(s: ChargeState) {
        isCharging = true; chargeStartMs = s.chargeStartMs; chargeStartBattery = s.chargeStartBattery
        chargeEndPendingMs = s.chargeEndPendingMs; chargeEndBattery = s.chargeEndBattery
        startChargeVoltageLimit = s.startChargeVoltageLimit; startChargeCurrentOpt = s.startChargeCurrentOpt
    }

    private val chargeBands = listOf(
        65 to 1.00, // Flat CC region
        75 to 0.82, // Softer current after 65%
        85 to 0.52, // CV taper becomes noticeable
        92 to 0.28, // Aggressive taper
        100 to 0.12 // Top-off crawl
    )

    private fun chargeBandFactor(batteryPct: Int): Double =
        chargeBands.firstOrNull { batteryPct < it.first }?.second ?: chargeBands.last().second

    /**
     * Estimate remaining charge time using a piecewise device-specific curve.
     *
     * The current device charges at a fairly flat rate up to ~65%, softens through ~75%,
     * then enters an aggressive CV taper. We normalize the observed rate back to the
     * base CC rate for the current band, then integrate across the remaining bands.
     */
    private fun piecewiseChargeEtaMinutes(fromPct: Int, targetPct: Int, observedRate: Double): Double {
        if (targetPct <= fromPct || observedRate <= 0.0) return 0.0

        val currentFactor = chargeBandFactor(fromPct).coerceAtLeast(0.12)
        val baseCcRate = (observedRate / currentFactor).coerceAtLeast(0.1)

        var totalMinutes = 0.0
        var currentPct = fromPct
        for ((bandEnd, factor) in chargeBands) {
            if (currentPct >= targetPct) break
            val segEnd = minOf(targetPct, bandEnd)
            if (segEnd > currentPct) {
                totalMinutes += (segEnd - currentPct) / (baseCcRate * factor)
                currentPct = segEnd
            }
        }
        return totalMinutes
    }

    fun update(s: DeviceStatus, rawBytes: ByteArray, serial: String?, heaterRuntime: Int): UpdateResult {
        val now = System.currentTimeMillis()
        var completedSession: Session? = null
        var completedCharge:  ChargeCycle? = null
        currentHeaterRuntime = heaterRuntime

        val isHeaterOn = s.heaterMode > 0

        if (state == State.IDLE && isHeaterOn) {
            state = State.ACTIVE
            sessionStartMs = s.timestampMs; lastUpdateMs = now; startBattery = s.batteryLevel
            hitCount = 0; peakTempC = s.currentTempC; tempAccum = 0L; tempSamples = 0
            prevShutdownSecs = s.autoShutdownSeconds; sessionEndPendingMs = -1L
            prevTargetTempC = s.targetTempC; prevBoostOffsets = s.boostOffsetC + s.superBoostOffsetC
            heatUpStartMs = s.timestampMs; heatUpEndMs = 0L; hitInProgress = false; hitStartTimeMs = 0L
            totalHitDurationMs = 0L; tempTimeMap.clear(); startHeaterRuntime = heaterRuntime
            heatingMs = 0L; readyMs = 0L; lastBaselineTemp = 0
        } else if (state == State.ACTIVE) {
            val deltaMs = now - lastUpdateMs
            lastUpdateMs = now

            if (isHeaterOn) {
                sessionEndPendingMs = -1L
                
                tempAccum += s.currentTempC
                tempSamples++
                peakTempC = max(peakTempC, s.currentTempC)
                tempTimeMap[s.currentTempC] = (tempTimeMap[s.currentTempC] ?: 0L) + deltaMs

                // V1.0 FIX: Trigger hit on ANY timer jump, even if setpoint hasn't been reached yet.
                // This handles users taking "early hits" during the heat-up phase.
                val timerResetTrigger = prevShutdownSecs != -1 && s.autoShutdownSeconds > prevShutdownSecs + 2

                if (timerResetTrigger) {
                    if (!hitInProgress) {
                        hitCount++
                        hitInProgress = true
                        hitStartTimeMs = now
                        hitPeakTempC = s.currentTempC
                        lastBaselineTemp = s.currentTempC
                    }
                }

                if (hitInProgress) {
                    hitPeakTempC = max(hitPeakTempC, s.currentTempC)
                    val isTimerTickingDown = prevShutdownSecs != -1 && s.autoShutdownSeconds < prevShutdownSecs
                    
                    // Hit ends if:
                    // 1. Timer is ticking down (not resetting) AND
                    // 2. Temperature has recovered or dip is gone
                    val tempRecovered = lastBaselineTemp > 0 && (lastBaselineTemp - s.currentTempC) < 1.0
                    if (!timerResetTrigger && isTimerTickingDown && tempRecovered) {
                        endHit(now)
                    }
                }

                prevShutdownSecs = s.autoShutdownSeconds
                prevTargetTempC = s.targetTempC
                prevBoostOffsets = s.boostOffsetC + s.superBoostOffsetC
                if (s.setpointReached) {
                    readyMs += deltaMs
                    if (heatUpEndMs == 0L) heatUpEndMs = now
                } else {
                    heatingMs += deltaMs
                }
            } else {
                if (hitInProgress) endHit(now)

                if (sessionEndPendingMs == -1L) sessionEndPendingMs = s.timestampMs
                if (s.timestampMs - sessionEndPendingMs >= SESSION_END_GRACE_MS) {
                    val durationSec = (sessionEndPendingMs - sessionStartMs) / 1000
                    if (durationSec >= MIN_SESSION_DURATION_SEC) {
                        completedSession = Session(
                            deviceAddress = s.deviceAddress,
                            serialNumber  = serial,
                            startTimeMs   = sessionStartMs,
                            endTimeMs     = sessionEndPendingMs
                        )
                    }
                    resetSession()
                }
            }
        }

        // Charge Logic
        if (!isCharging && s.isCharging) {
            isCharging = true; chargeStartMs = now; chargeStartBattery = s.batteryLevel; chargeEndPendingMs = -1L
            startChargeVoltageLimit = s.chargeVoltageLimit; startChargeCurrentOpt = s.chargeCurrentOptimization; chargeRateWindow.clear()
        } else if (isCharging) {
            if (s.isCharging) chargeEndPendingMs = -1L
            else {
                if (chargeEndPendingMs == -1L) { chargeEndPendingMs = now; chargeEndBattery = s.batteryLevel }
                if (now - chargeEndPendingMs >= CHARGE_END_GRACE_MS) {
                    val durationMin = (chargeEndPendingMs - chargeStartMs) / 60_000f
                    val gain = (chargeEndBattery - chargeStartBattery).coerceAtLeast(0)
                    if (durationMin > 1f && gain > 0) {
                        val rate = gain / durationMin
                        rateHistory.addLast(rate)
                        if (rateHistory.size > RATE_HISTORY_SIZE) rateHistory.removeFirst()
                        completedCharge = ChargeCycle(
                            deviceAddress = s.deviceAddress,
                            serialNumber = serial,
                            startTimeMs = chargeStartMs,
                            endTimeMs = chargeEndPendingMs,
                            startBattery = chargeStartBattery,
                            endBattery = chargeEndBattery,
                            avgRatePctPerMin = rate,
                            chargeVoltageLimit = startChargeVoltageLimit,
                            chargeCurrentOptimization = startChargeCurrentOpt
                        )
                    }
                    isCharging = false; chargeRateWindow.clear()
                }
            }
        }

        // Stats Extrapolation
        val avgDrain  = if (drainHistory.isNotEmpty()) drainHistory.average() else 15.0
        val stdDev    = drainStdDev()
        val pessimisticDrain  = (avgDrain + stdDev).coerceAtLeast(1.0)
        val optimisticDrain   = (avgDrain - stdDev).coerceAtLeast(1.0)
        val sessionsLeft      = if (avgDrain > 0) (s.batteryLevel / avgDrain).toInt() else 0
        val sessionsLow       = if (pessimisticDrain > 0) (s.batteryLevel / pessimisticDrain).toInt() else 0
        val sessionsHigh      = if (optimisticDrain  > 0) (s.batteryLevel / optimisticDrain ).toInt() else 0
        val sessionsToCritical = if (avgDrain > 0) max(0, ((s.batteryLevel - CRITICAL_BATTERY_LEVEL) / avgDrain).toInt()) else 0
        
        var chargeEta: Int? = null; var chargeEta80: Int? = null
        var chargeRatePctPerMin = 0f
        if (isCharging) {
            chargeRateWindow.addLast(now to s.batteryLevel)
            if (chargeRateWindow.size > RATE_WINDOW_SIZE) chargeRateWindow.removeFirst()

            val target = if (s.chargeVoltageLimit) 90 else 100
            val pctRem = (target - s.batteryLevel).coerceAtLeast(0)

            if (pctRem > 0) {
                val liveRate: Double? = if (chargeRateWindow.size >= 2) {
                    val o = chargeRateWindow.first(); val n = chargeRateWindow.last()
                    val min = (n.first - o.first) / 60_000.0
                    val g = (n.second - o.second).toDouble()
                    if (min >= 1.0 && g > 0) g / min else null
                } else null

                val sessionRate: Double? = run {
                    val min = (now - chargeStartMs) / 60_000.0
                    val g = (s.batteryLevel - chargeStartBattery).toDouble()
                    if (min >= 2.0 && g > 0) g / min else null
                }

                val historicalRate: Double? =
                    if (rateHistory.isNotEmpty()) rateHistory.average().toDouble() else null

                val bestRate = liveRate ?: sessionRate

                if (bestRate != null && bestRate > 0) {
                    chargeRatePctPerMin = bestRate.toFloat()
                    chargeEta = kotlin.math.ceil(
                        piecewiseChargeEtaMinutes(s.batteryLevel, target, bestRate)
                    ).toInt()
                    if (s.batteryLevel < 80) {
                        chargeEta80 = kotlin.math.ceil(
                            piecewiseChargeEtaMinutes(s.batteryLevel, 80, bestRate)
                        ).toInt()
                    }
                } else if (historicalRate != null && historicalRate > 0) {
                    chargeEta = kotlin.math.ceil(
                        piecewiseChargeEtaMinutes(s.batteryLevel, target, historicalRate)
                    ).toInt()
                    if (s.batteryLevel < 80) {
                        chargeEta80 = kotlin.math.ceil(
                            piecewiseChargeEtaMinutes(s.batteryLevel, 80, historicalRate)
                        ).toInt()
                    }
                }
            }
        }

        val stats = SessionStats(
            state = state,
            hitCount = hitCount,
            durationSeconds = if (state == State.ACTIVE) (now - sessionStartMs) / 1000 else 0L,
            heatingDurationSec = heatingMs / 1000,
            readyDurationSec = readyMs / 1000,
            totalHitDurationSec = totalHitDurationMs / 1000,
            isHitActive = hitInProgress,
            currentHitDurationSec = if (hitInProgress) (now - hitStartTimeMs) / 1000 else 0L,
            avgHitDurationSec = if (hitCount > 0) (totalHitDurationMs / 1000f) / hitCount else 0f,
            batteryDrain = startBattery - s.batteryLevel,
            startingBattery = if (state == State.ACTIVE) startBattery else 0,
            drainRatePctPerMin = if (state == State.ACTIVE && (now - sessionStartMs) > 60_000) (startBattery - s.batteryLevel) / ((now - sessionStartMs) / 60_000f) else 0f,
            avgTempC = if (tempSamples > 0) (tempAccum / tempSamples).toInt() else 0,
            peakTempC = peakTempC,
            heatUpTimeSecs = if (heatUpEndMs > 0) (heatUpEndMs - heatUpStartMs) / 1000 else 0L,
            currentBattery = s.batteryLevel,
            sessionsRemaining = sessionsLeft,
            sessionsToCritical = sessionsToCritical,
            avgDrainPctPerSession = avgDrain.toFloat(),
            drainSampleCount = drainHistory.size,
            drainEstimateReliable = drainHistory.size >= MIN_DRAIN_SAMPLES_FOR_CONFIDENCE,
            sessionsRemainingLow  = sessionsLow,
            sessionsRemainingHigh = sessionsHigh,
            chargeEtaMinutes = chargeEta,
            chargeEta80Minutes = chargeEta80,
            chargeRatePctPerMin = chargeRatePctPerMin,
            debugHex = rawBytes.joinToString("") { "%02X".format(it) },
            tempTimeMap = tempTimeMap
        )

        return UpdateResult(stats, completedSession, completedCharge)
    }

    private fun endHit(endTimeMs: Long) {
        val durationMs = endTimeMs - hitStartTimeMs
        if (durationMs >= 2000) {
            totalHitDurationMs += durationMs
        } else if (durationMs > 0 && hitInProgress) {
            // If it was a valid start but too short, we revert count
            if (hitCount > 0) hitCount--
        }
        hitInProgress  = false
        hitStartTimeMs = 0L
    }

    private fun resetSession() {
        state = State.IDLE
        sessionStartMs = 0L; startBattery = 0; hitCount = 0; peakTempC = 0
        tempAccum = 0L; tempSamples = 0; prevShutdownSecs = -1; sessionEndPendingMs = -1L
        prevTargetTempC = 0; prevBoostOffsets = 0
        heatUpStartMs = 0L; heatUpEndMs = 0L; hitInProgress = false; hitStartTimeMs = 0L
        totalHitDurationMs = 0L; tempTimeMap.clear(); startHeaterRuntime = 0
        heatingMs = 0L; readyMs = 0L
    }

    private var disconnectedMs = 0L

    fun markDisconnected() {
        disconnectedMs = System.currentTimeMillis()
    }

    fun markReconnected() {
        if (isCharging && disconnectedMs > 0) {
            val gap = System.currentTimeMillis() - disconnectedMs
            if (gap > 120_000) {
                chargeRateWindow.clear()
            }
        }
        disconnectedMs = 0L
    }

    fun recordSessionDrain(drain: Int) {
        if (drain > 0) {
            drainHistory.addLast(drain)
            if (drainHistory.size > DRAIN_HISTORY_SIZE) drainHistory.removeFirst()
        }
    }

    private fun drainStdDev(): Double {
        if (drainHistory.size < 2) return 0.0
        val mean = drainHistory.average()
        val variance = drainHistory.sumOf { (it - mean) * (it - mean) } / drainHistory.size
        return sqrt(variance)
    }

    fun setHistoricalData(drains: List<Int>, rates: List<Float>) {
        drainHistory.clear()
        drains.filter { it > 0 }.takeLast(DRAIN_HISTORY_SIZE).forEach { drainHistory.addLast(it) }

        rateHistory.clear()
        rates.filter { it > 0f }.takeLast(RATE_HISTORY_SIZE).forEach { rateHistory.addLast(it) }
    }
    
    fun reset() {
        resetSession()
    }
}
