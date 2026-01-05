package com.example.aitasklist.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class UserPreferencesRepository(private val context: Context) {

    private val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
    private val IS_DAILY_PLANNER = booleanPreferencesKey("is_daily_planner")

    val isDarkTheme: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[IS_DARK_THEME] ?: false // Default to light theme (false)
        }

    val isDailyPlanner: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[IS_DAILY_PLANNER] ?: false // Default to off
        }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_DARK_THEME] = enabled
        }
    }

    suspend fun setDailyPlanner(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_DAILY_PLANNER] = enabled
        }
    }

    private val EXCLUDED_CALENDAR_IDS = androidx.datastore.preferences.core.stringSetPreferencesKey("excluded_calendar_ids")

    val excludedCalendarIds: Flow<Set<String>> = context.dataStore.data
        .map { preferences ->
            preferences[EXCLUDED_CALENDAR_IDS] ?: emptySet()
        }

    suspend fun setExcludedCalendarIds(ids: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[EXCLUDED_CALENDAR_IDS] = ids
        }
    }
}
