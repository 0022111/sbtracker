package com.sbtracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.*
import com.sbtracker.data.TempPreset
import com.sbtracker.data.TempPresetSerializer
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val USER_PREFERENCES = "user_preferences"
private const val APP_PREFS = "app_prefs"

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = USER_PREFERENCES,
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, APP_PREFS))
    }
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    private object PreferencesKeys {
        val DAY_START_HOUR = intPreferencesKey("day_start_hour")
        val PHONE_ALERTS = booleanPreferencesKey("phone_alerts")
        val DIM_ON_CHARGE = booleanPreferencesKey("dim_on_charge")
        val IS_CELSIUS = booleanPreferencesKey("is_celsius")
        val PRE_DIM_BRIGHTNESS = intPreferencesKey("pre_dim_brightness")
        val RETENTION_DAYS = intPreferencesKey("retention_days")
        val CAPSULE_WEIGHT_GRAMS = floatPreferencesKey("capsule_weight_grams")
        val DEFAULT_IS_CAPSULE = booleanPreferencesKey("default_is_capsule")
        val TARGET_TEMP = intPreferencesKey("target_temp")
        val ALERT_TEMP_READY = booleanPreferencesKey("alert_temp_ready")
        val ALERT_CHARGE_80 = booleanPreferencesKey("alert_charge_80")
        val ALERT_SESSION_END = booleanPreferencesKey("alert_session_end")
        val TEMP_PRESETS = stringPreferencesKey("temp_presets")
    }

    val userPreferencesFlow: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }.map { preferences ->
            UserPreferences(
                dayStartHour = preferences[PreferencesKeys.DAY_START_HOUR] ?: 4,
                phoneAlertsEnabled = preferences[PreferencesKeys.PHONE_ALERTS] ?: true,
                dimOnChargeEnabled = preferences[PreferencesKeys.DIM_ON_CHARGE] ?: false,
                isCelsius = preferences[PreferencesKeys.IS_CELSIUS] ?: true,
                preDimBrightness = preferences[PreferencesKeys.PRE_DIM_BRIGHTNESS] ?: -1,
                retentionDays = preferences[PreferencesKeys.RETENTION_DAYS] ?: 90,
                capsuleWeightGrams = preferences[PreferencesKeys.CAPSULE_WEIGHT_GRAMS] ?: 0.10f,
                defaultIsCapsule = preferences[PreferencesKeys.DEFAULT_IS_CAPSULE] ?: false,
                targetTemp = preferences[PreferencesKeys.TARGET_TEMP] ?: 180,
                alertTempReady = preferences[PreferencesKeys.ALERT_TEMP_READY] ?: true,
                alertCharge80 = preferences[PreferencesKeys.ALERT_CHARGE_80] ?: true,
                alertSessionEnd = preferences[PreferencesKeys.ALERT_SESSION_END] ?: false
            )
        }

    suspend fun updateDayStartHour(hour: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DAY_START_HOUR] = hour
        }
    }

    suspend fun updatePhoneAlerts(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PHONE_ALERTS] = enabled
        }
    }

    suspend fun updateDimOnCharge(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DIM_ON_CHARGE] = enabled
        }
    }

    suspend fun updateIsCelsius(isCelsius: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_CELSIUS] = isCelsius
        }
    }

    suspend fun updatePreDimBrightness(brightness: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PRE_DIM_BRIGHTNESS] = brightness
        }
    }

    suspend fun updateRetentionDays(days: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.RETENTION_DAYS] = days
        }
    }

    suspend fun updateCapsuleWeight(grams: Float) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CAPSULE_WEIGHT_GRAMS] = grams
        }
    }

    suspend fun updateDefaultIsCapsule(isCapsule: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_IS_CAPSULE] = isCapsule
        }
    }

    suspend fun updateTargetTemp(temp: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TARGET_TEMP] = temp
        }
    }

    suspend fun updateAlertTempReady(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ALERT_TEMP_READY] = enabled
        }
    }

    suspend fun updateAlertCharge80(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ALERT_CHARGE_80] = enabled
        }
    }

    suspend fun updateAlertSessionEnd(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ALERT_SESSION_END] = enabled
        }
    }

    val tempPresetsFlow: kotlinx.coroutines.flow.Flow<List<TempPreset>> = dataStore.data
        .catch { exception ->
            if (exception is java.io.IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            TempPresetSerializer.fromJson(
                preferences[PreferencesKeys.TEMP_PRESETS] ?: TempPresetSerializer.defaultJson()
            )
        }

    suspend fun updateTempPresets(presets: List<TempPreset>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TEMP_PRESETS] = TempPresetSerializer.toJson(presets)
        }
    }
}

data class UserPreferences(
    val dayStartHour: Int,
    val phoneAlertsEnabled: Boolean,
    val dimOnChargeEnabled: Boolean,
    val isCelsius: Boolean,
    val preDimBrightness: Int,
    val retentionDays: Int,
    val capsuleWeightGrams: Float,
    val defaultIsCapsule: Boolean,
    val targetTemp: Int,
    val alertTempReady: Boolean,
    val alertCharge80: Boolean,
    val alertSessionEnd: Boolean
)
