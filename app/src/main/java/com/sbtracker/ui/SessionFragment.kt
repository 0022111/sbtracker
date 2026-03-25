package com.sbtracker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.app.AlertDialog
import com.google.android.material.button.MaterialButton
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.sbtracker.*
import com.sbtracker.databinding.FragmentSessionBinding
import com.sbtracker.data.SessionProgram
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@AndroidEntryPoint
class SessionFragment : Fragment() {
    private val bleVm: BleViewModel by activityViewModels()
    private val sessionVm: SessionViewModel by activityViewModels()
    private val historyVm: HistoryViewModel by activityViewModels()
    private var _binding: FragmentSessionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSessionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        sessionVm.cancelBoostSchedule()
        super.onDestroyView()
        _binding = null
    }

    override fun onPause() {
        sessionVm.selectProgram(null)
        super.onPause()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val tvTemp = binding.sessionTvTemp
        val tvStatus = binding.sessionTvStatus
        val tvHits = binding.sessionTvHits
        val tvDrain = binding.sessionTvDrain
        val tvTime = binding.sessionTvTime
        val tvBattery = binding.sessionTvBattery
        val tvHeatUp = binding.sessionTvHeatUp
        val tvReadyTime = binding.sessionTvReadyTime
        val tvHeatUpEstimate = binding.sessionTvHeatUpEstimate

        val rowRunningStats = binding.sessionRunningStatsRow
        val gridStats = binding.statsGrid
        val btnEnd = binding.btnEndSession
        val btnStartNormal = binding.btnStartNormal

        // Hit / Breath Tracking UI
        val cardActiveHit = binding.cardActiveHit
        val tvHitTimer = binding.tvHitTimer

        // Controls
        val tvTargetBase = binding.tvTargetTemp
        val tvBoostOffset = binding.tvBoostOffset
        val tvSuperBoostOffset = binding.tvSuperboostOffset
        val groupModeSelection = binding.modeSelectionGroup
        val groupBoostControls = binding.groupBoostControls

        val btnNormal = binding.btnModeNormal
        val btnBoost = binding.btnModeBoost
        val btnSuper = binding.btnModeSuperboost

        btnStartNormal.setOnClickListener {
            val prog = sessionVm.selectedProgram.value
            if (prog != null) {
                sessionVm.startSessionWithProgram(prog)
            } else {
                sessionVm.startSession(bleVm.targetTemp.value)
            }
        }
        btnEnd.setOnClickListener { sessionVm.setHeater(false) }

        binding.btnTempPlus.setOnClickListener {
            val newTemp = (bleVm.targetTemp.value + 1).coerceIn(40, 230)
            bleVm.setTargetTemp(newTemp)
            sessionVm.setTemp(newTemp, bleVm.latestStatus.value?.heaterMode ?: 0)
        }
        binding.btnTempMinus.setOnClickListener {
            val newTemp = (bleVm.targetTemp.value - 1).coerceIn(40, 230)
            bleVm.setTargetTemp(newTemp)
            sessionVm.setTemp(newTemp, bleVm.latestStatus.value?.heaterMode ?: 0)
        }
        binding.btnBoostPlus.setOnClickListener {
            val current = bleVm.latestStatus.value?.boostOffsetC ?: 0
            sessionVm.setBoost(current + 1)
        }
        binding.btnBoostMinus.setOnClickListener {
            val current = bleVm.latestStatus.value?.boostOffsetC ?: 0
            sessionVm.setBoost(current - 1)
        }
        binding.btnSuperboostPlus.setOnClickListener {
            val current = bleVm.latestStatus.value?.superBoostOffsetC ?: 0
            sessionVm.setSuperBoost(current + 1)
        }
        binding.btnSuperboostMinus.setOnClickListener {
            val current = bleVm.latestStatus.value?.superBoostOffsetC ?: 0
            sessionVm.setSuperBoost(current - 1)
        }

        btnNormal.setOnClickListener { sessionVm.setHeaterMode(1, bleVm.targetTemp.value) }
        btnBoost.setOnClickListener { sessionVm.setHeaterMode(2, bleVm.targetTemp.value) }
        btnSuper.setOnClickListener { sessionVm.setHeaterMode(3, bleVm.targetTemp.value) }

        viewLifecycleOwner.lifecycleScope.launch {
            combine(bleVm.latestStatus, bleVm.isCelsius) { s, celsius -> s to celsius }.collect { (s, celsius) ->
                val isRunning = s != null && s.heaterMode > 0

                rowRunningStats.visibility = if (isRunning) View.VISIBLE else View.GONE
                gridStats.visibility = if (isRunning) View.VISIBLE else View.GONE
                btnEnd.visibility = if (isRunning) View.VISIBLE else View.GONE
                btnStartNormal.visibility = if (isRunning) View.GONE else View.VISIBLE

                if (s == null) {
                    tvTemp.text = "---°"
                    tvStatus.text = "OFFLINE"
                    tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_gray_dim))
                    return@collect
                }

                tvTemp.text = "${s.currentTempC.toDisplayTemp(celsius)}°"
                tvBattery.text = "${s.batteryLevel}%"
                tvStatus.text = if (s.heaterMode == 0) "IDLE" else if (s.setpointReached) "READY" else "HEATING"
                tvStatus.setTextColor(if (s.heaterMode == 0) ContextCompat.getColor(requireContext(), R.color.color_gray_dim) else if (s.setpointReached) ContextCompat.getColor(requireContext(), R.color.color_green) else ContextCompat.getColor(requireContext(), R.color.color_yellow))

                // Boost offsets are deltas — no +32 offset, just scale
                tvBoostOffset.text = "+${s.boostOffsetC.toDisplayTempDelta(celsius)}°"
                tvSuperBoostOffset.text = "+${s.superBoostOffsetC.toDisplayTempDelta(celsius)}°"

                // Update mode button states
                btnNormal.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(requireContext(), if (s.heaterMode == 1) R.color.color_blue else R.color.color_surface)))
                btnBoost.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(requireContext(), if (s.heaterMode == 2) R.color.color_yellow else R.color.color_surface)))
                btnSuper.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(requireContext(), if (s.heaterMode == 3) R.color.color_orange else R.color.color_surface)))
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // Include target temp unit text for the controls card
            combine(bleVm.targetTemp, bleVm.isCelsius) { t, celsius -> t to celsius }.collect { (t, celsius) ->
                tvTargetBase.text = "${t.toDisplayTemp(celsius)}°"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            combine(bleVm.latestStatus, historyVm.estimatedHeatUpTimeSecsWithContext(bleVm.targetTemp, bleVm)) { s, est -> s to est }.collect { (s, est) ->
                val isIdleOrOffline = s == null || s.heaterMode == 0
                if (isIdleOrOffline && est != null) {
                    tvHeatUpEstimate.visibility = View.VISIBLE
                    tvHeatUpEstimate.text = "Est. heat-up: ${est}s"
                } else {
                    tvHeatUpEstimate.visibility = View.GONE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            combine(bleVm.sessionStats, sessionVm.selectedProgram) { ss, selectedProgram ->
                ss to selectedProgram
            }.collect { (ss, selectedProgram) ->
                tvHits.text = ss.hitCount.toString()
                tvDrain.text = "${maxOf(0, ss.batteryDrain)}%"

                fun format(sec: Long) = "%02d:%02d".format(sec / 60, sec % 60)

                // Show estimated program time if selected and session idle, otherwise elapsed time
                if (selectedProgram != null && ss.state == SessionTracker.State.IDLE) {
                    val estimatedMinutes = sessionVm.estimateProgramDurationMinutes(selectedProgram).toInt()
                    tvTime.text = format(estimatedMinutes * 60L) + " (est.)"
                } else {
                    tvTime.text = format(ss.durationSeconds)
                }

                // HEAT-UP: time from heater-on to setpoint first reached.
                // Shows "—" until setpoint is reached, then locks in the measured duration.
                tvHeatUp.text = if (ss.heatUpTimeSecs > 0) "${ss.heatUpTimeSecs}s" else "—"
                tvReadyTime.text = format(ss.readyDurationSec)

                // Active Hit Feedback
                if (ss.isHitActive) {
                    cardActiveHit.visibility = View.VISIBLE
                    tvHitTimer.text = "${ss.currentHitDurationSec}s"
                } else {
                    cardActiveHit.visibility = View.GONE
                }
            }
        }

        // Setup Program Chip Row
        setupProgramChipRow(binding.sessionContentScroll)

        // Hide chip row during active sessions
        viewLifecycleOwner.lifecycleScope.launch {
            bleVm.sessionStats.collect { stats ->
                // chipRow visibility will be handled in setupProgramChipRow
            }
        }

        // Cancel boost Job when session ends
        viewLifecycleOwner.lifecycleScope.launch {
            bleVm.sessionStats.collectLatest { stats ->
                if (stats.state == SessionTracker.State.IDLE) {
                    sessionVm.cancelBoostSchedule()
                }
            }
        }

        // Setup Session Programs Grid (2x3)
        setupProgramsGrid()
    }

    private fun setupProgramChipRow(scrollView: View) {
        val parentLayout = scrollView as? LinearLayout ?: return

        val chipContainer = LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
        }

        val chipScroll = HorizontalScrollView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(chipContainer)
        }

        // Insert the chip row at the beginning (index 0)
        parentLayout.addView(chipScroll, 0)

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

        // Hide chip row during active sessions
        viewLifecycleOwner.lifecycleScope.launch {
            bleVm.sessionStats.collect { stats ->
                chipScroll.visibility = if (stats.state == SessionTracker.State.IDLE) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
        }

        // Update chips and preview when selectedProgram changes
        viewLifecycleOwner.lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(
                sessionVm.selectedProgram,
                historyVm.avgDrainPerMinute
            ) { program, drainPerMin ->
                Pair(program, drainPerMin)
            }.collect { (selectedProgram, drainPerMin) ->
                // Update temp preview
                updateTempPreview(selectedProgram)

                // Update drain preview if program is selected
                if (selectedProgram != null) {
                    val estimatedDrain = sessionVm.estimateProgramDrain(selectedProgram, drainPerMin)
                    val durationMin = sessionVm.estimateProgramDurationMinutes(selectedProgram)
                    binding.sessionTvDrain.text = "-${estimatedDrain}% (${durationMin.toInt()}m est.)"
                }

                // Rebuild chips to reflect new selection state
                chipContainer.removeAllViews()

                val noProgramChip = createProgramChip(
                    name = "No Program",
                    isSelected = sessionVm.selectedProgram.value == null,
                    onClick = {
                        sessionVm.selectProgram(null)
                        updateTempPreview(null)
                    }
                )
                chipContainer.addView(noProgramChip)

                sessionVm.programs.value.forEach { program ->
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

            setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(
                    requireContext(),
                    if (isSelected) R.color.color_blue else R.color.color_dark_card
                )
            ))

            setOnClickListener { onClick() }
        }
    }

    private fun updateTempPreview(program: SessionProgram?) {
        val tvTargetBase = binding.tvTargetTemp
        tvTargetBase.text = "${program?.targetTempC ?: bleVm.targetTemp.value}°"
    }

    private fun Int.dpToPx() = (this * requireContext().resources.displayMetrics.density).toInt()

    private fun setupProgramsGrid() {
        val scrollView = binding.sessionContentScroll
        val layout = scrollView.getChildAt(0) as LinearLayout

        // Create section label
        val sectionLabel = TextView(requireContext()).apply {
            text = "SESSION PROGRAMS"
            textSize = 12f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.color_boost_bar_fill))
            setPadding(0, 32, 0, 12)
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.1f
        }
        layout.addView(sectionLabel)

        // Create 2x3 grid
        val gridLayout = GridLayout(requireContext()).apply {
            columnCount = 3
            rowCount = 2
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        layout.addView(gridLayout)

        viewLifecycleOwner.lifecycleScope.launch {
            combine(sessionVm.defaultPrograms, sessionVm.customPrograms) { defaults, customs ->
                defaults to customs
            }.collect { (defaults, customs) ->
                gridLayout.removeAllViews()

                val allPrograms = (defaults + customs).take(6)

                allPrograms.forEachIndexed { idx, program ->
                    val isDefaultProgram = program.isDefault
                    val btnProgram = Button(requireContext()).apply {
                        text = "${program.name}\n${program.targetTempC}°C"
                        textSize = 12f
                        setTextColor(0xFFFFFFFF.toInt())
                        setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(requireContext(), R.color.color_surface)
                        ))
                        layoutParams = GridLayout.LayoutParams().apply {
                            width = 0
                            height = ViewGroup.LayoutParams.WRAP_CONTENT
                            columnSpec = GridLayout.spec(idx % 3, 1f)
                            rowSpec = GridLayout.spec(idx / 3)
                            setMargins(6, 6, 6, 6)
                        }
                        setPadding(16, 24, 16, 24)

                        setOnClickListener {
                            showProgramEditor(program)
                        }
                    }
                    gridLayout.addView(btnProgram)
                }

                // Add "New Program" button if fewer than 6 programs
                if (allPrograms.size < 6) {
                    for (idx in allPrograms.size until 6) {
                        val btnNew = Button(requireContext()).apply {
                            text = "+ NEW"
                            textSize = 12f
                            setTextColor(ContextCompat.getColor(requireContext(), R.color.color_boost_bar_fill))
                            setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                                ContextCompat.getColor(requireContext(), R.color.color_surface)
                            ))
                            layoutParams = GridLayout.LayoutParams().apply {
                                width = 0
                                height = ViewGroup.LayoutParams.WRAP_CONTENT
                                columnSpec = GridLayout.spec(idx % 3, 1f)
                                rowSpec = GridLayout.spec(idx / 3)
                                setMargins(6, 6, 6, 6)
                            }
                            setPadding(16, 24, 16, 24)

                            setOnClickListener {
                                showProgramEditor(null)
                            }
                        }
                        gridLayout.addView(btnNew)
                    }
                }
            }
        }
    }

    private fun showProgramEditor(program: SessionProgram?) {
        val context = requireContext()
        val isNew = program == null

        // Parse existing steps or create default
        data class StepUI(var temp: Int, var timeSec: Int)
        val baseTemp = program?.targetTempC ?: bleVm.targetTemp.value
        val steps = mutableListOf<StepUI>()

        // Parse boostStepsJson into StepUI list: temp = base + boost, time = duration at this step
        try {
            val json = program?.boostStepsJson ?: "[{\"offsetSec\":0,\"boostC\":0}]"
            val stepsArray = org.json.JSONArray(json)
            for (i in 0 until stepsArray.length()) {
                val obj = stepsArray.getJSONObject(i)
                val boostC = obj.getInt("boostC")
                val nextOffset = if (i + 1 < stepsArray.length()) {
                    stepsArray.getJSONObject(i + 1).getInt("offsetSec")
                } else {
                    obj.getInt("offsetSec") + 60
                }
                val currentOffset = obj.getInt("offsetSec")
                val timeSec = nextOffset - currentOffset
                steps.add(StepUI(baseTemp + boostC, timeSec))
            }
        } catch (e: Exception) {
            steps.add(StepUI(baseTemp, 60))
        }

        val nameInput = EditText(context).apply {
            setText(program?.name ?: "")
            hint = "Program name"
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(ContextCompat.getColor(context, R.color.color_gray_dim))
            setPadding(16, 12, 16, 12)
        }

        val baseTempInput = EditText(context).apply {
            setText(baseTemp.toString())
            hint = "Base temp (°C)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(ContextCompat.getColor(context, R.color.color_gray_dim))
            setPadding(16, 12, 16, 12)
        }

        val tableContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun updateTableUI() {
            tableContainer.removeAllViews()

            // Header row
            val headerRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 8) }
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(12, 8, 12, 8)
                setBackgroundColor(ContextCompat.getColor(context, R.color.color_surface))
            }

            arrayOf("Step#", "Temp(°C)", "Time(s)", "").forEach { label ->
                val width = when (label) {
                    "Step#" -> 40
                    "Temp(°C)" -> 0
                    "Time(s)" -> 0
                    else -> 50 // Remove column
                }
                val weight = when (label) {
                    "Step#" -> 0f
                    else -> if (label.isEmpty()) 0f else 1f
                }

                val headerText = TextView(context).apply {
                    text = label
                    textSize = 11f
                    setTextColor(ContextCompat.getColor(context, R.color.color_boost_bar_fill))
                    layoutParams = if (weight > 0) {
                        LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
                    } else {
                        LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT)
                    }
                    setPadding(8, 8, 8, 8)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                headerRow.addView(headerText)
            }
            tableContainer.addView(headerRow)

            // Data rows
            var totalSec = 0
            steps.forEachIndexed { idx, step ->
                totalSec += step.timeSec
                val dataRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 4, 0, 4) }
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(8, 8, 8, 8)
                    setBackgroundColor(ContextCompat.getColor(context, R.color.color_tint_green))
                }

                // Step #
                val stepNumText = TextView(context).apply {
                    text = (idx + 1).toString()
                    textSize = 11f
                    setTextColor(0xFFFFFFFF.toInt())
                    layoutParams = LinearLayout.LayoutParams(40, LinearLayout.LayoutParams.WRAP_CONTENT)
                    setPadding(8, 4, 8, 4)
                    gravity = android.view.Gravity.CENTER
                }
                dataRow.addView(stepNumText)

                // Temperature input
                val tempInput = EditText(context).apply {
                    setText(step.temp.toString())
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER
                    textSize = 11f
                    setTextColor(0xFFFFFFFF.toInt())
                    setHintTextColor(ContextCompat.getColor(context, R.color.color_gray_dim))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setPadding(8, 4, 8, 4)
                    gravity = android.view.Gravity.CENTER
                }
                dataRow.addView(tempInput)

                // Time (seconds) input
                val timeInput = EditText(context).apply {
                    setText(step.timeSec.toString())
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER
                    textSize = 11f
                    setTextColor(0xFFFFFFFF.toInt())
                    setHintTextColor(ContextCompat.getColor(context, R.color.color_gray_dim))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setPadding(8, 4, 8, 4)
                    gravity = android.view.Gravity.CENTER
                }
                dataRow.addView(timeInput)

                // Remove button
                val removeBtn = Button(context).apply {
                    text = "−"
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(context, R.color.color_red))
                    setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.color_surface)
                    ))
                    layoutParams = LinearLayout.LayoutParams(50, LinearLayout.LayoutParams.WRAP_CONTENT)
                    setPadding(4, 4, 4, 4)
                    setOnClickListener {
                        steps.removeAt(idx)
                        updateTableUI()
                    }
                }
                dataRow.addView(removeBtn)

                tableContainer.addView(dataRow)

                // Sync inputs to step data
                tempInput.onFocusChangeListener = android.view.View.OnFocusChangeListener { _, _ ->
                    step.temp = tempInput.text.toString().toIntOrNull() ?: baseTemp
                }
                timeInput.onFocusChangeListener = android.view.View.OnFocusChangeListener { _, _ ->
                    step.timeSec = timeInput.text.toString().toIntOrNull() ?: 60
                }
            }

            // Add Step button
            val addStepBtn = Button(context).apply {
                text = "+ ADD STEP"
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.color_boost_bar_fill))
                setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.color_surface)
                ))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 12, 0, 0) }
                setPadding(16, 12, 16, 12)
                setOnClickListener {
                    steps.add(StepUI(baseTemp + 5, 60))
                    updateTableUI()
                }
            }
            tableContainer.addView(addStepBtn)

            // Total time summary
            val summaryText = TextView(context).apply {
                text = "Total: ${totalSec / 60}m ${totalSec % 60}s"
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.color_boost_bar_fill))
                setPadding(12, 12, 12, 4)
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            tableContainer.addView(summaryText)
        }

        updateTableUI()

        val scrollView = android.widget.ScrollView(context).apply {
            addView(tableContainer)
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
            addView(nameInput)
            addView(baseTempInput)
            val stepsLabel = TextView(context).apply {
                text = "HEATING STEPS"
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.color_boost_bar_fill))
                setPadding(12, 16, 12, 8)
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            addView(stepsLabel)
            addView(scrollView)
        }

        AlertDialog.Builder(context)
            .setTitle(if (isNew) "New Program" else "Edit Program")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString().trim()
                val newBaseTemp = baseTempInput.text.toString().toIntOrNull()?.coerceIn(40, 230) ?: baseTemp

                if (name.isNotEmpty() && steps.isNotEmpty()) {
                    // Rebuild boostStepsJson: boost = temp - newBaseTemp
                    val stepsJson = mutableListOf<String>()
                    var offsetSec = 0
                    steps.forEach { step ->
                        val boostC = step.temp - newBaseTemp
                        stepsJson.add("{\"offsetSec\":$offsetSec,\"boostC\":$boostC}")
                        offsetSec += step.timeSec
                    }
                    val json = "[${stepsJson.joinToString(",")}]"

                    val updated = (program ?: SessionProgram(
                        name = name,
                        targetTempC = newBaseTemp,
                        boostStepsJson = json,
                        isDefault = false,
                        stayOnAtEnd = false
                    )).copy(name = name, targetTempC = newBaseTemp, boostStepsJson = json)

                    sessionVm.saveProgram(updated)
                }
            }
            .setNegativeButton("Cancel", null)
            .apply {
                if (!isNew && !program!!.isDefault) {
                    setNeutralButton("Delete") { _, _ ->
                        AlertDialog.Builder(context)
                            .setTitle("Delete Program?")
                            .setMessage("Are you sure? This cannot be undone.")
                            .setPositiveButton("Delete") { _, _ ->
                                sessionVm.deleteProgram(program.id)
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
            }
            .show()
    }
}
