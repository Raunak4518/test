package com.raunak.daytimeline.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "planner_settings")

data class PlannerSettings(
    val dayStartMinute: Int = 6 * 60,
    val dayEndMinute: Int = 23 * 60,
    val granularityMinutes: Int = 15,
    val autoScrollNow: Boolean = true,
    val showCompleted: Boolean = true,
    val haptics: Boolean = true
)

class SettingsStore(private val context: Context) {
    private val start = intPreferencesKey("day_start")
    private val end = intPreferencesKey("day_end")
    private val granularity = intPreferencesKey("granularity")
    private val autoScroll = booleanPreferencesKey("auto_scroll")
    private val showCompleted = booleanPreferencesKey("show_completed")
    private val haptics = booleanPreferencesKey("haptics")

    val settings: Flow<PlannerSettings> = context.dataStore.data.map { pref ->
        PlannerSettings(
            dayStartMinute = pref[start] ?: 6 * 60,
            dayEndMinute = pref[end] ?: 23 * 60,
            granularityMinutes = pref[granularity] ?: 15,
            autoScrollNow = pref[autoScroll] ?: true,
            showCompleted = pref[showCompleted] ?: true,
            haptics = pref[haptics] ?: true
        )
    }

    suspend fun update(block: PlannerSettings.() -> PlannerSettings) {
        context.dataStore.edit { pref ->
            val current = PlannerSettings(
                dayStartMinute = pref[start] ?: 6 * 60,
                dayEndMinute = pref[end] ?: 23 * 60,
                granularityMinutes = pref[granularity] ?: 15,
                autoScrollNow = pref[autoScroll] ?: true,
                showCompleted = pref[showCompleted] ?: true,
                haptics = pref[haptics] ?: true
            )
            val updated = current.block()
            pref[start] = updated.dayStartMinute
            pref[end] = updated.dayEndMinute
            pref[granularity] = updated.granularityMinutes
            pref[autoScroll] = updated.autoScrollNow
            pref[showCompleted] = updated.showCompleted
            pref[haptics] = updated.haptics
        }
    }
}
