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
import org.onekash.kashcal.ui.model.MonthGrid
import org.onekash.kashcal.ui.util.DayPagerUtils
import java.time.LocalDate

/**
 * Split Agenda Widget (FORK:experimental-widgets)
 *
 * Responsive dual-column widget on wide grids (e.g. 4x3):
 * Left column: Full month calendar grid with indicator dots for days with events and today highlighted.
 * Right column: Controls, plus scrollable LazyColumn for upcoming events (Today, Tomorrow, Tomorrow+1).
 * Compact single column on narrow placements (< 260dp).
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
        val firstDayOfWeek = dataStore.getFirstDayOfWeek()

        provideContent {
            val prefs = currentState<Preferences>()
            val stamp = prefs[WIDGET_REFRESH_STAMP] ?: 0L
            val isRefreshing = isRefreshCueActive(prefs[WIDGET_REFRESHING_UNTIL], System.currentTimeMillis())

            val nowMs = System.currentTimeMillis()
            val today = LocalDate.now()
            val todayCode = DayPagerUtils.msToDayCode(nowMs)

            val data by produceState(
                initialValue = SplitAgendaData(
                    eventsByDay = emptyMap(),
                    monthEvents = emptyMap(),
                    monthGrid = MonthGrid.compute(today.year, today.monthValue - 1, firstDayOfWeek),
                    today = today,
                    todayDayCode = todayCode,
                    maxEventsPerDay = 5,
                    detailedRows = false,
                    firstDayOfWeek = firstDayOfWeek,
                    timePattern = "h:mm a"
                ),
                key1 = stamp
            ) {
                value = fetchSplitAgendaData(repository, dataStore, context, nowMs)
            }
            val accentColors by produceState(initialValue = initialAccent, key1 = stamp) {
                value = resolveWidgetAccentColors(context, dataStore).colors
            }
            GlanceTheme(colors = accentColors ?: GlanceTheme.colors) {
                SplitAgendaWidgetContent(
                    data = data,
                    isRefreshing = isRefreshing
                )
            }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { AgendaPreviewContent(context) }
    }
}
