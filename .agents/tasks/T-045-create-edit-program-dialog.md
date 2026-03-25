# T-045 — Create/Edit Program Dialog

**Phase**: Phase 3 — F-027 Session Programs/Presets
**Blocked by**: T-044
**Estimated diff**: ~120 lines across 2 files

## Goal
Implement a full Create/Edit program `AlertDialog` that allows the user to set program name, target temperature, base boost offset, and up to 3 timed boost steps.

## Read these files first
- `app/src/main/java/com/sbtracker/ui/SettingsFragment.kt` — where "+ New Program" is wired (T-044 output); this task replaces the stub dialog
- `app/src/main/java/com/sbtracker/SettingsViewModel.kt` — `saveProgram` method to be added here

## Change only these files
- `app/src/main/java/com/sbtracker/SettingsViewModel.kt`
- `app/src/main/java/com/sbtracker/ui/SettingsFragment.kt`

## Steps

1. **SettingsViewModel.kt** — add save method:
   ```kotlin
   fun saveProgram(name: String, targetTempC: Int, boostOffsetC: Int, boostStepsJson: String) {
       viewModelScope.launch {
           programRepository.saveProgram(
               SessionProgram(name = name, targetTempC = targetTempC,
                   boostOffsetC = boostOffsetC, boostStepsJson = boostStepsJson)
           )
       }
   }
   ```

2. **SettingsFragment.kt** — replace the stub from T-044 with `showProgramEditDialog(existing: SessionProgram? = null)`:
   - Build the dialog programmatically using a `LinearLayout` with these fields:
     - Name: `EditText` (pre-filled if editing)
     - Target Temp: `EditText` with `inputType = TYPE_CLASS_NUMBER` (°C, range 40–230)
     - Boost Offset: `EditText` with `inputType = TYPE_CLASS_NUMBER` (°C, range 0–20)
     - Up to 3 boost step rows, each with two `EditText`s: offset in seconds + boost °C
       - Boost step rows are labelled "Step 1 at Xs: +Y°C"
   - "Save" button: validates name is non-empty and temp is in range, serializes steps to JSON string:
     `[{"offsetSec": X, "boostC": Y}, ...]` — only include steps where both fields are non-empty
     Then calls `settingsVm.saveProgram(...)`
   - "Cancel" button: dismiss dialog
   - Wire "+ New Program" to `showProgramEditDialog(null)` and each program row's edit tap (if any) to `showProgramEditDialog(program)`

3. JSON serialization of steps: use `org.json.JSONArray` and `org.json.JSONObject` (available on all Android API levels, no extra dependency needed).

4. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] Dialog opens when "+ New Program" is tapped
- [ ] Name, target temp, boost offset, and up to 3 boost step fields are present
- [ ] "Save" creates a new `SessionProgram` row via `settingsVm.saveProgram(...)`
- [ ] New program immediately appears in the programs list
- [ ] Input validation rejects empty name and out-of-range temperatures with a Toast
- [ ] `./gradlew assembleDebug` passes

## Do NOT
- Create XML layout files — build dialog UI programmatically
- Allow editing of `isDefault = true` presets
- Add more than 3 boost steps in the UI — complexity caps here
