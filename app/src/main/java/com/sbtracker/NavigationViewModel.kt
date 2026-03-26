package com.sbtracker

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

/**
 * Decouples Fragment navigation and scan requests from direct Activity casts.
 * MainActivity collects these events; Fragments emit via activityViewModels().
 */
@HiltViewModel
class NavigationViewModel @Inject constructor() : ViewModel() {

    private val _navigateTo = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val navigateTo: SharedFlow<Int> = _navigateTo.asSharedFlow()

    private val _requestScan = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requestScan: SharedFlow<Unit> = _requestScan.asSharedFlow()

    fun navigateTo(tab: Int) { _navigateTo.tryEmit(tab) }

    fun requestScan() { _requestScan.tryEmit(Unit) }
}
