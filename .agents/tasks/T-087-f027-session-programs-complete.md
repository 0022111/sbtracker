# T-087 — F-027 Session Programs UI Complete Implementation

**Phase**: Phase 3 — Session Programs & Presets
**Blocked by**: T-043 (ProgramRepository CRUD + seeding)
**Estimated diff**: ~300 lines across 4 files

## Goal
Implement the complete F-027 feature: a 2×3 grid of session programs on the Session page where users can select, apply, and create custom heating profiles. This is the user-facing wrapper around the existing program data model (T-042/T-043).

## Context
The data model is ready (SessionProgram entity, ProgramRepository). This task wires the UI to make programs discoverable and actionable:
1. **Program Grid** — visual selector for 6 programs (3 preset + 3 custom)
2. **Program Editor Dialog** — full program creation/editing with step-by-step heating schedule
3. **Program Application** — apply program to session or queue boost steps if session active

## Read these files first
- `BACKLOG.md` lines 150–202 — complete F-027 spec with all UI components detailed
- `app/src/main/java/com/sbtracker/data/SessionProgram.kt` — entity fields (boostStepsJson, name, targetTempC)
- `app/src/main/java/com/sbtracker/data/ProgramRepository.kt` — CRUD interface (created in T-043)
- `app/src/main/java/com/sbtracker/ui/session/SessionFragment.kt` — where grid will be added

## Change only these files
- `app/src/main/java/com/sbtracker/ui/session/SessionFragment.kt` (add program grid + controls)
- `app/src/main/java/com/sbtracker/ui/session/ProgramEditorDialog.kt` (new, full editor)
- `app/src/main/java/com/sbtracker/ui/session/SessionViewModel.kt` (extend with program methods)
- `app/src/main/java/com/sbtracker/data/SessionProgram.kt` (add UI-friendly helper methods)

## Steps

### 1. Add Program Grid to SessionFragment
In `SessionFragment.onViewCreated()`, add a 2×3 grid of buttons:

```kotlin
private lateinit var programGridContainer: LinearLayout

override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    // Create grid container (2 columns × 3 rows)
    programGridContainer = LinearLayout(requireContext()).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        orientation = LinearLayout.VERTICAL
    }

    // Add 3 rows
    repeat(3) { rowIndex ->
        val row = LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                120.dpToPx()
            )
            orientation = LinearLayout.HORIZONTAL
            spacing = 8.dpToPx()
        }

        // Add 2 buttons per row
        repeat(2) { colIndex ->
            val programIndex = rowIndex * 2 + colIndex
            val programButton = Button(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f  // Equal weight
                )
                setPadding(8.dpToPx(), 4.dpToPx(), 8.dpToPx(), 4.dpToPx())
                setOnClickListener {
                    viewModel.selectProgram(programIndex)
                    showProgramEditorIfCustom(programIndex)
                }
            }
            row.addView(programButton)
            programButtons[programIndex] = programButton
        }
        programGridContainer.addView(row)
    }
    parentContainer.addView(programGridContainer, 0)  // Add at top of session view
}
```

### 2. Bind Programs to Buttons
In `SessionViewModel`, expose program list and selected state:

```kotlin
class SessionViewModel @HiltViewModel constructor(
    private val programRepository: ProgramRepository,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    val programs: StateFlow<List<SessionProgram>> =
        programRepository.getAllPrograms()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val selectedProgramId: StateFlow<Long?> = MutableStateFlow(null)

    fun selectProgram(program: SessionProgram) {
        (selectedProgramId as MutableStateFlow).value = program.id
    }

    fun deleteCustomProgram(programId: Long) {
        viewModelScope.launch {
            programRepository.deleteProgram(programId)
        }
    }
}
```

Update button binding in Fragment:

```kotlin
lifecycleScope.launch {
    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.programs.collect { programs ->
            programs.forEachIndexed { index, program ->
                programButtons[index]?.apply {
                    text = program.name
                    setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
                    // Show subtitle with target temp
                    val subtitle = "${program.targetTempC}°C"
                    // (would need custom button layout for subtitle, or use text with newline)
                    isSelected = program.id == viewModel.selectedProgramId.value
                }
            }
        }
    }
}
```

### 3. Create ProgramEditorDialog
New file `ProgramEditorDialog.kt`:

