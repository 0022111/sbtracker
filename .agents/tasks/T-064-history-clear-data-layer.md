# T-064 — History Clear: Data Layer Fix

## Feature
F-025 — History Clear (per-device wipe of all 6 tables)

## Status
`ready`

## Blocked by
—

## Goal
Ensure `HistoryViewModel.clearSessionHistory()` wipes ALL 7 Room tables for the target device
(the 6 core tables + `session_metadata`). Currently `session_metadata` is **not** cleared,
leaving orphaned metadata rows after a history wipe.

## Files to Read
- `app/src/main/java/com/sbtracker/data/SessionMetadata.kt` — contains `SessionMetadataDao`
- `app/src/main/java/com/sbtracker/HistoryViewModel.kt` — contains `clearSessionHistory()`

## Files to Touch (max 2)
1. `app/src/main/java/com/sbtracker/data/SessionMetadata.kt`
2. `app/src/main/java/com/sbtracker/HistoryViewModel.kt`

## Steps

### 1. Add device-level delete to `SessionMetadataDao`
Inside `SessionMetadataDao` (in `SessionMetadata.kt`), add:
```kotlin
@Query("""
    DELETE FROM session_metadata
    WHERE sessionId IN (
        SELECT id FROM sessions WHERE deviceAddress = :address
    )
""")
suspend fun clearAllForDevice(address: String)
```

### 2. Call it in `HistoryViewModel.clearSessionHistory()`
In `HistoryViewModel.kt`, inside the `viewModelScope.launch { }` block of
`clearSessionHistory()`, add a call to `db.sessionMetadataDao().clearAllForDevice(addr)`
**before** `db.sessionDao().clearHistory(...)` (sessions must still exist when the subquery runs).

The final call order should be:
```
db.hitDao().clearAll(addr)
db.sessionMetadataDao().clearAllForDevice(addr)   // NEW — must run before sessions deleted
db.sessionDao().clearHistory(serial, addr)
db.chargeCycleDao().clearAll(addr)
db.deviceStatusDao().clearAll(addr)
db.extendedDataDao().clearAll(addr)
db.deviceInfoDao().clearAll(addr)
analyticsRepo.clearCache()
```

## Acceptance Criteria
- `SessionMetadataDao` has a `clearAllForDevice(address: String)` method.
- `clearSessionHistory()` calls it (in the correct order, before sessions are deleted).
- `./gradlew assembleDebug` passes with no new errors.

## Notes
- `session_programs` table does not exist yet (F-027 is still planned); skip it.
- Do NOT bump the Room schema version — no table structure is changing.
- Do NOT add a `@Transaction` annotation; the six separate deletes mirror the existing pattern.
