# T-084 — Add Drain Confidence Flag to SessionStats (B-015)

**Phase**: Phase 3 — Data Trust & Protocol Reliability
**Blocked by**: T-083 (Analytics Logic Tests)
**Estimated diff**: ~60 lines across 3 files

## Goal
Add a `drainEstimateReliable` boolean flag to `SessionStats` and related analytics models. This flag indicates whether the battery drain estimate for a session is based on trustworthy data (sufficient similar prior sessions) or is a low-confidence guess. UI will show a warning badge when drain estimates are unreliable.

## Context
Currently, `computeSessionStats()` estimates battery drain by averaging the last 5 similar sessions (temp ±10°C). If fewer than 3 prior sessions exist, the estimate is speculative and should be flagged as unreliable for the user.

## Read these files first
- `app/src/main/java/com/sbtracker/data/SessionStats.kt` — current fields
- `app/src/main/java/com/sbtracker/analytics/AnalyticsRepository.kt` — `computeSessionStats()` function (~50 lines)
- `app/src/main/java/com/sbtracker/ui/history/SessionSummaryAdapter.kt` — where SessionStats are displayed

## Change only these files
- `app/src/main/java/com/sbtracker/data/SessionStats.kt` (add field)
- `app/src/main/java/com/sbtracker/analytics/AnalyticsRepository.kt` (compute flag)
- `app/src/main/java/com/sbtracker/ui/history/SessionSummaryAdapter.kt` (show warning badge)

## Steps

### 1. Add Flag to SessionStats Data Class
In `SessionStats.kt`, add:
```kotlin
data class SessionStats(
    val sessionId: Long,
    val startTimeMs: Long,
    val durationMs: Long,
    val hitCount: Int,
    val batteryStartPercent: Int,
    val batteryEndPercent: Int,
    val estimatedDrainPercent: Int,
    val drainEstimateReliable: Boolean = true,  // NEW: default true for backward compat
    val peakTempC: Int,
    val startingBattery: Int = batteryStartPercent
)
```

### 2. Compute Flag in `computeSessionStats()`
In `AnalyticsRepository.kt`, modify the drain calculation logic:

```kotlin
fun computeSessionStats(
    sessionId: Long,
    summaries: List<SessionSummary>,
    targetTempC: Int
): SessionStats {
    // ... existing code ...

    // Find similar sessions for drain estimate
    val similarSessions = summaries.filter {
        (it.peakTempC - targetTempC).absoluteValue <= 10
    }

    val drainEstimateReliable = similarSessions.size >= 3  // NEW

    val estimatedDrainPercent = if (similarSessions.isNotEmpty()) {
        similarSessions.take(5)
            .map { it.batteryStartPercent - it.batteryEndPercent }
            .average()
            .toInt()
    } else {
        10  // default guess
    }

    return SessionStats(
        sessionId = sessionId,
        startTimeMs = session.startTimeMs,
        // ... other fields ...
        estimatedDrainPercent = estimatedDrainPercent,
        drainEstimateReliable = drainEstimateReliable  // NEW
    )
}
```

### 3. Add Visual Warning Badge in SessionSummaryAdapter
In `SessionSummaryAdapter.kt`, bind() method, add a warning indicator when `drainEstimateReliable == false`:

```kotlin
if (!sessionStats.drainEstimateReliable) {
    // Show warning icon next to battery drain
    drainWarningIcon.visibility = View.VISIBLE
    drainWarningIcon.setContentDescription("Low confidence drain estimate")
    drainValue.setCompoundDrawablesRelativeWithIntrinsicBounds(
        null, null,
        ResourcesCompat.getDrawable(context, R.drawable.ic_warning_small, null),
        null
    )
} else {
    drainWarningIcon.visibility = View.GONE
}
```

### 4. Update Related Functions
Check these functions and ensure they propagate the flag if they use SessionStats:
- `computeHistoryStats()` — if it aggregates drain, mention when aggregated stats include unreliable sessions
- `computeProfileStats()` — if lifetime drain is computed, flag it if recent sessions are unreliable

For now: **Document in comments** that future UI may want to surface confidence levels.

### 5. Run Tests
```bash
./gradlew test
```

Ensure existing tests still pass and new flag doesn't break anything.

## Acceptance criteria
- [ ] `SessionStats.drainEstimateReliable: Boolean` field added
- [ ] `computeSessionStats()` sets flag = (similarSessions.size >= 3)
- [ ] Warning badge/icon shown in session history row when flag = false
- [ ] `./gradlew test` passes
- [ ] Build passes: `./gradlew assembleDebug`

## Do NOT
- Change the underlying drain estimate algorithm (only add the confidence flag)
- Hide sessions with unreliable drain (always show, just warn)
- Add configuration for the "3 similar sessions" threshold (hardcode for now; may be configurable in future)
- Change any other SessionStats fields
