# T-014 — Tolerance Break Tracker

**Status**: blocked
**Phase**: 2
**Blocked by**: T-007

---

## Goal
Add streak and break tracking. This is a core use case for the target
demographic and requires zero new DB queries — everything is derived from
the existing `List<SessionSummary>`.

---

## New pure functions (add to `AnalyticsRepository` or a new `StreakUtils.kt`)
- `currentStreak(summaries)` → consecutive days with ≥ 1 session ending today
- `longestStreak(summaries)` → all-time best consecutive days
- `daysSinceLastSession(summaries)` → Int, for break tracking
- `breakProgress(summaries, goalDays)` → Float 0.0–1.0, progress toward a goal

## UI
- Add a "Streak / Break" card to `LandingFragment` below the hero.
- Show: current streak, days since last session, break goal progress bar.
- Break goal (target N days off) configurable in Settings.

*(Fill in full steps when T-007 is done.)*

## Do NOT touch
- Database schema
- BLE layer
- Existing analytics models (add alongside, don't modify)
