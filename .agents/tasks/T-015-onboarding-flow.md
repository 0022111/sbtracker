# T-015 — Onboarding Flow (First-Run)

**Status**: blocked
**Phase**: 2
**Blocked by**: T-005 (permission handling must be solid first)
**Blocks**: —

---

## Goal
New users currently land on the main screen with no context, no permission
explanations, and no guidance. Add a first-run onboarding flow.

---

## Screens (3-step)
1. **Welcome** — what SBTracker is, what it tracks.
2. **Bluetooth permissions** — explain why BLE is needed, trigger `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT` request.
3. **Notification permissions** — explain alerts (temp ready, 80% charge), trigger `POST_NOTIFICATIONS` request (API 33+ only).

## Implementation notes
- Detect first run via a DataStore boolean flag `onboarding_complete`.
- If false, launch `OnboardingActivity` before `MainActivity`.
- Use `ActivityResultContracts.RequestMultiplePermissions` for permission requests.
- Skip already-granted permissions silently.
- "Skip" option on each screen for users who granted permissions previously.

*(Fill in full steps when T-005 is done.)*

## Do NOT touch
- BLE connection logic
- Database schema
- Analytics
