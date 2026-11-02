package org.onekash.kashcal.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onekash.kashcal.data.preferences.PreferencesKeys

class ForkBackupPreferencesTest {

    @Test
    fun `fork envelope with extended preferences round-trips`() {
        val original = BackupEnvelope(
            fileFormatVersion = 1,
            appVersion = "23.6.4",
            exportedAt = "2026-10-31T18:00:00Z",
            preferences = mapOf("theme" to BackupPreferenceValue.StringPref("dark")),
            subscriptions = emptyList(),
            forkPreferences = mapOf(
                PreferencesKeys.TASKS_ORG_ENABLED.name to BackupPreferenceValue.BoolPref(true),
                PreferencesKeys.TASKS_IN_WIDGETS_ENABLED.name to BackupPreferenceValue.BoolPref(true),
                PreferencesKeys.EXPERIMENTAL_WIDGETS_ENABLED.name to BackupPreferenceValue.BoolPref(true),
                PreferencesKeys.TRAVEL_TOMTOM_ENABLED.name to BackupPreferenceValue.BoolPref(true),
                PreferencesKeys.TRAVEL_TOMTOM_API_KEY.name to BackupPreferenceValue.StringPref("test-tomtom-key"),
                PreferencesKeys.HIDDEN_TASKS_ORG_LIST_IDS.name to BackupPreferenceValue.StringSetPref(setOf("1", "2")),
            )
        )

        val json = BackupJson.encodeToString(BackupEnvelope.serializer(), original)
        val decoded = BackupJson.decodeFromString(BackupEnvelope.serializer(), json)

        assertEquals(original, decoded)
        assertEquals(6, decoded.forkPreferences.size)
        assertEquals(
            BackupPreferenceValue.StringPref("test-tomtom-key"),
            decoded.forkPreferences[PreferencesKeys.TRAVEL_TOMTOM_API_KEY.name]
        )
    }

    @Test
    fun `fromBackupValue converts all supported fork preference types`() {
        val boolConverted = ForkBackupPreferences.fromBackupValue(
            PreferencesKeys.TASKS_ORG_ENABLED.name,
            BackupPreferenceValue.BoolPref(true)
        )
        assertNotNull(boolConverted)
        assertEquals(PreferencesKeys.TASKS_ORG_ENABLED, boolConverted!!.first)
        assertEquals(true, boolConverted.second)

        val stringConverted = ForkBackupPreferences.fromBackupValue(
            PreferencesKeys.TRAVEL_TOMTOM_API_KEY.name,
            BackupPreferenceValue.StringPref("my-key")
        )
        assertNotNull(stringConverted)
        assertEquals(PreferencesKeys.TRAVEL_TOMTOM_API_KEY, stringConverted!!.first)
        assertEquals("my-key", stringConverted.second)

        val setConverted = ForkBackupPreferences.fromBackupValue(
            PreferencesKeys.HIDDEN_TASKS_ORG_LIST_IDS.name,
            BackupPreferenceValue.StringSetPref(setOf("10", "20"))
        )
        assertNotNull(setConverted)
        assertEquals(PreferencesKeys.HIDDEN_TASKS_ORG_LIST_IDS, setConverted!!.first)
        assertEquals(setOf("10", "20"), setConverted.second)
    }
}
