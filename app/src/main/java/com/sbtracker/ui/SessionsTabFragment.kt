package com.sbtracker.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sbtracker.*
import com.sbtracker.data.SessionSummary
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SessionsTabFragment : Fragment() {
    private val bleVm: BleViewModel by activityViewModels()
    private val historyVm: HistoryViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_sessions_tab, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val etSearch       = view.findViewById<EditText>(R.id.et_search_sessions)
        val rv             = view.findViewById<RecyclerView>(R.id.rv_history)
        val tvCount        = view.findViewById<TextView>(R.id.tv_history_count)
        val llDeviceFilter = view.findViewById<android.widget.LinearLayout>(R.id.ll_device_filter)
        val tvSortDate     = view.findViewById<TextView>(R.id.tv_sort_date)
        val tvSortHits     = view.findViewById<TextView>(R.id.tv_sort_hits)
        val tvSortDuration = view.findViewById<TextView>(R.id.tv_sort_duration)
        val tvSortDrain    = view.findViewById<TextView>(R.id.tv_sort_drain)
        val tvSortTemp     = view.findViewById<TextView>(R.id.tv_sort_temp)

        // ── RecyclerView setup ────────────────────────────────────────────────

        val adapter = SessionHistoryAdapter(
            onSessionClick = { openSessionReport(it) },
            onDeleteClick = { summary ->
                AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog)
                    .setTitle("Delete Session")
                    .setMessage("Remove this session?")
                    .setPositiveButton("Delete") { _, _ -> historyVm.deleteSession(summary.session) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = adapter

        // ── Search ────────────────────────────────────────────────────────────
        etSearch.doAfterTextChanged { historyVm.setSearchQuery(it?.toString() ?: "") }

        // ── Sort bar ──────────────────────────────────────────────────────────

        tvSortDate.setOnClickListener     { historyVm.setSessionSort(HistoryViewModel.SessionSort.DATE) }
        tvSortHits.setOnClickListener     { historyVm.setSessionSort(HistoryViewModel.SessionSort.HITS) }
        tvSortDuration.setOnClickListener { historyVm.setSessionSort(HistoryViewModel.SessionSort.DURATION) }
        tvSortDrain.setOnClickListener    { historyVm.setSessionSort(HistoryViewModel.SessionSort.DRAIN) }
        tvSortTemp.setOnClickListener     { historyVm.setSessionSort(HistoryViewModel.SessionSort.TEMP) }

        viewLifecycleOwner.lifecycleScope.launch {
            historyVm.sessionSort.collect { sort ->
                val sortViews = listOf(
                    tvSortDate     to HistoryViewModel.SessionSort.DATE,
                    tvSortHits     to HistoryViewModel.SessionSort.HITS,
                    tvSortDuration to HistoryViewModel.SessionSort.DURATION,
                    tvSortDrain    to HistoryViewModel.SessionSort.DRAIN,
                    tvSortTemp     to HistoryViewModel.SessionSort.TEMP
                )
                sortViews.forEach { (tv, s) ->
                    val active = s == sort
                    tv.setTextColor(ContextCompat.getColor(requireContext(), if (active) R.color.color_blue else R.color.color_gray_mid))
                    tv.setTypeface(null, if (active) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                    tv.setBackgroundResource(if (active) R.drawable.bg_badge_blue else 0)
                }
            }
        }

        // ── Device filter chips ───────────────────────────────────────────────

        viewLifecycleOwner.lifecycleScope.launch {
            combine(bleVm.knownDevices, historyVm.sessionFilter) { devices, filter ->
                devices to filter
            }.collect { (devices, filter) ->
                buildDeviceFilterChips(devices, filter, llDeviceFilter)
            }
        }

        // ── Session History List ──────────────────────────────────────────────
        viewLifecycleOwner.lifecycleScope.launch {
            combine(historyVm.filteredSessionHistory, historyVm.sessionFilter, bleVm.activeDevice) { items, filter, device ->
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
                .setPositiveButton("Delete Everything") { _, _ ->
                    val device = bleVm.activeDevice.value ?: return@setPositiveButton
                    historyVm.clearSessionHistory(device)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // ── Export ────────────────────────────────────────────────────────────
        view.findViewById<View>(R.id.btn_export_history).setOnClickListener {
            historyVm.exportHistoryCsv()
        }
    }

    private fun buildDeviceFilterChips(
        devices: List<BleViewModel.SavedDevice>,
        activeFilter: String?,
        container: android.widget.LinearLayout
    ) {
        container.removeAllViews()
        val activeSerial = bleVm.activeDevice.value?.serialNumber

        val mineLabel = bleVm.activeDevice.value?.deviceType?.ifEmpty { "Device" } ?: "Mine"
        container.addView(makeChip(mineLabel, activeFilter == null) {
            historyVm.setSessionFilter(null)
        })

        if (devices.size > 1) {
            container.addView(makeChip("All", activeFilter == "all") {
                historyVm.setSessionFilter("all")
            })
        }

        devices.filter { it.serialNumber != activeSerial }.forEach { device ->
            val label = device.deviceType.ifEmpty { device.serialNumber.takeLast(6) }
            val active = activeFilter == device.serialNumber || activeFilter == device.deviceAddress
            container.addView(makeChip(label, active, {
                historyVm.setSessionFilter(device.serialNumber)
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
