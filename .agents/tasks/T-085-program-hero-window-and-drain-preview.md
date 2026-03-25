# T-085 — SessionFragment: Program Hero Window + Drain Estimate Preview

**Phase**: Phase 3 — F-027 Session Programs/Presets
**Blocked by**: T-046, T-084
**Estimated diff**: ~60 lines in 1 file

## Goal
When a program is selected from the chip row (T-046) and the device is idle, show:
1. **Hero window**: the program's estimated total duration in `MM:SS (est.)` format
   in the session time display (`tvTime`), instead of `00:00`.
2. **Drain preview**: below the chip row, show estimated battery cost as `−X% (Ym est.)`.

Both displays reset to normal once the session starts.

## Read these files first
- `app/src/main/java/com/sbtracker/ui/SessionFragment.kt` — understand the flows already
  collected (`bleVm.sessionStats`, `sessionVm.selectedProgram`), the time TextView
  (`tvTime`), and where the chip row was added in T-046
- `app/src/main/java/com/sbtracker/SessionViewModel.kt` — `estimateProgramDurationMinutes()` and `estimateProgramDrain()` added in T-084
- `app/src/main/java/com/sbtracker/HistoryViewModel.kt` — `avgDrainPerMinute` StateFlow from T-084

## Change only these files
- `app/src/main/java/com/sbtracker/ui/SessionFragment.kt`

## Steps

### 1. Hero window — estimated time in `tvTime`

In the coroutine that collects `bleVm.sessionStats`, extend the existing time display
logic. Use `combine` on `bleVm.sessionStats` and `sessionVm.selectedProgram`:

```kotlin
viewLifecycleOwner.lifecycleScope.launch {
    combine(bleVm.sessionStats, sessionVm.selectedProgram) { stats, program ->
        stats to program
    }.collect { (stats, program) ->
        val isIdle = stats.state == SessionTracker.State.IDLE   // or heaterMode == 0
        if (isIdle && program != null) {
            val totalSec = (sessionVm.estimateProgramDurationMinutes(program) * 60).toLong()
            tvTime.text = "%02d:%02d (est.)".format(totalSec / 60, totalSec % 60)
        } else {
            // Normal elapsed time — already formatted elsewhere; leave existing logic intact
        }
    }
}
```

> **Important**: only override `tvTime` in the IDLE + program-selected case. Preserve all
> existing elapsed-time display for active sessions. Do not duplicate the elapsed-time
> collector — combine into the existing one or add a separate observer that only writes
> when the condition is met.

### 2. Drain preview — `"−X% (Ym est.)"` label

Add a `TextView` for the drain preview immediately below the chip row added in T-046.
Create it programmatically alongside the chip row setup:

```kotlin
val tvDrainPreview = TextView(requireContext()).apply {
    textSize = 11f
    setTextColor(ContextCompat.getColor(requireContext(), R.color.color_boost_bar_fill))
    visibility = View.GONE
}
layout.addView(tvDrainPreview)
```

Then collect the drain estimate reactively:

```kotlin
viewLifecycleOwner.lifecycleScope.launch {
    combine(sessionVm.selectedProgram, historyVm.avgDrainPerMinute) { program, rate ->
        program to rate
    }.collect { (program, rate) ->
        if (program != null) {
            val drain = sessionVm.estimateProgramDrain(program, rate)
            val mins = sessionVm.estimateProgramDurationMinutes(program).toInt()
            tvDrainPreview.text = if (drain > 0) "−${drain}% (${mins}m est.)" else "(${mins}m est.)"
            tvDrainPreview.visibility = View.VISIBLE
        } else {
            tvDrainPreview.visibility = View.GONE
        }
    }
}
```

Also hide `tvDrainPreview` once the session starts (same condition used to hide the chip
row in T-046).

### 3. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] When program is selected and device is idle, `tvTime` shows `"MM:SS (est.)"` using estimated duration
- [ ] Once session starts, `tvTime` reverts to normal elapsed time
- [ ] When program is selected, drain preview label is visible below chip row
- [ ] Drain preview is hidden when no program is selected or during active session
- [ ] If `avgDrainPerMinute` is 0 (no history), only duration is shown, no crash
- [ ] `./gradlew assembleDebug` passes

## Do NOT
- Persist the preview state across Fragment recreation
- Add any new XML layout files
- Modify `HistoryViewModel`, `SessionViewModel`, or `AnalyticsRepository` in this task
- Show drain preview during an active session — hide it with the chip row
