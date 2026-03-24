# SBTracker — Changelog

### 2026-03-24 — F-018 Task Planning + Workflow Hardening (Direct push to dev)
- **Added** Task files T-034 through T-038 for F-018 Health & Dosage Tracking
- **Hardened** `feature-work.md` and `orchestrate.md`: explicit PR-to-dev rules, `mcp__github__create_pull_request` replaces `gh pr create`, NEVER-push-to-dev-directly rule enforced in writing
- **Verified** Matrix persona protocol in `AGENT_INFO.md` — complete and correct

### 2026-03-24 — Fix BLE Background Process Handoff (Direct push to dev)
- **Fixed** `BleService` now `@AndroidEntryPoint` with direct `@Inject BleManager` — no longer depends on `MainViewModel` being alive to act
- **Added** `BleManager.reconnectToAddress(address)` using `autoConnect = true` so Android's BT stack manages reconnection at the OS level, surviving process restarts
- **Fixed** `BleService.onStartCommand` triggers `reconnectToAddress` on `START_STICKY` restarts, reading last known device from SharedPrefs
- **Fixed** `serviceScope` changed from `Dispatchers.Main` to `Dispatchers.Default` (background service should not run on main thread)
- **Improved** Notification shows "Waiting for device..." for OS-managed background reconnect (attempt 0) vs "Reconnecting (N)..." for active retry loop

### 2026-03-24 — Remove Duplicate CMD_FIRMWARE Constant (Direct push to dev)
- **Fixed** Removed `CMD_FIRMWARE` (0x02) from `BleConstants` — was identical to `CMD_INITIAL_RESET` after protocol reconciliation in PR #26
- **Updated** `BlePacket.parseFirmware()` and `BleManager` notification router to reference `CMD_INITIAL_RESET` instead

### 2026-03-24 13:15 — Meta-file Live Sync (Antigravity)
- **Direct push to `dev`** (Origin: Orchestration)
- **Added** "Meta-file Live Sync" policy to ensure core project state (`BACKLOG.md`, `TASKS.md`, agent instructions) is always current on `dev` across all branches.
- **Updated** `AGENT_INFO.md`, `CLAUDE.md`, `.cursorrules`, and all `.agents/workflows/` to enforce immediate syncing of meta-files.
- **Note**: Simultaneous edits to these files get out of hand fast; direct syncing to `dev` minimizes fragmentation.

### 2026-03-24 15:47 — BLE Protocol Reconciliation (Operator/Apoc)
- **PR to `dev`** (Origin: Protocol alignment)
- **Fixed** Aligned Venty/Veazy handshake with reference app (`0x02` reset + `0x1D` status handshake).
- **Added** Initial support for Crafty+ and Volcano Hybrid Service UUIDs.
- **Fixed** `CMD_BRIGHTNESS_VIBRATION (0x06)` request size (now 7 bytes instead of 20).
- **Fixed** Stabilized reconnection loop to prevent concurrent `connectGatt` collisions.

### [Unreleased]
- **Added** (F-018/T-037) Capsule Weight and Default Pack Type settings rows wired to MainViewModel
- **Added** (F-018/T-034) `capsuleWeightGrams` and `defaultIsCapsule` SharedPrefs-backed StateFlows + setters in `MainViewModel`
- **Added** (F-048) Implemented an exponential backoff auto-reconnect loop (up to 30s intervals) that runs in the background if the device drops unexpectedly. Includes a 5-minute hard timeout to prevent endless battery drain if the device is permanently out of range.
- **Added** (F-048) Updated `BleService` persistent notification to reflect active reconnection attempts.
- **Added** (F-018) Unblocked Health & Dosage tracking by creating the `session_metadata` Room table and explicitly migrating the database from v2 to v3. This safely isolates user-entered data from destructive session rebuilds.
- **Added** (Orchestration) Formalized overhaul tasks for `MainViewModel` decomposition, Jetpack Compose migration, and BLE state machine refactoring.
- **Fixed** (B-001) Removed `fallbackToDestructiveMigration()` from `AppModule` to enforce explicit schema migrations.
- **Added** (Meta) Matrix Persona Protocol: All agents now identify themselves (e.g., "Neo, this is Morpheus") and maintain a singular identity per instance.
- **Added** (Meta) Matrix Persona Protocol: All agents now address the user as **Neo** and adopt a Matrix-inspired terminal persona across all instructions and workflows.
- **Improved** (B-004) Data retention setting now explicitly states "Delete after X days" for better clarity.
- **Fixed** (B-009) Dim-on-charge logic now correctly handles app restarts (persisted) and manual brightness overrides while charging.
- **Fixed** (T-028) Silent exceptions in `BleCommandQueue` and `MainViewModel` are now logged to logcat for easier troubleshooting.

