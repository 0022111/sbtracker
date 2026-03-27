# SBTracker — Feature Backlog

> **How to use**: Mark items `in-progress` when starting, `done` when complete. Add new items at the bottom of their priority group. Reference IDs in commit messages and branch names.

---

## Roadmap to Alpha (v0.2.0)
The codebase is currently in the "Final Hardening" phase. To reach a technical Alpha release, focusing on these consolidated Epics is required to move away from fragmented bugs toward a high-trust, stable system.

**Critical Alpha Milestones:**
* **🛡️ [Epic] Data Trust (B-010):** Resolving synthetic temperature accuracy for Venty/Veazy (Critical Blocker).
* **📂 [Epic] Data Mobility (F-026/027):** Secure database backup/restore and session program schedules for testers.
* **🎨 [Epic] UI/UX Refresh (F-100):** Transitioned to a **Hybrid Web UI** (React/Vite). Home, Session, and History surfaces now live in `ui/`.
* **🔌 [Epic] Bridge Maturity:** Hardening the WebSocket Telemetry Bridge (v0.2.0 milestone).

*Note: Infrastructure refactors (Compose migration) are superseded by the React UI transition for primary user-facing features.*

---

## 🚨 UX Debt (Oracle Audit 2026-03-26)

*Items identified in the Oracle UX evaluation. These are not cosmetic — they are structural gaps between "data logger with controls" and "companion app." Ordered by urgency.*

| ID | Status | Feature | Description | Acceptance Criteria |
|---|---|---|---|---|
| B-016 | `planned` | Developer Tools Segregation | Hide `btnDevInjectTestDevice` / `btnDevRemoveTestDevice` behind a 7-tap hidden toggle in Settings (Android developer-options pattern). **Pre-Alpha blocker.** | Regular users cannot reach dev tools; no phantom devices in tester data |
| F-057 | `planned` | Session Annotation | Add `notes: String` to `session_metadata` (Migration 5→6); surface as an editable field in `SessionReportActivity` and as a truncated line in `SessionHistoryAdapter` | User can write a note on any session; note visible in history list |
| F-058 | `planned` | Time Range Filter | 7d / 30d / 90d / All filter chips on History and Battery screens; `HistoryViewModel` gains a `selectedRange: StateFlow<DateRange>`; analytics queries respect the range | Charts and stats update to selected range; default is 30d |
| F-059 | `planned` | Onboarding & Empty States | One-time 3–4 step onboarding flow on first launch; per-screen empty state layouts for LandingFragment (offline / no history), HistoryFragment (no sessions), BatteryFragment (no charge data) | New user is guided; every empty screen teaches rather than blank-stares |
| F-060 | `planned` | Device Care Signals | Surface `lifetimeHeaterOnSec` from `ExtendedData` as a cleaning reminder; user-entered `lastCleanedAtMs` per device (SharedPreferences); LandingFragment banner or BatteryFragment card when threshold is approaching | User sees "X hours since last clean" with appropriate urgency; notification fires near threshold |
| F-061 | `planned` | Session Search & Filter | Filter sessions in HistoryFragment by date range, program used, content type, hit count range; optional week/month grouping in the list | History list is navigable with 200+ sessions; search bar above RecyclerView |
| F-062 | `planned` | Session Flow Arc | Unified home → heat → session → inline summary experience; Pre-Ignite card expands to full-screen heating modal from LandingFragment; session end transitions to inline summary rather than launching `SessionReportActivity` as a separate Activity | No screen jump during a single session lifecycle; the arc feels continuous |

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
| F-025 | `done` | History Clear | Per-device clear of all tables | All 7 tables wiped for target device (incl. session_metadata) |
| F-026 | `done` | Data Backup / Restore | Export/import full database | User can backup and restore DB file |
| F-027 | `in-progress` | Session Programs | User-defined or preset session profiles with automatic boost scheduling (T-046/T-085 done) | Presets trigger immediate session start |
| F-050 | `in-progress` | Notifications Redesign | Modernize notification system; persistent status | Rich status tray + quick controls |

