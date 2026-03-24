# T-039 — Battery Phase Multiplier Utility

**Phase**: Phase 2 — User-Facing Features
**Blocked by**: nothing
**Estimated diff**: ~30 lines in 1 file

## Goal
Add a pure utility function that maps battery percentage (0–100) to a heating-speed multiplier based on Li-ion CC/CV charging phases.

## Read these files first
- `app/src/main/java/com/sbtracker/analytics/AnalyticsRepository.kt` — understand the existing `computeEstimatedHeatUpTime` function

## Change only these files
- `app/src/main/java/com/sbtracker/analytics/AnalyticsRepository.kt`

## Steps
1. In `AnalyticsRepository.kt`, add a private companion object with a function `batteryPhaseMultiplier(batteryPercent: Int): Float` that:
   - Takes battery percentage (0–100)
   - Returns a multiplier (1.0 = baseline, <1.0 = faster, >1.0 = slower)
   - **Logic**:
     - 0–65%: CC phase (constant current) → multiplier `0.85` (fastest)
     - 65–80%: CV phase (constant voltage) → multiplier `1.0` (baseline)
     - 80–100%: taper phase → multiplier `1.15` (slowest)
   - Use linear interpolation for smooth transitions between boundaries
   - Example: 65% should return 0.85 (still in CC), 72.5% should return ~0.975 (partway through CV)
2. Add a unit test comment (not a full test file) documenting expected outputs:
   ```
   // batteryPhaseMultiplier(30) ≈ 0.85
   // batteryPhaseMultiplier(70) ≈ 1.0
   // batteryPhaseMultiplier(90) ≈ 1.15
   ```
3. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] Function `batteryPhaseMultiplier` exists in `AnalyticsRepository` companion
- [ ] Returns 0.85 for battery 0–65%
- [ ] Returns 1.0 for battery 65–80%
- [ ] Returns 1.15 for battery 80–100%
- [ ] Uses linear interpolation for smooth transitions
- [ ] `./gradlew assembleDebug` passes

## Do NOT
- Create a separate utility class (keep it in AnalyticsRepository)
- Write full unit tests (that's a later task)
- Modify `computeEstimatedHeatUpTime` yet (that's T-040)
- Change any existing function signatures
