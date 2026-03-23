package com.sbtracker.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sbtracker.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BatteryFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_battery, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val vm = (requireActivity() as MainActivity).vm

        // Hero state containers
        val heroIdle = view.findViewById<View>(R.id.hero_idle)
        val heroCharging = view.findViewById<View>(R.id.hero_charging)

        // Hero Idle Views
        val tvHeroSessionsLeft = view.findViewById<TextView>(R.id.tv_hero_sessions_left)
        val tvHeroIdleSubtext = view.findViewById<TextView>(R.id.tv_hero_idle_subtext)

        // Hero Charging Views
        val tvHeroEta80 = view.findViewById<TextView>(R.id.tv_hero_eta_80)
        val tvHeroEtaFull = view.findViewById<TextView>(R.id.tv_hero_eta_full)
        val tvHeroChargeRate = view.findViewById<TextView>(R.id.tv_hero_charge_rate)

        // Status row
        val tvPercent = view.findViewById<TextView>(R.id.batt_tv_percent)
        val tvStatus = view.findViewById<TextView>(R.id.batt_tv_status)

        // Graph
        val graph = view.findViewById<BatteryGraphView>(R.id.batt_graph)
        val tvBattPeriodDay = view.findViewById<TextView>(R.id.batt_graph_period_day)
        val tvBattPeriodWeek = view.findViewById<TextView>(R.id.batt_graph_period_week)

        tvBattPeriodDay.setOnClickListener  { vm.setGraphPeriod(MainViewModel.GraphPeriod.DAY) }
        tvBattPeriodWeek.setOnClickListener { vm.setGraphPeriod(MainViewModel.GraphPeriod.WEEK) }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.graphPeriod.collect { p ->
                val dayActive = p == MainViewModel.GraphPeriod.DAY
                tvBattPeriodDay.setTextColor(Color.parseColor(if (dayActive)  "#0A84FF" else "#636366"))
                tvBattPeriodDay.setTypeface(null, if (dayActive)  android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                tvBattPeriodDay.setBackgroundResource(if (dayActive)  R.drawable.bg_badge_blue else 0)
                tvBattPeriodWeek.setTextColor(Color.parseColor(if (!dayActive) "#0A84FF" else "#636366"))
                tvBattPeriodWeek.setTypeface(null, if (!dayActive) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                tvBattPeriodWeek.setBackgroundResource(if (!dayActive) R.drawable.bg_badge_blue else 0)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                vm.graphStatuses,
                vm.graphWindowStartMs,
                vm.sessionStats,
                vm.latestStatus
            ) { statuses, windowStart, stats, latest ->
                val etaMin = stats.chargeEtaMinutes
                val eta80Min = stats.chargeEta80Minutes
                val charging = latest?.isCharging == true
                val etaMs = if (charging && etaMin != null && etaMin > 0) etaMin * 60_000L else 0L
                val eta80Ms = if (charging && eta80Min != null && eta80Min > 0) eta80Min * 60_000L else 0L
                val projLevel = if (etaMs > 0L && latest != null) latest.batteryLevel else null
                graph.setData(statuses, windowStart, etaMs, eta80Ms, projLevel)
                Unit
            }.collect { }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    delay(30_000L)
                    graph.invalidate()
                }
            }
        }

        // Expandable Cards
        val contentDrain = view.findViewById<View>(R.id.content_drain_analysis)
        val headerDrain = view.findViewById<View>(R.id.header_drain_analysis)
        val tvExpandDrain = view.findViewById<TextView>(R.id.tv_expand_drain)
        var drainExpanded = true

        headerDrain.setOnClickListener {
            drainExpanded = !drainExpanded
            contentDrain.visibility = if (drainExpanded) View.VISIBLE else View.GONE
            tvExpandDrain.text = if (drainExpanded) "▼" else "◀"
        }

        val contentHealth = view.findViewById<View>(R.id.content_charge_health)
        val headerHealth = view.findViewById<View>(R.id.header_charge_health)
        val tvExpandHealth = view.findViewById<TextView>(R.id.tv_expand_health)
        var healthExpanded = false
        contentHealth.visibility = View.GONE
        tvExpandHealth.text = "◀"

        headerHealth.setOnClickListener {
            healthExpanded = !healthExpanded
            contentHealth.visibility = if (healthExpanded) View.VISIBLE else View.GONE
            tvExpandHealth.text = if (healthExpanded) "▼" else "◀"
        }

        // Data bindings for Tier 3 Cards
        val tvAvgDrainAll = view.findViewById<TextView>(R.id.tv_stats_avg_drain_all)
        val tvAvgDrainRecent = view.findViewById<TextView>(R.id.tv_stats_avg_drain_recent)
        val tvDrainTrend = view.findViewById<TextView>(R.id.tv_stats_drain_trend)
        val tvDrainMedian = view.findViewById<TextView>(R.id.tv_stats_drain_median)
        val tvDrainStdDev = view.findViewById<TextView>(R.id.tv_stats_drain_std_dev)
        val tvSessionsPerCharge = view.findViewById<TextView>(R.id.tv_stats_sessions_per_charge)

        val tvAvgChargeTime = view.findViewById<TextView>(R.id.tv_stats_avg_charge_time)
        val tvAvgPctGained = view.findViewById<TextView>(R.id.tv_stats_avg_pct_gained)
        val tvAvgDod = view.findViewById<TextView>(R.id.tv_stats_avg_dod)
        val tvDaysPerCycle = view.findViewById<TextView>(R.id.tv_stats_days_per_cycle)
        val tvLongestRun = view.findViewById<TextView>(R.id.tv_stats_longest_run)

        viewLifecycleOwner.lifecycleScope.launch {
            vm.latestStatus.collect { s ->
                if (s == null) {
                    heroIdle.visibility = View.VISIBLE
                    heroCharging.visibility = View.GONE
                    tvPercent.text = "--"
                    tvStatus.text = "OFFLINE"
                    tvStatus.setTextColor(Color.parseColor("#636366"))
                    return@collect
                }
                
                heroIdle.visibility = if (s.isCharging) View.GONE else View.VISIBLE
                heroCharging.visibility = if (s.isCharging) View.VISIBLE else View.GONE

                tvPercent.text = "${s.batteryLevel}%"
                tvStatus.text = when {
                    s.isCharging -> "CHARGING"
                    s.heaterMode > 0 -> "ACTIVE"
                    else -> "IDLE"
                }
                tvStatus.setTextColor(when {
                    s.isCharging -> Color.parseColor("#30D158")
                    s.heaterMode > 0 -> Color.parseColor("#FFD60A")
                    else -> Color.parseColor("#8E8E93")
                })
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.sessionStats.collect { ss ->
                // Hero Idle Data
                tvHeroSessionsLeft.text = if (ss.sessionsRemaining > 0) "${ss.sessionsRemaining}" else "--"
                if (ss.drainSampleCount >= 5 && ss.sessionsRemainingLow != ss.sessionsRemainingHigh) {
                    tvHeroIdleSubtext.text = "Based on avg drain (Range: ${ss.sessionsRemainingLow} to ${ss.sessionsRemainingHigh})"
                } else {
                    tvHeroIdleSubtext.text = "Based on average drain"
                }

                // Hero Charging Data
                tvHeroEta80.text = ss.chargeEta80Minutes?.let { "$it" } ?: "--"
                tvHeroEtaFull.text = ss.chargeEtaMinutes?.let { "${it}m" } ?: "--"
                tvHeroChargeRate.text = if (ss.chargeRatePctPerMin > 0) "%.1f%%/m".format(ss.chargeRatePctPerMin) else "--"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.batteryInsights.collect { bi ->
                tvAvgDrainAll.text = if (bi.allTimeAvgDrain > 0f) "%.1f%%".format(bi.allTimeAvgDrain) else "—"
                tvAvgDrainRecent.text = if (bi.recentAvgDrain > 0f) "%.1f%%".format(bi.recentAvgDrain) else "—"
                
                val trendText  = when {
                    bi.drainTrend == 0f || (bi.drainTrend > -0.5f && bi.drainTrend < 0.5f) -> "Stable"
                    bi.drainTrend > 0f -> "+%.1f%%".format(bi.drainTrend)
                    else               -> "%.1f%%".format(bi.drainTrend)
                }
                val trendColor = when {
                    bi.drainTrend > 0.5f  -> Color.parseColor("#FF453A")
                    bi.drainTrend < -0.5f -> Color.parseColor("#30D158")
                    else                  -> Color.parseColor("#8E8E93")
                }
                tvDrainTrend.text = trendText
                tvDrainTrend.setTextColor(trendColor)
                
                tvDrainMedian.text = if (bi.medianDrain > 0f) "%.1f%%".format(bi.medianDrain) else "—"
                tvDrainStdDev.text = if (bi.drainStdDev > 0f) "±%.1f%%".format(bi.drainStdDev) else "—"
                tvSessionsPerCharge.text = if (bi.sessionsPerChargeCycle > 0f) "%.1f".format(bi.sessionsPerChargeCycle) else "—"

                tvAvgChargeTime.text = when {
                    bi.avgChargeDurationMin <= 0 -> "—"
                    bi.avgChargeDurationMin >= 60 -> "${bi.avgChargeDurationMin / 60}h ${bi.avgChargeDurationMin % 60}m"
                    else -> "${bi.avgChargeDurationMin}m"
                }
                tvAvgPctGained.text = if (bi.avgBatteryGainedPct > 0f) "%.0f%%".format(bi.avgBatteryGainedPct) else "—"
                tvAvgDod.text = if (bi.avgDepthOfDischarge > 0f) "%.0f%%".format(bi.avgDepthOfDischarge) else "—"
                tvDaysPerCycle.text = bi.avgDaysPerChargeCycle?.let { "%.1f".format(it) } ?: "—"
                tvLongestRun.text = if (bi.longestRunSessions > 0) "${bi.longestRunSessions}" else "—"
            }
        }
    }
}
