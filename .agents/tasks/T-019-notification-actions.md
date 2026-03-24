# T-019 — Notification Action Buttons

**Status**: blocked
**Phase**: 3
**Blocked by**: T-008

---

## Goal
Current notifications ("Temp Ready", "80% Charge") are fire-and-forget with no
actions. Add tappable action buttons so users don't need to open the app.

---

## "Temp Ready" notification
- Action: **Start Timer** — starts a configurable countdown (default 30s) shown in the notification.
- Action: **Dismiss** — cancels the notification.

## "80% Charge" notification
- Action: **Disconnect** — sends BLE command to stop charging / disconnect.
- Action: **Dismiss** — cancels the notification.

## Implementation notes
- Use `NotificationCompat.Action` with `PendingIntent` pointing to a `BroadcastReceiver`.
- Create `NotificationActionReceiver` that handles the action intents and delegates to `BleViewModel`.

*(Fill in full steps when T-008 is done.)*

## Do NOT touch
- BLE connection logic
- Database
- Analytics
