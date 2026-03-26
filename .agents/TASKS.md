# SBTracker — Task Index

> **For agents**: Pick a `ready` task, read its file in `.agents/tasks/`, follow it exactly.
> Do NOT read the entire codebase. The task file tells you exactly what to read and touch.
> When done, mark status `done` here and update `BACKLOG.md` + `CHANGELOG.md`.

---

## Branching model

```
main  ← stable, merges only from dev
  └── dev  ← integration branch; all agent PRs target this
        └── claude/T-XXX-...  ← your branch
```

## How to pick up a task

1. Find a `ready` task below with no agent assigned.
2. Read `.agents/tasks/<task-file>.md` — that is your full scope.
3. Create branch `claude/T-XXX-short-description` from **`dev`**.
4. Complete the steps in the task file.
5. Run `./gradlew assembleDebug` — must pass before pushing.
6. Open a PR targeting **`dev`** (not `main`) and mark the task `done` here.

---

## Phase 0 — Stop the Bleeding

| ID | Status | Title | Task File | Blocks |
|---|---|---|---|---|
| T-001 | `done` | Upgrade Dependencies | [T-001](tasks/T-001-upgrade-dependencies.md) | T-006, T-007 |
| T-002 | `done` | Enable R8 Minification | [T-002](tasks/T-002-enable-r8-minification.md) | — |
| T-003 | `done` | Fix Constant Duplication | [T-003](tasks/T-003-fix-temp-threshold-constant.md) | — |
| T-004 | `done` | Data Retention / Pruning | [T-004](tasks/T-004-data-retention-pruning.md) | — |
| T-005 | `done` | targetSdk 35 Compat Pass | [T-005](tasks/T-005-targetsdk35-compat.md) | — |
| T-022 | `done` | Fix "TARGET TARGET" Typo | [T-022](tasks/T-022-fix-target-target-typo.md) | — |
| T-023 | `done` | Wire Boost Visualization Toggle | [T-023](tasks/T-023-wire-boost-viz-toggle.md) | — |
| T-024 | `done` | Wire Factory Reset Button | [T-024](tasks/T-024-wire-factory-reset-button.md) | — |
| T-025 | `done` | Fix Day Start Hour Subtitle | [T-025](tasks/T-025-fix-day-start-subtitle.md) | — |
| T-028 | `done` | Log Silent Exception Catches | [T-028](tasks/T-028-log-silent-exceptions.md) | — |
| T-029 | `done` | Persist Battery Card Expand State | [T-029](tasks/T-029-persist-battery-expand-state.md) | — |
| T-033 | `done` | Squash Bugs B-001, B-004, B-009 | [T-033](tasks/T-033-squash-bugs.md) | — |

## Phase 1 — Foundation

| ID | Status | Title | Task File | Blocked by |
|---|---|---|---|---|
| T-006 | `done` | Introduce Hilt DI | [T-006](tasks/T-006-introduce-hilt-di.md) | — |
| T-007 | `done` | Decompose MainViewModel | [T-007](tasks/T-007-decompose-mainviewmodel.md) | — |
| T-008 | `ready` | Remove Fragment–Activity Casts | [T-008](tasks/T-008-remove-activity-casts.md) | T-007 |
| T-009 | `done` | Preferences → DataStore | [T-009](tasks/T-009-preferences-datastore.md) | — |
| T-010 | `done` | Unit Tests | [T-010](tasks/T-010-unit-tests.md) | — |
| T-026 | `done` | Enable ViewBinding | [T-026](tasks/T-026-enable-viewbinding.md) | — |
| T-027 | `done` | Extract Hardcoded Colors to colors.xml | [T-027](tasks/T-027-extract-colors-xml.md) | — |

## Phase 2 — F-018 Health & Dosage Tracking

