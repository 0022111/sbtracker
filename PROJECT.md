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
| `SessionMetadata` | `session_metadata` | User-provided metadata (notes, grams, appliedProgramId) | `sessionId` (PK) |
| `SessionProgram` | `session_programs` | Reusable heating profiles (boost steps, stayOnAtEnd) | auto PK |

**Schema version**: 5 (see `AppModule.kt` for migrations).
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
| `BleViewModel.kt` | Owns connection lifecycle, data pipeline, and device persistence |
| `SessionViewModel.kt` | Device control logic (temp, modes, program execution) |
| `HistoryViewModel.kt` | Session list, analytics, and CSV exports |
| `BatteryViewModel.kt` | Battery health and charge cycle management |
| `SessionTracker.kt` | Real-time session state machine (heater-on/off transitions, grace periods) |
| `HitDetector.kt` | Detects hits from `DeviceStatus` temperature patterns |
| `ActiveProgramHolder.kt` | Singleton for cross-VM session program state management |
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

| File / Folder | Purpose |
|---|---|
| `MainActivity.kt` | Single hosting activity, minimal lifecycle and navigation |
| `ui/LandingFragment.kt` | Home screen (Command Center / Quick stats) |
| `ui/SessionFragment.kt` | Active session tracking, temp controls, and live graphs |
| `ui/HistoryFragment.kt` | Analytics and historical sessions list |
| `ui/BatteryFragment.kt` | Battery health and charging graphs |
| `ui/SettingsFragment.kt` | App settings and preferences |
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

## UX Principles

*Established by Oracle audit 2026-03-26. Any agent writing UI code must internalize these before touching a screen.*

### The Three User Moments

Every screen serves one of three states. Screens that conflate multiple states become confusing:

| Moment | When | Goal | Feeling |
|---|---|---|---|
| **Pre-Session** | Device connected, about to start | Minimum friction to ignite | Ready |
| **Mid-Session** | Heater on, session active | Calm feedback; show only what matters | In control |
| **Post-Session / Review** | Session ended or reviewing history | Reflection and insight; meaning from data | Informed |

### Screen-to-Moment Map

| Screen | Primary Moment | Core Promise |
|---|---|---|
| `LandingFragment` | Pre-session + Status | "Your device, right now" — connect, ignite, go |
| `SessionFragment` | Mid-session | "You are in control" — temperature, mode, program |
| `SessionReportActivity` | Post-session | "Here is what happened" — clear, contextual, annotatable |
| `HistoryFragment` | Review | "Here is your pattern" — trends, not just numbers |
| `BatteryFragment` | Review + Maintenance | "Here is your device's health" — drain, cycles, care |
| `SettingsFragment` | Configuration | Device and app preferences — rarely visited, always correct |

### Empty States

Every screen must have a designed empty state. An empty screen is a **teaching moment**, not a failure. Empty states must:
- Explain what the screen shows when populated
- Tell the user what action will populate it
- Be visually distinct from a loading state

### Data Presentation Rules

- **Never show a raw number without context.** "247 minutes of heater runtime" is arcane. "247 min — consider cleaning soon" is useful.
- **Derivable data should be derived.** Don't make the user mentally compute what the app already knows.
- **Confidence signals matter.** When an estimate is unreliable (see `drainEstimateReliable` in B-015), say so, concisely and without burying the value.
- **Developer data is not user data.** MAC addresses, colour indices, and raw firmware strings belong behind a "Device Info" expandable, not in the main settings flow. Developer tools (test device injection, history rebuild) must be hidden behind a developer mode toggle.

### Session Flow Invariant

The primary session lifecycle — **connect → ignite → session → report** — must feel like one continuous arc, not four disconnected screens. Any design decision that interrupts this arc requires explicit justification. See F-062 for the full vision.

### Design Language

SBTracker uses a dark "Cyber Green" aesthetic. Until a formal design language document exists (tracked under B-007), agents must not invent new colors or type sizes. Use the existing established values in the codebase consistently. When B-007 is built, it will define the canonical palette, type scale, and component library that all agents must follow.

---

## Rules for Agents

> **If you are an agent picking up a task: go to `.agents/TASKS.md` first.**
> Find a `ready` task, read only its task file, and follow it exactly.
> You do not need to read the whole codebase. The task file tells you what to read and touch.

1. **Check `.agents/TASKS.md` first** — find a `ready` task and read its file in `.agents/tasks/`. This is your scope.
2. **Read this file (`PROJECT.md`)** for architecture context before any code changes.
3. **Never store derived data** in the DB — compute from `device_status`.
4. **Never skip schema versions** — always increment by 1.
5. **Keep `fallbackToDestructiveMigration()`** during development (removed in T-016).
6. **Update `BACKLOG.md`** when starting and completing work.
7. **Append to `CHANGELOG.md`** after completing changes.
8. **Mark tasks done in `.agents/TASKS.md`** when complete.
9. **Use agent branches** — `claude/T-XXX-description` for task work.
10. **Follow PR-based workflow** — create a branch, implement, verify, submit PR. Never commit directly to `main`.
11. **Verify build** — `./gradlew assembleDebug` must pass before pushing.
12. **Reference app** in `!gitignore.referenceonly-reactive-volcano-app-main/` is for protocol reference only — do not modify.

---

## Agent Infrastructure
Detailed guidelines and branch strategies for AI assistants are maintained in [AGENT_INFO.md](file:///Users/a0110/AndroidStudioProjects/sbtracker/AGENT_INFO.md).
