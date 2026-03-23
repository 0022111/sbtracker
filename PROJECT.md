# SBTracker

**Android BLE companion app** for Storz & Bickel vaporizer devices (Veazy, Venty, Crafty+, Mighty+).
Connects via Bluetooth LE, tracks usage sessions, and provides analytics — all derived from a raw device status log.

---

## Architecture — Event Sourcing

The entire system follows an **event-sourcing pattern**. One table rules everything:

### The God Log: `device_status`
- Every ~500ms while the heater is on (every ~30s when idle), the BLE layer polls CMD 0x01 and inserts a row.
- Contains: temperature (current / target / boost), battery, heater mode, charging state, settings flags.
- Indexed on `(deviceAddress, timestampMs)` — covers all range/aggregate queries.

### Derived Data (computed, never stored)
- **Sessions** — boundary rows in `sessions` table (start/end timestamps only). Everything else is computed from `device_status` at query time.
- **Hits** — detected from `device_status` temperature patterns via `HitDetector`. Stored in `hits` table but re-detectable from raw log.
- **SessionSummary** — NOT a Room entity. Assembled on demand from `device_status` + `hits` + `extended_data`. Cached in-memory by `AnalyticsRepository`.
- **All analytics** (HistoryStats, UsageInsights, BatteryInsights, PersonalRecords, DailyStats) — pure functions over `List<SessionSummary>`. No DB access.

### Key Invariant
> **Never store derived data.** Compute from `device_status` at query time.
> Improving any algorithm retroactively improves all history without migrations or re-ingestion.

### Retroactive Rebuild
`MainViewModel.backcompileSessionsFromLogs()` can reconstruct all sessions and hits from the raw `device_status` log. This means feature tables can be wiped and rebuilt.

---

## Data Layer (Room DB: `sbtracker.db`)

| Entity | Table | Purpose | Key |
|---|---|---|---|
| `DeviceStatus` | `device_status` | Time-series status snapshots (god log) | auto PK, idx on `(deviceAddress, timestampMs)` |
| `ExtendedData` | `extended_data` | Lifetime counters (heater runtime, charge time) | `deviceAddress` (upsert, 1 row per device) |
| `DeviceInfo` | `device_info` | Identity (serial, type, colour) | `deviceAddress` (upsert, 1 row per device) |
| `Session` | `sessions` | Heater session boundaries | auto PK, idx on `deviceAddress`, `serialNumber` |
| `ChargeCycle` | `charge_cycles` | Charging events (start/end battery, rate) | auto PK, idx on `deviceAddress`, `serialNumber` |
| `Hit` | `hits` | Individual hits within a session | auto PK, idx on `sessionId`, `deviceAddress` |

**Schema version**: 2 (see `MIGRATIONS.md` in `data/`).
**Migration strategy**: explicit `Migration(N, N+1)` + `fallbackToDestructiveMigration()` as dev safety net.

---

## BLE Layer

| File | Purpose |
|---|---|
| `BleConstants.kt` | UUIDs, command bytes, protocol masks for S&B BLE protocol |
| `BleManager.kt` | GATT connection lifecycle, characteristic reads/writes, notification routing |
| `BleService.kt` | Foreground service keeping BLE connection alive |
| `BleCommandQueue.kt` | Serial queue preventing concurrent GATT operations |
| `BlePacket.kt` | Parses raw 20-byte packets → `DeviceStatus`, `ExtendedData`, `DeviceInfo` |

**Protocol**: Single GATT service, single characteristic. Commands are 20-byte packets; byte 0 = command ID. Notifications deliver status (CMD 0x01) continuously; extended (CMD 0x04) and identity (CMD 0x05) are polled.

---

## App Layer

| File | Purpose |
|---|---|
| `MainViewModel.kt` | Central orchestrator: BLE → DB → analytics → UI state. ~1074 lines. |
| `SessionTracker.kt` | Real-time session state machine (heater-on/off transitions, grace periods) |
| `HitDetector.kt` | Detects hits from `DeviceStatus` temperature patterns |
| `TempUtils.kt` | °C ↔ °F conversion helpers |

---

## Analytics Layer

| File | Purpose |
|---|---|
| `AnalyticsRepository.kt` | SessionSummary computation (parallel DB queries, cache), all aggregate computations |
| `AnalyticsModels.kt` | Data classes: HistoryStats, UsageInsights, BatteryInsights, PersonalRecords, DailyStats, ProfileStats |

**All analytics functions are pure** — they take `List<SessionSummary>` and return a result. No DB access. Trivially testable.

---

## UI Layer

| File | Purpose |
|---|---|
| `MainActivity.kt` | Single activity (~1048 lines), all screens via programmatic views |
| `SessionReportActivity.kt` | Detail view for a single session |
| `SessionHistoryAdapter.kt` | RecyclerView adapter for session list |
| `GraphView.kt` | Real-time temperature graph (custom View) |
| `BatteryGraphView.kt` | Battery level graph (custom View) |
| `SessionGraphView.kt` | Per-session temperature detail graph (custom View) |
| `HistoryBarChartView.kt` | Daily sessions bar chart (custom View) |
| `HistoryTimelineView.kt` | Timeline visualization (custom View) |

---

## Tech Stack

- **Language**: Kotlin
- **UI**: Programmatic Views (no XML layouts, no Compose)
- **Database**: Room (SQLite)
- **Async**: Kotlin Coroutines + Flow
- **BLE**: Android BLE API (BluetoothGatt) 
- **Architecture**: Single-Activity, MVVM via `MainViewModel`
- **Build**: Gradle (Groovy DSL)
- **Min SDK**: Android

---

## Rules for Agents

1. **Read this file first** before any code changes.
2. **Never store derived data** in the DB — compute from `device_status`.
3. **Never skip schema versions** — always increment by 1.
4. **Keep `fallbackToDestructiveMigration()`** during development.
5. **Update `BACKLOG.md`** when starting and completing work.
6. **Append to `CHANGELOG.md`** after completing changes.
7. **Use feature branches** — `feature/F-XXX-description` or `fix/B-XXX-description`.
8. **Run `./gradlew assembleDebug`** before committing to verify build.
9. **Reference app** in `!gitignore.referenceonly-reactive-volcano-app-main/` is for protocol reference only — do not modify.
