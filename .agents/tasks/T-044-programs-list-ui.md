# T-044 — Programs List UI in SettingsFragment

**Phase**: Phase 3 — F-027 Session Programs/Presets
**Blocked by**: T-043
**Estimated diff**: ~90 lines across 2 files

## Goal
Add a "Session Programs" section to `SettingsFragment` that lists all programs, lets the user delete user-created ones, and navigates to the create/edit dialog (T-045).

## Read these files first
- `app/src/main/java/com/sbtracker/ui/SettingsFragment.kt` — understand current settings layout building pattern
- `app/src/main/java/com/sbtracker/SettingsViewModel.kt` — understand what state is exposed, where to add `programs` flow

## Change only these files
- `app/src/main/java/com/sbtracker/SettingsViewModel.kt`
- `app/src/main/java/com/sbtracker/ui/SettingsFragment.kt`

## Steps

1. **SettingsViewModel.kt** — add program management:
   ```kotlin
   @Inject lateinit var programRepository: ProgramRepository

   val programs: StateFlow<List<SessionProgram>> = programRepository.programs
       .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

   fun deleteProgram(id: Long) {
       viewModelScope.launch { programRepository.deleteProgram(id) }
   }
   ```

2. **SettingsFragment.kt** — add a "Session Programs" card section:
   - Collect `settingsVm.programs` in `viewLifecycleOwner.lifecycleScope`
   - For each program, render a row showing: `[program.name]  [program.targetTempC]°C  [DELETE button if !isDefault]`
   - Add a "+ New Program" button below the list
   - "DELETE" calls `settingsVm.deleteProgram(program.id)` with a confirmation Toast
   - "+ New Program" shows an `AlertDialog` with a name field and target temp field as a placeholder (full dialog is T-045 — this stub can navigate using a tag or show a simple two-field dialog inline)
   - Use the same programmatic View building pattern already in `SettingsFragment` (no new XML layout file needed)

3. Scroll section label: "SESSION PROGRAMS" in the same style as existing section labels in `SettingsFragment`.

4. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] Settings screen shows a "SESSION PROGRAMS" section
- [ ] Each program row shows name and target temperature
- [ ] User-created programs have a Delete button; default presets do not
- [ ] Delete button removes the program from the list
- [ ] "+ New Program" button is visible (full implementation in T-045)
- [ ] `./gradlew assembleDebug` passes

## Do NOT
- Create a new Fragment or Activity for this — it stays inside `SettingsFragment`
- Touch `ProgramRepository` or `AppDatabase`
- Implement boost step editing here — keep it to name + targetTempC only for now
