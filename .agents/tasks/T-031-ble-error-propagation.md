# T-031 — Structured BLE Error Propagation

**Status**: ready
**Phase**: 0
**Blocked by**: —
**Blocks**: —

---

## Goal
BleManager has 10+ `catch (e: SecurityException)` blocks that silently return.
The user sees nothing when BLE operations fail — the app just stops working.
Add a structured error flow so the UI can display connection/permission failures.

---

## Read first
- `app/src/main/java/com/sbtracker/BleManager.kt`
- `app/src/main/java/com/sbtracker/MainViewModel.kt` (BLE interaction points)

## Change only these files
- `app/src/main/java/com/sbtracker/BleManager.kt`
- `app/src/main/java/com/sbtracker/MainViewModel.kt` (observe error flow)

## Steps
1. In `BleManager.kt`, add a `SharedFlow<BleError>` (or sealed class) that emits error events:
   ```kotlin
   sealed class BleError {
       data class PermissionDenied(val operation: String) : BleError()
       data class ConnectionFailed(val reason: String) : BleError()
       data class WriteFailed(val command: String, val cause: Throwable) : BleError()
   }

   private val _errors = MutableSharedFlow<BleError>(extraBufferCapacity = 10)
   val errors: SharedFlow<BleError> = _errors
   ```
2. In every `catch (e: SecurityException)` block, emit the appropriate error event AND keep the existing `Log.e()` call (from T-028).
3. In `MainViewModel`, collect `bleManager.errors` and expose a `StateFlow<String?>` for the UI to show a Snackbar or Toast.
4. Do NOT change any BLE protocol logic, connection retry behavior, or GATT callbacks.
5. `./gradlew assembleDebug` — must pass.

## Do NOT touch
- BLE protocol parsing (BlePacket.kt)
- Database schema
- Analytics logic
- Any UI layout files
