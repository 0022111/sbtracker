# SBTracker — Changelog

All notable changes to this project. Agents: append to the top of the relevant section after completing work.

### 2026-03-24 — T-022: Fix "TARGET TARGET" Typo
- **Fixed** copy-paste error in `fragment_session.xml` line 398 — changed `android:text="TARGET TARGET"` to `android:text="TARGET TEMP"`

### 2026-03-24 — Phase 0: Stop the Bleeding
- **Changed** Room dependency from `2.7.0-alpha13` to `2.6.1` (stable); drop alpha channel
- **Changed** `targetSdk`/`compileSdk` from 34 to 35 (Play Store requirement)
- **Upgraded** `lifecycle` 2.7.0 → 2.8.7, `coroutines` 1.7.3 → 1.9.0, `core-ktx` 1.12.0 → 1.15.0, `appcompat` 1.6.1 → 1.7.0, `material` 1.11.0 → 1.12.0
- **Enabled** R8 minification and resource shrinking for release builds (`minifyEnabled true`, `shrinkResources true`)
- **Fixed** `TEMP_DIP_THRESHOLD` constant duplication — moved to `BleConstants.TEMP_DIP_THRESHOLD_C` as single source of truth
- **Added** data retention pruning — `DeviceStatusDao.deleteRowsOlderThan()`, `AnalyticsRepository.pruneOldData()`, called on app startup; configurable 30/60/90/180/Never days in Settings (default 90)
- **Verified** targetSdk 35 compat: `foregroundServiceType`, `POST_NOTIFICATIONS` runtime permission, and `VibratorManager` API guards were already correct
- **Changed** build toolchain from JetBrains JDK 17 to JDK 21 (AGP 9.x requirement); updated CI workflow accordingly

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
