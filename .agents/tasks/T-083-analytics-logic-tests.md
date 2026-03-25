# T-083 — Comprehensive Unit Tests for Analytics Logic (F-042)

**Phase**: Phase 1 — Foundation
**Blocked by**: T-010 (Unit Tests groundwork)
**Estimated diff**: ~250 lines across 4 test files

## Goal
Achieve 100% logic coverage on pure analytics functions in `AnalyticsRepository`, ensuring computed stats (HistoryStats, UsageInsights, PersonalRecords, DailyStats, BatteryInsights) are correct across edge cases and real-world data scenarios.

## Read these files first
- `app/src/main/java/com/sbtracker/analytics/AnalyticsRepository.kt` — all public analytics functions
- `app/src/main/java/com/sbtracker/data/SessionSummary.kt` — SessionSummary fields and semantics
- `app/src/test/java/com/sbtracker/` — existing test structure (T-010)

## Change only these files
- `app/src/test/java/com/sbtracker/analytics/AnalyticsRepositoryTest.kt` (new)
- `app/src/test/java/com/sbtracker/data/SessionSummaryTestData.kt` (new, test fixtures)

## Steps

### 1. Create Test Fixtures
Create a `SessionSummaryTestData.kt` in `app/src/test/java/com/sbtracker/data/`:
```kotlin
object SessionSummaryTestData {
    fun createSummary(
        sessionId: Long = 1L,
        startTimeMs: Long = 0L,
        durationMs: Long = 300000L,  // 5 min default
        hits: Int = 5,
        batteryStartPercent: Int = 100,
        batteryEndPercent: Int = 85,
        peakTempC: Int = 200,
        dayStartHour: Int = 0
    ): SessionSummary = SessionSummary(
        sessionId = sessionId,
        startTimeMs = startTimeMs,
        durationMs = durationMs,
        hitCount = hits,
        batteryStartPercent = batteryStartPercent,
        batteryEndPercent = batteryEndPercent,
        peakTempC = peakTempC,
        dayStartHour = dayStartHour
    )
}
```

### 2. Test `computeHistoryStats()`
```kotlin
// Test empty list
// Test single session
// Test multiple sessions with varied durations/hits
// Test battery drain averaging
// Test date filtering (sessions on different days)
// Test dayStartHour boundary crossing (sessions that span midnight)
```

**Acceptance criteria**:
- [ ] avgDurationMs correctly averages all session durations
- [ ] totalHits sums all hits
- [ ] avgHitsPerSession = totalHits / sessionCount
- [ ] avgBatteryDrain averages (start% - end%) correctly
- [ ] Returns null for empty list

### 3. Test `computeUsageInsights()`
```kotlin
// Test current streak calculation:
//   - Consecutive days → correct count
//   - Single session → streak of 1
//   - Gap breaks streak
// Test daily frequency (sessions per day)
// Test time-of-day patterns (hour-binned frequency)
// Test weekly comparison (this week vs. last week)
```

**Acceptance criteria**:
- [ ] currentStreak counts consecutive days with ≥1 session
- [ ] dailyFrequency is normalized to sessions/day
- [ ] timeOfDayDistribution has correct hour bins
- [ ] weeklyComparison computes prev week correctly with dayStartHour offset

### 4. Test `computePersonalRecords()`
```kotlin
// Test max hits (find session with highest hit count)
// Test longest session (max duration)
// Test most efficient (hits/battery drain)
// Test max temperature reached
// Test when records don't exist (empty list or all same values)
```

**Acceptance criteria**:
- [ ] mostHitsSession returns correct session with max hits
- [ ] longestSession returns correct session with max duration
- [ ] mostEfficientSession = max(hits / batteryDrain)
- [ ] Returns null/defaults for empty list

### 5. Test `computeDailyStats()`
```kotlin
// Test aggregation by calendar day (respecting dayStartHour)
// Test sessions spanning midnight with dayStartHour offset
// Test date-to-stats mapping correctness
// Test battery deltas per day
```

**Acceptance criteria**:
- [ ] DailyStats list has one entry per day with sessions
- [ ] sessionCount, hitCount, batteryDrain sum correctly per day
- [ ] Respects dayStartHour offset for day boundaries

### 6. Test `computeBatteryInsights()`
```kotlin
// Test depth-of-discharge (min battery level across all sessions)
// Test charge patterns (gaps between consecutive charge cycles)
// Test drain trend (early sessions vs. recent sessions)
```

**Acceptance criteria**:
- [ ] depthOfDischarge = lowest battery% across all sessions
- [ ] chargeFrequency computes time between charge cycles
- [ ] drainTrendPercent shows acceleration or deceleration

### 7. Run Tests
```bash
./gradlew test
```

Ensure all tests pass and coverage report includes AnalyticsRepository at 100% branch coverage.

## Acceptance criteria
- [ ] `app/src/test/java/com/sbtracker/analytics/AnalyticsRepositoryTest.kt` exists with ≥25 test cases
- [ ] All pure functions in AnalyticsRepository are tested
- [ ] Edge cases covered: empty lists, single items, boundary times (midnight), null optionals
- [ ] `./gradlew test` passes 100%
- [ ] Code coverage for AnalyticsRepository ≥ 95%

## Do NOT
- Mock database calls or repositories (test pure functions only)
- Test UI binding or ViewModel integration (those are separate)
- Add dependencies beyond `junit`, `kotlin-test`, `mockk` (if needed for time mocking)
- Test analytics that depend on live device data (that's B-010 integration scope)
