# SBTracker — Feature Backlog

> **How to use**: Mark items `in-progress` when starting, `done` when complete. Add new items at the bottom of their priority group. Reference IDs in commit messages and branch names.

---

## Roadmap to Alpha (v0.2.0)
The current dev codebase (v0.1) is extremely close to a functional Alpha. To officially cut a stable release for early testers to use in the real world, the following items must be resolved:

**Critical Path (Must-Haves for Alpha):**
* **[B-010] Fix Temp Accuracy:** Resolve boost offset and target temp reporting. Since this is a vaporizer tracker, core temperature data must be explicitly trusted by users.
* **[F-026] Data Backup / Restore:** Allow testers to export/import the `sbtracker.db` file. This protects their "god log" during early experimental schema iterations.
* **[F-018] Health & Dosage Tracking:** *(Done)* Finalized the UI and Analytics layer; capsule weight settings, session type toggle, and intake card now fully functional.

**Stability Path (Should-Haves for Alpha):**
* **[F-048] BLE State Machine Overhaul:** Ensure robust backoff and reconnection so background tracking survives real-world Bluetooth flakiness (e.g., phones in pockets). *(Done)*
* **[F-042] Analytics Unit Tests:** Validate the pure-function analytics layer so the UI doesn't accidentally display garbage data.

*Note: Major architectural refactors (F-043 ViewModel Decomposition, F-047 Compose Migration) and P3 UI bugs should be deferred to the Beta/v1.0 phase so they don't block getting the app into testers' hands.*

---

## Core Systems (P0 — Foundation)

| ID | Status | Feature | Description | Acceptance Criteria |
|---|---|---|---|---|
| F-001 | `done` | BLE Connection | Connect to S&B device via BLE, maintain GATT session (Reconciled with ref app) | Scans, connects, receives notifications, handles reconnect |
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
| F-018 | `done` | Health & Dosage | Track capsule vs free-pack per session, calculate intake weight, habit analysis. | Settings for capsule weight, session toggle, intake stats |

## Device Management (P1 — Polish)

| ID | Status | Feature | Description | Acceptance Criteria |
|---|---|---|---|---|
| F-020 | `done` | Multi-Device | Track multiple devices, switch between them | Known devices list, per-device history |
| F-021 | `done` | Device Controls | Write temp, heater on/off, boost, brightness, vibration | All CMD 0x01 / 0x06 write operations |
| F-022 | `done` | Phone Alerts | Vibrate/notify on temp ready, charge 80% | Alerts fire in foreground + background |
| F-023 | `done` | Dim on Charge | Auto-dim display brightness while charging | Brightness saves/restores correctly |
| F-024 | `done` | CSV Export | Export session history to CSV | File generated, share intent fires |
| F-025 | `in-progress` | History Clear | Per-device clear of all tables | All 6 tables wiped for target device |
| F-026 | `in-progress` | Data Backup / Restore | Export/import full database | User can backup and restore DB file |
| F-027 | `in-progress` | Session Programs/Presets | User-defined or preset session profiles with automatic boost scheduling | Create/edit profiles with custom names, define boost times & amounts, select from default presets (terpene optimization, even step, full heat max rip), profiles trigger immediate session start with parameters |
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
| F-051 | `done` | Heat-up Calculation Enhancement | Improve F-017 with real-time weighted calculation based on proximity to last session; back-to-back sessions are more efficient | ETA accounts for device temperature, time since last session, average heat-up history |
| F-052 | `planned` | Analytics Display Refactoring | Reorganize confusing analytics: frequency focus, hit analysis (large hits, many sips, temperature-based achievements), clear dose/session/cycle insights | Analytics clearly categorized by use pattern, dose amounts visible, hit achievements shown |
| F-053 | `in-progress` | Session Battery Starting Level | Show starting battery level during active session for battery drain context | Session screen displays starting battery alongside current drain |
| F-054 | `in-progress` | Session Page Complete Redesign | Full rework of session detail: extraction log with clear labels, context for raw data (not just numbers) | Session report shows labeled data, extraction timeline is human-readable |
| F-055 | `in-progress` | Homepage/Landing Page Redesign | Overhaul command center: remove idle 0°C/32°F display, improve charge state visibility, enable access during sessions | Homepage is accessible during session, temperature display accurate, charge state prominently visible |
| F-056 | `in-progress` | History/Analytics Page Organization | Implement sub-pages/tabs to separate session log, analytics dashboard, and health/intake tracking | History page tabs/sub-pages for: Sessions, Analytics, Health & Intake; no single-page clutter |

## Quality & Infra (P2 — Foundation for Scale)

