# T-046 — Apply Program When Starting a Session

**Phase**: Phase 3 — F-027 Session Programs/Presets
**Blocked by**: T-045
**Estimated diff**: ~130 lines across 2 files

## Goal
Add a program picker chip row to `SessionFragment`. When the user starts the heater with
a program selected, set the target temperature, start the heater, and schedule boost steps
via a cancellable coroutine using `setBoost()` — NOT by manipulating target temp.

## ⚠️ Critical design notes (read before coding)

### Use `setBoost()`, not `setTemp(base + boost)`
`SessionViewModel.setBoost(offsetC)` sends `WRITE_BOOST` — the device's dedicated boost
offset command. Do NOT call `setTemp(targetTempC + step.boostC, ...)` for scheduled steps.
Reasons:
1. `SessionTracker` hit detection watches `isTempStable` via `prevTargetTempC`. Changing
   target temp triggers false instability and breaks hit detection mid-session.
2. Boost offset is semantically separate from base temperature. The device treats them
   differently on its display and for setpoint calculations.
3. `setBoost()` already exists and is correct — use it.

### Always cancel boost job on session end
Store the scheduled coroutine as a `Job?` in `SessionViewModel`. Cancel it whenever:
- The user taps the stop/off button
- The session ends naturally (observed via `SessionTracker.state` → IDLE)
- The Fragment is destroyed

### `setTemp` needs a heater mode
`setTemp(tempC, mode)` requires the current heater mode. Use mode `1` (standard) for all
program starts — this matches `startSession()` and is correct for all current S&B devices.
If future devices need variable mode, this is the place to revisit.

## Read these files first
- `app/src/main/java/com/sbtracker/ui/SessionFragment.kt` — current start/stop flow; how `sessionVm` and `bleVm` are accessed
- `app/src/main/java/com/sbtracker/SessionViewModel.kt` — existing `setBoost()`, `setTemp()`, `setHeater()` signatures
- `app/src/main/java/com/sbtracker/BleViewModel.kt` — observe `sessionStats.state` to know when session ends (to cancel boost job)

## Change only these files
- `app/src/main/java/com/sbtracker/SessionViewModel.kt`
- `app/src/main/java/com/sbtracker/ui/SessionFragment.kt`

## Steps

### 1. `SessionViewModel.kt` — add program state and cancellable boost scheduler

```kotlin
@Inject lateinit var programRepository: ProgramRepository

val programs: StateFlow<List<SessionProgram>> = programRepository.programs
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

private val _selectedProgram = MutableStateFlow<SessionProgram?>(null)
val selectedProgram: StateFlow<SessionProgram?> = _selectedProgram.asStateFlow()

fun selectProgram(program: SessionProgram?) { _selectedProgram.value = program }

private var boostJob: Job? = null

fun startSessionWithProgram(program: SessionProgram) {
    boostJob?.cancel()
    // 1. Set target temperature (mode 1 = standard heater mode)
    setTemp(program.targetTempC, 1)
    // 2. Start the heater
    setHeater(true)
    // 3. Schedule boost steps
    val steps = parseBoostSteps(program.boostStepsJson)
    if (steps.isEmpty()) return
    boostJob = viewModelScope.launch {
        for (step in steps) {
            if (step.offsetSec > 0) delay(step.offsetSec * 1000L)
            if (!isActive) break // cancelled — do not fire further commands
            if (step.boostC > 0) {
                setBoost(step.boostC)
            }
        }
    }
}

fun cancelBoostSchedule() {
    boostJob?.cancel()
    boostJob = null
}

private data class BoostStep(val offsetSec: Int, val boostC: Int)

private fun parseBoostSteps(json: String): List<BoostStep> {
    return try {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).map {
            val obj = arr.getJSONObject(it)
            BoostStep(obj.optInt("offsetSec", 0), obj.optInt("boostC", 0))
        }
    } catch (e: Exception) { emptyList() }
}
```

### 2. `SessionFragment.kt` — program chip row and wired start button

Observe `sessionVm.programs` and `sessionVm.selectedProgram` in `viewLifecycleOwner.lifecycleScope`.

**Chip row UI** (programmatic, no XML):
- Horizontal `HorizontalScrollView` containing a `LinearLayout`
- First chip: "No Program" — always present; tapping calls `sessionVm.selectProgram(null)` and resets boost to 0 via `sessionVm.setBoost(0)` if session is active
- One chip per `SessionProgram` from the list
- Selected chip highlighted with `color_blue` background tint; unselected uses `color_dark_card`
- Show chip row only when device is connected and not actively in a session (hide it once heater turns on to avoid mid-session confusion)

**Wire start button**:
```kotlin
btnStartNormal.setOnClickListener {
    val prog = sessionVm.selectedProgram.value
    if (prog != null) {
        sessionVm.startSessionWithProgram(prog)
    } else {
        sessionVm.startSession(bleVm.targetTemp.value)
    }
}
```

**Cancel boost on session end**: Observe `bleVm.sessionStats` state:
```kotlin
viewLifecycleOwner.lifecycleScope.launch {
    bleVm.sessionStats.collectLatest { stats ->
        if (stats.state == SessionTracker.State.IDLE) {
            sessionVm.cancelBoostSchedule()
        }
    }
}
```

**Preview temp**: When a program chip is selected, update `tvTargetBase` (or equivalent
temp display TextView) to show `program.targetTempC` as a preview before session start.

### 3. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] Session screen shows a horizontally scrollable program chip row above start buttons
- [ ] Chip row is hidden once heater turns on (cannot change program mid-session)
- [ ] Selecting a chip previews the program's target temp in the temp display
- [ ] Starting with a program: sets target temp, starts heater, schedules boost steps using `setBoost()` not `setTemp(base+offset)`
- [ ] Boost steps fire at the correct second offsets (via coroutine delay)
- [ ] Boost job is cancelled when session ends (state → IDLE) or when Fragment is destroyed (via viewModelScope/viewLifecycleOwner)
- [ ] "No Program" chip restores standard `startSession()` behavior
- [ ] `./gradlew assembleDebug` passes

## Do NOT
- Call `setTemp(targetTempC + step.boostC, ...)` for boost steps — use `setBoost(step.boostC)` exclusively
- Leave the boost coroutine running without a `Job` reference — always store it in `boostJob`
- Show the chip row during an active session — hide it once the heater is on
- Modify `BleManager`, `BleService`, `SessionTracker`, or `ProgramRepository`
- Persist the selected program across Fragment recreations — selection is ephemeral per screen visit
