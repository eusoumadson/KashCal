package org.onekash.kashcal.widget

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.onekash.kashcal.data.preferences.KashCalDataStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the component state of experimental widgets in the system package manager.
 * FORK:experimental-widgets
 */
@Singleton
class ExperimentalWidgetsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: KashCalDataStore
) {
    fun setWidgetsEnabled(enabled: Boolean) {
        val pm = context.packageManager
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        val splitAgenda = ComponentName(context, SplitAgendaWidgetReceiver::class.java)
        val monthAgenda = ComponentName(context, MonthAgendaWidgetReceiver::class.java)
        try {
            pm.setComponentEnabledSetting(splitAgenda, state, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(monthAgenda, state, PackageManager.DONT_KILL_APP)
            Log.d("ExperimentalWidgets", "Experimental widgets component enabled state set to: $enabled")
        } catch (e: Exception) {
            Log.e("ExperimentalWidgets", "Error setting widget component state", e)
        }
    }
}
