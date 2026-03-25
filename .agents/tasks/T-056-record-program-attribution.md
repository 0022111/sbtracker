# T-056 — Record Program Attribution to session_metadata on Session Completion

**Phase**: Phase 3 — F-027 Session Programs/Presets
**Blocked by**: T-046
**Estimated diff**: ~50 lines across 2 files

## Goal
When a session is started with a `SessionProgram`, record the program's ID in
`session_metadata.appliedProgramId` as soon as the session is finalised by `SessionTracker`.
This is the critical history link: without it, the session log has no record of which
program was used.

## Background: why this is tricky

`session_metadata` is keyed on `sessionId` (the Room auto-generated PK from the `sessions`
table). But a `Session` row isn't written until `SessionTracker.update()` produces a
`completedSession`. The program selection happens *before* session start (in
`SessionFragment`). So we need to hold the "pending program ID" in `MainViewModel` across
the gap between session start and session end.

`MainViewModel` already handles `completedSession` from `SessionTracker` (it inserts the
`Session` row and gets back its DB-assigned `sessionId`). That is exactly the right place
to also write the `SessionMetadata` with `appliedProgramId`.

## Read these files first
- `app/src/main/java/com/sbtracker/MainViewModel.kt` — find where `completedSession` is
  inserted into the DB and a `sessionId` is obtained; this is the injection point
- `app/src/main/java/com/sbtracker/SessionViewModel.kt` — `selectedProgram` StateFlow
  added in T-046; we need its value here in MainViewModel

## Change only these files
- `app/src/main/java/com/sbtracker/MainViewModel.kt`
- `app/src/main/java/com/sbtracker/SessionViewModel.kt`

## Steps

### 1. Expose `pendingProgramId` from `SessionViewModel`

`SessionViewModel` already holds `selectedProgram: StateFlow<SessionProgram?>` (from T-046).
Add a simple function that `MainViewModel` can call to read the *active* program ID at the
moment a session completes and then clear it:

```kotlin
/** Returns the ID of the currently-selected program, then clears selection. */
fun consumeSelectedProgramId(): Long? {
    val id = _selectedProgram.value?.id
    _selectedProgram.value = null
    return id
}
```

### 2. Write `appliedProgramId` in `MainViewModel`

Find the section in `MainViewModel` where a completed `Session` is inserted. It should look
roughly like:

```kotlin
val sessionId = sessionDao.insert(completedSession)
```

Immediately after that insert, write `session_metadata` if a program was active:

```kotlin
val programId = sessionViewModel.consumeSelectedProgramId()
if (programId != null) {
    val existing = sessionMetadataDao.getMetadataForSession(sessionId)
    if (existing != null) {
        sessionMetadataDao.insertOrUpdate(existing.copy(appliedProgramId = programId))
    } else {
        sessionMetadataDao.insertOrUpdate(
            SessionMetadata(sessionId = sessionId, appliedProgramId = programId)
        )
    }
}
```

> **Why merge with existing?** The user may have already toggled capsule/free-pack on the
> active session (T-036 flow). We must not overwrite that. Always read-then-merge.

### 3. Verify `SessionViewModel` is accessible from `MainViewModel`

Check how `MainViewModel` is set up with Hilt. If `SessionViewModel` is a
`@HiltViewModel`, `MainViewModel` cannot `@Inject` it directly (ViewModels are not
injectable into other ViewModels). Instead:

- Extract the "pending program ID" concern into a small singleton holder:

```kotlin
// In SessionViewModel.kt (or a new file if you prefer):
@Singleton
class ActiveProgramHolder @Inject constructor() {
    private val _programId = MutableStateFlow<Long?>(null)
    val programId: StateFlow<Long?> = _programId.asStateFlow()
    fun set(id: Long?) { _programId.value = id }
    fun consume(): Long? { val v = _programId.value; _programId.value = null; return v }
}
```

- Inject `ActiveProgramHolder` into both `SessionViewModel` and `MainViewModel` via Hilt.
- In `SessionViewModel.startSessionWithProgram()`: call `activeProgramHolder.set(program.id)`.
- In `SessionViewModel.cancelBoostSchedule()`: call `activeProgramHolder.set(null)`.
- In `MainViewModel` (where session is persisted): call `activeProgramHolder.consume()`.

This avoids a cross-ViewModel dependency and works cleanly with Hilt's singleton scope.

### 4. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] `ActiveProgramHolder` (or equivalent) is a Hilt `@Singleton` and injectable
- [ ] `SessionViewModel.startSessionWithProgram()` sets the holder with the program ID
- [ ] `SessionViewModel.cancelBoostSchedule()` clears the holder
- [ ] When a session completes, `MainViewModel` reads and consumes the program ID
- [ ] `session_metadata` row for the session contains the correct `appliedProgramId`
- [ ] If no program was selected, `appliedProgramId` is NULL (no row pollution)
- [ ] Existing `isCapsule` / `notes` data on a session is never overwritten — always merge
- [ ] `./gradlew assembleDebug` passes

## Do NOT
- Inject `SessionViewModel` directly into `MainViewModel`
- Store `appliedProgramId` in `device_status` or `sessions` — it belongs in `session_metadata` only
- Skip the read-then-merge pattern when writing metadata — don't blindly overwrite
- Modify `SessionTracker`, `BleManager`, or `AnalyticsRepository` in this task
