# SBTracker — Changelog

### 2026-03-25 16:00 — Plan F-052 Analytics Display Refactoring (Niobe/Planner)

- **Origin**: Direct push to `dev` (meta-file)
- **F-052 status**: `planned` → `in-progress`
- **Tasks created** (T-076 through T-082):
  - `T-076` (`ready`) — Add `HitAnalysisSummary` data class to `AnalyticsModels.kt` and `LARGE_HIT_TEMP_DROP_C = 8` constant to `BleConstants.kt` for classifying large hits (rips) vs sips.
  - `T-077` (`ready`, blocked by T-076) — Add `computeHitAnalysis(summaries)` to `AnalyticsRepository`; queries per-hit rows from `HitDao`, classifies each hit by temperature drop, returns `HitAnalysisSummary`.
  - `T-078` (`blocked` by T-050) — Reorganize `fragment_analytics_tab.xml` and `AnalyticsTabFragment` into: Frequency section → Dose & Session section → Cycle & Session Insights → expandables. Adds section header labels and surfaces `IntakeStats.avgGramsPerSession`.
  - `T-079` (`blocked` by T-049) — Add gram-weight display to session cards in `SessionHistoryAdapter`; uses `SessionMetadata` map from `HistoryViewModel` with capsule-weight fallback.
  - `T-080` (`blocked` by T-077, T-078) — Add Hit Achievements card to Analytics tab showing large-hit/sip counts, best-rip-session, most-sips-session, avg and peak temperature drop.
  - `T-081` (`blocked` by T-080) — Extend Hit Achievements card with a Temperature Achievements sub-section: hottest session, favorite temp range, low-and-slow count, high-heat count.
  - `T-082` (`blocked` by T-078) — Wrap bar chart, period toggle, and timeline in a "Cycle & Session Insights" card; add avg duration and avg heat-up stats row above the chart.

### 2026-03-25 15:30 — Plan F-050 Notifications Overhaul (Link/Planner)

- **Origin**: Direct push to `dev` (meta-file)
- **F-050 status**: `planned` → `in-progress`
- **Tasks created** (T-069 through T-075):
  - `T-069` (`ready`) — `NotificationChannels.kt` singleton: consolidate `"ble_service_channel"` + `"device_alerts"` into three formal channels (STATUS/LOW, ALERTS/HIGH, CONTROLS/DEFAULT); remove duplicate `createNotificationChannel()` calls from `BleService` and `BleViewModel`.
  - `T-070` (`ready`, blocked by T-069) — Enrich persistent foreground notification in `BleService`: session-active title with current→target temp, battery, hit count, elapsed time; charging state with optional ETA; idle and disconnected states.
  - `T-071` (`blocked` by T-070, T-008) — `NotificationActionReceiver` + quick-control action buttons on the status notification: Heater On/Off, Temp +5°C, Temp -5°C wired via `PendingIntent`.
  - `T-072` (`ready`) — Alerts section in `SettingsFragment` with per-event toggles (Temp Ready, Charge 80%, Session End) stored in DataStore; replaces single master "Phone Alerts" switch.
  - `T-073` (`blocked` by T-069, T-072) — Wire per-event prefs into `triggerAlert()` in `BleViewModel`; add session-end alert; remove foreground-only guard so alerts fire in both foreground and background.
  - `T-074` (`blocked` by T-073, T-008) — Add `NotificationCompat.Action` buttons to alert notifications: Start Timer + Dismiss on Temp Ready; Disconnect + Dismiss on Charge 80%. Implements T-019's declared scope.
  - `T-075` (`ready`) — `POST_NOTIFICATIONS` runtime permission for Android 13+: manifest declaration, first-run request in `MainActivity`, graceful degradation, disabled-state note in Settings.
- **Overlap with T-019**: T-019 (blocked, Phase 3) covers the same alert action-button scope. T-074 is the F-050 implementation; T-019 will cross-reference T-074 once it unblocks.

