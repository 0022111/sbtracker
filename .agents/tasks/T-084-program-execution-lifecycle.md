# T-084 — Program Execution Lifecycle (SessionViewModel Integration)

**Phase**: Phase 3 — F-027 Session Programs/Presets
**Blocked by**: T-083
**Unblocks**: T-085
**Estimated diff**: ~70 lines in SessionViewModel.kt

## Goal

Add program selection state and execution logic to SessionViewModel. Wire up `startSessionWithProgram()`, `selectProgram()`, and `cancelBoostSchedule()` methods. This layer owns the coroutine Job and boost timing, but does NOT handle UI updates or edge cases (those are T-085 and T-086).

## Read these files first

- `app/src/main/java/com/sbtracker/SessionViewModel.kt` — focus on existing `startSession()`, `setBoost()`, `setTemp()`, `setHeater()` methods
- `app/src/main/java/com/sbtracker/data/SessionProgram.kt` — understand the SessionProgram entity structure
- T-083 task file — review BoostStep and parseBoostSteps()

## Change only these files

- `app/src/main/java/com/sbtracker/SessionViewModel.kt`

## Steps

### 1. Add ProgramRepository injection (if not already added from T-045)

```kotlin
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val bleManager: BleManager,
    private val programRepository: ProgramRepository
) : ViewModel() {
```

### 2. Add program state flows

```kotlin
val programs: StateFlow<List<SessionProgram>> = programRepository.programs
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

private val _selectedProgram = MutableStateFlow<SessionProgram?>(null)
val selectedProgram: StateFlow<SessionProgram?> = _selectedProgram.asStateFlow()

fun selectProgram(program: SessionProgram?) {
    _selectedProgram.value = program
}
```

### 3. Add boost Job field and execution methods

```kotlin
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
```

### 4. Add delay import (if not present)

```kotlin
import kotlinx.coroutines.delay
```

### 5. Verify the logic in isolation

- `startSessionWithProgram()` does NOT crash on empty boostStepsJson
- Boost steps execute in order with correct delays
- `isActive` check prevents firing commands after cancellation
- `cancelBoostSchedule()` cleanly nullifies the Job

### 6. Run `./gradlew assembleDebug` and confirm it passes

No UI or Fragment changes yet — purely ViewModel logic.

## Acceptance criteria

- [ ] SessionViewModel has `selectedProgram` StateFlow (nullable, reactive)
- [ ] `selectProgram()` updates the state
- [ ] `startSessionWithProgram()` calls setTemp + setHeater + schedules boost steps with correct timing
- [ ] Boost steps only fire if boostC > 0
- [ ] Job cancellation is checked with `isActive` before each command
- [ ] `cancelBoostSchedule()` cleanly cancels and nullifies the Job
- [ ] No crashes on empty or invalid boostStepsJson
- [ ] `./gradlew assembleDebug` passes

## Do NOT

- Add UI changes — that's T-085
- Handle edge cases (disconnect, backgrounding, etc.) — that's T-086
- Persist selected program across Fragment recreations
- Modify BleManager, BleService, or SessionTracker
- Add preview temp updates — that's T-085 (Fragment responsibility)
