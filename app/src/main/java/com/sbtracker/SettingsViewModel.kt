package com.sbtracker

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sbtracker.analytics.AnalyticsRepository
import com.sbtracker.data.AppDatabase
import com.sbtracker.data.ProgramRepository
import com.sbtracker.data.SessionProgram
import com.sbtracker.data.TempPreset
import com.sbtracker.data.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Owns user preferences: day start hour, data retention, capsule weight, default pack type.
 * Also manages session programs.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val db: AppDatabase,
    private val analyticsRepo: AnalyticsRepository,
    private val prefsRepo: UserPreferencesRepository,
    private val programRepository: ProgramRepository,
    application: Application
) : AndroidViewModel(application) {

    val dayStartHour: StateFlow<Int> = prefsRepo.userPreferencesFlow
        .map { it.dayStartHour }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 4)

    val retentionDays: StateFlow<Int> = prefsRepo.userPreferencesFlow
        .map { it.retentionDays }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 90)

    val capsuleWeightGrams: StateFlow<Float> = prefsRepo.userPreferencesFlow
        .map { it.capsuleWeightGrams }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.10f)

    val defaultIsCapsule: StateFlow<Boolean> = prefsRepo.userPreferencesFlow
        .map { it.defaultIsCapsule }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val programs: StateFlow<List<SessionProgram>> = programRepository.programs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val alertTempReady: StateFlow<Boolean> = prefsRepo.userPreferencesFlow
        .map { it.alertTempReady }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val alertCharge80: StateFlow<Boolean> = prefsRepo.userPreferencesFlow
        .map { it.alertCharge80 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val alertSessionEnd: StateFlow<Boolean> = prefsRepo.userPreferencesFlow
        .map { it.alertSessionEnd }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        viewModelScope.launch {
            prefsRepo.userPreferencesFlow.collect { prefs ->
                analyticsRepo.pruneOldData(prefs.retentionDays)
            }
        }
    }

    fun setDayStartHour(hour: Int) {
        viewModelScope.launch {
            prefsRepo.updateDayStartHour(hour.coerceIn(0, 23))
        }
    }

    fun setRetentionDays(days: Int) {
        viewModelScope.launch {
            prefsRepo.updateRetentionDays(days)
        }
    }

    fun setCapsuleWeight(grams: Float) {
        viewModelScope.launch {
            prefsRepo.updateCapsuleWeight(grams.coerceIn(0.01f, 2.00f))
        }
    }

    fun setDefaultIsCapsule(isCapsule: Boolean) {
        viewModelScope.launch {
            prefsRepo.updateDefaultIsCapsule(isCapsule)
        }
    }

    fun deleteProgram(id: Long) {
        viewModelScope.launch {
            programRepository.deleteProgram(id)
        }
    }

    fun setAlertTempReady(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.updateAlertTempReady(enabled) }
    }

    fun setAlertCharge80(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.updateAlertCharge80(enabled) }
    }

    fun setAlertSessionEnd(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.updateAlertSessionEnd(enabled) }
    }

    val breakGoalDays: StateFlow<Int> = prefsRepo.userPreferencesFlow
        .map { it.breakGoalDays }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 7)

    fun setBreakGoalDays(days: Int) {
        viewModelScope.launch { prefsRepo.updateBreakGoalDays(days.coerceIn(1, 365)) }
    }

    val tempPresets: StateFlow<List<TempPreset>> = prefsRepo.tempPresetsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveTempPresets(presets: List<TempPreset>) {
        viewModelScope.launch { prefsRepo.updateTempPresets(presets) }
    }

    fun addTempPreset(name: String, tempC: Int) {
        val updated = tempPresets.value + TempPreset(name, tempC)
        saveTempPresets(updated)
    }

    fun deleteTempPreset(index: Int) {
        val updated = tempPresets.value.toMutableList().also { it.removeAt(index) }
        saveTempPresets(updated)
    }
}
