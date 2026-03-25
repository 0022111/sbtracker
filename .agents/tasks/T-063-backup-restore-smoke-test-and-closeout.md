# T-063 — F-026 Smoke Test, CHANGELOG, and BACKLOG Closeout

**Phase**: Phase 3 — F-026 Data Backup/Restore
**Blocked by**: T-062
**Estimated diff**: ~20 lines changed across 2 meta files

## Goal
Verify the end-to-end backup/restore flow on a real device or emulator, then mark F-026 `done` in `BACKLOG.md` and append an entry to `CHANGELOG.md`.

## Read these files first
- `BACKLOG.md` — find the F-026 row (currently `in-progress`) to change to `done`.
- `CHANGELOG.md` — read the top section to understand the current entry format (`### YYYY-MM-DD HH:MM — Short Title (Author)`).

## Change only these files
- `BACKLOG.md`
- `CHANGELOG.md`

## Steps

1. **Build and install the debug APK on a device or emulator.**
   ```
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/*.apk
   ```

2. **Smoke test — Backup:**
   - Open the app, connect to a device (or use the synthetic test device) so `device_status` rows exist.
   - Navigate to Settings → tap "Backup Database".
   - Confirm the OS share sheet appears with a file named `sbtracker_backup_<timestamp>.db`.
   - Save the file to local storage or share to Files app.

3. **Smoke test — Restore:**
   - In Settings, tap "Restore Database".
   - Select the previously saved `.db` file from the file picker.
   - Confirm the app shows "Restore complete. Restarting…" Toast and then relaunches.
   - Verify session history is intact after restart.

4. **Smoke test — Invalid file rejection:**
   - Attempt to restore a non-SQLite file (e.g. a `.txt` file).
   - Confirm the error Toast appears and the app does not crash or corrupt the database.

5. **Update `BACKLOG.md`:** change F-026 status from `in-progress` → `done`.

6. **Append to `CHANGELOG.md`** (at the top of the relevant section):
   ```
   ### 2026-MM-DD HH:MM — F-026 Data Backup/Restore (Worker Agent)
   Origin: PR merge to dev
   - Added BackupRepository: WAL checkpoint + db file copy + FileProvider URI emission.
   - Added RestoreRepository: SQLite magic-byte validation, db overwrite, WAL/SHM cleanup.
   - Added SettingsViewModel backup/restore delegation methods.
   - Added "Backup Database" and "Restore Database" buttons in SettingsFragment.
   - MainActivity observes backup URI (share intent) and restore success (process restart).
   ```
   Replace `MM-DD HH:MM` with the actual date and time.

7. Mark T-058 through T-063 as `done` in `.agents/TASKS.md`.

8. Run `./gradlew assembleDebug` one final time to confirm no regressions.

## Acceptance criteria
- [ ] Backup produces a valid `.db` file that can be opened by any SQLite viewer.
- [ ] Restore with the backup file results in identical session history after app restart.
- [ ] Restore with a non-SQLite file shows an error Toast and does not crash.
- [ ] F-026 is marked `done` in `BACKLOG.md`.
- [ ] `CHANGELOG.md` has a new entry describing all F-026 changes.
- [ ] All T-058–T-063 rows are `done` in `.agents/TASKS.md`.
- [ ] `./gradlew assembleDebug` passes.

## Do NOT
- Do not skip the invalid-file test — protecting the god log from accidental corruption is the entire point of this feature.
- Do not push directly to `main` — the PR must target `dev`.
- Do not modify any production Kotlin or XML source files in this task.
