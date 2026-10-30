package org.onekash.kashcal.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onekash.kashcal.data.tasks_org.TasksOrgDate
import org.onekash.kashcal.data.tasks_org.TasksOrgList
import org.onekash.kashcal.data.tasks_org.TasksOrgPriority
import org.onekash.kashcal.data.tasks_org.TasksOrgTask
import org.onekash.kashcal.data.tasks_org.TasksOrgTaskPatch

/**
 * Tests [TaskEditorState]: the draft it builds for a new task and, above all, the sparse patch
 * it diffs for an edit so an update sends only what changed (report §4.3).
 */
class TaskEditorStateTest {

    private val lists = listOf(
        TasksOrgList(id = 1L, title = "Inbox", color = 0, isReadOnly = false),
        TasksOrgList(id = 2L, title = "Work", color = 0, isReadOnly = false)
    )

    private fun task(
        title: String = "Task",
        notes: String = "",
        dueTs: Long = 1000L,
        allDay: Boolean = false,
        listId: Long = 1L,
        priority: TasksOrgPriority = TasksOrgPriority.NONE,
        recurrence: String? = null
    ) = TasksOrgTask(
        id = 5L, title = title, notes = notes, dueTs = dueTs, isAllDay = allDay,
        startTs = dueTs, endTs = dueTs, startDay = 20261029, endDay = 20261029,
        completedAt = null, recurrence = recurrence, listId = listId, listTitle = "Inbox",
        listColor = 0, isReadOnly = false, priority = priority
    )

    @Test
    fun `forNew picks the first writable list by default`() {
        val state = TaskEditorState.forNew(lists, defaultListId = null, defaultDueMs = 2000L, defaultAllDay = false)
        assertEquals(1L, state.listId)
        assertEquals(2000L, state.dueMs)
        assertFalse(state.isEditing)
    }

    @Test
    fun `toDraft trims and carries the fields`() {
        val state = TaskEditorState.forNew(lists, defaultListId = 2L, defaultDueMs = 2000L, defaultAllDay = true)
            .copy(title = "  Buy milk ", notes = " note ", priority = TasksOrgPriority.HIGH)
        val draft = state.toDraft()
        assertEquals("Buy milk", draft.title)
        assertEquals("note", draft.notes)
        assertEquals(2L, draft.listId)
        assertEquals(TasksOrgDate(2000L, allDay = true), draft.due)
        assertEquals(TasksOrgPriority.HIGH, draft.priority)
    }

    @Test
    fun `an unchanged edit builds an empty patch`() {
        val state = TaskEditorState.forEdit(task(), lists)
        assertTrue(state.buildPatch().isEmpty)
    }

    @Test
    fun `only the changed title is in the patch`() {
        val state = TaskEditorState.forEdit(task(title = "Old"), lists).copy(title = "New")
        val patch = state.buildPatch()
        assertEquals(TasksOrgTaskPatch.Field.Value("New"), patch.title)
        assertTrue(patch.notes is TasksOrgTaskPatch.Field.Unset)
        assertTrue(patch.due is TasksOrgTaskPatch.Field.Unset)
        assertTrue(patch.listId is TasksOrgTaskPatch.Field.Unset)
    }

    @Test
    fun `moving the task to another list sets only list_id`() {
        val state = TaskEditorState.forEdit(task(listId = 1L), lists).copy(listId = 2L)
        assertEquals(TasksOrgTaskPatch.Field.Value(2L), state.buildPatch().listId)
    }

    @Test
    fun `changing the due date sets the due field`() {
        val state = TaskEditorState.forEdit(task(dueTs = 1000L), lists).copy(dueMs = 9999L)
        assertEquals(TasksOrgTaskPatch.Field.Value(TasksOrgDate(9999L, allDay = false)), state.buildPatch().due)
    }

    @Test
    fun `clearing the due date sets due to a null value, not unset`() {
        val state = TaskEditorState.forEdit(task(dueTs = 1000L), lists).copy(dueMs = null)
        assertEquals(TasksOrgTaskPatch.Field.Value(null), state.buildPatch().due)
    }

    @Test
    fun `switching all-day on an otherwise unchanged date still sets the due field`() {
        val state = TaskEditorState.forEdit(task(dueTs = 1000L, allDay = false), lists).copy(isAllDay = true)
        val due = state.buildPatch().due
        assertTrue(due is TasksOrgTaskPatch.Field.Value)
        assertEquals(TasksOrgDate(1000L, allDay = true), (due as TasksOrgTaskPatch.Field.Value).value)
    }

    @Test
    fun `changing the priority sets it`() {
        val state = TaskEditorState.forEdit(task(priority = TasksOrgPriority.NONE), lists)
            .copy(priority = TasksOrgPriority.LOW)
        assertEquals(TasksOrgTaskPatch.Field.Value(TasksOrgPriority.LOW), state.buildPatch().priority)
    }

    @Test
    fun `a recurring task is flagged so the editor can warn`() {
        assertTrue(TaskEditorState.forEdit(task(recurrence = "FREQ=DAILY"), lists).isRecurring)
        assertFalse(TaskEditorState.forEdit(task(recurrence = null), lists).isRecurring)
    }

    @Test
    fun `a blank title is invalid`() {
        val state = TaskEditorState.forNew(lists, null, null, false).copy(title = "   ")
        assertFalse(state.isTitleValid)
    }
}
