package org.onekash.kashcal.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.layout.Row
import androidx.glance.testing.unit.hasClickAction
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasStartActivityClickAction
import androidx.glance.testing.unit.hasText
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.onekash.kashcal.MainActivity
import org.onekash.kashcal.R
import org.onekash.kashcal.data.preferences.KashCalDataStore
import org.onekash.kashcal.ui.model.MonthGrid
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
class SplitAgendaWidgetContentTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun makeSplitAgendaData(
        events: List<WidgetDataRepository.WidgetEvent> = emptyList(),
        today: LocalDate = LocalDate.of(2026, 11, 5),
        detailedRows: Boolean = false
    ): SplitAgendaData {
        val todayCode = today.year * 10000 + today.monthValue * 100 + today.dayOfMonth
        val monthGrid = MonthGrid.compute(today.year, today.monthValue - 1, KashCalDataStore.FIRST_DAY_SYSTEM)
        return SplitAgendaData(
            eventsByDay = mapOf(todayCode to events),
            monthEvents = emptyMap(),
            monthGrid = monthGrid,
            today = today,
            todayDayCode = todayCode,
            maxEventsPerDay = 5,
            detailedRows = detailedRows,
            firstDayOfWeek = KashCalDataStore.FIRST_DAY_SYSTEM,
            timePattern = "h:mm a"
        )
    }

    private fun makeEvent(
        eventId: Long = 1L,
        title: String = "Test Item",
        startTs: Long = 1000L,
        endTs: Long = 2000L,
        isAllDay: Boolean = false,
        calendarColor: Int = 0xFF0000FF.toInt(),
        isPast: Boolean = false,
        isDeviceEvent: Boolean = false,
        startDay: Int = 20261105,
        isCancelled: Boolean = false,
        isTask: Boolean = false,
        isCompleted: Boolean = false,
        isFutureOccurrence: Boolean = false,
        isReadOnly: Boolean = false
    ) = WidgetDataRepository.WidgetEvent(
        eventId = eventId,
        occurrenceStartTs = startTs,
        title = title,
        startTs = startTs,
        endTs = endTs,
        isAllDay = isAllDay,
        calendarColor = calendarColor,
        isPast = isPast,
        isDeviceEvent = isDeviceEvent,
        startDay = startDay,
        isCancelled = isCancelled,
        isTask = isTask,
        isCompleted = isCompleted,
        isFutureOccurrence = isFutureOccurrence,
        isReadOnly = isReadOnly
    )

    // ==================== T03: All-Day Task Label Suppression ====================

    @Test
    fun `all-day task suppresses all-day label in compact mode`() = runGlanceAppWidgetUnitTest(timeout = 15.seconds) {
        setContext(context)
        val allDayTask = makeEvent(
            eventId = 101L,
            title = "Pagar conta",
            isTask = true,
            isAllDay = true
        )
        val allDayText = context.getString(R.string.label_all_day)

        provideComposable {
            SplitEventRow(
                event = allDayTask,
                timePattern = "h:mm a",
                detailedRows = false,
                dayCode = 20261105
            )
        }

        onNode(hasTextEqualTo("Pagar conta")).assertExists()
        onAllNodes(hasTextEqualTo(allDayText)).assertCountEquals(0)
    }

    @Test
    fun `all-day task suppresses all-day label in detailed mode`() = runGlanceAppWidgetUnitTest(timeout = 15.seconds) {
        setContext(context)
        val allDayTask = makeEvent(
            eventId = 102L,
            title = "Comprar mantimentos",
            isTask = true,
            isAllDay = true
        )
        val allDayText = context.getString(R.string.label_all_day)

        provideComposable {
            SplitEventRow(
                event = allDayTask,
                timePattern = "h:mm a",
                detailedRows = true,
                dayCode = 20261105
            )
        }

        onNode(hasTextEqualTo("Comprar mantimentos")).assertExists()
        onAllNodes(hasTextEqualTo(allDayText)).assertCountEquals(0)
    }

    @Test
    fun `all-day calendar event keeps all-day label in both compact and detailed modes`() = runGlanceAppWidgetUnitTest(timeout = 15.seconds) {
        setContext(context)
        val allDayCalendarEvent = makeEvent(
            eventId = 201L,
            title = "Feriado",
            isTask = false,
            isAllDay = true
        )
        val allDayText = context.getString(R.string.label_all_day)

        provideComposable {
            SplitEventRow(
                event = allDayCalendarEvent,
                timePattern = "h:mm a",
                detailedRows = false,
                dayCode = 20261105
            )
        }

        onNode(hasTextEqualTo("Feriado")).assertExists()
        onAllNodes(hasTextEqualTo(allDayText)).assertCountEquals(1)
    }

    // ==================== T04: Elimination of Right Column Toolbar ====================

    @Test
    fun `split agenda list column contains no refresh or add buttons or upcoming title`() = runGlanceAppWidgetUnitTest(timeout = 15.seconds) {
        setContext(context)
        val data = makeSplitAgendaData(
            events = listOf(makeEvent(eventId = 1L, title = "Reunião de alinhamento"))
        )

        provideComposable {
            SplitAgendaListColumn(data = data)
        }

        val refreshDesc = context.getString(R.string.cd_widget_refresh)
        val addDesc = context.getString(R.string.cd_widget_add_event)
        val upcomingTitle = context.getString(R.string.upcoming_widget_name)

        onAllNodes(hasContentDescription(refreshDesc)).assertCountEquals(0)
        onAllNodes(hasContentDescription(addDesc)).assertCountEquals(0)
        onAllNodes(hasText(upcomingTitle)).assertCountEquals(0)
    }

    @Test
    fun `split agenda wide layout contains no refresh or add buttons or upcoming title`() = runGlanceAppWidgetUnitTest(timeout = 15.seconds) {
        setContext(context)
        setAppWidgetSize(DpSize(400.dp, 600.dp))
        val data = makeSplitAgendaData(
            events = listOf(makeEvent(eventId = 1L, title = "Reunião de alinhamento"))
        )

        provideComposable {
            SplitAgendaWidgetContent(data = data)
        }

        val refreshDesc = context.getString(R.string.cd_widget_refresh)
        val addDesc = context.getString(R.string.cd_widget_add_event)
        val upcomingTitle = context.getString(R.string.upcoming_widget_name)

        onAllNodes(hasContentDescription(refreshDesc)).assertCountEquals(0)
        onAllNodes(hasContentDescription(addDesc)).assertCountEquals(0)
        onAllNodes(hasText(upcomingTitle)).assertCountEquals(0)
    }

    // ==================== T05: Compact Date Header with 2-digit Year ====================

    @Test
    fun `split date header formats day number weekday and abbreviated month with two-digit year`() = runGlanceAppWidgetUnitTest(timeout = 15.seconds) {
        setContext(context)
        val testDate = LocalDate.of(2026, 11, 5) // November 5, 2026 is Thursday

        provideComposable {
            SplitDateHeader(today = testDate, locale = Locale.US)
        }

        onNode(hasTextEqualTo("5")).assertExists()
        onNode(hasTextEqualTo("THU")).assertExists()
        onNode(hasTextEqualTo("Nov '26")).assertExists()

        val expectedParams = actionParametersOf(
            ActionParameters.Key<String>(EXTRA_ACTION) to ACTION_GO_TO_TODAY
        )
        onNode(hasStartActivityClickAction<MainActivity>(expectedParams)).assertExists()
    }

    @Test
    fun `split date header formats year in two digits in pt-BR locale`() = runGlanceAppWidgetUnitTest(timeout = 15.seconds) {
        setContext(context)
        val testDate = LocalDate.of(2026, 11, 5)
        val expectedDayOfWeek = testDate.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.forLanguageTag("pt-BR")).uppercase()

        provideComposable {
            SplitDateHeader(today = testDate, locale = Locale.forLanguageTag("pt-BR"))
        }

        onNode(hasTextEqualTo("5")).assertExists()
        onNode(hasTextEqualTo(expectedDayOfWeek)).assertExists()
        onNode(hasText("'26")).assertExists()
    }

    @Test
    fun `wide split layout displays compact date header with two-digit year`() = runGlanceAppWidgetUnitTest(timeout = 15.seconds) {
        setContext(context)
        setAppWidgetSize(DpSize(400.dp, 600.dp))
        val testDate = LocalDate.of(2026, 11, 5)
        val data = makeSplitAgendaData(
            today = testDate
        )

        provideComposable {
            SplitAgendaWidgetContent(data = data)
        }

        // Header has "5" and calendar grid day 5 also has "5"
        onAllNodes(hasTextEqualTo("5")).assertCountEquals(2)
        onNode(hasText("'26")).assertExists()
    }

    // ==================== T06: Stacking Pills and Convention Navigation ====================

    @Test
    fun `all-day event stack pill for today navigates to today`() = runGlanceAppWidgetUnitTest(timeout = 15.seconds) {
        setContext(context)
        val todayDayCode = 20261105

        provideComposable {
            SplitAllDayEventStack(
                count = 3,
                dayCode = todayDayCode,
                todayDayCode = todayDayCode
            )
        }

        onNode(hasText("+3 events")).assertExists()
        val expectedParams = actionParametersOf(
            ActionParameters.Key<String>(EXTRA_ACTION) to ACTION_GO_TO_TODAY
        )
        onNode(hasStartActivityClickAction<MainActivity>(expectedParams)).assertExists()
    }

    @Test
    fun `all-day event stack pill for another day navigates to that date`() = runGlanceAppWidgetUnitTest(timeout = 15.seconds) {
        setContext(context)
        val todayDayCode = 20261105
        val futureDayCode = 20261106

        provideComposable {
            SplitAllDayEventStack(
                count = 2,
                dayCode = futureDayCode,
                todayDayCode = todayDayCode
            )
        }

        onNode(hasText("+2 events")).assertExists()
        val expectedParams = actionParametersOf(
            ActionParameters.Key<String>(EXTRA_ACTION) to ACTION_GO_TO_DATE,
            ActionParameters.Key<Int>(EXTRA_DAY_CODE) to futureDayCode
        )
        onNode(hasStartActivityClickAction<MainActivity>(expectedParams)).assertExists()
    }

    @Test
    fun `all-day task stack pill for today navigates to today`() = runGlanceAppWidgetUnitTest(timeout = 15.seconds) {
        setContext(context)
        val todayDayCode = 20261105

        provideComposable {
            SplitAllDayTaskStack(
                count = 7,
                dayCode = todayDayCode,
                todayDayCode = todayDayCode
            )
        }

        onNode(hasText("+7 tasks for today")).assertExists()
        val expectedParams = actionParametersOf(
            ActionParameters.Key<String>(EXTRA_ACTION) to ACTION_GO_TO_TODAY
        )
        onNode(hasStartActivityClickAction<MainActivity>(expectedParams)).assertExists()
    }

    @Test
    fun `all-day task stack pill for another day navigates to that date`() = runGlanceAppWidgetUnitTest(timeout = 15.seconds) {
        setContext(context)
        val todayDayCode = 20261105
        val futureDayCode = 20261106

        provideComposable {
            SplitAllDayTaskStack(
                count = 4,
                dayCode = futureDayCode,
                todayDayCode = todayDayCode
            )
        }

        onNode(hasText("+4 tasks")).assertExists()
        val expectedParams = actionParametersOf(
            ActionParameters.Key<String>(EXTRA_ACTION) to ACTION_GO_TO_DATE,
            ActionParameters.Key<Int>(EXTRA_DAY_CODE) to futureDayCode
        )
        onNode(hasStartActivityClickAction<MainActivity>(expectedParams)).assertExists()
    }

    @Test
    fun `split agenda list column renders event and task stacks when day has multiple all-day items`() =
        runGlanceAppWidgetUnitTest(timeout = 15.seconds) {
            setContext(context)
            val todayDate = LocalDate.of(2026, 11, 5)
            val todayDayCode = 20261105

            val events = listOf(
                // 3 all-day events -> 1 EventRow + 1 AllDayEventStack(+2)
                makeEvent(eventId = 1L, title = "Feriado Nacional", isAllDay = true, isTask = false),
                makeEvent(eventId = 2L, title = "Aniversário Alice", isAllDay = true, isTask = false),
                makeEvent(eventId = 3L, title = "Aniversário Bob", isAllDay = true, isTask = false),
                // 4 all-day tasks -> 1 EventRow + 1 AllDayTaskStack(+3)
                makeEvent(eventId = 11L, title = "Pagar aluguel", isAllDay = true, isTask = true),
                makeEvent(eventId = 12L, title = "Comprar café", isAllDay = true, isTask = true),
                makeEvent(eventId = 13L, title = "Lavar o carro", isAllDay = true, isTask = true),
                makeEvent(eventId = 14L, title = "Enviar relatório", isAllDay = true, isTask = true),
                // 1 timed event
                makeEvent(eventId = 21L, title = "Reunião de equipe", isAllDay = false, isTask = false)
            )

            val data = makeSplitAgendaData(
                events = events,
                today = todayDate
            )

            provideComposable {
                SplitAgendaListColumn(data = data)
            }

            // 1st all-day event is shown
            onNode(hasTextEqualTo("Feriado Nacional")).assertExists()
            // Exceeding all-day events are stacked in a pill
            onNode(hasText("+2 events")).assertExists()

            // 1st all-day task is shown
            onNode(hasTextEqualTo("Pagar aluguel")).assertExists()
            // Exceeding all-day tasks are stacked in a pill
            onNode(hasText("+3 tasks for today")).assertExists()

            // Timed event is shown below stacks
            onNode(hasTextEqualTo("Reunião de equipe")).assertExists()
        }
}