| ID | Status | Title | Task File | Blocked by |
|---|---|---|---|---|
| T-034 | `done`  | Intake Prefs: capsule_weight + default_is_capsule in VM | [T-034](tasks/T-034-intake-prefs-viewmodel.md) | — |
| T-035 | `done` | IntakeStats analytics model + computeIntakeStats | [T-035](tasks/T-035-intake-analytics-model.md) | — |
| T-036 | `done` | Session Report: Capsule / Free-Pack Toggle | [T-036](tasks/T-036-session-report-capsule-toggle.md) | — |
| T-037 | `done`    | Settings UI: Capsule Weight + Default Pack Type | [T-037](tasks/T-037-settings-capsule-ui.md) | T-034 |
| T-038 | `done`    | History Screen: Health & Intake Card | [T-038](tasks/T-038-history-health-card.md) | T-034, T-035 |

## Phase 2 — User-Facing Features

| ID | Status | Title | Task File | Blocked by |
|---|---|---|---|---|
| T-011 | `ready` | Session Notes + Rating | [T-011](tasks/T-011-session-notes-rating.md) | T-007 |
| T-012 | `ready` | Temperature Presets | [T-012](tasks/T-012-temperature-presets.md) | T-009 |
| T-013 | `ready` | History Search & Filtering | [T-013](tasks/T-013-history-filtering.md) | T-007 |
| T-014 | `ready` | Tolerance Break Tracker | [T-014](tasks/T-014-tolerance-break-tracker.md) | T-007 |
| T-015 | `ready` | Onboarding Flow | [T-015](tasks/T-015-onboarding-flow.md) | T-009 |
| T-030 | `ready` | Migrate to Jetpack Compose | [T-030](tasks/T-030-migrate-to-jetpack-compose.md) | — |
| T-031 | `done` | Overhaul BLE State Machine | [T-031](tasks/T-031-overhaul-ble-state-machine.md) | — |
| T-032 | `ready` | Consolidate Multi-Device Analytics | [T-032](tasks/T-032-consolidate-multi-device-analytics.md) | — |
| T-040 | `done` | Enhance Heat-up Estimation | [T-040](tasks/T-040-enhance-heat-up-estimation.md) | — |
| T-041 | `done` | Wire Enhanced Heat-up Estimation | [T-041](tasks/T-041-wire-enhanced-heat-up-estimation.md) | T-040 |

## Phase 3 — Release Readiness

| ID | Status | Title | Task File | Blocked by |
|---|---|---|---|---|
| T-016 | `blocked` | Remove fallbackToDestructiveMigration | [T-016](tasks/T-016-remove-fallback-migration.md) | T-011 |
| T-017 | `ready` | Release Build Pipeline | [T-017](tasks/T-017-release-build-pipeline.md) | T-001 |
| T-018 | `ready` | Crash Reporting | [T-018](tasks/T-018-crash-reporting.md) | T-001 |
| T-019 | `blocked` | Notification Action Buttons | [T-019](tasks/T-019-notification-actions.md) | T-008 |
| T-020 | `blocked` | Quick Settings Tile | [T-020](tasks/T-020-quick-settings-tile.md) | T-008 |
| T-021 | `blocked` | Localization Groundwork | [T-021](tasks/T-021-localization-groundwork.md) | T-008 |

## Phase 3 — F-027 Session Programs/Presets

