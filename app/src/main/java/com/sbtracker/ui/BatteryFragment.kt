package com.sbtracker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sbtracker.*
import com.sbtracker.databinding.FragmentBatteryBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BatteryFragment : Fragment() {
    private var _binding: FragmentBatteryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBatteryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val vm = (requireActivity() as MainActivity).vm

        // Hero state containers
        val heroIdle = binding.heroIdle
        val heroCharging = binding.heroCharging

        // Hero Idle Views
        val tvHeroSessionsLeft = binding.tvHeroSessionsLeft
        val tvHeroIdleSubtext = binding.tvHeroIdleSubtext

        // Hero Charging Views
        val tvHeroEta80 = binding.tvHeroEta80
        val tvHeroEtaFull = binding.tvHeroEtaFull
        val tvHeroChargeRate = binding.tvHeroChargeRate

        // Status row
        val tvPercent = binding.battTvPercent
        val tvStatus = binding.battTvStatus

        // Graph
        val graph = binding.battGraph
        val tvBattPeriodDay = binding.battGraphPeriodDay
        val tvBattPeriodWeek = binding.battGraphPeriodWeek

        tvBattPeriodDay.setOnClickListener  { vm.setGraphPeriod(MainViewModel.GraphPeriod.DAY) }
        tvBattPeriodWeek.setOnClickListener { vm.setGraphPeriod(MainViewModel.GraphPeriod.WEEK) }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.graphPeriod.collect { p ->
                val dayActive = p == MainViewModel.GraphPeriod.DAY
                tvBattPeriodDay.setTextColor(ContextCompat.getColor(requireContext(), if (dayActive)  R.color.color_blue else R.color.color_gray_mid))
                tvBattPeriodDay.setTypeface(null, if (dayActive)  android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                tvBattPeriodDay.setBackgroundResource(if (dayActive)  R.drawable.bg_badge_blue else 0)
                tvBattPeriodWeek.setTextColor(ContextCompat.getColor(requireContext(), if (!dayActive) R.color.color_blue else R.color.color_gray_mid))
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
                    tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_gray_mid))
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
                    s.isCharging -> ContextCompat.getColor(requireContext(), R.color.color_green)
                    s.heaterMode > 0 -> ContextCompat.getColor(requireContext(), R.color.color_yellow)
                    else -> ContextCompat.getColor(requireContext(), R.color.color_gray_dim)
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
                    bi.drainTrend > 0.5f  -> ContextCompat.getColor(requireContext(), R.color.color_red)
                    bi.drainTrend < -0.5f -> ContextCompat.getColor(requireContext(), R.color.color_green)
                    else                  -> ContextCompat.getColor(requireContext(), R.color.color_gray_dim)
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
