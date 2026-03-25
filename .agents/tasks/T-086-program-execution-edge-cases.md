# T-086 — Program Execution Edge Cases & Lifecycle Cleanup

**Phase**: Phase 3 — F-027 Session Programs/Presets
**Blocked by**: T-085
**Unblocks**: Nothing (closes F-027 execution phase)
**Estimated diff**: ~40 lines in SessionFragment.kt + SessionViewModel.kt

## Goal

Handle edge cases and lifecycle cleanup for program execution:
1. Cancel boost Job when session ends (state → IDLE)
2. Cancel boost Job when Fragment is destroyed
3. Prevent manual boost interference (program boost takes priority)
4. Recover gracefully from device disconnect during program execution
5. Reset selected program when backgrounding

## Read these files first

- `app/src/main/java/com/sbtracker/ui/SessionFragment.kt` — understand onViewCreated, onDestroyView, lifecycle observation
- `app/src/main/java/com/sbtracker/SessionViewModel.kt` — review `cancelBoostSchedule()` and `setBoost()` methods
- `app/src/main/java/com/sbtracker/BleViewModel.kt` — understand sessionStats observation and connection state

## Change only these files

- `app/src/main/java/com/sbtracker/SessionFragment.kt`
- `app/src/main/java/com/sbtracker/SessionViewModel.kt` (minimal — edge case handling only)

## Steps

### 1. Cancel boost Job when session ends (SessionFragment)

Observe `bleVm.sessionStats` and cancel when state transitions to IDLE:

```kotlin
viewLifecycleOwner.lifecycleScope.launch {
    bleVm.sessionStats.collectLatest { stats ->
        if (stats.state == SessionTracker.State.IDLE) {
            sessionVm.cancelBoostSchedule()
        }
    }
}
```

Place this in `onViewCreated()` alongside other ViewModel observations.

### 2. Cancel boost Job when Fragment is destroyed (SessionFragment)

Add cleanup in `onDestroyView()`:

```kotlin
override fun onDestroyView() {
    sessionVm.cancelBoostSchedule()
    super.onDestroyView()
}
```

This ensures the Job is cleaned up even if the session is still active (e.g., user navigates away).

### 3. Prevent manual boost interference — program boost takes priority (SessionViewModel)

Modify `setBoost()` to check if a program is currently executing:

```kotlin
fun setBoost(offsetC: Int) {
    // If a program is executing (boostJob is active), ignore manual boost commands
    if (boostJob?.isActive == true) {
        return // Silent ignore — program controls boost timing
    }
    bleManager.sendWrite(BlePacket.buildStatusWrite(BleConstants.WRITE_BOOST, boostC = offsetC.coerceAtLeast(0)))
}
```

**Rationale**: Once a program is running, user manual boost attempts should not interfere. The program's scheduled boosts take full priority.

### 4. Recover from device disconnect during program execution (SessionViewModel)

Modify `startSessionWithProgram()` to handle connection loss:

```kotlin
fun startSessionWithProgram(program: SessionProgram) {
    boostJob?.cancel()

    // 1. Set target temperature
    setTemp(program.targetTempC, 1)

    // 2. Start the heater
    setHeater(true)

    // 3. Schedule boost steps with disconnect recovery
    val steps = parseBoostSteps(program.boostStepsJson)
    if (steps.isEmpty()) return

    boostJob = viewModelScope.launch {
        for (step in steps) {
            if (step.offsetSec > 0) {
                try {
                    delay(step.offsetSec * 1000L)
                } catch (e: CancellationException) {
                    // Job was cancelled (disconnect or user action)
                    break
                }
            }
            if (!isActive) break // Check again after delay

            // Verify device is still connected before sending boost
            // (This assumes BleViewModel exposes a connection state)
            // if (!bleVm.isConnected.value) break

            if (step.boostC > 0) {
                setBoost(step.boostC)
            }
        }
    }
}
```

**Note**: If BleViewModel exposes connection state, add an optional check before `setBoost()`. For now, rely on implicit BleManager failure handling.

### 5. Reset selected program on pause/background (SessionFragment)

When the Fragment is paused (user navigates away), clear the selected program:

```kotlin
override fun onPause() {
    sessionVm.selectProgram(null)
    super.onPause()
}
```

This prevents confusion if the user returns to the session screen hours later with a stale program selection.

### 6. Add logging for debugging edge cases (SessionViewModel)

```kotlin
private fun parseBoostSteps(json: String): List<BoostStep> {
    return try {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).map {
            val obj = arr.getJSONObject(it)
            BoostStep(obj.optInt("offsetSec", 0), obj.optInt("boostC", 0))
        }
    } catch (e: Exception) {
        Log.w("SessionViewModel", "Failed to parse boostStepsJson: ${e.message}")
        emptyList()
    }
}

fun startSessionWithProgram(program: SessionProgram) {
    Log.d("SessionViewModel", "Starting session with program: ${program.name}")
    // ... rest of method
}

fun cancelBoostSchedule() {
    Log.d("SessionViewModel", "Cancelling boost schedule")
    boostJob?.cancel()
    boostJob = null
}
```

Add `import android.util.Log` if not present.

### 7. Run `./gradlew assembleDebug` and confirm it passes

All edge case handling is now in place.

## Acceptance criteria

- [ ] Boost Job is cancelled when session ends (state → IDLE)
- [ ] Boost Job is cancelled when Fragment is destroyed (onDestroyView)
- [ ] Manual `setBoost()` calls are ignored if a program is executing
- [ ] Program execution handles CancellationException gracefully during delay
- [ ] Selected program is cleared on Fragment pause (onPause)
- [ ] Logging shows program start, cancellation, and parse errors
- [ ] Device disconnect during boost execution does not crash the app
- [ ] Boost steps resume naturally if connection is restored (no manual retry needed)
- [ ] `./gradlew assembleDebug` passes

## Do NOT

- Remove or modify existing `setBoost()` signature — only add the guard check
- Implement boost step visual feedback (display current step, ETA) — that's a Phase 2 enhancement
- Change the "No Program" chip behavior — it should persist across all edge cases
- Modify BleManager, BleService, SessionTracker, or ProgramRepository
- Add retry logic for failed boost commands — let BleManager handle failures implicitly

## Edge Cases Documented

| Scenario | Behavior | Test |
|----------|----------|------|
| Manual boost during program | Ignored; program boost takes priority | Tap boost button while program running; verify no command sent |
| Disconnect during program | Job cancelled implicitly; session state → IDLE | Pull device connection; verify Job cancelled on reconnect |
| Fragment destroyed mid-program | Job cancelled explicitly in onDestroyView | Navigate away during program; return; verify no orphaned Job |
| Session ends during program | Job cancelled when state → IDLE observed | Program completes naturally; verify boost Job nullified |
| User backgrounds app during program | Selected program cleared in onPause; Job runs to completion in background | Press home key; return to app; verify program selection reset |
