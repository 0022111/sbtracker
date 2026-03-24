package com.sbtracker

import com.sbtracker.data.DeviceStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionTrackerTest {

    private fun createStatus(
        timestampMs: Long,
        currentTempC: Int,
        targetTempC: Int,
        boostOffsetC: Int,
        superBoostOffsetC: Int,
        autoShutdownSeconds: Int,
        setpointReached: Boolean,
        heaterMode: Int = 1
    ): DeviceStatus {
        return DeviceStatus(
            timestampMs = timestampMs,
            deviceAddress = "00:11:22:33:44:55",
            deviceType = "VENTY",
            currentTempC = currentTempC,
            targetTempC = targetTempC,
            boostOffsetC = boostOffsetC,
            superBoostOffsetC = superBoostOffsetC,
            batteryLevel = 100,
            heaterMode = heaterMode,
            isCharging = false,
            setpointReached = setpointReached,
            autoShutdownSeconds = autoShutdownSeconds,
            isCelsius = true,
            vibrationEnabled = false,
            chargeCurrentOptimization = false,
            chargeVoltageLimit = false,
            permanentBluetooth = false,
            boostVisualization = false
        )
    }

    @Test
    fun testHitDetection_StrongDraw_LosesSetpoint() {
        val tracker = SessionTracker()
        
        // 1. Start heating
        tracker.update(createStatus(1000, 20, 180, 0, 0, 120, false), ByteArray(0), null, 0)
        
        // 2. Reaches setpoint, timer starts ticking down
        tracker.update(createStatus(2000, 180, 180, 0, 0, 119, true), ByteArray(0), null, 0)
        tracker.update(createStatus(3000, 180, 180, 0, 0, 118, true), ByteArray(0), null, 0)
        
        // 3. Strong draw occurs: temp dips to 170, setpointReached becomes false, timer resets to 120
        tracker.update(createStatus(4000, 170, 180, 0, 0, 120, false), ByteArray(0), null, 0)
        
        // 4. Hit continues: timer ticks down while hit is active
        tracker.update(createStatus(5000, 172, 180, 0, 0, 119, false), ByteArray(0), null, 0)
        tracker.update(createStatus(6000, 175, 180, 0, 0, 118, false), ByteArray(0), null, 0)
        
        val result = tracker.update(createStatus(7000, 180, 180, 0, 0, 117, true), ByteArray(0), null, 0)
        
        assertEquals(1, result.stats.hitCount)
        assertEquals(false, result.stats.isHitActive)
        assertEquals(2L, result.stats.totalHitDurationSec) // from 4000 to 6000 was hit
    }

    @Test
    fun testHitDetection_FalsePositive_BoostActivation() {
        val tracker = SessionTracker()
        
        // 1. Start heating
        tracker.update(createStatus(1000, 20, 180, 0, 0, 120, false), ByteArray(0), null, 0)
        
        // 2. Reaches setpoint, timer ticks down
        tracker.update(createStatus(2000, 180, 180, 0, 0, 119, true), ByteArray(0), null, 0)
        tracker.update(createStatus(3000, 180, 180, 0, 0, 118, true), ByteArray(0), null, 0)
        
        // 3. User double clicks Boost: target temp increases, timer resets to 120
        tracker.update(createStatus(4000, 180, 180, 15, 0, 120, false), ByteArray(0), null, 0)
        
        val result = tracker.update(createStatus(5000, 185, 180, 15, 0, 119, false), ByteArray(0), null, 0)
        
        assertEquals(0, result.stats.hitCount) // Should be 0 because it was a temp/boost change
    }
}
