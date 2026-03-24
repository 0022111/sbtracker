# T-005 — targetSdk 35 Compatibility Pass

**Status**: ready
**Phase**: 0
**Effort**: medium (2–4h)
**Branch**: `claude/T-005-targetsdk35-compat`
**Blocked by**: T-001 (T-001 sets targetSdk 35; complete that first)

---

## Goal
Android 14+ (API 34+) and Android 15 (API 35) introduced breaking changes for
foreground services, notifications, and Bluetooth. Audit and fix the three
known risk areas so the app runs correctly on modern Android.

---

## Read these files first
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/sbtracker/BleService.kt`
- `app/src/main/java/com/sbtracker/MainViewModel.kt` (search for `POST_NOTIFICATIONS`, `Vibrator`, `VibratorManager`)

## Change only these files
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/sbtracker/BleService.kt`
- `app/src/main/java/com/sbtracker/MainViewModel.kt` (Vibrator API guard only)

---

## Steps

### 1. Foreground Service Type (required on API 34+)
In `AndroidManifest.xml`, the `BleService` declaration must include a
`android:foregroundServiceType`. For a BLE connection service, the correct type
is `connectedDevice`:
```xml
<service
    android:name=".BleService"
    android:foregroundServiceType="connectedDevice"
    ... />
```
Also ensure `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />`
is declared.

### 2. POST_NOTIFICATIONS Permission (API 33+)
Search `MainViewModel.kt` and anywhere notifications are posted. Confirm that
`POST_NOTIFICATIONS` is declared in the manifest AND that a runtime permission
request is made on API 33+ before posting any notification. If the runtime
request is missing, add it (follow the existing BLE permission request pattern).

### 3. Vibrator API Guard
`VibratorManager` is API 31+. `Vibrator` (deprecated) is the fallback.
Check `MainViewModel.kt` for vibration code. Ensure there is a proper
`if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)` guard. If it exists,
verify it's correct. If it doesn't, add it.

### 4. Run `./gradlew assembleDebug` — must pass.

---

## Done when
- [ ] `BleService` has `foregroundServiceType="connectedDevice"` in manifest
- [ ] `FOREGROUND_SERVICE_CONNECTED_DEVICE` permission declared in manifest
- [ ] `POST_NOTIFICATIONS` runtime permission requested on API 33+
- [ ] `VibratorManager` / `Vibrator` usage is properly API-guarded
- [ ] `./gradlew assembleDebug` passes

## Do NOT touch
- BLE scanning or connection logic
- Any database files
- Any analytics files
