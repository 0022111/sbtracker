# T-072 — Configurable Alert Settings UI

**Status**: ready
**Phase**: 3 — F-050 Notifications Overhaul
**Blocked by**: —

---

## Goal

Add a dedicated "Alerts" section to `SettingsFragment` that lets users toggle which events trigger phone alerts (vibration + notification). Currently a single `switchPhoneAlerts` switch enables or disables all alerts globally. This task replaces that single toggle with per-event toggles.

---

## Acceptance Criteria

- The Settings screen has an "Alerts" section with individual toggles for:
  - **Temp Ready** (device reached target temperature) — default: ON
  - **Charge 80%** (battery reaches 80% while charging) — default: ON
  - **Session End** (heater turns off after an active session) — default: OFF
- The old single "Phone Alerts" master toggle is replaced by these three granular toggles, OR it becomes a master enable/disable that greys out the individual toggles when off. (Prefer individual toggles; master toggle is optional.)
- Each preference is persisted via `PrefsRepository` / DataStore with a distinct key.
- Toggling a switch takes effect immediately (no restart required).

---

## Files to touch

1. `app/src/main/java/com/sbtracker/ui/SettingsFragment.kt` — add toggle wiring
2. `app/src/main/java/com/sbtracker/PrefsRepository.kt` — add `alertTempReady`, `alertCharge80`, `alertSessionEnd` flows and update functions
3. `app/src/main/res/layout/fragment_settings.xml` (or programmatic equivalent) — add the three `SwitchCompat` views

---

## Implementation notes

- DataStore keys: `"alert_temp_ready"`, `"alert_charge_80"`, `"alert_session_end"`. All default to `true` except `alert_session_end` (default `false`).
- Expose each as a `StateFlow<Boolean>` in `SettingsViewModel` or `BleViewModel` (whichever owns alert logic).
- Do not add the alert delivery logic here — that is T-073.

---

## Do NOT touch

- BLE connection logic
- Database / analytics
- Notification channel definitions (T-069)