| ID | Status | Title | Task File | Blocked by |
|---|---|---|---|---|
| T-042 | `done` | SessionProgram Entity + DAO + Migration 3→4 (incl. appliedProgramId on session_metadata) | [T-042](tasks/T-042-session-program-entity.md) | — |
| T-043 | `done` | ProgramRepository: CRUD + Default Preset Seeding | [T-043](tasks/T-043-program-repository.md) | T-042 |
| T-044 | `done` | Program Grid + Editor UI in SessionFragment (2×3 grid, table step editor) | [T-044](tasks/T-044-programs-list-ui.md) | T-043 |
| T-045 | `done` | Create/Edit Program Dialog (table UI: Step# / Temp / Time rows) | [T-045](tasks/T-045-create-edit-program-dialog.md) | T-044 |
| T-046 | `done` | Apply Program on Heater Start (chip row, startSessionWithProgram, setBoost job) | [T-046](tasks/T-046-apply-program-on-start.md) | T-045 |
| T-056 | `done` | Record Program Attribution to session_metadata on Session Complete | [T-056](tasks/T-056-record-program-attribution.md) | T-046 |
| T-057 | `done` | Display Program Name in Session History + Session Report | [T-057](tasks/T-057-display-program-in-history.md) | T-056 |
| T-083 | `ready` | DB Migration v4→5: stayOnAtEnd field on SessionProgram | [T-083](tasks/T-083-session-program-stay-on-migration.md) | — |
| T-084 | `ready` | Program Drain Estimation: AnalyticsRepository + HistoryViewModel + SessionViewModel helpers | [T-084](tasks/T-084-program-drain-estimation.md) | T-043 |
| T-085 | `done` | SessionFragment: Program Hero Window + Drain Estimate Preview | [T-085](tasks/T-085-program-hero-window-and-drain-preview.md) | T-046, T-084 |
| T-087 | `done` | Live countdown to next program stage | [T-087](tasks/T-087-program-stage-timer.md) | T-085 |
| T-088 | `done` | Phone haptics (vibrations) on ignite/stage change | [T-088](tasks/T-088-program-haptics.md) | T-087 |
| T-089 | `ready` | UX: Manual Override Protection (Cancel job on manual temp) | — | T-046 |
| T-090 | `ready` | UX: Hero Card De-clutter (Split preview from live) | — | T-085 |
| T-091 | `ready` | UX: Grid Interaction Locking | — | T-088 |
| T-092 | `ready` | UX: Visual Step Editor (BottomSheet + Curve Graph) | — | T-045 |

## Phase 3 — F-055 Homepage Redesign

| ID | Status | Title | Task File | Blocked by |
|---|---|---|---|---|
| T-047 | `done` | Landing: Suppress Idle 0°C + Charge State Badge | [T-047](tasks/T-047-landing-idle-temp-charge-state.md) | — |
| T-048 | `ready` | Landing: Active Session Banner | [T-048](tasks/T-048-landing-session-banner.md) | T-047 |

## Phase 3 — F-056 History/Analytics Page Organization

| ID | Status | Title | Task File | Blocked by |
|---|---|---|---|---|
| T-049 | `done` | SessionsTabFragment: Sessions List Sub-Page | [T-049](tasks/T-049-sessions-tab-fragment.md) | — |
| T-050 | `ready` | AnalyticsTabFragment: Analytics Dashboard Sub-Page | [T-050](tasks/T-050-analytics-tab-fragment.md) | — |
| T-051 | `ready` | HealthTabFragment: Health & Intake Sub-Page | [T-051](tasks/T-051-health-tab-fragment.md) | — |
| T-052 | `blocked` | Wire History Tabs: TabLayout + ViewPager2 | [T-052](tasks/T-052-history-tabs-wire.md) | T-049, T-050, T-051 |

## Phase 3 — F-026 Data Backup/Restore

| ID | Status | Title | Task File | Blocked by |
|---|---|---|---|---|
| T-058 | `done` | BackupRepository: Close WAL, Copy DB to Cache, Emit FileProvider URI | [T-058](tasks/T-058-backup-repository.md) | — |
| T-059 | `blocked` | RestoreRepository: Validate, Close, Overwrite DB, Signal Restart | [T-059](tasks/T-059-restore-repository.md) | T-058 |
| T-060 | `blocked` | SettingsViewModel: Backup Action + Settings UI Button | [T-060](tasks/T-060-backup-viewmodel-and-settings-button.md) | T-058 |
| T-061 | `blocked` | SettingsViewModel: Restore Action + Settings UI Button | [T-061](tasks/T-061-restore-viewmodel-and-settings-button.md) | T-059, T-060 |
| T-062 | `blocked` | MainActivity: Wire Backup Share Intent + Restore App Restart | [T-062](tasks/T-062-mainactivity-wire-backup-restore-intents.md) | T-060, T-061 |
| T-063 | `blocked` | F-026 Smoke Test, CHANGELOG, and BACKLOG Closeout | [T-063](tasks/T-063-backup-restore-smoke-test-and-closeout.md) | T-062 |

## Phase 3 — F-025 History Clear

| ID | Status | Title | Task File | Blocked by |
|---|---|---|---|---|
| T-064 | `done` | History Clear: Data Layer Fix (SessionMetadataDao + HistoryViewModel) | [T-064](tasks/T-064-history-clear-data-layer.md) | — |
| T-065 | `ready` | History Clear: Settings UI Entry Point | [T-065](tasks/T-065-history-clear-settings-ui.md) | T-064 |
| T-066 | `ready` | History Clear: Integration Verification | [T-066](tasks/T-066-history-clear-verification.md) | T-064, T-065 |

## Phase 3 — F-053 Session Battery Starting Level

| ID | Status | Title | Task File | Blocked by |
|---|---|---|---|---|
| T-067 | `done` | Add startingBattery to SessionStats | [T-067](tasks/T-067-session-stats-starting-battery.md) | — |
| T-068 | `blocked` | Display Starting Battery in Active Session UI | [T-068](tasks/T-068-session-fragment-starting-battery-ui.md) | T-067 |

## Phase 3 — F-052 Analytics Display Refactoring

| ID | Status | Title | Task File | Blocked by |
|---|---|---|---|---|
| T-076 | `planned` | Hit Classification Fields in AnalyticsModels | [T-076](tasks/T-076-hit-classification-fields.md) | oracle F-052 redesign |
| T-077 | `planned` | Compute Hit Achievement Metrics in AnalyticsRepository | [T-077](tasks/T-077-hit-achievement-metrics.md) | oracle F-052 redesign |
| T-078 | `blocked` | Analytics Tab: Frequency & Dose Section Reorganization | [T-078](tasks/T-078-analytics-frequency-dose-section.md) | T-050 |
| T-079 | `blocked` | Dose Visibility in Session Cards | [T-079](tasks/T-079-dose-visibility-session-cards.md) | T-049 |
| T-080 | `planned` | Hit Achievements Display on Analytics Tab | [T-080](tasks/T-080-hit-achievements-display.md) | oracle F-052 redesign |
| T-081 | `planned` | Temperature-Based Achievement Display | [T-081](tasks/T-081-temperature-achievement-display.md) | oracle F-052 redesign |
| T-082 | `blocked` | Analytics Tab: Cycle & Session Insights Card | [T-082](tasks/T-082-analytics-cycle-insights-card.md) | T-078 |

## Phase 3 — F-054 Session Page Complete Redesign

| ID | Status | Title | Task File | Blocked by |
|---|---|---|---|---|
| T-053 | `done` | Session Report: Redesign Layout with Labeled Sections | [T-053](tasks/T-053-session-report-layout-redesign.md) | — |
| T-054 | `blocked` | Session Report: Human-Readable Extraction Timeline | [T-054](tasks/T-054-session-report-hit-timeline.md) | T-053 |
| T-055 | `blocked` | Session Report: Session Classification Label | [T-055](tasks/T-055-session-classification-label.md) | T-054 |

## Phase 3 — F-050 Notifications Overhaul

| ID | Status | Title | Task File | Blocked by |
|---|---|---|---|---|
| T-069 | `done` | Notification Channel Consolidation | [T-069](tasks/T-069-notification-channels.md) | — |
| T-070 | `done` | Persistent Status Notification Content | [T-070](tasks/T-070-persistent-status-notification.md) | T-069 |
| T-071 | `blocked` | Notification Drawer Quick Controls | [T-071](tasks/T-071-notification-quick-controls.md) | T-070, T-008 |
| T-072 | `ready` | Configurable Alert Settings UI | [T-072](tasks/T-072-alert-settings-ui.md) | — |
| T-073 | `blocked` | Alert Delivery Logic (Temp Ready, Charge 80%, Session End) | [T-073](tasks/T-073-alert-delivery-logic.md) | T-069, T-072 |
| T-074 | `blocked` | Alert Notification Action Buttons (T-019 implementation) | [T-074](tasks/T-074-t019-alert-action-buttons.md) | T-073, T-008 |
| T-075 | `ready` | POST_NOTIFICATIONS Permission Handling (Android 13+) | [T-075](tasks/T-075-notification-permission-handling.md) | — |
