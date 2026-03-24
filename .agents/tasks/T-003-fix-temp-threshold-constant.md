# T-003 — Fix TEMP_DIP_THRESHOLD Constant Duplication

**Status**: ready
**Phase**: 0
**Effort**: tiny (< 30min)
**Branch**: `claude/T-003-fix-temp-threshold-constant`
**Blocks**: —

---

## Goal
`TEMP_DIP_THRESHOLD = 2` is defined independently in both `HitDetector.kt` and
`SessionTracker.kt`. They must always match — but right now there's nothing
enforcing that. Move it to a single source of truth in `BleConstants.kt`.

---

## Read these files first
- `app/src/main/java/com/sbtracker/BleConstants.kt`
- `app/src/main/java/com/sbtracker/HitDetector.kt`
- `app/src/main/java/com/sbtracker/SessionTracker.kt`

## Change only these files
- `app/src/main/java/com/sbtracker/BleConstants.kt`
- `app/src/main/java/com/sbtracker/HitDetector.kt`
- `app/src/main/java/com/sbtracker/SessionTracker.kt`

---

## Steps

1. In `BleConstants.kt`, add:
   ```kotlin
   const val TEMP_DIP_THRESHOLD_C = 2 // °C — hit detection sensitivity
   ```
2. In `HitDetector.kt`, remove the local `private const val TEMP_DIP_THRESHOLD = 2`
   and replace its usage with `BleConstants.TEMP_DIP_THRESHOLD_C`.
3. In `SessionTracker.kt`, find the equivalent constant or magic number `2` used
   for the same threshold, remove it, and replace with `BleConstants.TEMP_DIP_THRESHOLD_C`.
4. Run `./gradlew assembleDebug` — must pass.

---

## Done when
- [ ] `TEMP_DIP_THRESHOLD` (or equivalent) no longer defined in `HitDetector.kt` or `SessionTracker.kt`
- [ ] Single definition exists in `BleConstants.kt`
- [ ] Both files import and use the constant from `BleConstants`
- [ ] `./gradlew assembleDebug` passes

## Do NOT touch
- Any other files
- The value itself (must remain `2`)
