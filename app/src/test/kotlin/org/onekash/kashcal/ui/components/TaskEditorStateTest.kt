package org.onekash.kashcal.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

/**
 * The fork's later editor fields: start date, repeat rule, repeat basis, place and the extras
 * saved with the task in one batch.
 */
class TaskEditorStateFeaturesTest {

    private val lists = listOf(org.onekash.kashcal.data.tasks_org.TasksOrgList(1L, "Inbox", 0, false))

    private fun task(
        recurrence: String? = null,
        hideUntil: TasksOrgDate? = null,
        repeatFrom: org.onekash.kashcal.data.tasks_org.TasksOrgRepeatFrom = org.onekash.kashcal.data.tasks_org.TasksOrgRepeatFrom.DUE_DATE,
        placeId: Long = 0L,
        dueTs: Long = 5_000L,
        dueAllDay: Boolean = false
    ) = TasksOrgTask(
        id = 5L, title = "T", notes = "", dueTs = dueTs, isAllDay = dueAllDay,
        startTs = dueTs, endTs = dueTs, startDay = 20261029, endDay = 20261029,
        completedAt = null, recurrence = recurrence, listId = 1L, listTitle = "Inbox",
        listColor = 0, isReadOnly = false, hideUntil = hideUntil, repeatFrom = repeatFrom, placeId = placeId
    )

    private fun edit(task: TasksOrgTask, reminders: List<org.onekash.kashcal.data.tasks_org.TasksOrgReminder> = emptyList()) =
        TaskEditorState.forEdit(task, lists, reminders = reminders)

    @Test
    fun `an untouched edit builds an empty patch even with a rule, a start and a place`() {
        val reminders = listOf(
            org.onekash.kashcal.data.tasks_org.TasksOrgReminder.Location(id = 9, placeId = 3, kind = org.onekash.kashcal.data.tasks_org.TasksOrgReminder.Location.Kind.ARRIVAL)
        )
        val t = task(recurrence = "FREQ=MONTHLY;BYDAY=2TU", hideUntil = TasksOrgDate(1_000L, false), placeId = 3)
        assertTrue(edit(t, reminders).buildPatch().isEmpty)
    }

    @Test
    fun `a custom rule is carried through untouched`() {
        val state = edit(task(recurrence = "FREQ=MONTHLY;BYDAY=2TU"))
        assertEquals(org.onekash.kashcal.domain.tasks.RecurrenceChoice.Custom("FREQ=MONTHLY;BYDAY=2TU"), state.recurrence)
        assertTrue(state.buildPatch().recurrence is TasksOrgTaskPatch.Field.Unset)
    }

    @Test
    fun `choosing a rule patches it, and clearing it writes null`() {
        val rule = org.onekash.kashcal.domain.tasks.RecurrenceChoice.Simple(
            org.onekash.kashcal.domain.tasks.SimpleRecurrence(org.onekash.kashcal.domain.tasks.SimpleRecurrence.Frequency.DAILY)
        )
        val set = edit(task()).copy(recurrence = rule).buildPatch()
        assertEquals(TasksOrgTaskPatch.Field.Value("FREQ=DAILY"), set.recurrence)

        val cleared = edit(task(recurrence = "FREQ=DAILY")).copy(recurrence = org.onekash.kashcal.domain.tasks.RecurrenceChoice.None).buildPatch()
        assertEquals(TasksOrgTaskPatch.Field.Value<String?>(null), cleared.recurrence)
    }

    @Test
    fun `the same rule spelled with the prefix is not a change`() {
        val state = edit(task(recurrence = "RRULE:FREQ=DAILY"))
        // Parsed to the simple rule and written back bare: equal once normalized.
        assertTrue(state.buildPatch().recurrence is TasksOrgTaskPatch.Field.Unset)
    }

    @Test
    fun `repeat basis is patched only while the task repeats and only when changed`() {
        val rule = org.onekash.kashcal.domain.tasks.RecurrenceChoice.of("FREQ=DAILY")
        val changed = edit(task(recurrence = "FREQ=DAILY")).copy(
            recurrence = rule, repeatFrom = org.onekash.kashcal.data.tasks_org.TasksOrgRepeatFrom.COMPLETION_DATE
        ).buildPatch()
        assertEquals(TasksOrgTaskPatch.Field.Value(org.onekash.kashcal.data.tasks_org.TasksOrgRepeatFrom.COMPLETION_DATE), changed.repeatFrom)

        val notRepeating = edit(task()).copy(
            repeatFrom = org.onekash.kashcal.data.tasks_org.TasksOrgRepeatFrom.COMPLETION_DATE
        ).buildPatch()
        assertTrue(notRepeating.repeatFrom is TasksOrgTaskPatch.Field.Unset)
    }

