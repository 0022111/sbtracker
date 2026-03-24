# T-007 — Decompose MainViewModel

**Status**: ready
**Phase**: 1
**Blocked by**: —
**Blocks**: T-008, T-011, T-013, T-014

---

## Goal
`MainViewModel.kt` is ~1074 lines and owns BLE state, session tracking,
analytics, CSV export, notifications, and preferences. Split into focused,
single-responsibility ViewModels.

---

## Target split

| New ViewModel | Owns |
|---|---|
| `BleViewModel` | Connection state, scan, disconnect, device info |
| `SessionViewModel` | Active session stats, hit stream, live temp |
| `HistoryViewModel` | Session list, date filter, analytics queries |
| `BatteryViewModel` | Charge cycle data, battery insights |
| `SettingsViewModel` | All preference reads/writes |

1. **Create `BleViewModel`** and move connection state, scanning, and disconnect logic from `MainViewModel`.
2. **Create `SessionViewModel`** and move active session stats, hit stream, and live temp observation.
3. **Create `HistoryViewModel`** for session list observation and analytics queries.
4. **Create `BatteryViewModel`** for charge cycle data and battery insights.
5. **Create `SettingsViewModel`** for preferences (should wait for T-009).

## Do NOT touch
- Analytics logic (keep in `AnalyticsRepository`)
- BLE protocol or packet parsing
- Database schema
