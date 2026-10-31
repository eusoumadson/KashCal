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
 * Month + Agenda Split Widget (FORK:experimental-widgets)
 *
 * Left column: Full month calendar grid with indicator dots for days with events and today highlighted.
 * Right column: Today's agenda with events and tasks (with interactive checkboxes).
 */
class MonthAgendaWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override val previewSizeMode = WidgetPreviewSizes.MONTH_AGENDA

    override val stateDefinition = PreferencesGlanceStateDefinition

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MonthAgendaWidgetEntryPoint {
        fun widgetDataRepository(): WidgetDataRepository
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context, MonthAgendaWidgetEntryPoint::class.java)
        val repository = entryPoint.widgetDataRepository()
        val dataStore = KashCalDataStore(context)
        val initialAccent = resolveWidgetAccentColors(context, dataStore).colors
        val firstDayOfWeek = dataStore.getFirstDayOfWeek()

        provideContent {
            val prefs = currentState<Preferences>()
            val stamp = prefs[WIDGET_REFRESH_STAMP] ?: 0L
            val isRefreshing = isRefreshCueActive(prefs[WIDGET_REFRESHING_UNTIL], System.currentTimeMillis())

            val today = LocalDate.now()
            val todayCode = DayPagerUtils.msToDayCode(System.currentTimeMillis())
            val monthGrid = MonthGrid.compute(today.year, today.monthValue - 1, firstDayOfWeek)
            val (startDayCode, endDayCode) = monthGrid.toDayCodeRange()

            val todayEvents by produceState(
                initialValue = emptyList<WidgetDataRepository.WidgetEvent>(),
                key1 = stamp
            ) {
                value = repository.getTodayEvents()
            }

            val monthEvents by produceState(
                initialValue = emptyMap<Int, List<WidgetDataRepository.WidgetEvent>>(),
                key1 = stamp
            ) {
                value = repository.getEventsInRange(startDayCode, endDayCode)
            }

            val accentColors by produceState(initialValue = initialAccent, key1 = stamp) {
                value = resolveWidgetAccentColors(context, dataStore).colors
            }

            GlanceTheme(colors = accentColors ?: GlanceTheme.colors) {
                MonthAgendaWidgetContent(
                    today = today,
                    todayCode = todayCode,
                    todayEvents = todayEvents,
                    monthGrid = monthGrid,
                    monthEvents = monthEvents,
                    firstDayOfWeek = firstDayOfWeek,
                    isRefreshing = isRefreshing
                )
            }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { MonthPreviewContent(context) }
    }
}
