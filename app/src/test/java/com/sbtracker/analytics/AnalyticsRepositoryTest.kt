package com.sbtracker.analytics

import com.sbtracker.data.AppDatabase
import com.sbtracker.data.Session
import com.sbtracker.data.SessionSummary
import com.sbtracker.data.SessionMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class AnalyticsRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: AnalyticsRepository

    @Before
    fun setup() {
        db = mock(AppDatabase::class.java)
        repository = AnalyticsRepository(db)
    }

    private fun createSummary(
        id: Long,
        startTimeMs: Long,
        durationMs: Long = 300_000L,
        hitCount: Int = 3,
        batteryConsumed: Int = 5,
        peakTempC: Int = 180
    ): SessionSummary {
        val session = Session(
            id = id,
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            serialNumber = "VENTY001",
            startTimeMs = startTimeMs,
            endTimeMs = startTimeMs + durationMs
        )
        return SessionSummary(
            session = session,
            hitCount = hitCount,
            totalHitDurationMs = 60_000L,
            startBattery = 100,
            endBattery = 100 - batteryConsumed,
            avgTempC = 175,
            peakTempC = peakTempC,
            heatUpTimeMs = 45_000L,
            heaterWearMinutes = 5
        )
    }

    @Test
    fun `computeDailyStats groups sessions by day correctly`() {
        // Monday, March 23, 2026, 10:00 AM (1774260000000 approx for 2026? No, let's use fixed offsets)
        val day1Ms = 1000000000000L // arbitrary fixed point
        val day2Ms = day1Ms + 24 * 3600_000L

        val summaries = listOf(
            createSummary(1, day1Ms + 1000),      // Day 1
            createSummary(2, day1Ms + 3600_000),  // Day 1
            createSummary(3, day2Ms + 1000)       // Day 2
        )

        val dailyStats = repository.computeDailyStats(summaries, 4)

        assertEquals(2, dailyStats.size)
        assertEquals(2, dailyStats[0].sessionCount) // Day 1
        assertEquals(1, dailyStats[1].sessionCount) // Day 2
    }

    @Test
    fun `computeProfileStats sums totals correctly`() {
        val summaries = listOf(
            createSummary(1, 1000, hitCount = 3),
            createSummary(2, 2000, hitCount = 5)
        )
        val stats = repository.computeProfileStats(summaries, 100)

        assertEquals(2, stats.totalSessions)
        assertEquals(8, stats.totalHits)
        assertEquals(100, stats.lifetimeHeaterMinutes)
    }

    @Test
    fun `computeIntakeStats calculates dosage correctly`() {
        val day1Ms = System.currentTimeMillis() - 3600_000L // 1 hour ago
        val s1 = createSummary(1, day1Ms)
        val s2 = createSummary(2, day1Ms - 1000)
        
        val summaries = listOf(s1, s2)
        val metadataMap = mapOf(
            1L to SessionMetadata(sessionId = 1, isCapsule = true, capsuleWeightGrams = 0.15f),
            2L to SessionMetadata(sessionId = 2, isCapsule = false)
        )

        val stats = repository.computeIntakeStats(summaries, metadataMap, 0.10f, false)

        // Only s1 is a capsule.
        assertEquals(0.15f, stats.totalGramsAllTime, 0.001f)
        assertTrue(stats.capsuleSessionCount == 1)
        assertTrue(stats.freePackSessionCount == 1)
    }

    @Test
    fun `computeEstimatedHeatUpTime gives reasonable estimate`() {
        val s1 = createSummary(1, 1000) // Dummy summary
        val estimate = repository.computeEstimatedHeatUpTime(180, listOf(s1), 3600_000L, 25)
        
        // At 25C, estimating to hit 180C should be some positive value.
        // The default in the repo is often around 45-90 seconds.
        assertTrue(estimate != null && estimate > 0)
    }
}
