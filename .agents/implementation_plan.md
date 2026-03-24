# SBTracker — Remediation & Roadmap Implementation Plan

**Created**: 2026-03-24
**Status**: Awaiting approval
**Scope**: Full project audit → phased remediation + feature roadmap

---

## Context

The event-sourcing architecture (`device_status` god log, pure analytics functions) is sound and worth preserving. The surrounding codebase is not release-ready. This plan addresses technical debt, missing critical infrastructure, and user-facing feature gaps in priority order.

Approval is required before execution of any phase.

---

## Phase 0 — Stop the Bleeding
*Estimated effort: 1–2 days. No new features. Blockers for any real use.*

### 0.1 — Dependency Upgrades
- [ ] Upgrade Room to latest stable (≥ 2.6.1; drop alpha)
- [ ] Bump `targetSdk` and `compileSdk` to 35 (Play Store requirement post-Aug 2025)
- [ ] Upgrade `lifecycle` to 2.8+
- [ ] Upgrade `kotlinx-coroutines` to 1.9+
- [ ] Upgrade `appcompat`, `material`, `core-ktx` to current stable

**Files**: `app/build.gradle`

### 0.2 — Enable R8/Minification in Release
- [ ] Set `minifyEnabled true` in `release` build type
- [ ] Audit and fix any ProGuard rules needed for Room, BLE, Coroutines

**Files**: `app/build.gradle`, `proguard-rules.pro`

### 0.3 — Fix Constant Duplication
- [ ] Move `TEMP_DIP_THRESHOLD = 2` from both `HitDetector.kt` and `SessionTracker.kt` into `BleConstants.kt`
- [ ] Update both call sites to reference the shared constant

**Files**: `BleConstants.kt`, `HitDetector.kt`, `SessionTracker.kt`

### 0.4 — Data Retention / Pruning
- [ ] Add `deleteStatusRowsOlderThan(deviceAddress, thresholdMs)` to `DeviceStatusDao`
- [ ] Add `pruneOldData(retentionDays: Int)` to `AnalyticsRepository` (default 90 days)
- [ ] Call on app startup from `MainViewModel.init` after DB is ready
- [ ] Expose retention days as a setting in `SettingsFragment`

**Files**: `DeviceStatusDao.kt` (new query), `AnalyticsRepository.kt`, `MainViewModel.kt`, `SettingsFragment.kt`

**DB impact**: No schema change required. Pure delete query.

### 0.5 — targetSdk 35 Compatibility Pass
- [ ] Audit `BleService` for foreground service type declaration (required on API 34+)
- [ ] Audit notification permission handling (`POST_NOTIFICATIONS` on API 33+)
- [ ] Audit `Vibrator` / `VibratorManager` API level guards

**Files**: `BleService.kt`, `MainViewModel.kt`, `AndroidManifest.xml`

---

## Phase 1 — Foundation
*Estimated effort: 1–2 weeks. Required before Phase 2 can scale.*

### 1.1 — Introduce Dependency Injection (Hilt)
- [ ] Add Hilt plugin + dependencies to `build.gradle`
- [ ] Create `@HiltAndroidApp` Application class
- [ ] Create `@Module` providing `AppDatabase`, `AnalyticsRepository`, `BleManager`
- [ ] Inject into `MainViewModel` via `@HiltViewModel`
- [ ] Remove `AppDatabase.getInstance()` singleton and `BleManager` direct construction

**Rationale**: Nothing is currently testable. This unlocks F-042 (unit tests) and removes god-object wiring.

**Files**: New `SBTrackerApp.kt`, `AppModule.kt`; modified `MainViewModel.kt`, `AppDatabase.kt`, `BleManager.kt`

### 1.2 — Decompose MainViewModel (F-043)
Split the 1074-line ViewModel into focused units:

| New ViewModel | Responsibility |
|---|---|
| `BleViewModel` | Connection state, scan, disconnect |
| `SessionViewModel` | Active session, live stats, hit stream |
| `HistoryViewModel` | Session list, filters, analytics queries |
| `BatteryViewModel` | Charge cycle data, battery insights |
| `SettingsViewModel` | Preferences read/write |

- [ ] Extract each ViewModel with its own `StateFlow`s
- [ ] Update each Fragment to observe its scoped ViewModel
- [ ] `MainViewModel` becomes a thin coordinator or is eliminated

**Files**: 5 new ViewModel files; updated all Fragment files

### 1.3 — Remove Fragment–Activity Cast Coupling
- [ ] Replace `requireActivity() as MainActivity` in all Fragments
- [ ] Use `activityViewModels<BleViewModel>()` pattern or a shared interface
- [ ] This unblocks proper Fragment testing

**Files**: `LandingFragment.kt`, `SessionFragment.kt`, `HistoryFragment.kt`, `BatteryFragment.kt`, `SettingsFragment.kt`

### 1.4 — Abstract Preferences into UserPreferencesRepository
- [ ] Create `UserPreferencesRepository` backed by AndroidX DataStore (not SharedPreferences)
- [ ] Migrate: `dayStartHour`, `phoneAlertsEnabled`, `dimOnCharge`, `targetTemp`, `retentionDays`
- [ ] Inject via Hilt

**Files**: New `UserPreferencesRepository.kt`; updated `SettingsViewModel`, `BleViewModel`

### 1.5 — Unit Tests (F-042)
Cover pure functions first — zero infrastructure needed:

- [ ] `HitDetector.detect()` — known input sequences → expected hit count/timing
- [ ] `AnalyticsRepository` aggregate functions (HistoryStats, UsageInsights, PersonalRecords)
- [ ] `SessionTracker` state machine transitions
- [ ] `TempUtils` °C ↔ °F

