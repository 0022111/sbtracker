# T-062 — MainActivity: Wire Backup Share Intent + Restore App Restart

**Phase**: Phase 3 — F-026 Data Backup/Restore
**Blocked by**: T-060, T-061
**Estimated diff**: ~40 lines changed across 1 file

## Goal
Observe `settingsVm.backupUri` and `settingsVm.restoreResult` in `MainActivity` and respond: fire a `ACTION_SEND` share intent for backup URIs, and trigger a process restart (via `ProcessPhoenix` or a manual `Intent` + `exitProcess`) on successful restore so Room reopens cleanly.

## Read these files first
- `app/src/main/java/com/sbtracker/MainActivity.kt` — read the existing `exportUri` observer (lines ~154–164) that fires a share intent for CSV export; replicate that exact pattern for the backup URI. Also note the `lifecycleScope.launch` + `collect` pattern used.
- `app/src/main/java/com/sbtracker/SettingsViewModel.kt` — read the state after T-060/T-061: `backupUri: SharedFlow<Uri>` and `restoreResult: SharedFlow<RestoreResult>`.

## Change only these files
- `app/src/main/java/com/sbtracker/MainActivity.kt`

## Steps

1. **Observe `settingsVm.backupUri` and fire a share intent.**

   In `MainActivity.onCreate`, add after the existing CSV export observer:

   ```kotlin
   // Handle DB backup share
   lifecycleScope.launch {
       settingsVm.backupUri.collect { uri ->
           val intent = Intent(Intent.ACTION_SEND).apply {
               type = "application/octet-stream"
               putExtra(Intent.EXTRA_STREAM, uri)
               addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
           }
           startActivity(Intent.createChooser(intent, "Save Database Backup"))
       }
   }
   ```

2. **Observe `settingsVm.restoreResult` and restart the app on success.**

   Add another `lifecycleScope.launch` block:

   ```kotlin
   // Handle DB restore completion
   lifecycleScope.launch {
       settingsVm.restoreResult.collect { result ->
           if (result is RestoreResult.Success) {
               // Hard-restart so Room reopens the freshly restored database.
               val restart = Intent(this@MainActivity, MainActivity::class.java)
               restart.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
               startActivity(restart)
               android.os.Process.killProcess(android.os.Process.myPid())
           }
           // Failure Toasts are already shown in SettingsFragment (T-061).
       }
   }
   ```

   Import `com.sbtracker.data.RestoreResult` at the top of the file.

3. Confirm `settingsVm` is already available in `MainActivity` (it should be, as an `activityViewModels()` delegate; add it if missing, following the pattern of `historyVm`).

4. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] Tapping "Backup Database" in Settings eventually shows the OS share sheet with `application/octet-stream` type.
- [ ] After a successful restore, `MainActivity` clears the activity stack and restarts the process via `killProcess`.
- [ ] No crash on rotation or back-press during the share chooser.
- [ ] `./gradlew assembleDebug` passes.

## Do NOT
- Do not add any third-party library (e.g. `ProcessPhoenix`) — the `startActivity` + `killProcess` pattern is sufficient and avoids new dependencies.
- Do not modify `SettingsFragment`, `SettingsViewModel`, or any Repository.
- Do not repeat the restore failure Toast here — it is already shown in `SettingsFragment` (T-061).
