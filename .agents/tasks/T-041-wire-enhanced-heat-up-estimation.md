# T-041 — Wire Enhanced Heat-up Estimation into SessionFragment

**Phase**: Phase 2 — User-Facing Features
**Blocked by**: T-040
**Estimated diff**: ~25 lines in 2 files

## Goal
Connect the enhanced heat-up estimation to the UI by passing time since last session and current device temperature to the estimation function.

## Read these files first
- `app/src/main/java/com/sbtracker/analytics/AnalyticsRepository.kt` — the enhanced `computeEstimatedHeatUpTime` signature (T-040)
- `app/src/main/java/com/sbtracker/HistoryViewModel.kt` — the `estimatedHeatUpTimeSecs` function (lines ~125–130)
- `app/src/main/java/com/sbtracker/ui/SessionFragment.kt` — where ETA is displayed during active session
- `app/src/main/java/com/sbtracker/BleViewModel.kt` — understand how to access `latestStatus.currentTempC`

## Change only these files
- `app/src/main/java/com/sbtracker/HistoryViewModel.kt`
- `app/src/main/java/com/sbtracker/ui/SessionFragment.kt`

## Steps

### In HistoryViewModel:

1. **Add a new method** `estimatedHeatUpTimeSecsWithContext` that accepts BleViewModel as a parameter:
   ```kotlin
   fun estimatedHeatUpTimeSecsWithContext(
       targetTemp: StateFlow<Int>,
       bleViewModel: BleViewModel
   ): StateFlow<Long?> =
       combine(
           deviceSessionSummaries,
           targetTemp,
           bleViewModel.latestStatus
       ) { summaries, target, status ->
           // Calculate time since last session
           val lastSession = summaries.lastOrNull()
           val timeSinceLast = if (lastSession != null) {
               System.currentTimeMillis() - lastSession.endTimeMs
           } else null

           // Call enhanced estimation with time and temperature parameters
           val ms = analyticsRepo.computeEstimatedHeatUpTime(
               targetTempC = target,
               summaries = summaries,
               timeSinceLastSessionMs = timeSinceLast,
               currentDeviceTempC = status.currentTempC
           )
           if (ms != null) ms / 1000 else null
       }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
   ```

2. Keep the original `estimatedHeatUpTimeSecs` function unchanged for backward compatibility.

### In SessionFragment:

1. **Find the line** that combines `bleVm.latestStatus` and `historyVm.estimatedHeatUpTimeSecs(bleVm.targetTemp)` (search for "combine")
2. **Replace the estimation call** with the new context-aware method:
   ```kotlin
   combine(
       bleVm.latestStatus,
       historyVm.estimatedHeatUpTimeSecsWithContext(bleVm.targetTemp, bleVm)
   ) { s, est -> s to est }
   ```

3. The rest of the logic (displaying `tvHeatUp`) remains unchanged.

4. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] `estimatedHeatUpTimeSecsWithContext` exists in HistoryViewModel
- [ ] Passes timeSinceLastSessionMs and currentDeviceTempC to analytics repo
- [ ] SessionFragment uses the new context-aware method
- [ ] UI displays the enhanced ETA (faster for back-to-back sessions, faster for warm devices)
- [ ] Original `estimatedHeatUpTimeSecs` still exists (backward compat)
- [ ] `./gradlew assembleDebug` passes

## Do NOT
- Modify BleViewModel or any other ViewModels
- Change SessionFragment's UI logic (only the data source)
- Break the existing `estimatedHeatUpTimeSecs` method
- Assume any specific device status fields exist beyond `currentTempC` (verify they exist first)
