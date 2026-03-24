package com.sbtracker

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sbtracker.analytics.AnalyticsRepository
import com.sbtracker.data.AppDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    application: Application
) : AndroidViewModel(application) {

    private val appPrefs by lazy {
        getApplication<Application>().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    }

    private val _dayStartHour = MutableStateFlow(4)
    val dayStartHour: StateFlow<Int> = _dayStartHour.asStateFlow()

    private val _retentionDays = MutableStateFlow(90)
    val retentionDays: StateFlow<Int> = _retentionDays.asStateFlow()

    private val _capsuleWeightGrams = MutableStateFlow(0.10f)
    val capsuleWeightGrams: StateFlow<Float> = _capsuleWeightGrams.asStateFlow()

    private val _defaultIsCapsule = MutableStateFlow(false)
    val defaultIsCapsule: StateFlow<Boolean> = _defaultIsCapsule.asStateFlow()

    init {
        _dayStartHour.value = appPrefs.getInt("day_start_hour", 4)
        _retentionDays.value = appPrefs.getInt("retention_days", 90)
        _capsuleWeightGrams.value = appPrefs.getFloat("capsule_weight_grams", 0.10f)
        _defaultIsCapsule.value = appPrefs.getBoolean("default_is_capsule", false)

        viewModelScope.launch {
            analyticsRepo.pruneOldData(_retentionDays.value)
        }
    }

    fun setDayStartHour(hour: Int) {
        val next = hour.coerceIn(0, 23)
        _dayStartHour.value = next
        appPrefs.edit().putInt("day_start_hour", next).apply()
    }

    fun setRetentionDays(days: Int) {
        _retentionDays.value = days
        appPrefs.edit().putInt("retention_days", days).apply()
    }

    fun setCapsuleWeight(grams: Float) {
        val clamped = grams.coerceIn(0.01f, 2.00f)
        _capsuleWeightGrams.value = clamped
        appPrefs.edit().putFloat("capsule_weight_grams", clamped).apply()
    }

    fun setDefaultIsCapsule(isCapsule: Boolean) {
        _defaultIsCapsule.value = isCapsule
        appPrefs.edit().putBoolean("default_is_capsule", isCapsule).apply()
    }
}
