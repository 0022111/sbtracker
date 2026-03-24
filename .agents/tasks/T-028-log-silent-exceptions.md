# T-028 — Replace Silent Exception Catches with Logging

**Status**: ready
**Phase**: 0
**Effort**: tiny (< 15 min)
**Branch**: `claude/T-028-log-silent-exceptions`
**Blocks**: —

---

## Goal
Two locations swallow exceptions completely, making production failures invisible:

1. `BleCommandQueue.kt:31` — catches `Exception` on every queued BLE command
   and discards it silently.
2. `MainViewModel.kt:322` — catches `Exception` during offline-gap detection and
   comments `// Ignore`.

Both should log the error with `Log.e()` so that logcat (and future crash
reporting) can surface them. **Do not change the swallow behaviour** — the queue
must keep running and the gap detection must not crash the app — just add
logging.

---

## Read these files first
- `app/src/main/java/com/sbtracker/BleCommandQueue.kt`
- `app/src/main/java/com/sbtracker/MainViewModel.kt` (around line 322)

## Change only these files
- `app/src/main/java/com/sbtracker/BleCommandQueue.kt`
- `app/src/main/java/com/sbtracker/MainViewModel.kt`

---

## Steps

### 1. BleCommandQueue.kt

```kotlin
// BEFORE
} catch (_: Exception) {
    // Swallow individual command errors so the queue keeps running.
}

// AFTER
} catch (e: Exception) {
    android.util.Log.e("BleCommandQueue", "BLE command failed", e)
}
```

Add `import android.util.Log` or use the fully-qualified `android.util.Log.e()`
— whichever style matches the file. Remove the now-redundant comment.

### 2. MainViewModel.kt

```kotlin
// BEFORE
} catch (e: Exception) {
    // Ignore
}

// AFTER
} catch (e: Exception) {
    android.util.Log.e("MainViewModel", "Offline gap detection failed", e)
}
```

### 3. Run `./gradlew assembleDebug` — must pass.

---

## Done when
- [ ] Neither catch block has a blank/`// Ignore` body
- [ ] Both sites use `Log.e(TAG, message, e)` with the throwable passed in
- [ ] `./gradlew assembleDebug` passes

## Do NOT touch
- The catch semantics — both still swallow the exception (do not rethrow)
- Any other catch blocks in the project
