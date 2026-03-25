# T-081 — Temperature-Based Achievement Display

**Phase**: Phase 3 — F-052 Analytics Display Refactoring
**Feature**: F-052
**Blocked by**: T-080 (Hit Achievements card must exist)
**Estimated diff**: ~60 lines across 2 files

## Goal

Extend the Hit Achievements card (added in T-080) with a
**Temperature Achievements** sub-section that surfaces:
- Hottest session ever (peak temp °C + date)
- Favorite temperature range (top-1 temp bucket from `HistoryStats.favoriteTempsCelsius`)
- "Low and slow" count — sessions where `peakTempC < 185°C`
- "High heat" count — sessions where `peakTempC >= 210°C`

These are all derivable from the existing `HistoryStats` and `PersonalRecords`
models; no new analytics computation is needed.

## Read these files first

- `app/src/main/res/layout/fragment_analytics_tab.xml` *(after T-080)*
- `app/src/main/java/com/sbtracker/ui/AnalyticsTabFragment.kt` *(after T-080)*
- `app/src/main/java/com/sbtracker/analytics/AnalyticsModels.kt` — `HistoryStats`, `PersonalRecords`

## Change only these files

- `app/src/main/res/layout/fragment_analytics_tab.xml`
- `app/src/main/java/com/sbtracker/ui/AnalyticsTabFragment.kt`

## Steps

1. **Layout (`fragment_analytics_tab.xml`)**: Append a divider + sub-section
   header "Temperature Achievements" inside the existing Hit Achievements
   `CardView` (do not create a new card). Add rows for:
   - "Hottest session": `tv_temp_hottest` (temp value) + `tv_temp_hottest_date`
   - "Favorite range": `tv_temp_favorite`
   - "Low & slow sessions (<185°C)": `tv_temp_low_slow_count`
   - "High heat sessions (≥210°C)": `tv_temp_high_heat_count`

2. **Fragment (`AnalyticsTabFragment.kt`)**: In the existing `historyStats`
   and `personalRecords` observer blocks, bind the new TextViews:
   - `tv_temp_hottest` ← `personalRecords.maxPeakTempC` formatted with units
   - `tv_temp_hottest_date` ← `personalRecords.hottestSession?.startTimeMs`
     formatted as a short date
   - `tv_temp_favorite` ← `historyStats.favoriteTempsCelsius.firstOrNull()?.first`
   - `tv_temp_low_slow_count` ← count of summaries where `peakTempC in 1..<185`
   - `tv_temp_high_heat_count` ← count of summaries where `peakTempC >= 210`

   The low/slow and high-heat counts require access to the full `summaries`
   list that the fragment already observes.

## Acceptance criteria

- [ ] Temperature achievements sub-section renders inside the Hit Achievements card
- [ ] Hottest session shows correct temp and date
- [ ] Favorite temp range matches the top bucket from HistoryStats
- [ ] Low & slow and high heat counts are accurate
- [ ] `./gradlew assembleDebug` passes

## Do NOT

- Create a new card — extend the existing Hit Achievements card
- Add new functions to `AnalyticsRepository` — derive from existing models
- Touch `AnalyticsModels.kt`
