# T-084 — Program Drain Estimation: AnalyticsRepository + HistoryViewModel + SessionViewModel

**Phase**: Phase 3 — F-027 Session Programs/Presets
**Blocked by**: T-043 (done)
**Estimated diff**: ~50 lines across 3 files

## Goal
Compute an average battery drain rate (% per minute) from session history and expose it
so that `SessionFragment` can show an estimated battery cost when a program is selected.
The UI that displays this estimate is wired in T-085.

## Read these files first
- `app/src/main/java/com/sbtracker/analytics/AnalyticsRepository.kt` — add `computeAvgDrainPerMinute()`
- `app/src/main/java/com/sbtracker/HistoryViewModel.kt` — expose `avgDrainPerMinute` StateFlow
- `app/src/main/java/com/sbtracker/SessionViewModel.kt` — add estimation helpers

## Change only these files
- `app/src/main/java/com/sbtracker/analytics/AnalyticsRepository.kt`
- `app/src/main/java/com/sbtracker/HistoryViewModel.kt`
- `app/src/main/java/com/sbtracker/SessionViewModel.kt`

## Steps

### 1. `AnalyticsRepository.kt` — add `computeAvgDrainPerMinute()`

Add alongside the other pure `compute*` functions (they all take `List<SessionSummary>`):

```kotlin
/**
 * Returns the average battery drain rate in % per minute, computed from
 * sessions that have both a positive battery drain and a non-zero duration.
 * Returns 0.0 when there is no qualifying history.
 */
fun computeAvgDrainPerMinute(summaries: List<SessionSummary>): Double {
    val qualifying = summaries.filter { it.batteryConsumed > 0 && it.durationMs > 0 }
    if (qualifying.isEmpty()) return 0.0
    return qualifying.sumOf { it.batteryConsumed.toDouble() / (it.durationMs / 60_000.0) } /
            qualifying.size
}
```

This is a pure function — no DB access, no suspend. Consistent with all other `compute*`
methods in this class.

### 2. `HistoryViewModel.kt` — expose `avgDrainPerMinute`

Find where `HistoryViewModel` derives stats from `sessionSummaries`. Add:

```kotlin
val avgDrainPerMinute: StateFlow<Double> = sessionSummaries
    .map { analyticsRepo.computeAvgDrainPerMinute(it) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
```

`analyticsRepo` is already injected into `HistoryViewModel`. No new dependency needed.

### 3. `SessionViewModel.kt` — add estimation helpers

These helpers let `SessionFragment` show an estimate without touching `HistoryViewModel`
directly (ViewModels should not reference each other):

```kotlin
/**
 * Estimates total program duration in minutes by summing step durations from
 * boostStepsJson. The last step's duration is assumed to be 60 seconds.
 * Returns 0.0 if the JSON is malformed or the program has no steps.
 */
fun estimateProgramDurationMinutes(program: SessionProgram): Double {
    return try {
        val arr = org.json.JSONArray(program.boostStepsJson)
        if (arr.length() == 0) return 0.0
        val lastOffset = arr.getJSONObject(arr.length() - 1).getInt("offsetSec")
        (lastOffset + 60) / 60.0   // last step assumed 60s duration
    } catch (e: Exception) { 0.0 }
}

/**
 * Estimates battery drain for a program given the current avg drain rate.
 * Returns 0 when rate or duration is unknown.
 */
fun estimateProgramDrain(program: SessionProgram, avgDrainPerMinute: Double): Int {
    if (avgDrainPerMinute <= 0.0) return 0
    return (estimateProgramDurationMinutes(program) * avgDrainPerMinute).toInt()
}
```

### 4. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] `AnalyticsRepository.computeAvgDrainPerMinute(summaries)` exists and returns 0.0 for empty input
- [ ] `HistoryViewModel.avgDrainPerMinute: StateFlow<Double>` exists
- [ ] `SessionViewModel.estimateProgramDurationMinutes(program)` and `estimateProgramDrain(program, rate)` exist
- [ ] `./gradlew assembleDebug` passes

## Do NOT
- Access the DB from `computeAvgDrainPerMinute` — it must remain a pure function
- Inject `HistoryViewModel` into `SessionViewModel` or vice versa
- Wire any UI in this task — that is T-085
- Modify `AppDatabase`, migrations, or `SessionProgram` entity