### 🎨 Epic: The "v0.2 Visual Refresh" (UX Overhaul)
| ID | Status | Feature | Description | Acceptance Criteria |
|---|---|---|---|---|
| F-053 | `in-progress` | Session Start Battery | Show % at session start for drain context | Displayed on active session screen |
| F-054 | `in-progress` | Session Report Redesign | Extraction log labels + context | Human-readable logs, not just numbers |
| F-055 | `in-progress` | Home Redesign | Suppress idle 0°; accessible during session | Seamless heating/session flow |
| F-056 | `in-progress` | History Tab Splitting | Tabs for Sessions vs. Analytics vs. Health | No single-page clutter |
| B-007 | `planned` | Global Modernization | "Cyber Green" UI pass; dynamic elements; design language document defining palette, type scale, component library | Modernized styling throughout; no per-agent style invention |
| F-052 | `parked` | Hit Analytics | Classification of hits by duration/temp | On hold until B-010 is resolved |
| B-005 | `planned` | History Graph Condensing | Collapse dead space in charts | Condensed timeline visualization |
| B-006 | `planned` | Zoomable Graphs | Enable zoom/pinch interaction | Interactive visual scope |
| B-016 | `planned` | Developer Tools Segregation | *(see UX Debt section above)* | Pre-Alpha blocker |
| F-057 | `planned` | Session Annotation / Notes | *(see UX Debt section above)* | Pairs with F-054 |
| F-058 | `planned` | Time Range Filter | *(see UX Debt section above)* | Pairs with F-056 |
| F-059 | `planned` | Onboarding & Empty States | *(see UX Debt section above)* | New user experience |

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
| F-060 | `planned` | Device Care Signals | *(see UX Debt section above)* — heater runtime → cleaning reminders |
| F-061 | `planned` | Session Search & Filter | *(see UX Debt section above)* — filterable/searchable history list |
| F-062 | `planned` | Session Flow Arc | *(see UX Debt section above)* — unified home→heat→session→summary arc; design-first, depends on F-047 Compose migration for clean implementation |

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
| 2026-03-26 | Oracle UX Audit — app is a data logger, not yet a companion | Full UX evaluation against the live codebase (all 5 screens). Core finding: the architecture is excellent but the human layer is missing. The session narrative is broken across 4 screens; there is no onboarding, no annotation, no time-range filter, and developer tools are exposed to users. Added B-016, F-057–F-062 to address. UX Principles added to PROJECT.md as a standing agent directive. |
| 2026-03-26 | Portability strategy documented | React Native viable for future Android port. Priority: logic/data/analytics first, UI last. iOS App Store likely blocked (guideline 1.4.3). PWA not viable (background BLE). See `PROJECT.md` — Future Platforms section. |
| 2026-03-26 | F-018b: capsule weight is a global setting, not a per-session variable | Users do not meaningfully vary pack weight session to session. The binary isCapsule flag is the meaningful annotation. Per-session weight override UI will not be built; `SessionMetadata.capsuleWeightGrams` field exists in schema but its UI path remains closed. Weight is always resolved from global preferences at query time. |
| 2026-03-26 | F-018b: Health tab requires day-level intake granularity, not period averages | 7-day and 30-day trailing averages are too coarse — they hide the patterns that matter (which days are heavy, whether trend is up or down). `DailyStats` must gain `totalGramsConsumed` for charting. Default chart view must show ≥14 days. `gramsPerDay7d`/`gramsPerDay30d` can remain as summary figures only. |
| 2026-03-26 | Agent rule consolidation — eliminate merge conflicts | Workers may ONLY edit code files and `changelogs/T-XXX.md` fragments. TASKS.md, BACKLOG.md, CHANGELOG.md and PROJECT.md are Orchestrator/Planner owned. Rules unified across `CLAUDE.md`, `.cursorrules`, `AGENT_INFO.md`, `feature-work.md`, `WORKFLOW_ENFORCEMENT.md`, and `PROJECT.md`. |

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

*Status: **Hardening Complete** — Feature is now Alpha-ready.*