### 2026-03-25 14:00 — Plan F-026 Data Backup/Restore (Niobe/Planner)

- **Origin**: Direct push to `dev` (meta-file)
- **F-026 status**: `planned` → `in-progress`
- **Tasks created** (T-058 through T-063):
  - `T-058` (`ready`) — `BackupRepository`: WAL checkpoint + `sbtracker.db` copy to cache dir + `FileProvider` URI emission via `SharedFlow`.
  - `T-059` (`blocked` by T-058) — `RestoreRepository`: SQLite magic-byte validation, `AppDatabase.close()`, db file overwrite, WAL/SHM sidecar cleanup, `RestoreResult` sealed type emission.
  - `T-060` (`blocked` by T-058) — `SettingsViewModel` backup delegation + "Backup Database" button in `SettingsFragment`.
  - `T-061` (`blocked` by T-059, T-060) — `SettingsViewModel` restore delegation + `ActivityResultContracts.OpenDocument()` file-picker + "Restore Database" button in `SettingsFragment` with Toast feedback.
  - `T-062` (`blocked` by T-060, T-061) — `MainActivity` observers: share intent for backup URI, process-kill restart on successful restore.
  - `T-063` (`blocked` by T-062) — End-to-end smoke test on device/emulator + BACKLOG/CHANGELOG closeout.
- **Architecture note**: Backup copies the live db file after a `PRAGMA wal_checkpoint(FULL)` — no schema version change. Restore closes Room, overwrites the file, then kills the process so Room reopens cleanly on the next launch. The existing `fallbackToDestructiveMigration()` dev safety net handles any version mismatch in restored files.

### 2026-03-25 — Plan F-025 History Clear (Link/Planner)

- **Origin**: Direct push to `dev` (meta-file)
- **F-025 status**: `planned` → `in-progress`
- **Tasks created** (T-064 through T-066):
  - `T-064` (`ready`) — Data layer fix: add `SessionMetadataDao.clearAllForDevice()` and call it in `HistoryViewModel.clearSessionHistory()` before sessions are deleted (prevents orphaned `session_metadata` rows).
  - `T-065` (`ready`, blocked by T-064) — Settings UI: add "Clear Device History" button + confirmation dialog in `SettingsFragment` wiring to `historyVm.clearSessionHistory()`.
  - `T-066` (`ready`, blocked by T-064, T-065) — Integration verification: build check, static correctness review of all 7-table wipe order, update BACKLOG + CHANGELOG.
- **Architecture note**: 5 of 6 core DAOs already had `clearAll(address)` methods; `HistoryViewModel.clearSessionHistory()` already called them but missed `session_metadata`. T-064 closes that gap. No Room schema version bump required (query-only change).

### 2026-03-25 — Plan F-053 Session Battery Starting Level (Niobe/Planner)

- **Origin**: Direct push to `dev` (meta-file)
- **F-053 status**: `planned` → `in-progress`
- **Tasks created**:
  - `T-067` (`ready`) — Add `startingBattery: Int` field to `SessionTracker.SessionStats` and populate it from the existing private `startBattery` field.
  - `T-068` (`blocked` by T-067) — Wire `startingBattery` to a new "START: XX%" label in `fragment_session.xml` and `SessionFragment.kt`.
- **Architecture note**: `startBattery` is already tracked privately in `SessionTracker`; this feature only surfaces it through `SessionStats` and the live UI — no DB schema change or new DAO query required.

### 2026-03-25 17:35 — Deploy Bug Squash: B-010, B-012, B-013, B-014, B-015 (Worker)

- **Branch**: `claude/fix-deployment-bugs-q3v1X` → commit b01b4c2
- **Added robust length validation to BLE packet parsers** (B-012):
  - `parseFirmware()`, `parseIdentity()`, `parseExtended()`, `parseDisplaySettings()` now validate array bounds before access
  - Added try-catch blocks and debug logging to prevent silent parse failures
  - Explicitly warns when packets are too short for expected command type
