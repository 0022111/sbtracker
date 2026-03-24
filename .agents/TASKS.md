# SBTracker — Task Index

> **For agents**: Pick a `ready` task, read its file in `.agents/tasks/`, follow it exactly.
> Do NOT read the entire codebase. The task file tells you exactly what to read and touch.
> When done, mark status `done` here and update `BACKLOG.md` + `CHANGELOG.md`.

---

## How to pick up a task

1. Find a `ready` task below with no agent assigned.
2. Read `.agents/tasks/<task-file>.md` — that is your full scope.
3. Create branch `claude/T-XXX-short-description` from `main`.
4. Complete the steps in the task file.
5. Run `./gradlew assembleDebug` — must pass before pushing.
6. Open a PR and mark the task `done` here.

---

## Phase 0 — Stop the Bleeding

| ID | Status | Title | Task File | Blocks |
|---|---|---|---|---|
| T-001 | `done` | Upgrade Dependencies | [T-001](tasks/T-001-upgrade-dependencies.md) | T-006, T-007 |
| T-002 | `done` | Enable R8 Minification | [T-002](tasks/T-002-enable-r8-minification.md) | — |
| T-003 | `done` | Fix Constant Duplication | [T-003](tasks/T-003-fix-temp-threshold-constant.md) | — |
| T-004 | `done` | Data Retention / Pruning | [T-004](tasks/T-004-data-retention-pruning.md) | — |
| T-005 | `done` | targetSdk 35 Compat Pass | [T-005](tasks/T-005-targetsdk35-compat.md) | — |
| T-022 | `ready` | Fix "TARGET TARGET" Typo | [T-022](tasks/T-022-fix-target-target-typo.md) | — |
| T-023 | `ready` | Wire Boost Visualization Toggle | [T-023](tasks/T-023-wire-boost-viz-toggle.md) | — |
| T-024 | `ready` | Wire Factory Reset Button | [T-024](tasks/T-024-wire-factory-reset-button.md) | — |
| T-025 | `ready` | Fix Day Start Hour Subtitle | [T-025](tasks/T-025-fix-day-start-subtitle.md) | — |
| T-028 | `ready` | Log Silent Exception Catches | [T-028](tasks/T-028-log-silent-exceptions.md) | — |
| T-029 | `ready` | Persist Battery Card Expand State | [T-029](tasks/T-029-persist-battery-expand-state.md) | — |

## Phase 1 — Foundation

| ID | Status | Title | Task File | Blocked by |
|---|---|---|---|---|
| T-006 | `ready` | Introduce Hilt DI | [T-006](tasks/T-006-introduce-hilt-di.md) | — |
| T-007 | `blocked` | Decompose MainViewModel | [T-007](tasks/T-007-decompose-mainviewmodel.md) | T-006 |
| T-008 | `blocked` | Remove Fragment–Activity Casts | [T-008](tasks/T-008-remove-activity-casts.md) | T-007 |
| T-009 | `blocked` | Preferences → DataStore | [T-009](tasks/T-009-preferences-datastore.md) | T-006 |
| T-010 | `blocked` | Unit Tests | [T-010](tasks/T-010-unit-tests.md) | T-006 |
| T-026 | `ready` | Enable ViewBinding | [T-026](tasks/T-026-enable-viewbinding.md) | — |
| T-027 | `ready` | Extract Hardcoded Colors to colors.xml | [T-027](tasks/T-027-extract-colors-xml.md) | — |

## Phase 2 — User-Facing Features

| ID | Status | Title | Task File | Blocked by |
|---|---|---|---|---|
| T-011 | `blocked` | Session Notes + Rating | [T-011](tasks/T-011-session-notes-rating.md) | T-007 |
| T-012 | `blocked` | Temperature Presets | [T-012](tasks/T-012-temperature-presets.md) | T-009 |
| T-013 | `blocked` | History Search & Filtering | [T-013](tasks/T-013-history-filtering.md) | T-007 |
| T-014 | `blocked` | Tolerance Break Tracker | [T-014](tasks/T-014-tolerance-break-tracker.md) | T-007 |
| T-015 | `blocked` | Onboarding Flow | [T-015](tasks/T-015-onboarding-flow.md) | T-005 |

## Phase 3 — Release Readiness

| ID | Status | Title | Task File | Blocked by |
|---|---|---|---|---|
| T-016 | `blocked` | Remove fallbackToDestructiveMigration | [T-016](tasks/T-016-remove-fallback-migration.md) | T-011 |
| T-017 | `blocked` | Release Build Pipeline | [T-017](tasks/T-017-release-build-pipeline.md) | T-001 |
| T-018 | `blocked` | Crash Reporting | [T-018](tasks/T-018-crash-reporting.md) | T-001 |
| T-019 | `blocked` | Notification Action Buttons | [T-019](tasks/T-019-notification-actions.md) | T-008 |
| T-020 | `blocked` | Quick Settings Tile | [T-020](tasks/T-020-quick-settings-tile.md) | T-008 |
| T-021 | `blocked` | Localization Groundwork | [T-021](tasks/T-021-localization-groundwork.md) | T-008 |
