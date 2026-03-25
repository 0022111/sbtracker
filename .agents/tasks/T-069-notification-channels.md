# T-069 — Notification Channel Consolidation

**Status**: ready
**Phase**: 3 — F-050 Notifications Overhaul
**Blocked by**: —

---

## Goal

Consolidate and formalize all notification channels into a single `NotificationChannels` object so every channel is registered once, consistently, at app startup — and downstream tasks can reference channel IDs by constant rather than hardcoded string.

Currently two channels exist in two separate files:
- `BleService.kt` — `"ble_service_channel"` (IMPORTANCE_LOW), name "SB Tracker Service"
- `BleViewModel.kt` — `"device_alerts"` (IMPORTANCE_HIGH), name "Device Alerts"

A future alert for session-end and heater quick-controls will require a third channel.

---

## Acceptance Criteria

- A new `NotificationChannels.kt` singleton object defines all channel IDs as `const val`.
- Three channels are registered: `STATUS` (IMPORTANCE_LOW), `ALERTS` (IMPORTANCE_HIGH), `CONTROLS` (IMPORTANCE_DEFAULT).
- Channel registration is called from `BleService.onCreate()` only (not ViewModel).
- `BleService.kt` and `BleViewModel.kt` reference `NotificationChannels.STATUS` / `NotificationChannels.ALERTS` instead of inline strings.
- No channel is registered twice.

---

## Files to touch

1. **Create** `app/src/main/java/com/sbtracker/NotificationChannels.kt`
2. `app/src/main/java/com/sbtracker/BleService.kt` — remove inline `createNotificationChannel()`, use constants
3. `app/src/main/java/com/sbtracker/BleViewModel.kt` — remove inline `createNotificationChannel()`, use constants

---

## Do NOT touch

- BLE connection logic
- Database / analytics
- Any UI fragments

---

## Channel Spec

| Constant | ID | Name | Importance | Purpose |
|---|---|---|---|---|
| `STATUS` | `"sb_status"` | "Device Status" | LOW | Persistent foreground service notification |
| `ALERTS` | `"sb_alerts"` | "Device Alerts" | HIGH | Temp ready, charge 80%, session end |
| `CONTROLS` | `"sb_controls"` | "Quick Controls" | DEFAULT | Heater toggle and temp adjustment actions |