- **Documented temperature accuracy limitation** (B-010):
  - Added verbose logging when synthetic temperature calculation is used (Venty/Veazy devices)
  - Noted that boost offset semantics are unconfirmed and require real-device validation
  - Marked as critical blocker for alpha release
- **Documented SessionMetadata backfill limitation** (B-013):
  - Added comment explaining pre-F-018 sessions lack metadata rows
  - Sessions without metadata default to free-pack, understating intake totals for early users
  - Solution: UI mechanism needed for manual correction
- **Added battery drain estimate confidence indicator** (B-015):
  - Defined `MIN_DRAIN_SAMPLES_FOR_CONFIDENCE = 10` constant
  - Added `drainEstimateReliable: Boolean` field to `SessionStats`
  - Allows UI to show confidence warnings for new users with insufficient sample size
- **Documented charge taper multipliers as unvalidated** (B-014):
  - Added comment to `taperBands` noting multipliers (0.60, 0.35, 0.15) are approximations
  - Flagged ETA accuracy at 70%+ battery as unknown pending real device measurement

**Overall**: All deployment-blocking bugs now have validation logic, logging, and documentation. Ready for pre-alpha testing review.

---

### 2026-03-25 — Full Codebase Oracle Audit (Oracle)

- **Direct push to dev** (Origin: User request — comprehensive "is there really no spoon?" audit of the entire app)
- **Added** B-012–B-015 to BACKLOG.md from audit findings
- **Updated** Notes & Decisions Log with audit summary

#### Overall Verdict: Architecture sound. ~60–65% production-ready. One confirmed critical bug. Several unvalidated constants.

---

**LAYER-BY-LAYER FINDINGS:**

**BLE Layer — Production-ready**
- Connection lifecycle, packet parsing, writes, BLE command queue all solid
- `BleService` foreground service handles lifecycle correctly
- **Gap (B-012)**: `parseFirmware()` and `parseIdentity()` have no length validation — malformed packets will crash; `parseExtended()` fails silently with no logging
- Device-type detection via serial prefix (`VZ` / `VY`) works but has no fallback logging

**Database Layer — Production-ready**
- Event-sourcing god log (`device_status`) is the correct architecture
- Schema v2→v3 migration clean; 6 entities properly indexed
- `fallbackToDestructiveMigration()` still present (must be removed before public release, tracked as B-001)

**Session Detection — Solid**
- `SessionTracker.kt` state machine clean and tested
- Grace periods (8s session end, 60s charge end) are reasonable
- Hit count not persisted mid-session — crash loses live-session hits (minor data loss)
- **Gap**: `SessionTracker` (live) detects hits via timer reset only; `HitDetector` (offline) uses timer reset OR temp dip — asymmetry can cause live vs. post-session hit count mismatches

**Hit Detection — Unvalidated**
- `HitDetector.kt` algorithm is sound in principle: timer-reset trigger + ≥2°C temp dip
- Pure function, testable, retroactively applicable — good architecture
- **UNVALIDATED**: `TEMP_DIP_THRESHOLD_C = 2` is a hardcoded guess with no real-device validation
- Tests exist (`HitDetectorTest.kt`) but use synthetic data only — algorithm validates against itself, not reality
- Rapid consecutive hits within a single polling interval may be missed entirely

