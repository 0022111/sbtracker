package com.sbtracker

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sbtracker.analytics.AnalyticsRepository
import com.sbtracker.data.AppDatabase
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
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val db: AppDatabase,
    private val analyticsRepo: AnalyticsRepository,
    private val prefsRepo: UserPreferencesRepository,
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
}
