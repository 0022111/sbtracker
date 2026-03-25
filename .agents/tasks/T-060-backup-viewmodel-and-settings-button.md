# T-060 — SettingsViewModel: Backup Action + Settings UI Button

**Phase**: Phase 3 — F-026 Data Backup/Restore
**Blocked by**: T-058
**Estimated diff**: ~40 lines changed across 2 files

## Goal
Expose a `triggerBackup()` method in `SettingsViewModel` (delegating to `BackupRepository`) and wire a "Backup Database" button in `SettingsFragment` so the user can initiate a backup from the Settings screen.

## Read these files first
- `app/src/main/java/com/sbtracker/SettingsViewModel.kt` — understand existing ViewModel structure, how it uses Hilt `@Inject constructor`, and where to add the new dependency and method.
- `app/src/main/java/com/sbtracker/ui/SettingsFragment.kt` — understand how existing buttons (e.g. factory reset) are wired: find the `onViewCreated` section, identify the binding property names used for buttons.
- `app/src/main/java/com/sbtracker/data/BackupRepository.kt` — read the `createBackup()` signature and `backupUri: SharedFlow<Uri>` (created in T-058).

## Change only these files
- `app/src/main/java/com/sbtracker/SettingsViewModel.kt`
- `app/src/main/java/com/sbtracker/ui/SettingsFragment.kt`

## Steps

1. **`SettingsViewModel.kt` — inject `BackupRepository` and add `triggerBackup()`.**
   - Add `private val backupRepo: BackupRepository` as a constructor parameter (Hilt injects it automatically).
   - Expose a `val backupUri = backupRepo.backupUri` pass-through `SharedFlow<Uri>`.
   - Add:
     ```kotlin
     fun triggerBackup() {
         viewModelScope.launch { backupRepo.createBackup() }
     }
     ```

2. **`SettingsFragment.kt` — add "Backup Database" button.**
   - In `onViewCreated`, obtain the button via its binding property (e.g. `binding.btnBackupDatabase`). If the layout binding property does not yet exist, add a view ID `btn_backup_database` to the `FragmentSettingsBinding` layout. **However**, because this project uses programmatic views (no XML layouts per `PROJECT.md`), instead follow the existing pattern used for other buttons in `SettingsFragment` — call `binding` to find an existing button reference or create one programmatically in the same style as neighboring buttons.
   - Wire the click listener:
     ```kotlin
     binding.btnBackupDatabase.setOnClickListener {
         settingsVm.triggerBackup()
     }
     ```
   - The `backupUri` SharedFlow is observed in `MainActivity` (T-062). Do NOT start the share intent here.

3. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] `SettingsViewModel` has a `triggerBackup()` method that calls `backupRepo.createBackup()`.
- [ ] `SettingsViewModel.backupUri` exposes `backupRepo.backupUri` as a `SharedFlow<Uri>`.
- [ ] `SettingsFragment` has a "Backup Database" button that calls `settingsVm.triggerBackup()` on click.
- [ ] `./gradlew assembleDebug` passes.

## Do NOT
- Do not open or launch any Intent or share chooser from the Fragment — that is `MainActivity`'s responsibility (T-062).
- Do not add restore UI in this task (see T-061).
- Do not modify any other ViewModel or Fragment.
