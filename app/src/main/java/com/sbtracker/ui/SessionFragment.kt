package com.sbtracker.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sbtracker.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SessionFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_session, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val vm = (requireActivity() as MainActivity).vm
        
        val tvTemp = view.findViewById<TextView>(R.id.session_tv_temp)
        val tvStatus = view.findViewById<TextView>(R.id.session_tv_status)
        val tvHits = view.findViewById<TextView>(R.id.session_tv_hits)
        val tvDrain = view.findViewById<TextView>(R.id.session_tv_drain)
        val tvTime = view.findViewById<TextView>(R.id.session_tv_time)
        val tvBattery = view.findViewById<TextView>(R.id.session_tv_battery)
        val tvHeatUp = view.findViewById<TextView>(R.id.session_tv_heat_up)
        val tvReadyTime = view.findViewById<TextView>(R.id.session_tv_ready_time)
        val tvHeatUpEstimate = view.findViewById<TextView>(R.id.session_tv_heat_up_estimate)
        
        val rowRunningStats = view.findViewById<View>(R.id.session_running_stats_row)
        val gridStats = view.findViewById<View>(R.id.stats_grid)
        val btnEnd = view.findViewById<View>(R.id.btn_end_session)
        val btnStartNormal = view.findViewById<View>(R.id.btn_start_normal)

        // Hit / Breath Tracking UI
        val cardActiveHit = view.findViewById<CardView>(R.id.card_active_hit)
        val tvHitTimer = view.findViewById<TextView>(R.id.tv_hit_timer)

        // Controls
        val tvTargetBase = view.findViewById<TextView>(R.id.tv_target_temp)
        val tvBoostOffset = view.findViewById<TextView>(R.id.tv_boost_offset)
        val tvSuperBoostOffset = view.findViewById<TextView>(R.id.tv_superboost_offset)
        val groupModeSelection = view.findViewById<View>(R.id.mode_selection_group)
        val groupBoostControls = view.findViewById<View>(R.id.group_boost_controls)

        val btnNormal = view.findViewById<Button>(R.id.btn_mode_normal)
        val btnBoost = view.findViewById<Button>(R.id.btn_mode_boost)
        val btnSuper = view.findViewById<Button>(R.id.btn_mode_superboost)

        btnStartNormal.setOnClickListener { vm.startSession() }
        btnEnd.setOnClickListener { vm.setHeater(false) }

        view.findViewById<ImageButton>(R.id.btn_temp_plus).setOnClickListener { vm.adjustTemp(1) }
        view.findViewById<ImageButton>(R.id.btn_temp_minus).setOnClickListener { vm.adjustTemp(-1) }
        view.findViewById<ImageButton>(R.id.btn_boost_plus).setOnClickListener { vm.adjustBoost(1) }
        view.findViewById<ImageButton>(R.id.btn_boost_minus).setOnClickListener { vm.adjustBoost(-1) }
        view.findViewById<ImageButton>(R.id.btn_superboost_plus).setOnClickListener { vm.adjustSuperBoost(1) }
        view.findViewById<ImageButton>(R.id.btn_superboost_minus).setOnClickListener { vm.adjustSuperBoost(-1) }

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
                    tvStatus.setTextColor(Color.parseColor("#8E8E93"))
                    return@collect
                }

                tvTemp.text = "${s.currentTempC.toDisplayTemp(celsius)}°"
                tvBattery.text = "${s.batteryLevel}%"
                tvStatus.text = if (s.heaterMode == 0) "IDLE" else if (s.setpointReached) "READY" else "HEATING"
                tvStatus.setTextColor(if (s.heaterMode == 0) Color.parseColor("#8E8E93") else if (s.setpointReached) Color.parseColor("#30D158") else Color.parseColor("#FFD60A"))

                // Boost offsets are deltas — no +32 offset, just scale
                tvBoostOffset.text = "+${s.boostOffsetC.toDisplayTempDelta(celsius)}°"
                tvSuperBoostOffset.text = "+${s.superBoostOffsetC.toDisplayTempDelta(celsius)}°"

                // Update mode button states
                btnNormal.setBackgroundTintList(android.content.res.ColorStateList.valueOf(if (s.heaterMode == 1) Color.parseColor("#0A84FF") else Color.parseColor("#2C2C2E")))
                btnBoost.setBackgroundTintList(android.content.res.ColorStateList.valueOf(if (s.heaterMode == 2) Color.parseColor("#FFD60A") else Color.parseColor("#2C2C2E")))
                btnSuper.setBackgroundTintList(android.content.res.ColorStateList.valueOf(if (s.heaterMode == 3) Color.parseColor("#FF9F0A") else Color.parseColor("#2C2C2E")))
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