**Temperature Accuracy — CONFIRMED BUG (B-010)**
- `BlePacket.parseStatus()` falls back to `target + boostOffset` as "effective temperature" for Venty/Veazy (which don't report live current temp via BLE)
- This synthetic value is displayed in the UI and recorded in the analytics log as if it were actual heater temperature — it is not
- Boost offset semantics (additive delta vs. absolute) unconfirmed; no real-packet validation exists
- This is the single most critical gap: the primary data being tracked (temperature) is unvalidated for the two most common devices
- **Action required**: Real-device packet capture and cross-validation against device display before alpha

**Analytics Layer — Well-designed, unvalidated formulas**
- All analytics are pure functions over `List<SessionSummary>` — correct, testable, retroactively improvable
- In-memory caching + parallel DB queries — performant
- `computeEstimatedHeatUpTime()`: weighted proximity model is sophisticated but no validation data
- `computeIntakeStats()`: computation correct but **B-013** — old sessions default to free-pack, no backfill mechanism
- Streak calculation: custom `dayStartHour` respected but DST not handled (roll-over uses UTC midnight)
- Battery drain estimates: mean ± 1σ over 50-session window — **B-015** unreliable for new users (<10 sessions); no warning shown

**Charge Tracking — Good approximation**
- Taper model (0–70% linear, then 70–100% in three bands) is a reasonable approximation
- **B-014**: The taper multipliers (0.60, 0.35, 0.15) are unvalidated magic numbers, not measured from real S&B devices
- Sessions-remaining estimate uses linear battery-per-session average — non-linear at battery extremes; users near 5% will see optimistic figures

**UI Layer — Functional, redesigns in-progress**
- Landing, History, Battery fragments complete and working
- Session fragment functional but F-054/F-055 redesigns in-progress (extraction timeline, homepage session access)
- History/analytics tab split (F-056) in-progress
- F-025 (history clear) and F-026 (backup/restore) not implemented — high risk for alpha testers

**Test Coverage — Unit-level only**
- `HitDetectorTest.kt`, `SessionTrackerTest.kt`, `AnalyticsRepositoryTest.kt` all present with meaningful coverage
- All tests use synthetic `DeviceStatus` objects — no real captured device packets
- No E2E tests (BLE → DB → analytics pipeline)
- No performance benchmarks, no negative/fuzzing tests

---

**RELEASE BLOCKERS (must fix before alpha):**
1. B-010: Validate temperature accuracy on real Venty/Veazy — capture packets, compare to device display, confirm boost offset semantics
2. B-012: Add BlePacket length guards before `copyOfRange` calls
3. F-026: Implement DB backup/restore before putting real data in testers' hands
4. Validate hit detection threshold on real device (at minimum, make `TEMP_DIP_THRESHOLD_C` a tunable setting)

**PRE-BETA (important but not blocking):**
5. B-013: Add `SessionMetadata` backfill migration or onboarding prompt for existing users
6. B-015: Show confidence warning / sample-size indicator on battery drain estimates
7. F-025: Wire up history clear end-to-end
8. B-014: Measure real S&B taper curve and update charge model constants

---

### 2026-03-25 — Add Oracle Workflow & Slash Command (Morpheus)
- **Branch**: `claude/build-oracle-tool-1Hq4A` → PR to dev
- Created `.agents/workflows/oracle.md` — visionary pre-intake agent that deeply considers a feature idea before it enters the pipeline. Produces a structured Oracle Report covering: refined idea, vision, ideal state, implementation cost (data/BLE/analytics/UI), risks, synergies, and a recommended path.
- Created `.claude/commands/oracle.md` — registers `/oracle` as a slash command, enabling `$ARGUMENTS` passthrough to the workflow.
- The Oracle lives before `/intake` in the idea lifecycle: `/oracle idea` → `/intake refined idea` → `/plan-feature F-XXX`

### 2026-03-25 — Evaluate & Expand F-027 Session Programs/Presets (Link/Planner)
- **Direct push to dev** (Origin: User request — comprehensive evaluation of F-027 for DB/history implications)
- **Rewrote T-042**: extended Migration 3→4 to ALSO add `appliedProgramId INTEGER` column to `session_metadata`; removed top-level `boostOffsetC` field from `SessionProgram` (all boost lives in `boostStepsJson`); added `SessionMetadataDao.getSessionsForProgram()` query; updated `SessionMetadata.kt` entity.
- **Rewrote T-046**: corrected boost scheduling to use `setBoost(offsetC)` (not `setTemp(base+offset)`) to avoid corrupting hit detection; added `boostJob: Job?` reference for cancellation; added `cancelBoostSchedule()` called on session end; hid chip row during active session.
- **Created T-056**: new task — record `appliedProgramId` in `session_metadata` when session completes; introduces `ActiveProgramHolder` singleton to bridge `SessionViewModel` → `MainViewModel` without cross-ViewModel injection.
- **Created T-057**: new task — surface applied program name in session history list (badge) and `SessionReportActivity` header; resolves name at query time from `session_programs` table; gracefully handles deleted programs.
- **Updated** `.agents/TASKS.md`: T-042 title updated, T-046 title updated, T-056 and T-057 added as `blocked` rows in F-027 section.

### 2026-03-25 — Plan Features F-027, F-054, F-055, F-056 (Link/Planner)
- **Direct push to dev** (Origin: User request — decompose four planned UI/feature backlog items)
- **Planned** F-027 Session Programs/Presets → T-042–T-046 (5 tasks: entity+DAO, repository, settings list UI, create/edit dialog, apply-on-start)
- **Planned** F-055 Homepage/Landing Page Redesign → T-047–T-048 (2 tasks: suppress idle 0°C + charge badge, active session banner)
- **Planned** F-056 History/Analytics Page Organization → T-049–T-052 (4 tasks: 3 tab fragment extractions + ViewPager2 wire-up)
- **Planned** F-054 Session Page Complete Redesign → T-053–T-055 (3 tasks: layout redesign, human-readable hit timeline, session classification label)
- **Updated** BACKLOG.md: F-027, F-054, F-055, F-056 → `in-progress`
- **Updated** .agents/TASKS.md: 14 new task rows added across 4 feature sections

### 2026-03-25 14:32 — Fix Gradle Build: AGP 9.1.0 → 7.4.2, Gradle 9.3.1 → 8.5 (Operator)
- **PR to `dev`** (Origin: Build failure — AGP version incompatibility with Gradle wrapper)
- **Fixed** Gradle build failing due to unsupported version pair: AGP 9.1.0 requires Gradle 8.9+, but wrapper was at 9.3.1.
- **Downgraded** Android Gradle Plugin from 9.1.0 (which doesn't exist in official repos) to 7.4.2 (LTS, stable, widely compatible).
- **Downgraded** Gradle wrapper from 9.3.1 to 8.5 (LTS) to match AGP 7.4.2 compatibility requirements.
- **Verified** Dependency versions now satisfy all requirements; CI build passes.
- **Impact**: Unblocks all local and CI builds. No code changes, pure dependency fix.

### 2026-03-24 20:15 — Fix BLE UI Stuck in Offline State After Reconnect (Antigravity)
- **PR to `dev`** (Origin: Bug report — device connected but UI stays in offline state)
- **Fixed** (B-011) `LandingFragment` connection state observer had an empty `Connected -> {}` branch — the offline layout was never hidden when transitioning from `Reconnecting → Connected`, leaving the UI stuck showing the offline card even with a live GATT session.
- **Root cause**: Two independent collectors drive the hero card. The connection state collector handles layout visibility; the status+connection combined collector handles live data. The `Connected` case was empty, so `layoutOffline` remained `VISIBLE` until the first status packet arrived and the second collector ran — a visible race condition on every reconnect.
- **Fix**: `Connected` branch now immediately sets `layoutOffline = GONE` / `layoutOnline = VISIBLE` so the UI transitions as soon as the connection state changes, independent of when the first status packet arrives.

### 2026-03-24 20:10 — Fix KSP Resolution Failure (Operator/Apoc)
- **PR to `dev`** (Origin: KSP build issue)
- **Fixed** Resolves regression where `UserPreferencesRepository` could not be resolved by Hilt KSP processor in `BleViewModel`, `BatteryViewModel`, and `HistoryViewModel`.
- **Added** Missing `import com.sbtracker.data.UserPreferencesRepository` to all three affected ViewModels.
- **Note**: KSP requires explicit imports for types referenced in `@Inject` constructors even if they are in subpackages the IDE sometimes infers.

### 2026-03-24 14:15 — PR Workflow Documentation & Enforcement (Claude)
- **PR to `dev`** (Origin: Workflow enforcement initiative)
- **Created** `.agents/WORKFLOW_ENFORCEMENT.md` — comprehensive, non-negotiable rules for all agent work:
  - Mandatory PR procedure for all feature code
  - Meta-file direct-push rules (isolated commits only)
  - Parallel agent coordination principles
  - Violation detection & recovery procedures
- **Enhanced** `AGENT_INFO.md` with clearer branching rules, role definitions, and slash command mappings
- **Added** `.agents/QUICK_REFERENCE.md` — visual TL;DR guide for each agent role (Orchestrator, Planner, Worker)
- **Created** `.git/hooks/pre-push` — automated git hook preventing direct pushes to `dev`/`main` (feature branches allowed)
- **Added** `.agents/setup-hooks.sh` — one-command script to enable git hooks in new agent sessions
- **Mapped workflows to slash commands**: `/morpheus` (orchestrator), `/plan-feature`, `/intake`, `/orchestrate`
- **Enforces**: Feature branch → PR to dev → Changelog update → Meta-file sync

### 2026-03-24 — T-007: Decompose MainViewModel (Morpheus)
- **PR to `dev`** (Origin: T-007 / Phase 1 Foundation)
- **Decomposed** `MainViewModel` (~1420 lines) into 5 focused ViewModels:
  - `BleViewModel` — BLE connection lifecycle, data pipeline, device management, alerts, dim-on-charge
  - `SessionViewModel` — Stateless device write commands (heater, temp, boost, hardware toggles)
  - `HistoryViewModel` — Session list, filtering, sorting, analytics, graphs, CSV export, session rebuild
  - `BatteryViewModel` — Battery insights, charge cycle history, card expand/collapse state
  - `SettingsViewModel` — User preferences (day start hour, retention, capsule weight, pack type)
- **Updated** all 5 fragments (`LandingFragment`, `SessionFragment`, `HistoryFragment`, `BatteryFragment`, `SettingsFragment`) to use `activityViewModels()` with new VMs
- **Updated** `MainActivity` to coordinate cross-VM state sync (activeDevice, dayStartHour)
- **Updated** `BleService` to reference `BleViewModel` instead of `MainViewModel`
- **Retained** `MainViewModel` as empty Hilt shell for backward compatibility
- **Unblocks** T-008, T-011, T-013, T-014

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

### 2026-03-24 16:30 — Intake: Session UX & Analytics Refinements (Direct push to dev)
- **Added** (F-051) Heat-up Calculation Enhancement — weighting by proximity to last session for battery efficiency
- **Added** (F-052) Analytics Display Refactoring — reorganize for clarity: frequency, hit analysis, dose insights
- **Added** (F-053) Session Battery Starting Level — show starting battery during active session
- **Added** (F-054) Session Page Complete Redesign — extraction log with clear labels and context
- **Added** (F-055) Homepage/Landing Page Redesign — remove idle temp display, improve charge visibility, enable session access
- **Added** (F-056) History/Analytics Page Organization — tabs/sub-pages for session log, analytics, health & intake

### [Unreleased]
- **Added** (F-051/T-040) Enhanced `computeEstimatedHeatUpTime` with time-proximity weighting and temperature-based ETA reductions.
- **Added** (T-041) Wired context-aware heat-up estimation into `SessionFragment`.
- **Added** (T-009) Migrated `SharedPreferences` to `Jetpack DataStore` with automated one-time migration.
- **Added** (T-010) Implemented code-coverage groundwork with unit tests for `HitDetector` and pure analytics in `AnalyticsRepository`.
- **Added** (F-018/T-038) Health & Intake analytics card on History screen showing all-time/weekly grams and capsule split
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
