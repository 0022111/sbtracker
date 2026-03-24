# SBTracker — Feature Backlog

> **How to use**: Mark items `in-progress` when starting, `done` when complete. Add new items at the bottom of their priority group. Reference IDs in commit messages and branch names.

---

## Core Systems (P0 — Foundation)

| ID | Status | Feature | Description | Acceptance Criteria |
|---|---|---|---|---|
| F-001 | `done` | BLE Connection | Connect to S&B device via BLE, maintain GATT session | Scans, connects, receives notifications, handles reconnect |
| F-002 | `done` | Status Logging | Continuous `device_status` insert (god log) | Rows inserted every ~500ms (heater on) / ~30s (idle) |
| F-003 | `done` | Session Detection | Auto-detect heater sessions from status log | Sessions created with correct start/end, survives grace periods |
| F-004 | `done` | Hit Detection | Detect individual hits from temperature patterns | Hits stored in `hits` table, linked to session |
| F-005 | `done` | Charge Cycle Tracking | Detect and record charging events | ChargeCycle rows with start/end battery, rate |
| F-006 | `done` | Device Identity | Parse CMD 0x05 (serial, type, colour) | DeviceInfo stored, multi-device support via serial |
| F-007 | `done` | Session Rebuild | Reconstruct sessions/hits from raw log | `backcompileSessionsFromLogs()` works end-to-end |
| F-008 | `done` | DB Schema + Migrations | Room DB with explicit migration path | v2 schema, MIGRATIONS.md documented |

## Data Insight (P0 — Analytics)

| ID | Status | Feature | Description | Acceptance Criteria |
|---|---|---|---|---|
| F-010 | `done` | Session Summaries | Compute full stats from raw log per session | `SessionSummary` includes hits, battery, temp, heat-up |
| F-011 | `done` | History Stats | Aggregate stats across filtered sessions | HistoryStats card shows avg duration, hits, drain, etc. |
| F-012 | `done` | Usage Insights | Streaks, time-of-day patterns, weekly comparison | UsageInsights populates correctly |
| F-013 | `done` | Battery Insights | Drain trends, charge patterns, depth of discharge | BatteryInsights derived from sessions + charge cycles |
| F-014 | `done` | Personal Records | All-time bests (most hits, longest, most efficient) | PersonalRecords computed from summaries |
| F-015 | `done` | Daily Stats | Per-day aggregates for trend charts | DailyStats list drives bar chart |
| F-016 | `done` | Profile Stats | Lifetime totals (sessions, hits, heater hours) | ProfileStats card reads correctly |
| F-017 | `done` | Heat-up Estimation | Estimate time to heat based on target temp and history | Session screen shows ETA |

## Device Management (P1 — Polish)

| ID | Status | Feature | Description | Acceptance Criteria |
|---|---|---|---|---|
| F-020 | `done` | Multi-Device | Track multiple devices, switch between them | Known devices list, per-device history |
| F-021 | `done` | Device Controls | Write temp, heater on/off, boost, brightness, vibration | All CMD 0x01 / 0x06 write operations |
| F-022 | `done` | Phone Alerts | Vibrate/notify on temp ready, charge 80% | Alerts fire in foreground + background |
| F-023 | `done` | Dim on Charge | Auto-dim display brightness while charging | Brightness saves/restores correctly |
| F-024 | `done` | CSV Export | Export session history to CSV | File generated, share intent fires |
| F-025 | `planned` | History Clear | Per-device clear of all tables | All 6 tables wiped for target device |
| F-026 | `planned` | Data Backup / Restore | Export/import full database | User can backup and restore DB file |
| F-050 | `planned` | Notifications Overhaul | Modernize and expand the notification system to support persistent status, rich controls, and configurable alerts. | Persistent status notification, drawer-based quick controls, and proper channel implementation. |

## UI & Visualization (P1 — Next Up)

| ID | Status | Feature | Description | Acceptance Criteria |
|---|---|---|---|---|
| F-030 | `done` | Real-time Temp Graph | Live temperature chart during session | `GraphView` renders smooth curve |
| F-031 | `done` | Battery Graph | Battery level over time | `BatteryGraphView` renders correctly |
| F-032 | `done` | Session Detail Graph | Per-session temperature replay | `SessionGraphView` on `SessionReportActivity` |
| F-033 | `done` | History Bar Chart | Daily sessions bar chart | `HistoryBarChartView` displays with tap interaction |
| F-034 | `done` | History Timeline | Visual timeline of sessions | `HistoryTimelineView` renders |
| F-035 | `done` | UI Polish Pass | Consistent styling, colours, spacing | All screens feel cohesive and premium |
| F-036 | `done` | Settings Screen | Dedicated settings (day start hour, units, alerts) | Currently scattered in ViewModel, needs proper UI |

