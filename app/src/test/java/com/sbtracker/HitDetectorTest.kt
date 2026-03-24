package com.sbtracker

import com.sbtracker.data.DeviceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HitDetectorTest {

    private fun createStatus(
        timestampMs: Long,
        heaterMode: Int = 1,
        setpointReached: Boolean = true,
        autoShutdownSeconds: Int = 120,
        currentTempC: Int = 180,
        targetTempC: Int = 180
    ) = DeviceStatus(
        timestampMs = timestampMs,
        deviceAddress = "AA:BB:CC:DD:EE:FF",
        deviceType = "Venty",
        currentTempC = currentTempC,
        targetTempC = targetTempC,
        boostOffsetC = 0,
        superBoostOffsetC = 0,
        batteryLevel = 100,
        heaterMode = heaterMode,
        isCharging = false,
        setpointReached = setpointReached,
        autoShutdownSeconds = autoShutdownSeconds,
        isCelsius = true,
        vibrationEnabled = true,
        chargeCurrentOptimization = false,
        chargeVoltageLimit = false,
        permanentBluetooth = true,
        boostVisualization = false
    )

    @Test
    fun `detects hit from timer reset`() {
        // Given: a sequence where timer resets from 118 to 120
        val statuses = listOf(
            createStatus(1000, autoShutdownSeconds = 120),
            createStatus(2000, autoShutdownSeconds = 119),
            createStatus(3000, autoShutdownSeconds = 118),
            createStatus(4000, autoShutdownSeconds = 120), // Hit starts here
            createStatus(5000, autoShutdownSeconds = 119),
            createStatus(6000, autoShutdownSeconds = 118)  // Hit ends here
        )

        // When
        val hits = HitDetector.detect(statuses)

        // Then
        assertEquals(1, hits.size)
        assertEquals(4000L, hits[0].startTimeMs)
        assertEquals(2000L, hits[0].durationMs) // 6000 - 4000
    }

    @Test
    fun `detects hit from temp dip`() {
        // Given: a sequence where temp dips from 180 to 175 (threshold is usually 2 or 3)
        // BleConstants.TEMP_DIP_THRESHOLD_C is 2 according to common patterns
        val statuses = listOf(
            createStatus(1000, currentTempC = 180),
            createStatus(2000, currentTempC = 179),
            createStatus(3000, currentTempC = 175), // Hit starts here (dip >= 2)
            createStatus(4000, currentTempC = 176),
            createStatus(5000, currentTempC = 180),
            createStatus(6000, currentTempC = 180, autoShutdownSeconds = 119) // Hit ends when timer starts counting down and no trigger
        )

        // When
        val hits = HitDetector.detect(statuses)

        // Then: Hit starts at 3000 (first status with >= 2 dip)
        // Hit ends at 6000 (timer starts counting down from 120)
        assertEquals(1, hits.size)
        assertEquals(3000L, hits[0].startTimeMs)
        assertEquals(3000L, hits[0].durationMs)
    }

    @Test
    fun `does not detect hit if heater is off`() {
        val statuses = listOf(
            createStatus(1000, heaterMode = 0, autoShutdownSeconds = 120),
            createStatus(2000, heaterMode = 0, autoShutdownSeconds = 120)
        )
        val hits = HitDetector.detect(statuses)
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `closes hit if heater turns off`() {
        val statuses = listOf(
            createStatus(1000, heaterMode = 1, autoShutdownSeconds = 120),
            createStatus(2000, heaterMode = 1, autoShutdownSeconds = 118),
            createStatus(3000, heaterMode = 1, autoShutdownSeconds = 120), // Hit starts
            createStatus(4000, heaterMode = 1, autoShutdownSeconds = 119),
            createStatus(5000, heaterMode = 0, autoShutdownSeconds = 119)  // Hit ends
        )
        val hits = HitDetector.detect(statuses)
        assertEquals(1, hits.size)
        assertEquals(3000L, hits[0].startTimeMs)
        assertEquals(2000L, hits[0].durationMs)
    }
}
