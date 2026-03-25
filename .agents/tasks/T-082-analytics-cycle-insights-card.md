# T-082 — Analytics Tab: Cycle & Session Insights Card

**Phase**: Phase 3 — F-052 Analytics Display Refactoring
**Feature**: F-052
**Blocked by**: T-078 (frequency/dose reorganization)
**Estimated diff**: ~70 lines across 2 files

## Goal

Consolidate the heat-up time, bar chart, and timeline into a clearly labeled
**Cycle & Session Insights** card on the Analytics tab. This gives users a
dedicated place to see session duration patterns and daily rhythm data
distinct from the hit-level achievement cards.

Currently these elements are scattered: the bar chart is a standalone view,
the timeline is at the top, and heat-up stats are buried in the session
averages expandable. This task groups them with clear labels.

## Read these files first

- `app/src/main/res/layout/fragment_analytics_tab.xml` *(after T-078)*
- `app/src/main/java/com/sbtracker/ui/AnalyticsTabFragment.kt` *(after T-078)*

## Change only these files

- `app/src/main/res/layout/fragment_analytics_tab.xml`
- `app/src/main/java/com/sbtracker/ui/AnalyticsTabFragment.kt`

## Steps

1. **Layout (`fragment_analytics_tab.xml`)**: Wrap the existing timeline
   (`analytics_timeline`), bar chart (`history_bar_chart`), and period toggle
   (`tv_graph_period_day`, `tv_graph_period_week`) inside a `CardView` with:
   - Section header: "Cycle & Session Insights"
   - A stats row above the chart: "Avg duration" / value, "Avg heat-up" / value
     (IDs: `tv_cycle_avg_duration`, `tv_cycle_avg_heatup`)
   - Then the period toggle + bar chart
   - Then the timeline view at the bottom of the card
   The card replaces the previously bare layout sections — do not duplicate views.

2. **Fragment (`AnalyticsTabFragment.kt`)**: Bind `tv_cycle_avg_duration` and
   `tv_cycle_avg_heatup` from `HistoryStats.avgSessionDurationSec` and
   `HistoryStats.avgHeatUpTimeSec`. Format duration as `"Xm Ys"` and heat-up
   as `"Xs"` (or `"—"` if zero). The existing bar chart and timeline binding
   code does not need modification — only the card wrapper and new stat rows
   are added.

## Acceptance criteria

- [ ] Bar chart, period toggle, and timeline are wrapped inside the Cycle & Session Insights card
- [ ] Avg duration and avg heat-up rows display above the chart
- [ ] Card header label "Cycle & Session Insights" is visible
- [ ] Period toggle (Day/Week) still functions correctly
- [ ] `./gradlew assembleDebug` passes

## Do NOT

- Extract bar chart or timeline to new classes
- Modify `AnalyticsRepository` or `AnalyticsModels`
- Touch the Sessions tab or Health tab
