# T-071 — Notification Drawer Quick Controls

**Status**: blocked
**Phase**: 3 — F-050 Notifications Overhaul
**Blocked by**: T-070, T-008

---

## Goal

Add tappable action buttons to the persistent status notification so users can control the device without opening the app. This task supersedes and absorbs the scope of T-019 (Notification Action Buttons) for the quick-control use case; T-019 remains as the specific "Temp Ready / 80% Charge" action-button task.

---

## Acceptance Criteria

- When a session is **active** (heater ON), the notification shows three actions:
  - **▲ Temp** — increases target temp by 5°C (or 10°F). Sends the write command immediately.
  - **▼ Temp** — decreases target temp by 5°C (or 10°F).
  - **Stop Heater** — sends heater-off command.
- When the device is **connected and idle** (heater OFF):
  - **Start Heater** — sends heater-on command using the last-used target temp.
- Actions are implemented via `NotificationCompat.Action` + `PendingIntent` → `BroadcastReceiver`.
- A new `NotificationActionReceiver` handles `ACTION_TEMP_UP`, `ACTION_TEMP_DOWN`, `ACTION_HEATER_ON`, `ACTION_HEATER_OFF` intents and delegates to `BleViewModel` via the service binder or a shared `BleManager` reference.
- Uses `NotificationChannels.CONTROLS` for action delivery (no sound/heads-up).
- Actions do not appear when the device is disconnected, connecting, or charging (charging state: heater-off is implied by device).

---

## Files to touch

1. **Create** `app/src/main/java/com/sbtracker/NotificationActionReceiver.kt`
2. `app/src/main/java/com/sbtracker/BleService.kt` — build action `PendingIntent`s, attach to notification
3. `app/AndroidManifest.xml` — register `NotificationActionReceiver`

---

## Implementation notes

- `NotificationActionReceiver` is a `BroadcastReceiver`; use `@AndroidEntryPoint` + Hilt injection so it can access `BleManager` directly.
- Keep action intent extras minimal: action string + optional int delta.
- Do NOT add notification actions for T-019 alert notifications here — that remains T-019's scope.

---

## Do NOT touch

- BLE connection logic internals
- Database / analytics
- T-019 alert notifications (temp ready, charge 80%)
