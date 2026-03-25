# T-080 — Hit Achievements Display on Analytics Tab

**Phase**: Phase 3 — F-052 Analytics Display Refactoring
**Feature**: F-052
**Blocked by**: T-077 (HitAnalysisSummary must be computed), T-078 (Analytics tab reorganized)
**Estimated diff**: ~90 lines across 3 files

## Goal

Add a **Hit Achievements** card to `fragment_analytics_tab.xml` that displays:
- Total hits (all time)
- Large hit (rip) count vs sip count
- "Best rip session" — most large hits in a single session
- "Most sips session" — most sips in a single session
- Average temperature drop per hit
- Peak temperature drop (single hit record)

This card appears after the "Dose & Session" card (section 2) and before the
"Cycle Insights" section, as the third section in the analytics screen.

## Read these files first

- `app/src/main/res/layout/fragment_analytics_tab.xml` *(after T-078)*
- `app/src/main/java/com/sbtracker/ui/AnalyticsTabFragment.kt` *(after T-078)*
- `app/src/main/java/com/sbtracker/analytics/AnalyticsModels.kt` *(after T-076)*
- `app/src/main/java/com/sbtracker/HistoryViewModel.kt`

## Change only these files

- `app/src/main/res/layout/fragment_analytics_tab.xml`
- `app/src/main/java/com/sbtracker/ui/AnalyticsTabFragment.kt`
- `app/src/main/java/com/sbtracker/HistoryViewModel.kt`

## Steps

1. **`HistoryViewModel`**: Add a `StateFlow<HitAnalysisSummary>` that calls
   `analyticsRepository.computeHitAnalysis(summaries)` whenever the summary
   list updates. Collect it like existing analytics flows.

2. **Layout (`fragment_analytics_tab.xml`)**: Insert a `CardView` block
   between the dose card and the cycle insights section with:
   - Section header: "Hit Achievements"
   - Row: "Total hits" / value
   - Row: "Large hits (rips)" / value, "Sips" / value (two-column row)
   - Row: "Best rip session" / value (count), "Most sips session" / value
   - Row: "Avg temp drop" / "X.X°C", "Peak drop" / "X°C"
   Use IDs: `tv_hit_total`, `tv_hit_large`, `tv_hit_sip`,
   `tv_hit_best_rip_session`, `tv_hit_best_sip_session`,
   `tv_hit_avg_drop`, `tv_hit_peak_drop`.

3. **Fragment (`AnalyticsTabFragment.kt`)**: Observe the new `hitAnalysis`
   flow from `HistoryViewModel` and bind each `TextView` to the corresponding
   field. Show "—" when `totalHits == 0`.

## Acceptance criteria

- [ ] Hit Achievements card appears in the Analytics tab
- [ ] Large hit count and sip count display correctly
- [ ] Best rip session and most sips session show per-session maximums
- [ ] Temperature drop stats display with correct units
- [ ] Card shows "—" placeholders when no hit data is available
- [ ] `./gradlew assembleDebug` passes

## Do NOT

- Modify `AnalyticsRepository.kt` (T-077 scope)
- Touch Session tab or Health tab
- Add DB columns or migrations
