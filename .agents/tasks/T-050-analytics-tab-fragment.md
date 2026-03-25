# T-050 — AnalyticsTabFragment: Analytics Dashboard Sub-Page

**Phase**: Phase 3 — F-056 History/Analytics Page Organization
**Blocked by**: nothing
**Estimated diff**: ~150 lines across 3 files

## Goal
Extract the timeline, hero quick stats, bar chart, usage insights, and session averages deep-dive sections from `HistoryFragment` into a new `AnalyticsTabFragment` + layout.

## Read these files first
- `app/src/main/java/com/sbtracker/ui/HistoryFragment.kt` — identify the analytics block: timeline, hero stats, bar chart, weekly comparison, streaks, expandable sections (~lines 36–284)
- `app/src/main/res/layout/fragment_history.xml` — find the IDs for all analytics views to copy into the new layout

## Change only these files
- `app/src/main/java/com/sbtracker/ui/AnalyticsTabFragment.kt` *(create new)*
- `app/src/main/res/layout/fragment_analytics_tab.xml` *(create new)*
- `app/src/main/java/com/sbtracker/ui/HistoryFragment.kt`

## Steps

1. **Create `fragment_analytics_tab.xml`**: Copy these sections from `fragment_history.xml`:
   - `HistoryTimelineView` (`analytics_timeline`)
   - Hero quick stats row (`tv_hero_sessions`, `tv_hero_avg_duration`, `tv_hero_avg_hits`, `tv_hero_avg_drain`, `tv_hero_avg_heatup`)
   - Graph period toggle (`tv_graph_period_day`, `tv_graph_period_week`)
   - `HistoryBarChartView` (`history_bar_chart`)
   - Weekly comparison card (`tv_week_sessions`, `tv_week_sessions_delta`, `tv_week_hits`, `tv_week_hits_delta`)
   - Streaks row (`tv_streak_current`, `tv_streak_longest`, `tv_peak_time`, `tv_busiest_day`)
   - Expandable "Session Averages" section (`header_session_averages`, `content_session_averages`, and all `tv_stats_*` children)
   - Expandable "Usage Insights" section (`header_usage_insights`, `content_usage_insights`, and all insight children)
   Wrap in a `ScrollView` > `LinearLayout` root with id `analytics_root`.

2. **Create `AnalyticsTabFragment.kt`**: Move all code from `HistoryFragment` that populates the above views:
   - Timeline data collection + `onSessionTapped` callback
   - Hero stats collector
   - Usage insights collector
   - Bar chart collector
   - Period toggle wiring
   - Expand/collapse `toggleSection()` logic

3. **HistoryFragment.kt** — remove the extracted code blocks. Keep: Health & Intake section (T-051).

4. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] `AnalyticsTabFragment` inflates `fragment_analytics_tab.xml`
- [ ] Timeline, stats, bar chart, and insights all render correctly in the new fragment
- [ ] Period toggle (Day/Week) functions in the new fragment
- [ ] Expand/collapse for deep-dive sections works
- [ ] `./gradlew assembleDebug` passes

## Do NOT
- Wire into the tab host — that is T-052
- Modify `HistoryViewModel` — just move UI binding code
- Touch Health & Intake section — that stays for T-051
