package com.sbtracker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.sbtracker.HistoryViewModel
import com.sbtracker.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HealthTabFragment : Fragment() {
    private val historyVm: HistoryViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_health_tab, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val tvIntakeTotalAll = view.findViewById<TextView>(R.id.tvIntakeTotalAll)
        val tvIntakeWeek     = view.findViewById<TextView>(R.id.tvIntakeWeek)
        val tvIntakeSplit    = view.findViewById<TextView>(R.id.tvIntakeSplit)

        viewLifecycleOwner.lifecycleScope.launch {
            historyVm.intakeStats.collect { stats ->
                tvIntakeTotalAll.text = "%.2fg".format(stats.totalGramsAllTime)
                tvIntakeWeek.text     = "%.2fg".format(stats.totalGramsThisWeek)
                tvIntakeSplit.text    = "${stats.capsuleSessionCount}·${stats.freePackSessionCount}"
            }
        }
    }
}