All notable changes to this project. Agents: append to the top of the relevant section after completing work.

### 2026-03-24 — Simplify Issue Intake: Auto-Priority + Drop `dream` Label
- **Changed** Issue intake now triggers on `bug` + `enhancement` only (both built-in GitHub labels; `feature`/`dream` removed)
- **Improved** Priority auto-inferred by Claude from issue content; explicit `p0`/`p1`/`p2` labels still override

### 2026-03-24 — Wire Boost Visualization Toggle
- **Fixed** boost visualization switch in Settings was unresponsive; now wired to ViewModel

### 2026-03-24 — Wire Factory Reset Button
- **Added** confirmation dialog for factory reset button in Settings

### 2026-03-24 — Fix Day Start Hour Subtitle
- **Fixed** day start subtitle was static; now updates dynamically with selected hour

### 2026-03-24 — Log Silent Exception Catches
- **Improved** silent exception catches in BleCommandQueue and MainViewModel now log to logcat

### 2026-03-24 — Fix TARGET TARGET Typo
- **Fixed** copy-paste typo in session layout: "TARGET TARGET" → "TARGET TEMP"
### 2026-03-24 — Hierarchical Agent System
- **Added** `orchestrate.md` workflow — top-level orchestrator that reads project state, unblocks tasks, and generates worker kickoff prompts
- **Added** `plan-feature.md` workflow — planner that decomposes a backlog feature into atomic scoped task files
- **Added** `/orchestrate` and `/plan-feature` slash commands
- **Changed** `feature-work.md` — updated branch target from `main` to `dev`
- **Created** `dev` branch as integration target (all agent PRs now target `dev`, not `main`)

### 2026-03-24 — Introduce Hilt DI
- **Added** (T-006) Introduce Hilt Dependency Injection.
- **Setup** `@HiltAndroidApp` in `SBTrackerApp`, created `AppModule` for database and manager providers.
- **Migrated** `MainViewModel`, `MainActivity`, `SessionReportActivity`, and all UI fragments to Hilt.
- **Removed** Manual singleton implementation in `AppDatabase`.

### 2026-03-24 — Phase 0: Stop the Bleeding
- **Changed** Room dependency from `2.7.0-alpha13` to `2.6.1` (stable); drop alpha channel
- **Changed** `targetSdk`/`compileSdk` from 34 to 35 (Play Store requirement)
- **Upgraded** `lifecycle` 2.7.0 → 2.8.7, `coroutines` 1.7.3 → 1.9.0, `core-ktx` 1.12.0 → 1.15.0, `appcompat` 1.6.1 → 1.7.0, `material` 1.11.0 → 1.12.0
- **Enabled** R8 minification and resource shrinking for release builds (`minifyEnabled true`, `shrinkResources true`)
- **Fixed** `TEMP_DIP_THRESHOLD` constant duplication — moved to `BleConstants.TEMP_DIP_THRESHOLD_C` as single source of truth
- **Added** data retention pruning — `DeviceStatusDao.deleteRowsOlderThan()`, `AnalyticsRepository.pruneOldData()`, called on app startup; configurable 30/60/90/180/Never days in Settings (default 90)
- **Verified** targetSdk 35 compat: `foregroundServiceType`, `POST_NOTIFICATIONS` runtime permission, and `VibratorManager` API guards were already correct
- **Changed** build toolchain from JetBrains JDK 17 to JDK 21 (AGP 9.x requirement); updated CI workflow accordingly
- **Fixed** "TARGET TARGET" typo in `fragment_session.xml` (T-022)
- **Fixed** Day Start Hour subtitle in `SettingsFragment.kt` to update dynamically (T-025)

### 2026-03-24 01:00 — Multi-Device Infrastructure (Antigravity)
- **Implemented** synthetic test device injector in Settings to simulate multi-device scenarios without extra hardware.
- **Improved** landing page with aggregated session and battery data across all known devices.
- **Added** "Last Activity" chronological tracking across devices with ownership identifiers (e.g., `[TEST001]`).
- **Added** per-device battery status row on the landing page when multiple devices are remembered.
- **Added** clear UI indicators for analytics scope on the History page when viewing "All" devices.