```kotlin
class ProgramEditorDialog(
    private val program: SessionProgram?,
    private val onSave: (SessionProgram) -> Unit
) : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())

        // Program name input
        addView(TextView(context).apply { text = "Program Name" })
        val nameInput = EditText(context).apply {
            setText(program?.name ?: "My Program")
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        addView(nameInput)

        // Target temperature selector
        addView(TextView(context).apply { text = "Base Temperature" })
        val tempSelector = Spinner(context).apply {
            // Temp range: 40–230°C
            adapter = ArrayAdapter(
                context,
                android.R.layout.simple_spinner_item,
                (40..230 step 5).map { "$it°C" }
            )
            setSelection((program?.targetTempC ?: 200 - 40) / 5)
        }
        addView(tempSelector)

        // Steps table
        addView(TextView(context).apply { text = "Heating Steps" })
        val stepsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Add step rows (name, timeOffset, boostOffset)
        // Each step: [Step#] | [Time] | [Boost]
        program?.boostSteps()?.forEachIndexed { index, step ->
            addStepRow(stepsContainer, index, step)
        }

        val addStepButton = Button(context).apply {
            text = "Add Step"
            setOnClickListener {
                val newStep = BoostStep(
                    offsetSec = (program?.boostSteps()?.lastOrNull()?.offsetSec ?: 0) + 60,
                    boostC = 0
                )
                addStepRow(stepsContainer, (program?.boostSteps()?.size ?: 0), newStep)
            }
        }

        addView(stepsContainer)
        addView(addStepButton)

        // Preview: "Total: MM:SS | Est. drain: X%"
        val previewText = TextView(context).apply {
            text = computePreview(program)
            textSize = 12f
            setTextColor(Color.GRAY)
        }
        addView(previewText)

        // Save/Cancel
        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        buttonRow.addView(Button(context).apply {
            text = "Save"
            setOnClickListener {
                val edited = SessionProgram(
                    id = program?.id ?: 0,
                    name = nameInput.text.toString(),
                    targetTempC = tempSelector.selectedItem.toString().toInt(),
                    boostStepsJson = serializeSteps(stepsContainer)
                )
                onSave(edited)
                dismiss()
            }
        })
        buttonRow.addView(Button(context).apply {
            text = "Cancel"
            setOnClickListener { dismiss() }
        })
        addView(buttonRow)
    }

    private fun computePreview(program: SessionProgram?): String {
        val totalSecs = program?.boostSteps()?.lastOrNull()?.offsetSec ?: 0
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        val estimatedDrain = "X%"  // Use AnalyticsRepository.estimateBatteryDrain(program)
        return "Total: ${mins}:${secs.toString().padStart(2, '0')} | Est. drain: $estimatedDrain"
    }
}
```

### 4. Wire Program Application
Add to `SessionViewModel`:

```kotlin
fun applyProgram(program: SessionProgram) {
    if (isSessionActive) {
        // Queue boost steps
        queueBoostSteps(program.boostSteps())
    } else {
        // Apply to staging, optionally auto-start
        currentStagingProgram = program
        // Show "IGNITE" button to user
    }
}

private fun queueBoostSteps(steps: List<BoostStep>) {
    // Detailed implementation in T-046
    // For now: schedule each step at its offsetSec
}
```

### 5. Add Edit/Delete Buttons for Custom Programs
In Fragment, custom programs (index 3–5) get delete button overlay:

```kotlin
if (index >= 3) {  // Custom programs
    programButton.apply {
        setOnLongClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Delete ${program.name}?")
                .setPositiveButton("Delete") { _, _ ->
                    viewModel.deleteCustomProgram(program.id)
                }
                .show()
            true
        }
    }
}
```

### 6. Run Tests & Build
```bash
./gradlew assembleDebug
```

## Acceptance criteria
- [ ] 2×3 grid of program buttons displayed on SessionFragment
- [ ] Top 3 buttons show default programs (non-deletable)
- [ ] Bottom 3 buttons show custom programs (long-press → delete)
- [ ] Clicking program opens ProgramEditorDialog with full editing UI
- [ ] Editor shows: name input, temp selector (40–230°C), steps table with add/remove
- [ ] Preview shows total duration and estimated battery drain
- [ ] Save button persists program via ProgramRepository
- [ ] Selected program is visually highlighted (button state)
- [ ] Applying program queues boost steps if session is active
- [ ] `./gradlew assembleDebug` passes
- [ ] No new database schema changes (uses existing SessionProgram)

## Do NOT
- Store programs in session_metadata on creation (only on session complete — that's T-056)
- Change existing program data model (SessionProgram entity is fixed)
- Implement boost step execution yet (that's T-046)
- Add XML layouts (use programmatic Views as per spec)
