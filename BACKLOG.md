# SBTracker — Feature Backlog

> **How to use**: Mark items `in-progress` when starting, `done` when complete. Add new items at the bottom of their priority group. Reference IDs in commit messages and branch names.

---

## Roadmap to Alpha (v0.2.0)
The codebase is currently in the "Final Hardening" phase. To reach a technical Alpha release, focusing on these consolidated Epics is required to move away from fragmented bugs toward a high-trust, stable system.

**Critical Alpha Milestones:**
* **🛡️ [Epic] Data Trust (B-010):** Resolving synthetic temperature accuracy for Venty/Veazy (Critical Blocker).
* **📂 [Epic] Data Mobility (F-026/027):** Secure database backup/restore and session program schedules for testers.
* **🎨 [Epic] UI/UX Refresh (F-100):** Consolidating F-053..F-056 and F-050; modernizing Home, Session, and History surfaces.

*Note: Infrastructure refactors (Compose migration) and minor UI bugs are deferred to the Beta/v1.0 phase to ensure a stable Alpha happens first.*

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
| F-018a | `done` | Health: Core | Phase 1: Capsule settings + basic intake tracking | Manual toggle, basic weight-based stats |
| F-018b | `planned` | Health: Insights | Phase 2: Grams/Week trends, habit analysis, dosage history | Advanced charts and efficiency metrics |

## 🎯 Milestone: v0.2 Alpha (Active Epics)

### 📂 Epic: Data Mobility & Recovery
| ID | Status | Feature | Description | Acceptance Criteria |
|---|---|---|---|---|
| F-025 | `in-progress` | History Clear | Per-device clear of all tables | All 6 tables wiped for target device |
| F-026 | `in-progress` | Data Backup / Restore | Export/import full database | User can backup and restore DB file |
| F-027 | `in-progress` | Session Programs | User-defined or preset session profiles with automatic boost scheduling | Presets trigger immediate session start |
| F-050 | `in-progress` | Notifications Redesign | Modernize notification system; persistent status | Rich status tray + quick controls |

### 🎨 Epic: The "v0.2 Visual Refresh" (UX Overhaul)
| ID | Status | Feature | Description | Acceptance Criteria |
|---|---|---|---|---|
| F-053 | `in-progress` | Session Start Battery | Show % at session start for drain context | Displayed on active session screen |
| F-054 | `in-progress` | Session Report Redesign | Extraction log labels + context | Human-readable logs, not just numbers |
| F-055 | `in-progress` | Home Redesign | Suppress idle 0°; accessible during session | Seamless heating/session flow |
| F-056 | `in-progress` | History Tab Splitting | Tabs for Sessions vs. Analytics vs. Health | No single-page clutter |
| B-007 | `planned` | Global Modernization | "Cyber Green" UI pass; dynamic elements | Modernized styling throughout |
| F-052 | `parked` | Hit Analytics | Classification of hits by duration/temp | On hold until B-010 is resolved |
| B-005 | `planned` | History Graph Condensing | Collapse dead space in charts | Condensed timeline visualization |
| B-006 | `planned` | Zoomable Graphs | Enable zoom/pinch interaction | Interactive visual scope |

### 🛡️ Epic: Data Trust & Protocol Reliability (Critical)
| ID | Status | Feature | Description | Acceptance Criteria |
|---|---|---|---|---|
| B-010 | `in-progress` | Temp Accuracy | Synthetic temp calculation for Venty/Veazy | Real packets capture/validate |
| B-012 | `in-progress` | Parser Guardrails | Add length validation for BlePacket parsers | NO ArrayIndexOutOfBounds crashes |
| F-042 | `ready` | Analytics Logic Tests | Unit tests for pure-function analytics | 100% logic coverage |
| B-014 | `documented` | Charge Taper Validation | Multipliers need real measurement | ETA accuracy at 70%+ |
| B-015 | `in-progress` | Drain Confidence | Add `drainEstimateReliable` flag to stats | UI shows confidence warning |
| B-013 | `documented` | Legacy Metadata Gap | pre-F-018 sessions lack metadata rows | Documented; UI fix planned |

## 🏗️ Future Architecture (v0.3 — Beta)
| ID | Status | Feature | Description |
|---|---|---|---|
| F-047 | `planned` | Compose Migration | Migrate UI fragments to Jetpack Compose |
| F-049 | `planned` | Multi-Device Sync | Cross-device aggregate tracking |

## 🪵 Legacy Foundation (All 'Done' P1/P2 Archive)
| ID | Feature | Summary |
|---|---|---|
| F-020–024 | Device Core | Multi-device tracking, controls, alerts, dim-on-charge, CSV export |
| F-030–036 | Visual Core | Real-time graphs (temp/battery), settings UI, style-pass |
| F-040–048 | Infrastructure | DI (Hilt), Git CI, BLE State Machine overhaul, MainActivity decomposition |
| B-001–B-004 | Native Cleanup | Migration safety, scroll fixes, crash fixes in Battery Page |
| B-011 | Reconnect Bug | Fix UI stuck in offline card after BLE reconnect |
| F-051 | Heat-up Enchancement | Logic for ETA weighting based on last session proximity |

