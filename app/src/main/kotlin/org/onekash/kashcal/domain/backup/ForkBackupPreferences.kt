package org.onekash.kashcal.domain.backup

import androidx.datastore.preferences.core.Preferences
import org.onekash.kashcal.data.preferences.PreferencesKeys

/**
 * Handles fork-specific preferences export and restore (FORK:backup-fork-configs).
 */
object ForkBackupPreferences {
    val KEYS: List<Preferences.Key<*>> = listOf(
        PreferencesKeys.TASKS_ORG_ENABLED,
        PreferencesKeys.TASKS_IN_WIDGETS_ENABLED,
        PreferencesKeys.HIDDEN_TASKS_ORG_LIST_IDS,
        PreferencesKeys.EXPERIMENTAL_WIDGETS_ENABLED,
        PreferencesKeys.TRAVEL_TOMTOM_ENABLED,
        PreferencesKeys.TRAVEL_STADIA_ENABLED,
        PreferencesKeys.TRAVEL_VALHALLA_ENABLED,
        PreferencesKeys.TRAVEL_RUSH_HOUR_ENABLED,
        PreferencesKeys.TRAVEL_TOMTOM_API_KEY,
        PreferencesKeys.TRAVEL_STADIA_API_KEY,
    )

    fun fromBackupValue(
        name: String,
        value: BackupPreferenceValue,
    ): Pair<Preferences.Key<*>, Any>? {
        val known = KEYS.firstOrNull { it.name == name } ?: return null
        return when (known) {
            PreferencesKeys.TASKS_ORG_ENABLED,
            PreferencesKeys.TASKS_IN_WIDGETS_ENABLED,
            PreferencesKeys.EXPERIMENTAL_WIDGETS_ENABLED,
            PreferencesKeys.TRAVEL_TOMTOM_ENABLED,
            PreferencesKeys.TRAVEL_STADIA_ENABLED,
            PreferencesKeys.TRAVEL_VALHALLA_ENABLED,
            PreferencesKeys.TRAVEL_RUSH_HOUR_ENABLED ->
                (value as? BackupPreferenceValue.BoolPref)?.value?.let { known to it }
            PreferencesKeys.HIDDEN_TASKS_ORG_LIST_IDS ->
                (value as? BackupPreferenceValue.StringSetPref)?.value?.let { known to it }
            PreferencesKeys.TRAVEL_TOMTOM_API_KEY,
            PreferencesKeys.TRAVEL_STADIA_API_KEY ->
                (value as? BackupPreferenceValue.StringPref)?.value?.let { known to it }
            else -> null
        }
    }
}
