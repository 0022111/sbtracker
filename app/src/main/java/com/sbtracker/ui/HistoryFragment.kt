package com.sbtracker.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sbtracker.*
import com.sbtracker.data.SessionSummary
import com.sbtracker.util.formatDurationShort
import com.sbtracker.util.relativeDate
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_history, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val activity = requireActivity() as MainActivity
        val vm = activity.vm

        // ── TIER 1: Hero — Timeline + Quick Stats ─────────────────────────────
        val timeline       = view.findViewById<HistoryTimelineView>(R.id.analytics_timeline)
        val tvHeroSessions = view.findViewById<TextView>(R.id.tv_hero_sessions)
        val tvHeroAvgDur   = view.findViewById<TextView>(R.id.tv_hero_avg_duration)
        val tvHeroAvgHits  = view.findViewById<TextView>(R.id.tv_hero_avg_hits)
        val tvHeroAvgDrain = view.findViewById<TextView>(R.id.tv_hero_avg_drain)
        val tvHeroHeatUp   = view.findViewById<TextView>(R.id.tv_hero_avg_heatup)

        // ── TIER 2: Charts & Trends ───────────────────────────────────────────
        val barChart             = view.findViewById<HistoryBarChartView>(R.id.history_bar_chart)
        val tvWeekSessions       = view.findViewById<TextView>(R.id.tv_week_sessions)
        val tvWeekSessionsDelta  = view.findViewById<TextView>(R.id.tv_week_sessions_delta)
        val tvWeekHits           = view.findViewById<TextView>(R.id.tv_week_hits)
        val tvWeekHitsDelta      = view.findViewById<TextView>(R.id.tv_week_hits_delta)
        val tvStreakCurrent       = view.findViewById<TextView>(R.id.tv_streak_current)
        val tvStreakLongest       = view.findViewById<TextView>(R.id.tv_streak_longest)
        val tvPeakTime           = view.findViewById<TextView>(R.id.tv_peak_time)
        val tvBusiestDay         = view.findViewById<TextView>(R.id.tv_busiest_day)

        // ── TIER 3: Deep Dive (expandable) ────────────────────────────────────
        val headerAverages    = view.findViewById<View>(R.id.header_session_averages)
        val contentAverages   = view.findViewById<View>(R.id.content_session_averages)
        val tvExpandAverages  = view.findViewById<TextView>(R.id.tv_expand_averages)
        val headerInsights    = view.findViewById<View>(R.id.header_usage_insights)
        val contentInsights   = view.findViewById<View>(R.id.content_usage_insights)
        val tvExpandInsights  = view.findViewById<TextView>(R.id.tv_expand_insights)

        val tvStatsAvgDuration   = view.findViewById<TextView>(R.id.tv_stats_avg_duration)
        val tvStatsMedianDuration = view.findViewById<TextView>(R.id.tv_stats_median_duration)
        val tvStatsAvgHits       = view.findViewById<TextView>(R.id.tv_stats_avg_hits)
        val tvStatsAvgHitLen     = view.findViewById<TextView>(R.id.tv_stats_avg_hit_len)
        val tvStatsAvgDrain      = view.findViewById<TextView>(R.id.tv_stats_avg_drain)
        val tvStatsSessPerDay7d  = view.findViewById<TextView>(R.id.tv_stats_sess_per_day_7d)
        val tvStatsSessPerDay30d = view.findViewById<TextView>(R.id.tv_stats_sess_per_day_30d)
        val tvStatsPeakInDay     = view.findViewById<TextView>(R.id.tv_stats_peak_in_day)
        val tvStatsFavTemps      = view.findViewById<TextView>(R.id.tv_stats_fav_temps)

        val tvHitsPerMin        = view.findViewById<TextView>(R.id.tv_hits_per_minute)
        val tvAvgSessionsPerDay = view.findViewById<TextView>(R.id.tv_avg_sessions_per_day)
        val tvTotalDaysActive   = view.findViewById<TextView>(R.id.tv_total_days_active)
        val tvAvgHeatUp         = view.findViewById<TextView>(R.id.tv_avg_heat_up)

        // ── Session list + controls ───────────────────────────────────────────
        val rv           = view.findViewById<RecyclerView>(R.id.rv_history)
        val tvCount      = view.findViewById<TextView>(R.id.tv_history_count)
        val llDeviceFilter = view.findViewById<android.widget.LinearLayout>(R.id.ll_device_filter)
        val tvSortDate     = view.findViewById<TextView>(R.id.tv_sort_date)
        val tvSortHits     = view.findViewById<TextView>(R.id.tv_sort_hits)
        val tvSortDuration = view.findViewById<TextView>(R.id.tv_sort_duration)
        val tvSortDrain    = view.findViewById<TextView>(R.id.tv_sort_drain)
        val tvSortTemp     = view.findViewById<TextView>(R.id.tv_sort_temp)

        // Period toggle
        val tvPeriodDay  = view.findViewById<TextView>(R.id.tv_graph_period_day)
        val tvPeriodWeek = view.findViewById<TextView>(R.id.tv_graph_period_week)

        // ── Expand/Collapse logic ─────────────────────────────────────────────

        fun toggleSection(content: View, chevron: TextView) {
            val parent = view.findViewById<View>(R.id.analytics_root) as? ViewGroup
            if (parent != null) {
                androidx.transition.TransitionManager.beginDelayedTransition(parent,
                    androidx.transition.AutoTransition().setDuration(200))
            }
            if (content.visibility == View.GONE) {
                content.visibility = View.VISIBLE
                chevron.text = "▼"
            } else {
                content.visibility = View.GONE
                chevron.text = "▶"
            }
        }

        headerAverages.setOnClickListener { toggleSection(contentAverages, tvExpandAverages) }
        headerInsights.setOnClickListener { toggleSection(contentInsights, tvExpandInsights) }

        // ── RecyclerView setup ────────────────────────────────────────────────

        val adapter = SessionHistoryAdapter(
            onSessionClick = { openSessionReport(it) },
            onDeleteClick = { activity.confirmDelete(it) }
        )
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = adapter

        // ── Sort bar ──────────────────────────────────────────────────────────

        tvSortDate.setOnClickListener     { vm.setSessionSort(MainViewModel.SessionSort.DATE) }
        tvSortHits.setOnClickListener     { vm.setSessionSort(MainViewModel.SessionSort.HITS) }
        tvSortDuration.setOnClickListener { vm.setSessionSort(MainViewModel.SessionSort.DURATION) }
        tvSortDrain.setOnClickListener    { vm.setSessionSort(MainViewModel.SessionSort.DRAIN) }
        tvSortTemp.setOnClickListener     { vm.setSessionSort(MainViewModel.SessionSort.TEMP) }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.sessionSort.collect { sort ->
                val sortViews = listOf(
                    tvSortDate     to MainViewModel.SessionSort.DATE,
                    tvSortHits     to MainViewModel.SessionSort.HITS,
                    tvSortDuration to MainViewModel.SessionSort.DURATION,
                    tvSortDrain    to MainViewModel.SessionSort.DRAIN,
                    tvSortTemp     to MainViewModel.SessionSort.TEMP
                )
                sortViews.forEach { (tv, s) ->
                    val active = s == sort
                    tv.setTextColor(ContextCompat.getColor(requireContext(), if (active) R.color.color_blue else R.color.color_gray_mid))
                    tv.setTypeface(null, if (active) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                    tv.setBackgroundResource(if (active) R.drawable.bg_badge_blue else 0)
                }
            }
        }

        // ── Period toggle ─────────────────────────────────────────────────────

        tvPeriodDay.setOnClickListener  { vm.setGraphPeriod(MainViewModel.GraphPeriod.DAY) }
        tvPeriodWeek.setOnClickListener { vm.setGraphPeriod(MainViewModel.GraphPeriod.WEEK) }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.graphPeriod.collect { p ->
                val dayActive = p == MainViewModel.GraphPeriod.DAY
                tvPeriodDay.setTextColor(ContextCompat.getColor(requireContext(), if (dayActive)  R.color.color_blue else R.color.color_gray_mid))
                tvPeriodDay.setTypeface(null, if (dayActive)  android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                tvPeriodDay.setBackgroundResource(if (dayActive)  R.drawable.bg_badge_blue else 0)
                tvPeriodWeek.setTextColor(ContextCompat.getColor(requireContext(), if (!dayActive) R.color.color_blue else R.color.color_gray_mid))
                tvPeriodWeek.setTypeface(null, if (!dayActive) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                tvPeriodWeek.setBackgroundResource(if (!dayActive) R.drawable.bg_badge_blue else 0)
            }
        }

        // ── Device filter chips ───────────────────────────────────────────────

        viewLifecycleOwner.lifecycleScope.launch {
            combine(vm.knownDevices, vm.sessionFilter) { devices, filter ->
                devices to filter
            }.collect { (devices, filter) ->
                buildDeviceFilterChips(devices, filter, llDeviceFilter, vm)
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // DATA COLLECTORS
        // ══════════════════════════════════════════════════════════════════════

        // ── Timeline (battery % over time with session markers) ───────────────
        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                vm.graphStatuses,
                vm.graphWindowStartMs,
                vm.rawSessionHistory,
                vm.graphPeriod
            ) { statuses, windowStart, sessions, period ->
                val windowEnd = System.currentTimeMillis()
                val timelinePeriod = if (period == MainViewModel.GraphPeriod.WEEK)
                    HistoryTimelineView.Period.WEEK else HistoryTimelineView.Period.DAY
                timeline.setData(statuses, sessions, windowStart, windowEnd, timelinePeriod)
            }.collect { }
        }

        // ── Timeline tap → open session report ───────────────────────────────
        timeline.onSessionTapped = { session ->
            viewLifecycleOwner.lifecycleScope.launch {
                val summary = vm.analyticsRepo.getSessionSummary(session)
                openSessionReport(summary)
            }
        }

        // ── Hero Quick Stats + Averages deep-dive ────────────────────────────
        viewLifecycleOwner.lifecycleScope.launch {
            combine(vm.historyStats, vm.isCelsius) { stats, celsius -> stats to celsius }.collect { (stats, celsius) ->
                // HERO
                tvHeroSessions.text = stats.sessionCount.toString()
                tvHeroAvgDur.text = formatDurationShort(stats.avgSessionDurationSec)
                tvHeroAvgHits.text = if (stats.avgHitsPerSession <= 0f) "—"
                    else if (stats.avgHitsPerSession == stats.avgHitsPerSession.toLong().toFloat()) stats.avgHitsPerSession.toInt().toString()
                    else "%.1f".format(stats.avgHitsPerSession)
                tvHeroAvgDrain.text = if (stats.avgBatteryDrainPct <= 0f) "—"
                    else "%.0f%%".format(stats.avgBatteryDrainPct)
                tvHeroHeatUp.text = if (stats.avgHeatUpTimeSec > 0) "${stats.avgHeatUpTimeSec}s" else "—"

                // DEEP DIVE: Session Averages
                tvStatsAvgDuration.text = formatDurationShort(stats.avgSessionDurationSec)
                tvStatsMedianDuration.text = formatDurationShort(stats.medianSessionDurationSec)
                tvStatsAvgHits.text = if (stats.avgHitsPerSession <= 0f) "—"
                    else if (stats.avgHitsPerSession == stats.avgHitsPerSession.toLong().toFloat()) stats.avgHitsPerSession.toInt().toString()
                    else "%.1f".format(stats.avgHitsPerSession)
                tvStatsAvgHitLen.text = if (stats.avgHitDurationSec <= 0f) "—"
                    else "${stats.avgHitDurationSec.roundToInt()}s"
                tvStatsAvgDrain.text = if (stats.avgBatteryDrainPct <= 0f) "—"
                    else "%.1f%%".format(stats.avgBatteryDrainPct)
                tvStatsFavTemps.text = if (stats.favoriteTempsCelsius.isEmpty()) "No hit data yet"
                    else stats.favoriteTempsCelsius.mapIndexed { i, (tempC, count) ->
                        "#${i + 1}  ${tempC.toDisplayTemp(celsius)}${celsius.unitSuffix()}  ·  $count hits"
                    }.joinToString("\n")
                tvStatsSessPerDay7d.text  = if (stats.sessionsPerDay7d  > 0f) "%.1f".format(stats.sessionsPerDay7d)  else "—"
                tvStatsSessPerDay30d.text = if (stats.sessionsPerDay30d > 0f) "%.1f".format(stats.sessionsPerDay30d) else "—"
                tvStatsPeakInDay.text     = if (stats.peakSessionsInADay > 0) stats.peakSessionsInADay.toString() else "—"
            }
        }

        // ── Usage Insights (weekly comparison, streaks, patterns) ─────────────
        viewLifecycleOwner.lifecycleScope.launch {
            combine(vm.usageInsights, vm.historyStats) { ui, hs -> ui to hs }.collect { (ui, hs) ->
                // Streaks
                tvStreakCurrent.text = if (ui.currentStreakDays > 0) "${ui.currentStreakDays}d" else "—"
                tvStreakLongest.text = if (ui.longestStreakDays > 0) "${ui.longestStreakDays}d" else "—"

                // Peak time & busiest day
                val todLabels = listOf("Night", "Morning", "Afternoon", "Evening")
                tvPeakTime.text = if (ui.peakTimeOfDay >= 0) todLabels[ui.peakTimeOfDay] else "—"
                val dowLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                tvBusiestDay.text = if (ui.busiestDayOfWeek >= 0) dowLabels[ui.busiestDayOfWeek] else "—"

                // Weekly comparison
                tvWeekSessions.text = ui.sessionsThisWeek.toString()
                tvWeekHits.text     = ui.hitsThisWeek.toString()
                val sessionDelta = ui.sessionsThisWeek - ui.sessionsLastWeek
                val hitsDelta    = ui.hitsThisWeek - ui.hitsLastWeek
                fun deltaText(d: Int) = when { d > 0 -> "+$d"; d < 0 -> "$d"; else -> "—" }
                fun deltaColor(d: Int) = when {
                    d > 0 -> ContextCompat.getColor(requireContext(), R.color.color_green)
                    d < 0 -> ContextCompat.getColor(requireContext(), R.color.color_red)
                    else  -> ContextCompat.getColor(requireContext(), R.color.color_gray_mid)
                }
                tvWeekSessionsDelta.text = deltaText(sessionDelta)
                tvWeekSessionsDelta.setTextColor(deltaColor(sessionDelta))
                tvWeekHitsDelta.text = deltaText(hitsDelta)
                tvWeekHitsDelta.setTextColor(deltaColor(hitsDelta))

                // DEEP DIVE: Usage Insights
                tvAvgHeatUp.text = if (hs.avgHeatUpTimeSec > 0) "${hs.avgHeatUpTimeSec}s" else "—"
                tvHitsPerMin.text = if (ui.avgHitsPerMinute > 0f) "%.2f".format(ui.avgHitsPerMinute) else "—"
                tvAvgSessionsPerDay.text = if (ui.avgSessionsPerDay > 0f) "%.1f".format(ui.avgSessionsPerDay) else "—"
                tvTotalDaysActive.text   = if (ui.totalDaysActive > 0) ui.totalDaysActive.toString() else "—"
            }
        }

        // ── Bar chart (sessions + charges) ────────────────────────────────────
        viewLifecycleOwner.lifecycleScope.launch {
            combine(vm.dailyStats, vm.rawChargeHistory, vm.graphPeriod) { daily, charges, period ->
                Triple(daily, charges, period)
            }.collect { (daily, charges, period) ->
                val chartPeriod = if (period == MainViewModel.GraphPeriod.WEEK)
                    HistoryBarChartView.Period.WEEK else HistoryBarChartView.Period.DAY
                barChart.setData(daily, charges, chartPeriod)
            }
        }

        // ── Session History List ──────────────────────────────────────────────
        viewLifecycleOwner.lifecycleScope.launch {
            combine(vm.sessionHistory, vm.sessionFilter, vm.activeDevice) { items, filter, device ->
                Triple(items, filter, device)
            }.collect { (items, filter, device) ->
                adapter.submitList(items)
                val sessions = items.count { it is HistoryItem.SessionItem }
                val charges  = items.count { it is HistoryItem.ChargeItem }
                val countText = when {
                    sessions > 0 && charges > 0 -> "$sessions sessions · $charges charges"
                    sessions > 0 -> "$sessions sessions"
                    charges  > 0 -> "$charges charges"
                    else -> "No history"
                }
                // Show scope indicator when viewing all devices
                val scopeText = if (filter == "all" && device != null) {
                    val label = device.deviceType.ifEmpty { device.serialNumber.takeLast(6) }
                    "$countText  ·  Stats for $label"
                } else countText
                tvCount.text = scopeText
            }
        }

        // ── Clear All (with confirmation) ─────────────────────────────────────
        view.findViewById<TextView>(R.id.tv_history_clear).setOnClickListener {
            AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog)
                .setTitle("Clear All History")
                .setMessage("This will permanently delete all sessions, hits, charge cycles, and device status logs for the current device.\n\nThis cannot be undone.")
                .setPositiveButton("Delete Everything") { _, _ -> vm.clearSessionHistory() }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // ── Export ────────────────────────────────────────────────────────────
        view.findViewById<View>(R.id.btn_export_history).setOnClickListener {
            vm.exportHistoryCsv()
        }
    }

    private fun buildDeviceFilterChips(
        devices: List<MainViewModel.SavedDevice>,
        activeFilter: String?,
        container: android.widget.LinearLayout,
        vm: MainViewModel
    ) {
        container.removeAllViews()
        val activeSerial = vm.activeDevice.value?.serialNumber

        val mineLabel = vm.activeDevice.value?.deviceType?.ifEmpty { "Device" } ?: "Mine"
        container.addView(makeChip(mineLabel, activeFilter == null) {
            vm.setSessionFilter(null)
        })

        if (devices.size > 1) {
            container.addView(makeChip("All", activeFilter == "all") {
                vm.setSessionFilter("all")
            })
        }

        devices.filter { it.serialNumber != activeSerial }.forEach { device ->
            val label = device.deviceType.ifEmpty { device.serialNumber.takeLast(6) }
            val active = activeFilter == device.serialNumber || activeFilter == device.deviceAddress
            container.addView(makeChip(label, active, {
                vm.setSessionFilter(device.serialNumber)
            }).also { chip ->
                chip.setOnLongClickListener {
                    android.widget.Toast.makeText(requireContext(), device.serialNumber, android.widget.Toast.LENGTH_SHORT).show()
                    true
                }
            })
        }
    }

    private fun makeChip(label: String, selected: Boolean, onClick: (() -> Unit)? = null): TextView {
        return TextView(requireContext()).apply {
            text = label
            textSize = 12f
            setTextColor(ContextCompat.getColor(requireContext(), if (selected) R.color.color_blue else R.color.color_gray_mid))
            setTypeface(null, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            setPadding(24, 10, 24, 10)
            if (selected) setBackgroundResource(R.drawable.bg_badge_blue)
            onClick?.let { setOnClickListener { it() } }
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = 8 }
        }
    }

    private fun openSessionReport(s: SessionSummary) {
        val intent = Intent(requireContext(), SessionReportActivity::class.java).apply {
            putExtra("session_id", s.id)
        }
        startActivity(intent)
    }
}
