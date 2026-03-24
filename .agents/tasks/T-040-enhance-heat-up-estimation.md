# T-040 — Enhance Heat-up Estimation with Battery & Time Weighting

**Phase**: Phase 2 — User-Facing Features
**Blocked by**: T-039
**Estimated diff**: ~50 lines in 1 file

## Goal
Enhance `computeEstimatedHeatUpTime` to weight recent heat-up times by battery phase (CC/CV) and proximity to the last session, improving accuracy for back-to-back and battery-aware scenarios.

## Read these files first
- `app/src/main/java/com/sbtracker/analytics/AnalyticsRepository.kt` — specifically `computeEstimatedHeatUpTime` (lines 210–223) and newly added `batteryPhaseMultiplier` (T-039)
- `app/src/main/java/com/sbtracker/data/SessionSummary.kt` — understand SessionSummary fields (startBattery, startTimeMs)

## Change only these files
- `app/src/main/java/com/sbtracker/analytics/AnalyticsRepository.kt`

## Steps
1. **Refactor `computeEstimatedHeatUpTime`** to accept three additional optional parameters:
   ```kotlin
   fun computeEstimatedHeatUpTime(
       targetTempC: Int,
       summaries: List<SessionSummary>,
       currentBatteryPercent: Int? = null,  // NEW: current device battery %
       timeSinceLastSessionMs: Long? = null, // NEW: ms since last session ended
       currentDeviceTempC: Int? = null       // NEW: current device temperature
   ): Long?
   ```

2. **Update the similarity filter** (currently only checks temp within ±10°C):
   - Still filter by temperature (±10°C)
   - Add optional battery-aware filtering: prefer sessions that started at similar battery levels (±15%)
   - If `currentBatteryPercent` is null, ignore this filter

3. **Add time-proximity weighting**:
   - If `timeSinceLastSessionMs` is provided, calculate a time-weight factor:
     - Sessions within 5 minutes of the last session: weight `1.2` (back-to-back boost)
     - Sessions within 30 minutes: weight `1.0` (baseline)
     - Sessions >30 minutes ago: weight `0.9` (device cooling effect, slight increase in heat-up time)
   - Apply this weight to the heat-up time before averaging

4. **Add battery-phase adjustment**:
   - After computing the weighted average, apply `batteryPhaseMultiplier(currentBatteryPercent)` to the final result
   - Only if `currentBatteryPercent` is provided; otherwise return the unmodified average

5. **Maintain backward compatibility**:
   - If no new parameters are provided, behavior is identical to the original (simple average of last 5)
   - All new parameters are optional (nullable or have sensible defaults)

6. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] `computeEstimatedHeatUpTime` accepts 3 new optional parameters (battery, timeSinceLast, currentTemp)
- [ ] Similar-session filter now considers battery level (±15%) when currentBatteryPercent is provided
- [ ] Time-proximity weighting applied: ×1.2 (0–5min), ×1.0 (5–30min), ×0.9 (>30min)
- [ ] Battery-phase multiplier applied to final result if currentBatteryPercent is provided
- [ ] Backward compatible: calling with only targetTemp and summaries behaves as before
- [ ] `./gradlew assembleDebug` passes

## Do NOT
- Change function behavior when new parameters are omitted (backward compat critical)
- Modify any other functions in AnalyticsRepository
- Hardcode battery thresholds in the function (use the multiplier from T-039)
- Change any existing callers yet (that's T-041)