## Quality & Infra (P2 — Foundation for Scale)

| ID | Status | Feature | Description | Acceptance Criteria |
|---|---|---|---|---|
| F-040 | `done` | Version Control | Git + GitHub setup | Repo initialized, remote linked, .gitignore in place |
| F-041 | `done` | Project Docs | PROJECT.md, BACKLOG.md, CHANGELOG.md | Agent memory system works across sessions |
| F-042 | `ready` | Unit Tests | Test analytics pure functions | AnalyticsRepository functions have test coverage |
| F-043 | `ready` | ViewModel Decomposition | Break up 1074-line MainViewModel | Extract to feature-specific ViewModels or use cases |
| F-044 | `done` | MainActivity Decomposition | Break up 1169-line MainActivity | Extract Fragments or Compose screens |
| F-045 | `done` | Agent Infrastructure | Document AI agent branch strategy, workflows, and workspace context | `AGENT_INFO.md` and `.agents/workflows/` exist |
| F-046 | `done` | GitHub Integrity | Automate CI builds, PR templates, and enforced branching | `build.yml` automated, `PULL_REQUEST_TEMPLATE.md` exists |
| F-047 | `planned` | Jetpack Compose Migration | Overhaul UI from programmatic Views to Compose | LandingFragment + Settings migrated to Compose |
| F-048 | `planned` | BLE State Machine Overhaul | Refactor BLE layer for robust state tracking | BleManager uses sealed class states, backoff reconnection |
| F-049 | `planned` | Multi-Device Analytics Consolidation | Unify cross-device tracking in AnalyticsRepository | Consistent aggregated metrics across all devices |

---

## Bugs

| ID | Status | Priority | Description |
|---|---|---|---|
| B-001 | `done` | P1 | `fallbackToDestructiveMigration()` must be removed before public release |
| B-002 | `done` | P2 | History Page vertical scroll locked by unwieldy charts |
| B-003 | `done` | P1 | Battery Page crashes on load due to narrow graph segments |
| B-004 | `done` | P3 | Data retention setting has inconsistent text, says keep history for x days when it should say delete after x days |
| B-005 | `planned` | P3 | All graphs should collapse dead space, especially on history graph. even long periods of slow discharge should be condensed. event items are most interesting. |
| B-006 | `planned` | P3 | Graphs are not zoomable or alterable in scope except for hard toggles |
| B-007 | `planned` | P3 | Entire UI needs modernization and dynamicization, probably less of a bug and more of an enhancement |
| B-008 | `planned` | P3 | Autoshutdown in settings doesnt show full granularity of data |
| B-009 | `done` | P2 | Dim LED while charging doesnt restore to previous level, and acts funny if LED level is changed manually while charging. |
| B-010 | `planned` | P2 | Boost offset does not seem correct, or target temp is reporting incorrectly. is effective temp really the best method? |

---

## Notes & Decisions Log

| Date | Decision | Rationale |
|---|---|---|
| 2026-03 | Event-sourcing via `device_status` god log | All analytics retroactively improvable; feature tables are rebuildable |
| 2026-03 | `SessionSummary` is computed, not stored | Algorithm improvements apply to all history automatically |
| 2026-03 | Single-Activity + programmatic Views | Started simple; Compose migration is a future option |
| 2026-03-23 | Project management via markdown files in repo | Solves context-loss across AI agent sessions |
| 2026-03-23 | AI Agent branch strategy (`claude/` prefix) | Separates automated agent work from manual development; uses `verify-git-access-BVVfi` for connectivity reference |
| 2026-03-23 | GitHub Integrity & PR Workflow | Enforces build verification and structured documentation for all agent contributions |
| 2026-03-24 | Multi-device infrastructure enhancements | Added synthetic test device and cross-device landing page aggregates for better testability and transparency. |
| 2026-03-24 | Introduce Hilt Dependency Injection (T-006) | Standardized dependency management; unblocked ViewModel decomposition and testing. |
