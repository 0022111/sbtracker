package com.sbtracker

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sbtracker.data.ChargeCycle
import com.sbtracker.data.SessionSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class HistoryItem {
    abstract val startTimeMs: Long
    abstract val serialNumber: String?
    data class SessionItem(val summary: SessionSummary) : HistoryItem() {
        override val startTimeMs get() = summary.startTimeMs
        override val serialNumber get() = summary.serialNumber
    }
    data class ChargeItem(val cycle: ChargeCycle) : HistoryItem() {
        override val startTimeMs get() = cycle.startTimeMs
        override val serialNumber get() = cycle.serialNumber
    }
}

class SessionHistoryAdapter(
    private val onSessionClick: (SessionSummary) -> Unit,
    private val onDeleteClick: (SessionSummary) -> Unit
) : ListAdapter<HistoryItem, SessionHistoryAdapter.ViewHolder>(HistoryDiffCallback()) {

    var showDeviceName: Boolean = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_session, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvDate: TextView = view.findViewById(R.id.tv_session_date)
        private val tvSummary: TextView = view.findViewById(R.id.tv_session_summary)
        private val tvDrain: TextView = view.findViewById(R.id.tv_session_drain_badge)
        private val vIndicator: View = view.findViewById(R.id.v_session_indicator)

        private val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

        fun bind(item: HistoryItem) {
            val dateStr = sdf.format(Date(item.startTimeMs)).uppercase()
            tvDate.text = if (showDeviceName && item.serialNumber != null) {
                "$dateStr · ${item.serialNumber}"
            } else {
                dateStr
            }

            when (item) {
                is HistoryItem.SessionItem -> bindSession(item.summary)
                is HistoryItem.ChargeItem -> bindCharge(item.cycle)
            }
        }

        private fun bindSession(summary: SessionSummary) {
            val durationSec = summary.durationMs / 1000
            val m = durationSec / 60
            val s = durationSec % 60
            val durStr = if (m > 0) "${m}m ${s}s" else "${s}s"

            tvSummary.text = "$durStr • ${summary.hitCount} hits"
            tvDrain.text = "-${summary.batteryConsumed}%"
            tvDrain.setTextColor(Color.parseColor("#FF453A"))
            tvDrain.setBackgroundResource(R.drawable.bg_badge_red)

            val indicatorColor = when {
                summary.hitCount >= 10 -> "#FF3B30"
                summary.hitCount >= 5  -> "#FFD60A"
                else                   -> "#30D158"
            }
            vIndicator.setBackgroundColor(Color.parseColor(indicatorColor))

            itemView.setOnClickListener { onSessionClick(summary) }
            itemView.setOnLongClickListener {
                onDeleteClick(summary)
                true
            }
        }

        private fun bindCharge(cycle: ChargeCycle) {
            val durationMin = cycle.durationMs / 60_000
            val h = durationMin / 60
            val m = durationMin % 60
            val durStr = if (h > 0) "${h}h ${m}m" else "${m}m"
            val rateStr = "%.1f".format(cycle.avgRatePctPerMin)

            tvSummary.text = "$durStr • ${rateStr}%/min"
            tvDrain.text = "+${cycle.batteryGained}%"
            tvDrain.setTextColor(Color.parseColor("#30D158"))
            tvDrain.setBackgroundResource(R.drawable.bg_badge_green)

            vIndicator.setBackgroundColor(Color.parseColor("#44AAFF"))

            itemView.setOnClickListener(null)
            itemView.isClickable = false
            itemView.setOnLongClickListener(null)
            itemView.isLongClickable = false
        }
    }

    class HistoryDiffCallback : DiffUtil.ItemCallback<HistoryItem>() {
        override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
            return when {
                oldItem is HistoryItem.SessionItem && newItem is HistoryItem.SessionItem ->
                    oldItem.summary.id == newItem.summary.id
                oldItem is HistoryItem.ChargeItem && newItem is HistoryItem.ChargeItem ->
                    oldItem.cycle.id == newItem.cycle.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem) = oldItem == newItem
    }
}
