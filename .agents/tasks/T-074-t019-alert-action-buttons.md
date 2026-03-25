# T-074 — Alert Notification Action Buttons (Temp Ready & Charge 80%)

**Status**: blocked
**Phase**: 3 — F-050 Notifications Overhaul
**Blocked by**: T-073, T-008

---

## Goal

Add tappable action buttons to the **alert** notifications ("Device Ready" and "Charging Progress"). This directly implements T-019's declared scope and unblocks it from T-008 by landing here in the F-050 series. T-019 remains in TASKS.md as `blocked` and cross-references this task.

---

## Acceptance Criteria

- **"Device Ready" (Temp Ready) notification**:
  - Action: **Start Timer** — posts a countdown notification (default 30 s, configurable via Settings) using `NotificationChannels.CONTROLS`. The countdown updates the notification text every second via a coroutine in `BleService`.
  - Action: **Dismiss** — cancels the alert notification.
- **"Charging Progress" (80% Charge) notification**:
  - Action: **Disconnect** — sends the BLE disconnect/stop-charging command via `NotificationActionReceiver` (created in T-071).
  - Action: **Dismiss** — cancels the alert notification.
- Actions use `NotificationCompat.Action` + `PendingIntent` → `NotificationActionReceiver`.
- Intent actions: `ACTION_START_TIMER`, `ACTION_DISMISS_ALERT`, `ACTION_DISCONNECT_CHARGE`.

---

## Files to touch

1. `app/src/main/java/com/sbtracker/BleViewModel.kt` — update `showNotification()` to attach the correct actions based on alert type
2. `app/src/main/java/com/sbtracker/NotificationActionReceiver.kt` — add `ACTION_START_TIMER`, `ACTION_DISMISS_ALERT`, `ACTION_DISCONNECT_CHARGE` handlers (receiver created in T-071)
3. `app/src/main/java/com/sbtracker/BleService.kt` — add countdown notification coroutine for Start Timer action

---

## Implementation notes

- Pass alert type to `showNotification()` as a parameter (e.g., a sealed class or enum `AlertType.TEMP_READY / AlertType.CHARGE_80`).
- Countdown timer ID: use a fixed `NOTIFICATION_ID_TIMER = 202` so it can be cancelled.
- `ACTION_DISMISS_ALERT` should `NotificationManagerCompat.cancel(notificationId)`.
- Timer duration preference key: `"timer_duration_seconds"`, default 30, exposed in Settings (T-072 can add the row).

---

## Do NOT touch

- BLE connection logic internals
- Database / analytics
- Persistent status notification (T-070)
- Quick-control action buttons (T-071)
