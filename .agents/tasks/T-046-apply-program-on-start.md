# T-046 — Apply Program When Starting a Session

**Phase**: Phase 3 — F-027 Session Programs/Presets
**Blocked by**: T-045
**Estimated diff**: ~100 lines across 2 files

## Goal
Add a program picker to `SessionFragment` so the user can select a `SessionProgram` before starting the heater. On start, the selected program's target temp is sent to the device and its boost steps are scheduled via a coroutine.

## Read these files first
- `app/src/main/java/com/sbtracker/ui/SessionFragment.kt` — current start-session flow: `btnStartNormal.setOnClickListener { sessionVm.startSession(...) }`
- `app/src/main/java/com/sbtracker/SessionViewModel.kt` — `startSession()` and `setTemp()` signatures; understand how BLE commands are issued

## Change only these files
- `app/src/main/java/com/sbtracker/SessionViewModel.kt`
- `app/src/main/java/com/sbtracker/ui/SessionFragment.kt`

## Steps

1. **SessionViewModel.kt** — add program state and start-with-program:
   ```kotlin
   @Inject lateinit var programRepository: ProgramRepository

   val programs: StateFlow<List<SessionProgram>> = programRepository.programs
       .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

   private val _selectedProgram = MutableStateFlow<SessionProgram?>(null)
   val selectedProgram: StateFlow<SessionProgram?> = _selectedProgram.asStateFlow()

   fun selectProgram(program: SessionProgram?) { _selectedProgram.value = program }

   fun startSessionWithProgram(program: SessionProgram) {
       viewModelScope.launch {
           // 1. Set target temperature
           setTemp(program.targetTempC, 1)
           // 2. Start heater
           setHeater(true)
           // 3. Schedule boost steps
           val steps = parseBoostSteps(program.boostStepsJson)
           for (step in steps) {
               if (step.offsetSec > 0) delay(step.offsetSec * 1000L)
               // Apply boost by updating target temp + boost offset
               // Use bleVm.setBoostOffset if available, else setTemp with adjusted value
               setTemp(program.targetTempC + step.boostC, 1)
           }
       }
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

2. **SessionFragment.kt** — add program picker UI above the start buttons:
   - Observe `sessionVm.programs` and `sessionVm.selectedProgram`
   - Render a horizontal chip row: "No Program" chip (always first) + one chip per `SessionProgram`
   - Selected chip is highlighted in `color_blue`; tapping sets `sessionVm.selectProgram(program)` or null
   - Update `btnStartNormal.setOnClickListener`:
     ```kotlin
     btnStartNormal.setOnClickListener {
         val prog = sessionVm.selectedProgram.value
         if (prog != null) sessionVm.startSessionWithProgram(prog)
         else sessionVm.startSession(bleVm.targetTemp.value)
     }
     ```
   - When a program is selected, display the program's target temp in `tvTargetBase` as a preview

3. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] Session screen shows a program chip row
- [ ] Selecting a chip shows the program's target temp as preview
- [ ] Starting with a program sets the correct target temp on the device
- [ ] Boost steps fire at the correct offsets during the session (via coroutine delay)
- [ ] "No Program" chip restores standard manual start behavior
- [ ] `./gradlew assembleDebug` passes

## Do NOT
- Cancel running boost steps if the heater is turned off mid-session (nice-to-have, deferred)
- Modify `BleManager` or `BleService`
- Persist the selected program across sessions — selection resets each time the screen is opened
