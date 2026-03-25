# T-085 — SessionFragment Program Chip UI and Start Button Wiring

**Phase**: Phase 3 — F-027 Session Programs/Presets
**Blocked by**: T-084
**Unblocks**: T-086
**Estimated diff**: ~60 lines in SessionFragment.kt

## Goal

Add a horizontal scrollable chip row to SessionFragment showing available programs. Wire program selection to preview the target temperature, and update the start button to launch sessions with the selected program (or standard start if none selected).

## Read these files first

- `app/src/main/java/com/sbtracker/ui/SessionFragment.kt` — understand current view hierarchy and start/stop button flow
- `app/src/main/java/com/sbtracker/SessionViewModel.kt` — review `selectedProgram`, `programs`, and `startSessionWithProgram()` signatures
- T-084 task file — understand the ViewModel layer

## Change only these files

- `app/src/main/java/com/sbtracker/ui/SessionFragment.kt`

## Steps

### 1. Add a method to build the program chip row (programmatic, no XML layout)

```kotlin
private fun setupProgramChipRow() {
    val chipContainer = LinearLayout(requireContext()).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        orientation = LinearLayout.HORIZONTAL
        setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
    }

    val scrollView = HorizontalScrollView(requireContext()).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        addView(chipContainer)
    }

    // Observe programs and build chips
    viewLifecycleOwner.lifecycleScope.launch {
        sessionVm.programs.collect { programs ->
            chipContainer.removeAllViews()

            // "No Program" chip (always first)
            val noProgramChip = createProgramChip(
                name = "No Program",
                isSelected = sessionVm.selectedProgram.value == null,
                onClick = {
                    sessionVm.selectProgram(null)
                    updateTempPreview(null)
                }
            )
            chipContainer.addView(noProgramChip)

            // One chip per program
            programs.forEach { program ->
                val chip = createProgramChip(
                    name = program.name,
                    isSelected = sessionVm.selectedProgram.value?.id == program.id,
                    onClick = {
                        sessionVm.selectProgram(program)
                        updateTempPreview(program)
                    }
                )
                chipContainer.addView(chip)
            }
        }
    }

    // Insert chipContainer into the layout hierarchy (before start buttons, after controls)
    // Example: yourParentContainer.addView(scrollView, index)
}

private fun createProgramChip(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
): MaterialButton {
    return MaterialButton(requireContext(), null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
        text = name
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(4.dpToPx(), 0, 4.dpToPx(), 0) }

        setBackgroundColor(
            if (isSelected) getColor(com.sbtracker.R.color.color_blue)
            else getColor(com.sbtracker.R.color.color_dark_card)
        )

        setOnClickListener { onClick() }
    }
}

private fun updateTempPreview(program: SessionProgram?) {
    // Update the temperature display to show program.targetTempC
    // Example: tvTargetBase.text = "${program?.targetTempC ?: bleVm.targetTemp.value}°C"
}

private fun Int.dpToPx() = (this * requireContext().resources.displayMetrics.density).toInt()
```

### 2. Update the start button click handler

```kotlin
btnStartNormal.setOnClickListener {
    val prog = sessionVm.selectedProgram.value
    if (prog != null) {
        sessionVm.startSessionWithProgram(prog)
    } else {
        sessionVm.startSession(bleVm.targetTemp.value)
    }
}
```

### 3. Hide the chip row during active sessions

Observe the session state and hide/show the chip row:

```kotlin
viewLifecycleOwner.lifecycleScope.launch {
    bleVm.sessionStats.collect { stats ->
        chipRow.visibility = if (stats.state == SessionTracker.State.IDLE) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }
}
```

### 4. Reactive chip selection updates

Update chip appearance when selectedProgram changes:

```kotlin
viewLifecycleOwner.lifecycleScope.launch {
    sessionVm.selectedProgram.collect { selected ->
        // Rebuild or update chip styles
        // This ensures visual feedback matches the ViewModel state
    }
}
```

### 5. Run `./gradlew assembleDebug` and confirm it passes

No functional integration of the boost execution yet — purely UI presentation.

## Acceptance criteria

- [ ] SessionFragment displays a horizontal scrollable chip row above start buttons
- [ ] "No Program" chip always appears first
- [ ] Each program appears as a chip with its name
- [ ] Selected chip has distinct background color (e.g., color_blue)
- [ ] Unselected chips have neutral background (e.g., color_dark_card)
- [ ] Tapping a chip calls `sessionVm.selectProgram()` and updates preview temp
- [ ] Chip row is hidden during active sessions (when state != IDLE)
- [ ] Start button calls `startSessionWithProgram()` if a program is selected, else standard `startSession()`
- [ ] Tapping "No Program" clears selection and restores standard temp display
- [ ] `./gradlew assembleDebug` passes

## Do NOT

- Add layout XML files — use programmatic View construction only
- Modify SessionViewModel or ProgramRepository
- Handle edge cases (disconnect, boost priority) — that's T-086
- Persist selected program — it's ephemeral per screen visit
- Implement boost step visual feedback (step counter, remaining time) — that's future enhancement