    @Test
    fun `start date set, moved and cleared are patched with the all-day flag`() {
        val none = edit(task())
        assertEquals(TasksOrgTaskPatch.Field.Value(TasksOrgDate(9_000L, true)),
            none.copy(startMs = 9_000L, startAllDay = true).buildPatch().start)

        val withStart = edit(task(hideUntil = TasksOrgDate(9_000L, true)))
        assertTrue(withStart.buildPatch().start is TasksOrgTaskPatch.Field.Unset)
        assertEquals(TasksOrgTaskPatch.Field.Value<TasksOrgDate?>(null), withStart.copy(startMs = null).buildPatch().start)
        // Same instant, flag flipped: a change, so the flag travels with the date.
        assertEquals(TasksOrgTaskPatch.Field.Value(TasksOrgDate(9_000L, false)),
            withStart.copy(startAllDay = false).buildPatch().start)
    }

    @Test
    fun `a draft carries the start, rule and repeat basis`() {
        val state = TaskEditorState.forNew(lists, 1L, 2_000L, false).copy(
            title = "T", startMs = 1_000L, startAllDay = false,
            recurrence = org.onekash.kashcal.domain.tasks.RecurrenceChoice.of("FREQ=WEEKLY;BYDAY=MO"),
            repeatFrom = org.onekash.kashcal.data.tasks_org.TasksOrgRepeatFrom.COMPLETION_DATE
        )
        val draft = state.toDraft()
        assertEquals(TasksOrgDate(1_000L, false), draft.start)
        assertEquals("FREQ=WEEKLY;BYDAY=MO", draft.recurrence)
        assertEquals(org.onekash.kashcal.data.tasks_org.TasksOrgRepeatFrom.COMPLETION_DATE, draft.repeatFrom)
    }

    @Test
    fun `location reminders become the place and are kept out of the reminder list`() {
        val time = org.onekash.kashcal.data.tasks_org.TasksOrgReminder.AtTime(id = 1, triggerAtMs = 100L)
        val arrival = org.onekash.kashcal.data.tasks_org.TasksOrgReminder.Location(id = 2, placeId = 3, kind = org.onekash.kashcal.data.tasks_org.TasksOrgReminder.Location.Kind.ARRIVAL)
        val departure = org.onekash.kashcal.data.tasks_org.TasksOrgReminder.Location(id = 4, placeId = 3, kind = org.onekash.kashcal.data.tasks_org.TasksOrgReminder.Location.Kind.DEPARTURE)
        val state = edit(task(placeId = 3), listOf(time, arrival, departure))
        assertEquals(listOf<org.onekash.kashcal.data.tasks_org.TasksOrgReminder>(time), state.reminders)
        assertEquals(org.onekash.kashcal.data.tasks_org.TasksOrgLocation(3, arrival = true, departure = true), state.location)
    }

    @Test
    fun `a place with no trigger is still the task's place`() {
        val state = edit(task(placeId = 3))
        assertEquals(org.onekash.kashcal.data.tasks_org.TasksOrgLocation(3, arrival = false, departure = false), state.location)
        assertNull(edit(task()).location)
    }

    @Test
    fun `extras carry tags, reminders and the place for the atomic save`() {
        val time = org.onekash.kashcal.data.tasks_org.TasksOrgReminder.AtTime(triggerAtMs = 7L)
        val place = org.onekash.kashcal.data.tasks_org.TasksOrgLocation(3, arrival = true, departure = false)
        val extras = TaskEditorState.forNew(lists, 1L, null, false).copy(
            selectedTagIds = setOf(1L, 2L), newTagNames = listOf("fresh"), reminders = listOf(time), location = place
        ).toExtras()
        assertEquals(setOf(1L, 2L), extras.tagIds)
        assertEquals(listOf("fresh"), extras.newTagNames)
        assertEquals(listOf<org.onekash.kashcal.data.tasks_org.TasksOrgReminder>(time), extras.reminders)
        assertEquals(place, extras.location)
    }

    @Test
    fun `moving the due date of a repeating or started task warns of side effects`() {
        val repeating = edit(task(recurrence = "FREQ=MONTHLY;BYDAY=2TU"))
        assertFalse(repeating.dueChangeHasSideEffects)
        assertTrue(repeating.copy(dueMs = 99_000L).dueChangeHasSideEffects)
        assertTrue(repeating.copy(isAllDay = true).dueChangeHasSideEffects)

        val started = edit(task(hideUntil = TasksOrgDate(1L, false)))
        assertTrue(started.copy(dueMs = 99_000L).dueChangeHasSideEffects)

        // A plain task has none, and neither does a new one.
        assertFalse(edit(task()).copy(dueMs = 99_000L).dueChangeHasSideEffects)
        assertFalse(TaskEditorState.forNew(lists, 1L, 1L, false).copy(dueMs = 2L).dueChangeHasSideEffects)
    }

    @Test
    fun `a rule without a due date is flagged`() {
        val state = TaskEditorState.forNew(lists, 1L, null, false)
            .copy(recurrence = org.onekash.kashcal.domain.tasks.RecurrenceChoice.of("FREQ=DAILY"))
        assertTrue(state.recurrenceNeedsDue)
        assertFalse(state.copy(dueMs = 1L).recurrenceNeedsDue)
    }
}