#### UX Hardening & Polish (Next Steps)
| Task | Priority | Description | Acceptance Criteria |
|---|---|---|---|
| **T-089** | 🟢 Done | **Manual Override Protection** | Cancel `programJob` immediately if user adjusts temp +/- or tops "Power Off". Prevents "ghost-boosts". |
| **T-090** | 🟢 Done | **Hero Card De-clutter** | Move "Drain Preview" and "Program Name" to a temporary "Pre-Ignite" card. Hero card only shows progress. |
| **T-091** | 🟢 Done | **Grid Interaction Locking** | Disable program grid buttons while a session is active to prevent state mismatch. |
| **T-092** | 🟢 Done | **Visual Step Editor** | Migrate dialog to BottomSheet; add a CSS-drawn or Canvas line chart showing the temp curve. |

#### What's done (in `dev`)
| Task | What shipped |
|------|-------------|
| T-042 | `SessionProgram` entity, `SessionProgramDao`, Migration 3→4 (creates `session_programs` + `appliedProgramId` column on `session_metadata`) |
| T-043 | `ProgramRepository` CRUD + default preset seeding |
| T-044/T-045 | `setupProgramsGrid()` 2×3 grid + `showProgramEditor()` table dialog (Step# / Temp / Time) in `SessionFragment` — programs can be created, edited, deleted |

#### Remaining tasks (in order)
| Task | Status | What it delivers |
|---|---|---|
| **T-083** | `done` | DB Migration v4→5 and `stayOnAtEnd` logic in `SessionViewModel`. |
| **T-084** | `done` | `AnalyticsRepository.computeAvgDrainPerMinute()` + `HistoryViewModel.avgDrainPerMinute` StateFlow + `SessionViewModel` estimation helpers |
| **T-046** | `done` | Chip row UI for program selection, `startSessionWithProgram()`, `ActiveProgramHolder` singleton, `setBoost()` coroutine job — **programs execute for the first time** |
| **T-056** | `done` | `MainViewModel` writes `appliedProgramId` to `session_metadata` on session complete via `ActiveProgramHolder.consume()` |
| **T-057** | `done` | `appliedProgramName` in `SessionSummary`, history badge `▶ ProgramName`, `SessionReportActivity` program line |
| **T-085** | `done` | SessionFragment hero window `MM:SS (est.)` + drain preview `−X% (Ym est.)` when program is selected and idle |
| **T-087** | `done` | Live countdown to next program stage in Hero window when program is executing |
| **T-088** | `done` | Phone haptic feedback (vibrations) on program ignite, stage changes, and auto-shutoff. |
| **T-089** | `done` | Manual Override Protection (Cancel job on manual temp +/- or Power Off) |
| **T-090** | `done` | Hero Card De-clutter (Split pre-ignite preview from live stats) |
| **T-091** | `done` | Grid Interaction Locking (Prevent mid-session program switching) |
| **T-092** | `done` | Visual Step Editor (BottomSheet + Curve Graph) |

#### Architecture notes
- `boostStepsJson` format: `[{offsetSec: Int, boostC: Int}, …]` — cumulative offsets from session start; `boostC` is the delta above `targetTempC`
- Execution uses `setBoost()` (WRITE_BOOST command), never `setTemp(base + boost)` — avoids disrupting hit detection in `SessionTracker`
- `ActiveProgramHolder` (@Singleton) bridges `SessionViewModel` → `MainViewModel` without a cross-ViewModel dependency
- `appliedProgramName` is always resolved at query time from `session_programs` — never stored as a string in the DB

---

### F-018b: Health & Dosage (Phase 2 Insights)
*Status: Partially unblocked! Database Schema for `SessionMetadata` successfully merged in schema v3.*

#### Design Constraints (Oracle session 2026-03-26)

**Weight is not a per-session variable.**
A session is either a capsule (fixed weight set once globally in Settings) or a free pack. Users don't meaningfully vary how much they pack from session to session. Therefore:
- The global `capsuleWeightGrams` preference is the canonical weight source.
- The `capsuleWeightGrams` field on `SessionMetadata` **may exist in schema** but per-session weight override UI should NOT be built — it adds complexity without solving a real user problem.
- The meaningful input the user makes is the binary `isCapsule` flag per session (capsule vs. free pack). That is the annotation to optimize for, not a weight slider.
- T-079 (dose visibility in session cards) correctly reflects this: show `"Xg"` for capsule sessions using the global default. Do not add a per-session weight edit field.

**Time granularity must be day-level, not period-average.**
`gramsPerDay7d` and `gramsPerDay30d` in `IntakeStats` are trailing averages — too coarse to be meaningful. Even a 7-day average smooths out the patterns that matter (which days are heavy days, whether consumption is trending up). The Health tab needs a day-by-day intake bar chart:
- `DailyStats` must gain a `totalGramsConsumed: Float` field (computed from `session_metadata` joins).
- The chart should show at minimum 14 days of daily bars by default. 7 days is not enough context.
- Period selectors (7d / 30d / 90d) from F-058 should apply here too, but the default view must be granular enough to read individual days.
- `gramsPerDay7d` / `gramsPerDay30d` can remain as summary figures but must not be the primary display.

**Free-pack sessions are excluded from gram totals — be honest about it.**
A user who free-packs will see `0.00g` everywhere. The UI should not show gram stats at all for users with no capsule sessions — instead show a prompt to configure intake tracking. Showing zeroes to free-pack users is misleading.

**Release-Complete Implementation Plan:**

1. **Unblock Phase (COMPLETED):**
   * **Database Schema Fix:** Created `session_metadata` mapping to `session_id`. Safely isolates user-provided data from destructive session rebuilds. Migration v2→v3 fully tested.
   * **UI Prep (Pending):** Ensure the session report screen and settings are either migrated to Compose or stabilized enough to accept new UI elements without creating merge conflicts.

2. **Data Layer Implementation:**
   * Global `capsule_weight_grams` and `default_is_capsule` preferences are **already implemented** in `UserPreferencesRepository`.
   * Extend `DailyStats` with `totalGramsConsumed: Float` — thread `metadataMap` into `computeDailyStats()` in `AnalyticsRepository`.
   * Do NOT add per-session weight override to the data layer — the schema field exists but the UI path should remain closed.

3. **Analytics Integration:**
   * `computeIntakeStats()` is already implemented and correct.
   * Add week-over-week delta: `gramsChangeVsLastWeek: Float` to `IntakeStats` — pure math, no DB access.
   * `computeDailyStats()` gains intake grams per day (see above).

4. **UI Updates:**
   * **Settings:** Capsule weight input and default pack type toggle are **already implemented**.
   * **Session Report:** The `isCapsule` toggle (Capsule / Free Pack) is **already implemented**. Do NOT add a weight override field.
   * **Health Tab:** Replace the three-stat text layout with:
     - A daily intake bar chart (min 14 days of bars, reuses `HistoryBarChartView` pattern)
     - A 3-stat summary row: total all time · avg/day · week-over-week delta
     - A session-type split line: `N capsule · M free-pack`
     - Empty state for users with no capsule sessions: explain the feature, link to Settings to configure capsule weight.

5. **B-013 Gap (Legacy Sessions):**
   * Sessions predating F-018 have no `SessionMetadata` row and default to free-pack / 0g.
   * Show a one-time dismissible banner in the Health tab: *"X sessions predate intake tracking and aren't included in these totals."*
   * Do not attempt a retroactive backfill — there is no reliable signal for what those sessions were.

6. **Testing & QA:**
   * Write unit tests for `computeIntakeStats()` and the extended `computeDailyStats()` — these are pure functions, trivially testable. Pairs with F-042.
   * Verify that changing global capsule weight retroactively updates all gram totals correctly (it should, since weight is resolved at query time).
   * Trigger `backcompileSessionsFromLogs()` to prove `isCapsule` flags in `session_metadata` survive a session rebuild.

7. **Release Polish:**
   * Update `CHANGELOG.md` with the new feature details.
   * Ensure string resources are localized/extracted.
   * Open the PR and pass all CI checks.
