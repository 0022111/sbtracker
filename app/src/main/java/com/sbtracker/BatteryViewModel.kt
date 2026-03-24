package com.sbtracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sbtracker.analytics.AnalyticsRepository
import com.sbtracker.analytics.BatteryInsights
import com.sbtracker.analytics.PersonalRecords
import com.sbtracker.data.AppDatabase
import com.sbtracker.data.ChargeCycle
import com.sbtracker.data.SessionSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Owns battery insights, charge cycle history, personal records, and expand/collapse UI state.
 */
@HiltViewModel
class BatteryViewModel @Inject constructor(
    private val db: AppDatabase,
    private val analyticsRepo: AnalyticsRepository,
    private val prefsRepo: UserPreferencesRepository,
    application: Application
) : AndroidViewModel(application) {

    private val devicePrefs by lazy {
        getApplication<Application>().getSharedPreferences("known_devices_v1", android.content.Context.MODE_PRIVATE)
    }

    private val _activeDevice = MutableStateFlow<BleViewModel.SavedDevice?>(null)
    val activeDevice: StateFlow<BleViewModel.SavedDevice?> = _activeDevice.asStateFlow()

    private val _dayStartHour = prefsRepo.userPreferencesFlow
        .map { it.dayStartHour }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 4)

    init {
        _activeDevice.value = loadLastDevice()
    }

    fun updateActiveDevice(device: BleViewModel.SavedDevice?) {
        _activeDevice.value = device
    }

    fun updateDayStartHour(hour: Int) {
        viewModelScope.launch {
            prefsRepo.updateDayStartHour(hour)
        }
    }

    // ── Device session summaries ──

    @OptIn(ExperimentalCoroutinesApi::class)
    private val deviceSessionSummaries: StateFlow<List<SessionSummary>> =
        _activeDevice.flatMapLatest { device ->
            val sessionsFlow = if (device != null)
                db.sessionDao().observeSessions(device.serialNumber, device.deviceAddress)
            else
                db.sessionDao().observeAllSessions()
            sessionsFlow.map { sessions -> analyticsRepo.getSessionSummaries(sessions) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Charge history ──

    @OptIn(ExperimentalCoroutinesApi::class)
    private val deviceChargeHistory: StateFlow<List<ChargeCycle>> =
        _activeDevice.flatMapLatest { device ->
            if (device != null)
                db.chargeCycleDao().observeCycles(device.serialNumber, device.deviceAddress)
            else
                db.chargeCycleDao().observeAllCycles()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Insights ──

    val batteryInsights: StateFlow<BatteryInsights> =
        combine(deviceSessionSummaries, deviceChargeHistory, _dayStartHour) { summaries, charges, startHour ->
            analyticsRepo.computeBatteryInsights(summaries, charges, startHour)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BatteryInsights())

    val personalRecords: StateFlow<PersonalRecords> =
        deviceSessionSummaries.map { summaries ->
            analyticsRepo.computePersonalRecords(summaries)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PersonalRecords())

    // ── Card expand/collapse ──

    private val _drainCardExpanded = MutableStateFlow(true)
    val drainCardExpanded: StateFlow<Boolean> = _drainCardExpanded.asStateFlow()

    private val _healthCardExpanded = MutableStateFlow(false)
    val healthCardExpanded: StateFlow<Boolean> = _healthCardExpanded.asStateFlow()

    fun toggleDrainCard() { _drainCardExpanded.value = !_drainCardExpanded.value }
    fun toggleHealthCard() { _healthCardExpanded.value = !_healthCardExpanded.value }

    private fun loadLastDevice(): BleViewModel.SavedDevice? {
        val serial = devicePrefs.getString("last_serial", null) ?: return null
        val address = devicePrefs.getString("dev_${serial}_addr", null) ?: return null
        val type = devicePrefs.getString("dev_${serial}_type", "") ?: ""
        return BleViewModel.SavedDevice(serial, address, type)
    }
}
