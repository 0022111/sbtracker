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
import android.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.sbtracker.*
import com.sbtracker.databinding.FragmentSessionBinding
import com.sbtracker.databinding.DialogProgramEditorBinding
import com.sbtracker.databinding.ItemProgramStepBinding
import com.sbtracker.data.SessionProgram
import com.sbtracker.data.DeviceStatus
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
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
        super.onDestroyView()
        _binding = null
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
        val tvDrainPreview = binding.sessionTvDrainPreview
        val tvNextStage = binding.sessionTvNextStage

        val rowRunningStats = binding.sessionRunningStatsRow
        val gridStats = binding.statsGrid
        val btnEnd = binding.btnEndSession
        val btnStartNormal = binding.btnStartNormal

        // Hit / Breath Tracking UI
        val cardActiveHit = binding.cardActiveHit
        val tvHitTimer = binding.tvHitTimer
        val cardProgramPreview = binding.cardProgramPreview

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
            val selected = sessionVm.selectedProgram.value
            if (selected != null) {
                sessionVm.startSessionWithProgram(selected)
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
            combine(
                listOf(
                    bleVm.latestStatus,
                    historyVm.estimatedHeatUpTimeSecsWithContext(bleVm.targetTemp, bleVm),
                    sessionVm.selectedProgram,
                    historyVm.avgDrainPerMinute,
                    sessionVm.nextStageTimeMs,
                    sessionVm.nextStageTempC,
                    bleVm.isCelsius
                )
            ) { flows ->
                val s = flows[0] as? DeviceStatus
                val heatUpEst = flows[1] as? Int
                val selected = flows[2] as? SessionProgram
                val avgDrain = flows[3] as Float
                val nextStageAt = flows[4] as? Long
                val nextStageTemp = flows[5] as? Int
                val isCelsius = flows[6] as Boolean

                val isIdleOrOffline = s == null || s.heaterMode == 0
                if (!isIdleOrOffline) {
                    cardProgramPreview.visibility = View.GONE
                    
                    // Show next stage countdown if we have one (T-085/87+)
                    if (nextStageAt != null) {
                        val remainingSec = ((nextStageAt - System.currentTimeMillis()) / 1000L).coerceAtLeast(0)
                        val min = remainingSec / 60
                        val sec = remainingSec % 60
                        
                        tvNextStage.visibility = View.VISIBLE
                        if (nextStageTemp != null) {
                            tvNextStage.text = "Next: ${nextStageTemp.toDisplayTemp(isCelsius)}° in %02d:%02d".format(min, sec)
                        } else {
                            tvNextStage.text = "Next stage in: %02d:%02d".format(min, sec)
                        }
                    } else {
                        tvNextStage.visibility = View.GONE
                    }
                    return@combine
                }
                
                tvNextStage.visibility = View.GONE
                
                // 1. Heat-up Est (from T-017 / T-084)
                if (heatUpEst != null) {
                    tvHeatUpEstimate.visibility = View.VISIBLE
                    tvHeatUpEstimate.text = "Est. ready in ${heatUpEst}s"
                } else {
                    tvHeatUpEstimate.visibility = View.GONE
                }
                
                // 2. Program Details (T-085)
                if (selected != null) {
                    val durationSec = sessionVm.calculateProgramDuration(selected)
                    val durationMin = durationSec / 60
                    val durationRem = durationSec % 60
                    
                    val drainPct = (avgDrain * (durationSec / 60f)).roundToInt()
                    
                    tvDrainPreview.visibility = View.VISIBLE
                    tvDrainPreview.text = "Program: ${selected.name}\n${"%02d:%02d".format(durationMin, durationRem)} (est.)  −${drainPct}% (avg drain)"
                    
                    // Update Ignite button text
                    btnStartNormal.text = "IGNITE PROGRAM"
                } else {
                    tvDrainPreview.visibility = View.GONE
                    btnStartNormal.text = "IGNITE"
                }

                cardProgramPreview.visibility = if (heatUpEst != null || selected != null) View.VISIBLE else View.GONE
            }.collect {}
        }

        viewLifecycleOwner.lifecycleScope.launch {
            bleVm.sessionStats.collect { ss ->
                tvHits.text = ss.hitCount.toString()
                tvDrain.text = "${maxOf(0, ss.batteryDrain)}%"

                fun format(sec: Long) = "%02d:%02d".format(sec / 60, sec % 60)

                tvTime.text = format(ss.durationSeconds)
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

        // Setup Session Programs Grid (2x3)
        setupProgramsGrid()
    }

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
            combine(
                sessionVm.defaultPrograms,
                sessionVm.customPrograms,
                bleVm.latestStatus
            ) { defaults, customs, status ->
                Triple(defaults, customs, status)
            }.collect { (defaults, customs, s) ->
                gridLayout.removeAllViews()
                val allPrograms = (defaults + customs).take(6)
                val selectedId = sessionVm.selectedProgram.value?.id
                val isRunning = s != null && s.heaterMode > 0

                allPrograms.forEachIndexed { idx, program ->
                    val isSelected = program.id == selectedId
                    val btnProgram = Button(requireContext()).apply {
                        text = "${program.name}\n${program.targetTempC}°C"
                        textSize = 12f
                        isEnabled = !isRunning
                        alpha = if (isRunning) 0.5f else 1.0f
                        setTextColor(if (isSelected) 0xFF00FF41.toInt() else 0xFFFFFFFF.toInt())
                        setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(requireContext(), if (isSelected) R.color.color_surface_highlight else R.color.color_surface)
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
                            if (isSelected) {
                                sessionVm.selectProgram(null)
                            } else {
                                sessionVm.selectProgram(program)
                            }
                        }
                        setOnLongClickListener {
                            showProgramEditor(program)
                            true
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
                            isEnabled = !isRunning
                            alpha = if (isRunning) 0.5f else 1.0f
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
        val dialog = BottomSheetDialog(context)
        val b = DialogProgramEditorBinding.inflate(layoutInflater)
        dialog.setContentView(b.root)

        data class StepUI(var temp: Int, var timeSec: Int)
        val baseTemp = program?.targetTempC ?: bleVm.targetTemp.value
        val steps = mutableListOf<StepUI>()

        // 1. Initial Data Load
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

        // 2. UI Setup
        b.editorTitle.text = if (isNew) "New Program" else "Edit Program"
        b.etProgramName.setText(program?.name ?: "")
        b.etBaseTemp.setText(baseTemp.toString())
        if (!isNew && !program!!.isDefault) b.btnDelete.visibility = View.VISIBLE

        fun updateGraph() {
            val currentBase = b.etBaseTemp.text.toString().toIntOrNull() ?: baseTemp
            val graphSteps = steps.map { it.timeSec to it.temp }
            b.programGraph.setSteps(graphSteps)
        }

        fun refreshStepsUI() {
            b.stepsContainer.removeAllViews()
            steps.forEachIndexed { idx, step ->
                val stepBinding = ItemProgramStepBinding.inflate(layoutInflater, b.stepsContainer, false)
                stepBinding.tvStepNum.text = (idx + 1).toString()
                stepBinding.etStepTemp.setText(step.temp.toString())
                stepBinding.etStepDuration.setText(step.timeSec.toString())

                stepBinding.etStepTemp.doAfterTextChanged {
                    step.temp = it.toString().toIntOrNull() ?: baseTemp
                    updateGraph()
                }
                stepBinding.etStepDuration.doAfterTextChanged {
                    step.timeSec = it.toString().toIntOrNull()?.coerceAtLeast(1) ?: 60
                    updateGraph()
                }
                stepBinding.btnRemoveStep.setOnClickListener {
                    steps.removeAt(idx)
                    refreshStepsUI()
                }
                b.stepsContainer.addView(stepBinding.root)
            }
            updateGraph()
        }

        b.etBaseTemp.doAfterTextChanged { updateGraph() }
        b.btnAddStep.setOnClickListener {
            val lastTemp = steps.lastOrNull()?.temp ?: (b.etBaseTemp.text.toString().toIntOrNull() ?: baseTemp)
            steps.add(StepUI(lastTemp + 5, 60))
            refreshStepsUI()
        }

        b.btnDelete.setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle("Delete Program?")
                .setMessage("Are you sure? This cannot be undone.")
                .setPositiveButton("Delete") { _, _ ->
                    sessionVm.deleteProgram(program!!.id)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        b.btnSave.setOnClickListener {
            val name = b.etProgramName.text.toString().trim()
            val newBaseTemp = b.etBaseTemp.text.toString().toIntOrNull()?.coerceIn(40, 230) ?: baseTemp

            if (name.isNotEmpty() && steps.isNotEmpty()) {
                val stepsJson = mutableListOf<String>()
                var offsetSec = 0
                steps.forEach { step ->
                    val clampedTemp = step.temp.coerceIn(40, 230)
                    val boostC = clampedTemp - newBaseTemp
                    stepsJson.add("{\"offsetSec\":$offsetSec,\"boostC\":$boostC}")
                    offsetSec += step.timeSec
                }
                val json = "[${stepsJson.joinToString(",")}]"
                val updated = (program ?: SessionProgram(
                    name = name,
                    targetTempC = newBaseTemp,
                    boostStepsJson = json,
                    isDefault = false
                )).copy(name = name, targetTempC = newBaseTemp, boostStepsJson = json)

                sessionVm.saveProgram(updated)
                dialog.dismiss()
            }
        }

        refreshStepsUI()
        dialog.show()
    }
}
