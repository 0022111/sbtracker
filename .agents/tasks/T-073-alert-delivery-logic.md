# T-073 — Alert Delivery Logic (Temp Ready, Charge 80%, Session End)

**Status**: blocked
**Phase**: 3 — F-050 Notifications Overhaul
**Blocked by**: T-069, T-072

---

## Goal

Wire the per-event alert preferences (from T-072) into the existing `triggerAlert()` call sites in `BleViewModel`, and add a new session-end alert. Notifications must fire both in foreground and background (current code skips foreground — see implementation notes).

---

## Acceptance Criteria

- `triggerAlert()` checks the relevant per-event preference before firing:
  - "Device Ready" alert: fires only if `alertTempReady == true`.
  - "Charging Progress" alert: fires only if `alertCharge80 == true`.
  - New "Session Ended" alert: fires when heater transitions from ON → OFF after a session that lasted ≥ 60 seconds, only if `alertSessionEnd == true`.
- Alerts fire in **both foreground and background** (remove the `if (!isAppInForeground)` guard on `showNotification`; vibration is already unconditional).
- Alert notifications use `NotificationChannels.ALERTS` (from T-069).
- Session-end alert content: title "Session Complete", text "Your session has ended." with `setAutoCancel(true)`.

---

## Files to touch

1. `app/src/main/java/com/sbtracker/BleViewModel.kt` — update `triggerAlert()`, add session-end trigger, read per-event prefs
2. `app/src/main/java/com/sbtracker/BleViewModel.kt` — update `showNotification()` to remove foreground guard

---

## Implementation notes

- Current `triggerAlert()` location: `BleViewModel.kt` lines ~399–404. The foreground guard is at line ~401: `if (!isAppInForeground) { showNotification(...) }`.
- Session-end detection: watch for the transition where `status.heaterMode` goes from `> 0` to `0`. Use a `lastHeaterOn: Boolean` field similar to the existing `lastSetpointReached`. Only fire if the session lasted ≥ 60 s (compare timestamp with session start from `SessionTracker`).
- Per-event preferences should be read as `StateFlow` collected in `init {}` to avoid DataStore suspension inside hot callbacks.

---

## Do NOT touch

- BLE connection logic internals
- Database / analytics
- T-019 notification action buttons (those are wired separately)
- Notification channel definitions (T-069)
