# T-066 — History Clear: Integration Verification

## Feature
F-025 — History Clear (per-device wipe of all 6 tables)

## Status
`ready`

## Blocked by
T-064, T-065

## Goal
Verify the full F-025 feature end-to-end: all 6 core tables (+ `session_metadata`) are wiped
for the target device, the build passes, and the UI behaves correctly.

## Files to Read
- `app/src/main/java/com/sbtracker/HistoryViewModel.kt` — confirm final wipe call order
- `app/src/main/java/com/sbtracker/data/SessionMetadata.kt` — confirm new DAO method

## Files to Touch
- `CHANGELOG.md` — append release note under the correct date section
- `BACKLOG.md` — mark F-025 `done`

## Steps

### 1. Build verification
```bash
./gradlew assembleDebug
```
Must complete with 0 errors and 0 relevant new warnings.

### 2. Static correctness checks
Verify by reading the source:

| Check | Expected |
|---|---|
| `SessionMetadataDao.clearAllForDevice(address)` exists | Yes — added in T-064 |
| `HistoryViewModel.clearSessionHistory()` calls `sessionMetadataDao().clearAllForDevice(addr)` before `sessionDao().clearHistory(...)` | Yes |
| All 7 tables cleared: `hits`, `session_metadata`, `sessions`, `charge_cycles`, `device_status`, `extended_data`, `device_info` | Yes |
| `analyticsRepo.clearCache()` called after all deletes | Yes |
| Settings UI has "Clear Device History" button | Yes — added in T-065 |
| Confirmation dialog shown before action executes | Yes |

### 3. Lint check (optional but recommended)
```bash
./gradlew lint
```
Review any new lint warnings related to T-064/T-065 changes and resolve critical ones.

### 4. Update meta files
- In `BACKLOG.md`, change F-025 status from `in-progress` → `done`.
- In `CHANGELOG.md`, append an entry at the top of the current date section:
  ```
  ### YYYY-MM-DD HH:MM — F-025 History Clear (Agent)
  Origin: PR claude/T-064-history-clear-data-layer → dev
  - Added `SessionMetadataDao.clearAllForDevice()` for device-scoped bulk delete
  - Fixed `HistoryViewModel.clearSessionHistory()` to wipe `session_metadata` before `sessions`
  - Added "Clear Device History" button + confirmation dialog in SettingsFragment
  - All 7 tables now fully wiped per-device (6 core + session_metadata)
  ```

## Acceptance Criteria (F-025 Complete)
- `./gradlew assembleDebug` passes.
- All 7 tables are wiped for the target `deviceAddress` when Clear is confirmed.
- No orphaned `session_metadata` rows remain after a clear.
- UI shows a confirmation dialog before destructive action.
- `BACKLOG.md` F-025 = `done`.
- `CHANGELOG.md` updated.

## Notes
- `session_programs` (F-027) is still planned; do not attempt to clear it here.
- Room schema version does NOT change — only a new `@Query` method was added.
- This task does not require a new branch; add the meta-file changes as a direct commit to `dev`.
