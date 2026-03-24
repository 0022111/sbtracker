package com.sbtracker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sbtracker.*
import com.sbtracker.databinding.FragmentSessionBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SessionFragment : Fragment() {
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
        val vm = (requireActivity() as MainActivity).vm

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

        btnStartNormal.setOnClickListener { vm.startSession() }
        btnEnd.setOnClickListener { vm.setHeater(false) }

        binding.btnTempPlus.setOnClickListener { vm.adjustTemp(1) }
        binding.btnTempMinus.setOnClickListener { vm.adjustTemp(-1) }
        binding.btnBoostPlus.setOnClickListener { vm.adjustBoost(1) }
        binding.btnBoostMinus.setOnClickListener { vm.adjustBoost(-1) }
        binding.btnSuperboostPlus.setOnClickListener { vm.adjustSuperBoost(1) }
        binding.btnSuperboostMinus.setOnClickListener { vm.adjustSuperBoost(-1) }

        btnNormal.setOnClickListener { vm.setHeaterMode(1) }
        btnBoost.setOnClickListener { vm.setHeaterMode(2) }
        btnSuper.setOnClickListener { vm.setHeaterMode(3) }

        viewLifecycleOwner.lifecycleScope.launch {
            combine(vm.latestStatus, vm.isCelsius) { s, celsius -> s to celsius }.collect { (s, celsius) ->
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
            combine(vm.targetTemp, vm.isCelsius) { t, celsius -> t to celsius }.collect { (t, celsius) ->
                tvTargetBase.text = "${t.toDisplayTemp(celsius)}°"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            combine(vm.latestStatus, vm.estimatedHeatUpTimeSecs) { s, est -> s to est }.collect { (s, est) ->
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
            vm.sessionStats.collect { ss ->
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
    }
}