---

---




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
| 2026-03-25 | Park F-052 Hit Analytics (Oracle verdict) | The threshold problem: no real hit-duration distribution data exists yet. Hardcoded thresholds (e.g. 3s "large hit") are arbitrary and will need ripping out after Alpha. Re-entry conditions: B-010 resolved, F-054 done, F-056 done, Alpha shipped. T-076 reverted; T-077/T-080/T-081 demoted to `planned`. |

---

## Draft Feature Specs

### F-052: Hit Analytics & Achievement System
*Status: **Parked** — Oracle verdict 2026-03-25. Do not build until re-entry conditions are met.*

**Re-entry conditions (all must be true before any code is written):**
1. **B-010 resolved** — temperature data is trustworthy. `peakTempC` is meaningless for temp-based achievements until this closes.
2. **F-054 complete** — `SessionReportActivity` redesign gives the hit log its stable display surface.
3. **F-056 complete** — Analytics sub-tab exists as the home for hit-type aggregate cards.
4. **Alpha shipped** — real users have produced real hit data. Look at the actual duration distribution before writing a single threshold value.

**Why parked:** The central risk is the threshold problem. There is no dataset. A "large hit" threshold of 3 seconds is a guess that could classify 10% or 90% of all hits as large. Building classification tiers before Alpha ships means building on sand, then ripping it out. The T-076 attempt (reverted 2026-03-25) proved this.

**The 80% version (build first when re-entry conditions met):**
- Hit type labels per row in session report hit log (duration + temp based, user-calibrated thresholds)
- Session-level badges computed at query time (no new DB tables — pure analytics)
- One aggregate card in Analytics sub-tab: hit type distribution, most achievement-dense session

**The full ideal (later decision):**
- Lifetime achievement ledger: persistent `achievements` table `(id, deviceAddress, type, earnedAtMs, sessionId)` — first deliberate exception to "no stored derived data" rule
- Achievement timeline view
- Live hit annotation during session in `SessionFragment`

**Data already available in `hits` table:** `durationMs`, `peakTempC`, `sessionId`, `startTime`, `endTime`. No schema changes needed for 80% version.

**Do NOT:** hardcode any duration or temperature thresholds before inspecting real hit distribution data.

---

### F-027: Session Programs — Implementation Status & Remaining Work

*Status: **In Development** — Library management done; execution, persistence, and display pipeline in progress.*

#### What's done (in `dev`)
| Task | What shipped |
|------|-------------|
| T-042 | `SessionProgram` entity, `SessionProgramDao`, Migration 3→4 (creates `session_programs` + `appliedProgramId` column on `session_metadata`) |
| T-043 | `ProgramRepository` CRUD + default preset seeding |
| T-044/T-045 | `setupProgramsGrid()` 2×3 grid + `showProgramEditor()` table dialog (Step# / Temp / Time) in `SessionFragment` — programs can be created, edited, deleted |

#### Remaining tasks (in order)
| Task | What it delivers |
|------|-----------------|
| **T-083** `ready` | DB Migration v4→5: `stayOnAtEnd: Boolean` field on `SessionProgram` (infrastructure; no UI yet) |
| **T-084** `ready` | `AnalyticsRepository.computeAvgDrainPerMinute()` + `HistoryViewModel.avgDrainPerMinute` StateFlow + `SessionViewModel` estimation helpers |
| **T-046** `ready` | Chip row UI for program selection, `startSessionWithProgram()`, `ActiveProgramHolder` singleton, `setBoost()` coroutine job — **programs execute for the first time** |
| **T-056** `blocked by T-046` | `MainViewModel` writes `appliedProgramId` to `session_metadata` on session complete via `ActiveProgramHolder.consume()` |
| **T-057** `blocked by T-056` | `appliedProgramName` in `SessionSummary`, history badge `▶ ProgramName`, `SessionReportActivity` program line |
| **T-085** `blocked by T-046, T-084` | SessionFragment hero window `MM:SS (est.)` + drain preview `−X% (Ym est.)` when program is selected and idle |

#### Architecture notes
- `boostStepsJson` format: `[{offsetSec: Int, boostC: Int}, …]` — cumulative offsets from session start; `boostC` is the delta above `targetTempC`
- Execution uses `setBoost()` (WRITE_BOOST command), never `setTemp(base + boost)` — avoids disrupting hit detection in `SessionTracker`
- `ActiveProgramHolder` (@Singleton) bridges `SessionViewModel` → `MainViewModel` without a cross-ViewModel dependency
- `appliedProgramName` is always resolved at query time from `session_programs` — never stored as a string in the DB

---

### F-018b: Health & Dosage (Phase 2 Insights)
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
