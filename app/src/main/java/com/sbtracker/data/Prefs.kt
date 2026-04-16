package com.sbtracker.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Light user preferences. DataStore because SharedPreferences is write-on-commit
 * and the whole rest of the app speaks Flow.
 */
private val Context.store by preferencesDataStore(name = "prefs")

class Prefs(private val context: Context) {

    val useCelsius: Flow<Boolean> = context.store.data.map { it[KEY_UNIT] ?: true }

    suspend fun setUseCelsius(on: Boolean) {
        context.store.edit { it[KEY_UNIT] = on }
    }

    companion object {
        private val KEY_UNIT: Preferences.Key<Boolean> = booleanPreferencesKey("use_celsius")
    }
}
