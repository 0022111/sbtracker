# T-007 — Decompose MainViewModel

**Status**: blocked
**Phase**: 1
**Blocked by**: T-006
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

*(Fill in full steps when T-006 is done and this task is unblocked.)*

## Do NOT touch
- Analytics logic (keep in `AnalyticsRepository`)
- BLE protocol or packet parsing
- Database schema
