package org.onekash.kashcal.widget

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.state.PreferencesGlanceStateDefinition
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.onekash.kashcal.data.preferences.KashCalDataStore

/**
 * Split Agenda Widget (FORK:experimental-widgets)
 *
 * Responsive dual-column widget on wide grids (e.g. 4x3):
 * Left column: Large date and primary day events/tasks.
 * Right column: Controls, plus scrollable LazyColumn for the rest of the day's events.
 * Compact single column on narrow placements.
 */
class SplitAgendaWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override val previewSizeMode = WidgetPreviewSizes.SPLIT_AGENDA

    override val stateDefinition = PreferencesGlanceStateDefinition

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SplitAgendaWidgetEntryPoint {
        fun widgetDataRepository(): WidgetDataRepository
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context, SplitAgendaWidgetEntryPoint::class.java)
        val repository = entryPoint.widgetDataRepository()
        val dataStore = KashCalDataStore(context)
        val initialAccent = resolveWidgetAccentColors(context, dataStore).colors

        provideContent {
            val prefs = currentState<Preferences>()
            val stamp = prefs[WIDGET_REFRESH_STAMP] ?: 0L
            val isRefreshing = isRefreshCueActive(prefs[WIDGET_REFRESHING_UNTIL], System.currentTimeMillis())
            val events by produceState(
                initialValue = emptyList<WidgetDataRepository.WidgetEvent>(),
                key1 = stamp
            ) {
                value = repository.getTodayEvents()
            }
            val accentColors by produceState(initialValue = initialAccent, key1 = stamp) {
                value = resolveWidgetAccentColors(context, dataStore).colors
            }
            GlanceTheme(colors = accentColors ?: GlanceTheme.colors) {
                SplitAgendaWidgetContent(
                    events = events,
                    isRefreshing = isRefreshing
                )
            }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { AgendaPreviewContent(context) }
    }
}