| ID | Status | Feature | Description | Acceptance Criteria |
|---|---|---|---|---|
| F-040 | `done` | Version Control | Git + GitHub setup | Repo initialized, remote linked, .gitignore in place |
| F-041 | `done` | Project Docs | PROJECT.md, BACKLOG.md, CHANGELOG.md | Agent memory system works across sessions |
| F-042 | `ready` | Unit Tests | Test analytics pure functions | AnalyticsRepository functions have test coverage |
| F-043 | `done` | ViewModel Decomposition | Break up 1074-line MainViewModel | Extract to feature-specific ViewModels or use cases |
| F-044 | `done` | MainActivity Decomposition | Break up 1169-line MainActivity | Extract Fragments or Compose screens |
| F-045 | `done` | Agent Infrastructure | Document AI agent branch strategy, workflows, and workspace context | `AGENT_INFO.md` and `.agents/workflows/` exist |
| F-046 | `done` | GitHub Integrity | Automate CI builds, PR templates, and enforced branching | `build.yml` automated, `PULL_REQUEST_TEMPLATE.md` exists |
| F-047 | `planned` | Jetpack Compose Migration | Overhaul UI from programmatic Views to Compose | LandingFragment + Settings migrated to Compose |
| F-048 | `done` | BLE State Machine Overhaul | Refactor BLE layer for robust state tracking | BleManager uses sealed class states, backoff reconnection |
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
| B-010 | `in-progress` | P2 | Boost offset/target temperature: synthetic temp calculation for Venty/Veazy unvalidated; added logging but requires real-device packet capture for confirmation. [See: BlePacket.parseStatus(), SessionTracker hardcoded boost offset semantics] |
| B-011 | `done` | P1 | BLE device stays physically connected but UI remains stuck in offline/reconnecting state — `LandingFragment` `Connected` branch was empty, never hid the offline layout. |
| B-012 | `in-progress` | P2 | Added length validation + logging to `BlePacket.parseFirmware()`, `parseIdentity()`, `parseExtended()`, `parseDisplaySettings()`. Now catches `ArrayIndexOutOfBoundsException` and logs failures. Ready for testing. |
| B-013 | `documented` | P2 | Sessions before F-018 lack `SessionMetadata` rows; default to free-pack. Documented limitation in `AnalyticsRepository.computeIntakeStats()`. Requires UI mechanism for users to manually correct pre-F-018 sessions. |
| B-014 | `documented` | P3 | Charge taper multipliers (0.60, 0.35, 0.15) documented as unvalidated approximations. Noted that ETA accuracy at 70%+ battery is unknown pending real S&B device measurement. |
| B-015 | `in-progress` | P3 | Added `drainEstimateReliable: Boolean` confidence flag to `SessionStats` (true if sample count ≥ 10). UI can now warn new users about unreliable predictions. |

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
| 2026-03-25 | Oracle full-codebase audit | Comprehensive review of implementation vs. claims across all layers. Verdict: architecture sound, core bug B-010 (temp accuracy) is the primary release blocker. Added B-012–B-015 from audit findings. See CHANGELOG for full report. |

---

## Draft Feature Specs

### F-018: Health & Dosage Tracking
*Status: Partially unblocked! Database Schema for `SessionMetadata` successfully merged in schema v3.*

**Release-Complete Implementation Plan:**

1. **Unblock Phase (COMPLETED):**
   * **Database Schema Fix:** Created `session_metadata` mapping to `session_id`. Safely isolates user-provided data from destructive session rebuilds. Migration v2->v3 fully tested.
   * **UI Prep (Pending):** Ensure the session report screen and settings are either migrated to Compose or stabilized enough to accept new UI elements without creating merge conflicts.

2. **Data Layer Implementation:**
   * Update the DataStore/SharedPreferences repository to support saving/reading `capsule_weight_grams` and `default_session_type`.

3. **Analytics Integration:**
   * Update the `AnalyticsRepository` to merge core Session info with the new `SessionMetadata`.
   * Write logic to calculate total dosage: count all capsule sessions in a given timeframe and multiply by the capsule weight setting. Calculate trend metrics (e.g., grams per week).

4. **UI Updates:**
   * **Settings:** Add a text input or slider for capsule weight, and a segmented button for the default pack type.
   * **Session Report:** Add a toggle near the top of the session summary allowing users to retroactively change a session between "Capsule" and "Free Pack."
   * **Insights Screen:** Build out the new "Health & Intake" card showing total consumed weight and usage habits over time.

5. **Testing & QA:**
   * Write unit tests for the intake calculations in `AnalyticsRepository`.
   * Verify UI states when changing the capsule weight retroactively (does it update past history correctly?).
   * Trigger a database rebuild (`backcompileSessionsFromLogs()`) manually to prove user-entered capsule flags survive the process.

6. **Release Polish:**
   * Update `CHANGELOG.md` with the new feature details.
   * Ensure string resources are localized/extracted.
   * Open the PR and pass all CI checks defined in your GitHub Integrity workflow.
