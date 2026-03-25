# T-061 — SettingsViewModel: Restore Action + Settings UI Button

**Phase**: Phase 3 — F-026 Data Backup/Restore
**Blocked by**: T-059, T-060
**Estimated diff**: ~45 lines changed across 2 files

## Goal
Expose a `triggerRestore(uri: Uri)` method and a `restoreResult: SharedFlow<RestoreResult>` in `SettingsViewModel` (delegating to `RestoreRepository`), and add a "Restore Database" button in `SettingsFragment` that registers an `ActivityResultContract` file-picker launcher.

## Read these files first
- `app/src/main/java/com/sbtracker/SettingsViewModel.kt` — read the state after T-060 (injected `BackupRepository`, `triggerBackup()`, `backupUri` already present).
- `app/src/main/java/com/sbtracker/ui/SettingsFragment.kt` — read the state after T-060 (backup button already wired). Understand how to register `registerForActivityResult` in a Fragment.
- `app/src/main/java/com/sbtracker/data/RestoreRepository.kt` — read `restoreFrom(uri)` signature and `restoreResult: SharedFlow<RestoreResult>` (created in T-059).

## Change only these files
- `app/src/main/java/com/sbtracker/SettingsViewModel.kt`
- `app/src/main/java/com/sbtracker/ui/SettingsFragment.kt`

## Steps

1. **`SettingsViewModel.kt` — inject `RestoreRepository` and add `triggerRestore()`.**
   - Add `private val restoreRepo: RestoreRepository` as a constructor parameter.
   - Expose `val restoreResult = restoreRepo.restoreResult` as a pass-through `SharedFlow<RestoreResult>`.
   - Add:
     ```kotlin
     fun triggerRestore(uri: Uri) {
         viewModelScope.launch { restoreRepo.restoreFrom(uri) }
     }
     ```

2. **`SettingsFragment.kt` — add "Restore Database" button and file-picker launcher.**
   - Declare the launcher as a class-level property (before `onViewCreated`):
     ```kotlin
     private val restoreLauncher = registerForActivityResult(
         ActivityResultContracts.OpenDocument()
     ) { uri: Uri? ->
         uri?.let { settingsVm.triggerRestore(it) }
     }
     ```
   - Add a `binding.btnRestoreDatabase` button (following the same programmatic style used for `btnBackupDatabase` in T-060).
   - Wire the click listener:
     ```kotlin
     binding.btnRestoreDatabase.setOnClickListener {
         restoreLauncher.launch(arrayOf("*/*"))
     }
     ```
   - Observe `settingsVm.restoreResult` in `viewLifecycleOwner.lifecycleScope` and show a brief `Toast`:
     - On `RestoreResult.Success` → `Toast.makeText(…, "Restore complete. Restarting…", Toast.LENGTH_SHORT).show()` — the actual restart is handled in `MainActivity` (T-062).
     - On `RestoreResult.Failure(reason)` → `Toast.makeText(…, reason, Toast.LENGTH_LONG).show()`.

3. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] `SettingsViewModel.triggerRestore(uri)` calls `restoreRepo.restoreFrom(uri)`.
- [ ] `SettingsViewModel.restoreResult` exposes `restoreRepo.restoreResult`.
- [ ] "Restore Database" button in `SettingsFragment` opens the system file picker on click.
- [ ] Selecting a file invokes `settingsVm.triggerRestore(uri)`.
- [ ] A Toast is shown on both success and failure without crashing.
- [ ] `./gradlew assembleDebug` passes.

## Do NOT
- Do not restart the app from `SettingsFragment` — that is `MainActivity`'s responsibility (T-062).
- Do not modify any file other than `SettingsViewModel.kt` and `SettingsFragment.kt`.
- Do not add MIME type filtering that would prevent `.db` files from appearing in the picker — use `"*/*"` to keep it permissive; validation happens in `RestoreRepository`.
