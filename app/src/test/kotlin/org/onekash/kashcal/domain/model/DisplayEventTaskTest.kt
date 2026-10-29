package org.onekash.kashcal.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onekash.kashcal.data.tasks_org.TasksOrgTask

/** Tests [DisplayEvent.Task]: how a Tasks.org task presents to the views. */
class DisplayEventTaskTest {

    private val task = TasksOrgTask(
        id = 42,
        title = "Teste 2",
        notes = "",
        dueTs = 1793304001000L,
        isAllDay = false,
        startTs = 1793304001000L,
        endTs = 1793304001000L,
        startDay = 20261029,
        endDay = 20261029,
        completedAt = null,
        recurrence = "FREQ=DAILY",
        listId = 1,
        listTitle = "Lembretes Comuns",
        listColor = 0xFFE91E63.toInt(),
        isReadOnly = false
    )

    @Test
    fun `an open task's title gets an empty checkbox and a completed one a ticked box`() {
        assertEquals("\u2610 Teste 2", DisplayEvent.Task(task).title)
        assertEquals("\u2611 Teste 2", DisplayEvent.Task(task.copy(completedAt = 1L)).title)
    }

    @Test
    fun `a task is read-only, free and never declined or cancelled`() {
        val shown = DisplayEvent.Task(task)
        assertTrue(shown.isReadOnly)
        assertTrue(shown.isFree)
        assertFalse(shown.isDeclinedByMe)
        assertFalse(shown.isCancelled)
    }

    @Test
    fun `list name and color identify the task's list, with a default color`() {
        assertEquals("Lembretes Comuns", DisplayEvent.Task(task).calendarName)
        assertEquals(0xFFE91E63.toInt(), DisplayEvent.Task(task).calendarColor)
        assertEquals(DEFAULT_TASK_LIST_COLOR, DisplayEvent.Task(task.copy(listColor = 0)).calendarColor)
        assertNull(DisplayEvent.Task(task).eventColor)
    }

    @Test
    fun `times, repeat flag, notes and key come from the task`() {
        val shown = DisplayEvent.Task(task.copy(notes = "call first"))
        assertEquals(task.startTs, shown.startTs)
        assertEquals(task.endTs, shown.endTs)
        assertEquals(20261029, shown.startDay)
        assertTrue(shown.hasRrule)
        assertEquals("call first", shown.description)
        assertNull(DisplayEvent.Task(task).description)
        assertEquals("task:42", shown.stableKey)
    }
}
