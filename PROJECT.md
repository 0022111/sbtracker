# SBTracker

**Android BLE companion app** for Storz & Bickel vaporizer devices (Veazy, Venty, Crafty+, Mighty+).
Connects via Bluetooth LE, tracks usage sessions, and provides analytics — all derived from a raw device status log.

---

## Architecture — Hybrid Web-Bridge

The system follows a **Hybrid Frontend / Native Backend** pattern. The Android application acts as a persistent BLE orchestrator and data-store, while the primary user interface is a **React/Vite** application hosted in a full-screen WebView.

### 1. The God Log: `device_status` (Event Sourcing)
- Every ~500ms while the heater is on (every ~30s when idle), the BLE layer polls CMD 0x01 and inserts a row.
- Contains: temperature (current / target / boost), battery, heater mode, charging state, settings flags.
- Indexed on `(deviceAddress, timestampMs)` — covers all range/aggregate queries.

### 2. Derived Data (computed, never stored)
- **Sessions** / **Hits** / **Analytics** — computed from `device_status` patterns via `HitDetector` and `AnalyticsRepository`.
- **Key Invariant**: Never store derived data. Improving any algorithm retroactively improves all history without migrations.

### 3. Communication Layer: WebSocket Bridge
- **VaporizerWebSocketServer**: A lightweight server running in `BleService` on port `8080`.
- **Telemetry Stream**: Native models are mapped to JSON via `TelemetryMapper` and broadcasted to Web clients.
- **Command Handling**: The Web UI sends commands (JSON) to the bridge, which `BleService` translates into BLE writes or ViewModel actions.

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
| `ui/` | **Primary UI**: React/Vite application (Dashboard, Session, History, etc.) |
| `MainActivity.kt` | Hybrid host: Configures the full-screen WebView and permission logic |
| `VaporizerWebSocketServer.kt` | Communication bridge between React and Native |
| `TelemetryMapper.kt` | Maps native models to JSON for the Web UI |
| `BleService.kt` | **Orchestrator**: Owns BLE lifecycle, WebSocket bridge, and session logic |
| `SessionReportActivity.kt` | *Legacy/Precursor*: Detail view for a single session (bridged by Web) |
| `ui/LandingFragment.kt` | *Legacy/Precursor*: Native home screen (superseded by Web) |

---

## Tech Stack

| Layer | Stack |
|---|---|
| **Android Core** | Kotlin, Room, Coroutines, Hilt |
| **User Interface** | **React**, Vite, Vanilla CSS, Framer Motion |
| **Communication** | Java-WebSocket (Port 8080) |
| **Build** | Gradle (Native) + NPM/Vite (UI) |
| **Min SDK** | Android 8.0 (API 26) |

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

## Future Platforms / Portability

The architecture is intentionally split to maximise portability:

**High portability — port these first:**
- Analytics layer — pure functions over `List<SessionSummary>`, zero platform dependencies
- Data model — event-sourcing pattern and SQL schema translate directly to any SQLite wrapper
- Session/hit detection algorithms — pure logic, no Android APIs

**Requires native reimplementation per platform:**
- BLE layer — same S&B protocol, different platform API (Android BLE, CoreBluetooth, react-native-ble-plx, etc.)
- Foreground service keepalive — Android-specific; equivalent needed on each target platform
- UI — full rewrite regardless of target

**Porting priority principle:**
> Prefer completing logic, data, and analytics work over UI/aesthetic work.
> A port inherits correctness; it doesn't inherit pixels.

**Platform notes:**
- iOS App Store: likely blocked by guideline 1.4.3 (cannabis device facilitation)
- iOS App Store: likely blocked by guideline 1.4.3 (cannabis device facilitation)
- PWA: **Hybrid-Viable** — The UI is now a standard React app. While background BLE is restricted in browsers, the UI can run in any modern browser to monitor a device running SBTracker on a local network or via the Android WebView host.
- Desktop: Viable via local WebSocket connection to the Android bridge.

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
6. **Drop a changelog fragment** — create `changelogs/T-XXX.md` on your feature branch. Never edit `CHANGELOG.md` directly.
7. **Do NOT edit `TASKS.md`, `BACKLOG.md`, or `CHANGELOG.md`** — that's the Orchestrator's job after your PR merges.
8. **Use agent branches** — `claude/T-XXX-description` for task work.
9. **Follow PR-based workflow** — create a branch, implement, verify, submit PR. Never commit directly to `main`.
10. **Verify build** — `./gradlew assembleDebug` must pass before pushing.
11. **Reference app** in `!gitignore.referenceonly-reactive-volcano-app-main/` is for protocol reference only — do not modify.

---

## Agent Infrastructure
Detailed guidelines and branch strategies for AI assistants are maintained in [AGENT_INFO.md](./AGENT_INFO.md).
