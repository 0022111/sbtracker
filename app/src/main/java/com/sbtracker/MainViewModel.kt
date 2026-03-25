package com.sbtracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sbtracker.data.ProgramRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Thin backward-compatibility shell.
 *
 * All logic has been decomposed into:
 * - [BleViewModel] — BLE connection, data pipeline, device management
 * - [SessionViewModel] — device write commands
 * - [HistoryViewModel] — session list, analytics, graphs, export
 * - [BatteryViewModel] — battery insights, charge cycles
 * - [SettingsViewModel] — user preferences
 *
 * New code should use those ViewModels directly via `activityViewModels()`.
 * This class is retained only for [BleService] backward compatibility.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val programRepository: ProgramRepository
) : AndroidViewModel(application) {
    init {
        viewModelScope.launch {
            programRepository.seedDefaultsIfNeeded()
        }
    }
}
