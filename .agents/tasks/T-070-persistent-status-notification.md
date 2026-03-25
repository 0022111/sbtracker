# T-070 — Persistent Status Notification Content

**Status**: ready
**Phase**: 3 — F-050 Notifications Overhaul
**Blocked by**: T-069

---

## Goal

Enrich the persistent foreground notification in `BleService` to show current temperature, session state, and battery level in a structured format. The notification already updates on each `latestStatus` + `connectionState` combine, but the content is minimal. This task upgrades the content and layout without adding action buttons (that is T-071).

---

## Acceptance Criteria

- When connected and heater is **ON** (active session):
  - Title: `"Session Active — 185°C → 195°C"` (current → target)
  - Text: `"Battery: 72% • Hit #4 • 3m 12s"`  (battery, hit count if available, elapsed session time)
- When connected and heater is **OFF** (idle):
  - Title: `"Device Online"`
  - Text: `"Battery: 88% • Idle"`
- When charging:
  - Title: `"Charging"`
  - Text: `"Battery: 45% • ETA ~22 min"` (ETA is optional/best-effort; omit if unavailable)
- When connecting / scanning / reconnecting:
  - Title: matches existing reconnecting states (no regression)
- When disconnected:
  - Title: `"Disconnected"`
  - Text: `"Tap to reconnect"`
- Notification uses `NotificationChannels.STATUS` (from T-069).
- `setOngoing(true)` and `setOnlyAlertOnce(true)` remain set.

---

## Files to touch

1. `app/src/main/java/com/sbtracker/BleService.kt` — update `updateNotification()` and `createNotification()` to accept richer state
2. *(Optional)* `app/src/main/java/com/sbtracker/BleViewModel.kt` — expose session elapsed time / hit count as a Flow if not already available

---

## Implementation notes

- `BleService.initialize(vm)` already collects `combine(vm.latestStatus, vm.connectionState)`. Extend this to also collect session state from the ViewModel if needed.
- Do not pull in a separate DAO reference in BleService — read only what the ViewModel exposes via Flow.
- Keep `createNotification()` as a pure function taking data parameters so it remains testable.

---

## Do NOT touch

- BLE connection logic
- Database / analytics
- Notification action buttons (T-071)
