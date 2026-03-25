# T-075 — POST_NOTIFICATIONS Permission Handling (Android 13+)

**Status**: ready
**Phase**: 3 — F-050 Notifications Overhaul
**Blocked by**: —

---

## Goal

On Android 13+ (API 33), apps must request the `POST_NOTIFICATIONS` runtime permission before any alert notifications are shown. The current codebase never requests this permission, meaning alert notifications silently fail on API 33+ devices. This task adds the permission declaration, a first-run request flow, and a graceful degradation path.

---

## Acceptance Criteria

- `AndroidManifest.xml` declares `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />`.
- On first launch (API 33+), the app requests `POST_NOTIFICATIONS` using the standard `ActivityResultContracts.RequestPermission` flow from `MainActivity` or the first fragment.
- If the user denies the permission, alert notifications are silently suppressed (vibration still works). No crash.
- If the user grants permission, all alert notifications (T-073, T-074) fire normally.
- A "Notifications disabled" informational note appears in the Alerts section of Settings (T-072) when the permission is denied, with a button to open system notification settings.
- Permission check is extracted to a helper `NotificationPermissionHelper.kt` (single function: `isGranted(context): Boolean`).

---

## Files to touch

1. `app/src/main/AndroidManifest.xml` — add `POST_NOTIFICATIONS` permission
2. **Create** `app/src/main/java/com/sbtracker/NotificationPermissionHelper.kt`
3. `app/src/main/java/com/sbtracker/MainActivity.kt` — add `ActivityResultLauncher` for runtime permission request on first run
4. `app/src/main/java/com/sbtracker/ui/SettingsFragment.kt` — add disabled state indicator in Alerts section

---

## Implementation notes

- Use `ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)` in `NotificationPermissionHelper.isGranted()`.
- Gate only `showNotification()` in `BleViewModel` behind this helper — vibration is not gated.
- `shouldShowRequestPermissionRationale()` is not required for a minimal implementation but can be added as a stretch goal.
- Foreground service notifications do NOT require `POST_NOTIFICATIONS` (system-managed); only user-visible alert notifications do.

---

## Do NOT touch

- BLE connection logic
- Database / analytics
- Notification channel setup (T-069)