### 2026-03-23 20:38 — Hit Detection Reliability (Antigravity)
- **Improved** hit detection by removing strict `setpointReached` requirement, allowing hits to register during temperature dips.
- **Fixed** false positives on hit detection by tracking target temperature changes and boost activations.
- **Removed** redundant temperature-dip detection in favor of the more reliable device idle timer reset.

### 2026-03-23 20:00 — UI Polish and Settings Screen (Antigravity)
- **Completed** F-035: Unified the app with a "Cyber Green" matrix aesthetic using a dark green-tinted charcoal base (`#0B110D`) and bright green accent (`#00FF41`).
- **Completed** F-036: Fully wired the native Settings screen, including `Day Start Hour` dialog and `Dim on Charge` bindings.

### 2026-03-23 19:45 — GitHub Integrity & Agent Instructions (Antigravity)
- **Automated** GitHub Actions build to run on `push` and `pull_request` to `main`.
- **Created** `PULL_REQUEST_TEMPLATE.md` to standardize contributions.
- **Created** `.cursorrules` to provide IDE-level agent guidance.
- **Enhanced** `AGENT_INFO.md` and `PROJECT.md` with strict PR requirements and branching policies.

 ### 2026-03-23 19:34 — Battery Page Crash Fix (Antigravity)
- **Fixed** an `IllegalArgumentException` in `BatteryGraphView` that occurred when drawing narrow segments on the battery history graph.

---

### 2026-03-23 19:28 — Enhanced Agent Infrastructure (Antigravity)
- **Created** `.agents/workflows/` with standardized workflows for `feature-work` and `documentation-sync`.
- **Updated** `AGENT_INFO.md` with guidelines on using internal agent artifacts (`task.md`, `implementation_plan.md`, `walkthrough.md`).
- **Refined** agent rules in `PROJECT.md` to formalize the use of workflows and internal artifacts.

### 2026-03-23 17:30 — History Page Scroll Fix (Antigravity)
- **Fixed** touch event interception logic in `HistoryBarChartView` and `HistoryTimelineView` to only trigger on horizontal dragging, allowing seamless vertical scrolling of the history page.

### 2026-03-23 17:10 — Session Feature Refactor (Antigravity)
- **Improved** hit detection accuracy in `SessionTracker`.
- **Added** estimated time to start based on target temperature and historical heat-up times.
- **Updated** Session UI for a more premium aesthetic.
- **Modified** `AnalyticsRepository`, `MainViewModel`, and `DeviceStatus` to support the new session logic.

### 2026-03-23 16:58 — UI Decomposition (Antigravity)
- **Extracted** `LandingFragment`, `SessionFragment`, `HistoryFragment`, `BatteryFragment`, and `SettingsFragment` from `MainActivity.kt` to their own files (`com/sbtracker/ui/`).
- **Extracted** `relativeDate` and `formatDurationShort` functions to `com/sbtracker/util/FormatUtils.kt`.
- `MainActivity.kt` reduced from 1169 lines to minimal boilerplate.

### 2026-03-23 15:44 — Project Management Setup (Antigravity)
- **Added** `PROJECT.md` — architecture documentation for agent continuity
- **Added** `BACKLOG.md` — structured feature/bug tracker with IDs and statuses
- **Added** `CHANGELOG.md` — this file
- **Added** `.gitignore` — standard Android ignores
- **Added** Git version control (initial commit)

### Pre-history (collapsed into initial commit)
- BLE connection layer (scan, connect, GATT, notifications, command queue)
- S&B protocol parser (CMD 0x01 status, 0x04 extended, 0x05 identity, 0x06 display)
- Room database v2 with 6 entities and event-sourcing architecture
- Session detection state machine with grace periods
- Hit detection from temperature patterns
- Charge cycle tracking
- Full analytics engine (HistoryStats, UsageInsights, BatteryInsights, PersonalRecords, DailyStats)
- Analytics caching via `AnalyticsRepository`
- Session rebuild from raw `device_status` log
- Multi-device support with serial-based tracking
- Real-time graphs (temperature, battery)
- History views (bar chart, timeline, session list)
- Session detail report
- CSV export
- Phone alerts (temp ready, 80% charge)
- Dim-on-charge feature
- Device controls (temperature, heater, boost, brightness, vibration)
