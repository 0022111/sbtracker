package com.sbtracker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.sbtracker.R
import dagger.hilt.android.AndroidEntryPoint

// TODO T-052: Replace body with TabLayout + ViewPager2 hosting
//   AnalyticsTabFragment, HealthTabFragment, SessionsTabFragment
@AndroidEntryPoint
class HistoryFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_history, container, false)
}
