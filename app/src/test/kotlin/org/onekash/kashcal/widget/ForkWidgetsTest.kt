package org.onekash.kashcal.widget

import android.content.Context
import androidx.glance.appwidget.SizeMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.onekash.kashcal.data.preferences.KashCalDataStore
import org.onekash.kashcal.data.tasks_org.TasksOrgTask
import org.onekash.kashcal.domain.model.DisplayEvent
import org.onekash.kashcal.domain.reader.DisplayEventRepository
import java.time.LocalDate

/**
 * Unit tests for fork widget features:
 * - Tasks in widgets (FORK:tasks-widget)
 * - Experimental widgets: SplitAgendaWidget and MonthAgendaWidget (FORK:experimental-widgets)
 */
class ForkWidgetsTest {

    private lateinit var mockDisplayEventRepository: DisplayEventRepository
    private lateinit var mockDataStore: KashCalDataStore
    private lateinit var mockContext: Context
    private lateinit var repository: WidgetDataRepository

    private val now = System.currentTimeMillis()
    private val sampleTask = TasksOrgTask(
        id = 101L,
        title = "Comprar café",
        notes = "Filtro 103",
        dueTs = now + 3600000L,
        isAllDay = false,
        startTs = now + 3600000L,
        endTs = now + 3600000L,
        startDay = 20261031,
        endDay = 20261031,
        completedAt = null,
        recurrence = null,
        listId = 1L,
        listTitle = "Mercado",
        listColor = 0xFF4CAF50.toInt(),
        isReadOnly = false,
        isFutureOccurrence = false
    )

    @Before
    fun setup() {
        mockDisplayEventRepository = mockk(relaxed = true)
        mockDataStore = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)
        repository = WidgetDataRepository(mockContext, mockDisplayEventRepository, mockDataStore)
    }

    @Test
    fun `task is excluded when tasks in widgets is disabled`() = runTest {
        val today = LocalDate.now()
        val todayCode = today.year * 10000 + today.monthValue * 100 + today.dayOfMonth

        coEvery { mockDataStore.getTasksOrgEnabled() } returns true
        coEvery { mockDataStore.getTasksInWidgetsEnabled() } returns false

        val taskDisplayEvent = DisplayEvent.Task(sampleTask)
        coEvery {
            mockDisplayEventRepository.getDisplayEventsGroupedByDayOnce(todayCode, todayCode)
        } returns mapOf(todayCode to listOf(taskDisplayEvent))

        val events = repository.getTodayEvents()
        assertTrue("Task must be filtered out when tasks in widgets is disabled", events.isEmpty())
    }

    @Test
    fun `task is included and mapped correctly when tasks in widgets is enabled`() = runTest {
        val today = LocalDate.now()
        val todayCode = today.year * 10000 + today.monthValue * 100 + today.dayOfMonth

        coEvery { mockDataStore.getTasksOrgEnabled() } returns true
        coEvery { mockDataStore.getTasksInWidgetsEnabled() } returns true

        val taskDisplayEvent = DisplayEvent.Task(sampleTask)
        coEvery {
            mockDisplayEventRepository.getDisplayEventsGroupedByDayOnce(todayCode, todayCode)
        } returns mapOf(todayCode to listOf(taskDisplayEvent))

        val events = repository.getTodayEvents()
        assertEquals(1, events.size)

        val widgetEvent = events.first()
        assertTrue("Event must have isTask = true", widgetEvent.isTask)
        assertEquals(101L, widgetEvent.eventId)
        assertEquals("Comprar café", widgetEvent.title)
        assertFalse("Task should not be completed", widgetEvent.isCompleted)
        assertEquals(101L, widgetEvent.taskId)
        assertFalse(widgetEvent.isReadOnly)
        assertFalse(widgetEvent.isFutureOccurrence)
    }

    @Test
    fun `completed task maps with isCompleted true`() = runTest {
        val today = LocalDate.now()
        val todayCode = today.year * 10000 + today.monthValue * 100 + today.dayOfMonth

        coEvery { mockDataStore.getTasksOrgEnabled() } returns true
        coEvery { mockDataStore.getTasksInWidgetsEnabled() } returns true

        val completedTask = sampleTask.copy(completedAt = System.currentTimeMillis())
        val taskDisplayEvent = DisplayEvent.Task(completedTask)
        coEvery {
            mockDisplayEventRepository.getDisplayEventsGroupedByDayOnce(todayCode, todayCode)
        } returns mapOf(todayCode to listOf(taskDisplayEvent))

        val events = repository.getTodayEvents()
        assertEquals(1, events.size)
        assertTrue(events.first().isCompleted)
    }

    @Test
    fun `split agenda widget has Exact sizeMode and responsive preview`() {
        val widget = SplitAgendaWidget()
        assertEquals(SizeMode.Exact, widget.sizeMode)
        assertTrue(widget.previewSizeMode is SizeMode.Responsive)
    }

    @Test
    fun `month agenda widget has Exact sizeMode and responsive preview`() {
        val widget = MonthAgendaWidget()
        assertEquals(SizeMode.Exact, widget.sizeMode)
        assertTrue(widget.previewSizeMode is SizeMode.Responsive)
    }

    @Test
    fun `widget kind enum contains split and month agenda kinds`() {
        assertNotNull(WidgetKind.valueOf("SPLIT_AGENDA"))
        assertNotNull(WidgetKind.valueOf("MONTH_AGENDA"))
        assertEquals(SplitAgendaWidget::class.java, WidgetKind.SPLIT_AGENDA.widget().javaClass)
        assertEquals(MonthAgendaWidget::class.java, WidgetKind.MONTH_AGENDA.widget().javaClass)
    }
}