**Files**: New test files under `app/src/test/java/com/sbtracker/`

---

## Phase 2 — User-Facing Features
*Estimated effort: 2–4 weeks. Highest retention impact.*

### 2.1 — Session Notes + Rating (schema v3)
- [ ] Add `notes: String?` and `rating: Int?` to `Session` entity
- [ ] Write `Migration(2, 3)` with `ALTER TABLE sessions ADD COLUMN ...`
- [ ] Add input UI in `SessionReportActivity` (editable note field + star rating widget)
- [ ] Surface rating in `SessionHistoryAdapter` (star badge on session row)
- [ ] Include notes/rating in CSV export

**Backlog**: New item `F-050`

### 2.2 — Temperature Presets
- [ ] Store list of `TempPreset(name: String, tempC: Int)` in DataStore (JSON array)
- [ ] Add preset management UI in `SettingsFragment` (add/rename/delete)
- [ ] Add preset quick-select strip in `SessionFragment` temperature controls
- [ ] Default presets: Low (170°C), Medium (185°C), High (200°C)

**Backlog**: New item `F-051`

### 2.3 — History Search & Filtering
- [ ] Add filter bar to `HistoryFragment`: date range picker, min/max hits, device selector
- [ ] Add text search on session notes (once 2.1 lands)
- [ ] Filter applied in-memory over cached `SessionSummary` list (no new DB queries)

**Backlog**: New item `F-052`

### 2.4 — Tolerance Break Tracker
- [ ] Compute "current streak" (consecutive days with ≥ 1 session) from session list — pure function
- [ ] Compute "days since last session" for break tracking
- [ ] Add break goal setting (target N days) in `SettingsFragment`
- [ ] Show streak card on `LandingFragment`

**Backlog**: New item `F-053`

### 2.5 — Onboarding Flow (First-Run)
- [ ] Detect first run via DataStore flag
- [ ] 3-screen `OnboardingActivity`: (1) What is SBTracker, (2) BLE permission explanation, (3) Notification permission explanation
- [ ] Call `ActivityResultContracts.RequestMultiplePermissions` during onboarding

**Backlog**: New item `F-054`

---

## Phase 3 — Polish & Release Readiness
*Estimated effort: 1–2 weeks.*

### 3.1 — Remove `fallbackToDestructiveMigration()` (B-001)
- [ ] Ensure explicit migrations exist for all version transitions (v1→v2, v2→v3)
- [ ] Remove `.fallbackToDestructiveMigration()` from `AppDatabase`

### 3.2 — Release Build Pipeline
- [ ] Add signing config to `build.gradle` (keystore via env vars for CI)
- [ ] Add `release` variant GitHub Actions job
- [ ] Version bump automation: `versionCode` from git tag or CI build number

### 3.3 — Crash Reporting
- [ ] Add Firebase Crashlytics (or Sentry if avoiding Google)
- [ ] Initialize in `SBTrackerApp`

### 3.4 — Notification Action Buttons
- [ ] "Temp Ready" notification: add "Start Timer" action (starts a countdown) and "Dismiss"
- [ ] "80% Charge" notification: add "Disconnect" action

### 3.5 — Quick Settings Tile
- [ ] Implement `TileService` for "Connect & Heat" quick tile
- [ ] Shows battery + connection state in tile subtitle

### 3.6 — Localization Groundwork
- [ ] Migrate all hard-coded strings to `res/values/strings.xml`
- [ ] No translations needed yet — just the infrastructure

### 3.7 — Accessibility Pass
- [ ] Add `contentDescription` to all interactive custom Views (`GraphView`, `BatteryGraphView`, etc.)
- [ ] Audit TalkBack traversal order in each Fragment

---

## Schema Migration Roadmap

| Version | Changes | Migration |
|---|---|---|
| v2 | Current baseline | — |
| v3 | `sessions.notes TEXT`, `sessions.rating INTEGER` | `ALTER TABLE sessions ADD COLUMN notes TEXT; ALTER TABLE sessions ADD COLUMN rating INTEGER;` |
| v4 | `device_status` pruning index optimization (if needed) | TBD after profiling |

---

## Backlog Items to Add

| ID | Title | Phase |
|---|---|---|
| F-050 | Session Notes + Rating | 2.1 |
| F-051 | Temperature Presets | 2.2 |
| F-052 | History Search & Filtering | 2.3 |
| F-053 | Tolerance Break Tracker | 2.4 |
| F-054 | Onboarding Flow | 2.5 |
| F-055 | Data Pruning / Retention | 0.4 |
| F-056 | Quick Settings Tile | 3.5 |
| F-057 | Crash Reporting | 3.3 |

---

## What This Plan Does NOT Cover (Deferred)

- **Wear OS companion** — significant scope, deferred post-v1
- **Cloud sync** — requires auth infrastructure, deferred post-v1
- **Compose migration** — architectural direction but not urgent
- **Tablet/landscape layouts** — low priority given target device usage context
- **Dose/weight tracking** — requires hardware scale integration or manual input UX design; deferred
- **Navigation Component** — useful but large refactor; defer until ViewModel decomposition is stable

---

## Approval Checklist

Before execution begins, confirm:

- [ ] Phase 0 approved — dependency upgrades + pruning
- [ ] Phase 1 approved — DI + ViewModel decomp (significant refactor)
- [ ] Phase 2 approved — schema v3 migration + new features
- [ ] Phase 3 approved — release pipeline decisions (signing, crash reporting service)
- [ ] Package name / Play Store legal review (`com.sbtracker` trademark risk)
