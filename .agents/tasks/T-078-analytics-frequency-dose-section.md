# T-078 — Analytics Tab: Frequency & Dose Section Reorganization

**Phase**: Phase 3 — F-052 Analytics Display Refactoring
**Feature**: F-052
**Blocked by**: T-050 (AnalyticsTabFragment must exist first)
**Estimated diff**: ~80 lines across 2 files

## Goal

Reorder the analytics cards inside `fragment_analytics_tab.xml` and the
corresponding binding code in `AnalyticsTabFragment.kt` so that the screen
opens on a **Frequency** card, followed immediately by a **Dose & Session**
card that surfaces gram intake data.

Current order (from T-050 output): timeline → hero stats → bar chart →
weekly comparison → streaks → session averages → usage insights.

Target order:
1. **Frequency** — weekly/daily session rate, current streak, busiest day,
   weekly comparison (sessions + hits delta)
2. **Dose & Session** — avg hits per session, avg hit duration, avg grams per
   session (from `IntakeStats`), avg battery drain
3. **Cycle Insights** — heat-up time, bar chart (daily sessions), timeline
4. **Session Averages** (expandable) — existing content
5. **Usage Insights** (expandable) — existing content

## Read these files first

- `app/src/main/res/layout/fragment_analytics_tab.xml` *(created by T-050)*
- `app/src/main/java/com/sbtracker/ui/AnalyticsTabFragment.kt` *(created by T-050)*

## Change only these files

- `app/src/main/res/layout/fragment_analytics_tab.xml`
- `app/src/main/java/com/sbtracker/ui/AnalyticsTabFragment.kt`

## Steps

1. **Layout (`fragment_analytics_tab.xml`)**: Move XML view groups into the
   new order described above. No new views needed — only reordering existing
   `CardView` / `LinearLayout` blocks. Add a section-header `TextView` for
   "Frequency", "Dose & Session", and "Cycle Insights" labels (style:
   `textAppearanceTitleMedium`, left-padded 16dp) between sections.

2. **Fragment (`AnalyticsTabFragment.kt`)**: The data-binding calls do not
   need reordering (they are independent assignments). Only confirm the
   `IntakeStats` data is observed from `HistoryViewModel` and that
   `avgGramsPerSession` is bound to the dose card label (add the `TextView`
   reference and assignment if it does not already exist).

3. The `IntakeStats` is already computed by `AnalyticsRepository.computeIntakeStats`
   and exposed on `HistoryViewModel`. If `HistoryViewModel` does not yet emit
   `IntakeStats`, add a `StateFlow<IntakeStats>` that collects from the existing
   `summaries` flow.

## Acceptance criteria

- [ ] Screen opens with Frequency card at the top
- [ ] Dose & Session card shows avg grams per session (or "—" when no intake data)
- [ ] Section header labels are visible between the three main sections
- [ ] `./gradlew assembleDebug` passes

## Do NOT

- Add new DB queries — use existing `IntakeStats` flow
- Touch `AnalyticsModels.kt` or `AnalyticsRepository.kt`
- Modify the Health tab (T-051 scope)
