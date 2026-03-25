# T-067 — Add startingBattery to SessionStats

**Phase**: Phase 3 — F-053 Session Battery Starting Level
**Blocked by**: nothing
**Estimated diff**: ~5 lines changed across 1 file

## Goal
Expose the already-tracked `startBattery` field from `SessionTracker` in `SessionStats` so the active-session UI can display it.

## Read these files first
- `app/src/main/java/com/sbtracker/SessionTracker.kt` — contains `SessionStats` data class (lines ~33–68) and the stats construction block (lines ~334–370); `startBattery` is a private field captured at session start and used only for `batteryDrain` today.

## Change only these files
- `app/src/main/java/com/sbtracker/SessionTracker.kt`

## Steps
1. In the `SessionStats` data class (around line 43), add a new field after `batteryDrain`:
   ```kotlin
   val startingBattery:     Int     = 0,
   ```
2. In the `SessionStats(...)` construction block (around line 334), add the corresponding assignment after the `batteryDrain` line:
   ```kotlin
   startingBattery = if (state == State.ACTIVE) startBattery else 0,
   ```
3. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] `SessionStats` has a `startingBattery: Int` field with a default of `0`.
- [ ] When a session is `ACTIVE`, `startingBattery` equals the battery level recorded when the heater first turned on.
- [ ] When `IDLE`, `startingBattery` is `0`.
- [ ] `./gradlew assembleDebug` passes with no errors.

## Do NOT
- Do not change any DAO, database entity, or layout file.
- Do not rename or remove the private `startBattery` field — it is still needed for `batteryDrain` and `drainRatePctPerMin` calculations.
- Do not touch `SessionFragment.kt` — that is T-068's scope.
