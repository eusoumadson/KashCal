package org.onekash.kashcal.domain.tasks

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onekash.kashcal.data.preferences.KashCalDataStore
import org.onekash.kashcal.data.tasks_org.TasksOrgList
import org.onekash.kashcal.data.tasks_org.TasksOrgManager
import org.onekash.kashcal.data.tasks_org.TasksOrgReminder
import org.onekash.kashcal.data.tasks_org.TasksOrgRepository
import org.onekash.kashcal.data.tasks_org.TasksOrgSchedule
import org.onekash.kashcal.data.tasks_org.TasksOrgTask
import org.onekash.kashcal.data.tasks_org.TasksOrgTaskDraft
import org.onekash.kashcal.data.tasks_org.TasksOrgTaskPatch
import org.onekash.kashcal.data.tasks_org.TasksOrgWriteResult

/** Tests [TasksOrgCoordinator]: the switch, list visibility, completion and its undo. */
class TasksOrgCoordinatorTest {

    private val repository: TasksOrgRepository = mockk()
    private val manager: TasksOrgManager = mockk(relaxed = true)
    private val dataStore: KashCalDataStore = mockk(relaxed = true)
    private val coordinator = TasksOrgCoordinator(repository, manager, dataStore)

    @Test
    fun `switching on stores it and starts observing, switching off stops`() = runTest {
        coordinator.setEnabled(true)
        coVerify { dataStore.setTasksOrgEnabled(true) }
        verify { manager.onEnabled() }

        coordinator.setEnabled(false)
        coVerify { dataStore.setTasksOrgEnabled(false) }
        verify { manager.onDisabled() }
    }

    @Test
    fun `hiding and showing a list updates the hidden set and re-queries`() = runTest {
        coEvery { dataStore.getHiddenTasksOrgListIds() } returns setOf(1L)

        coordinator.setListShown(2L, shown = false)
        coVerify { dataStore.setHiddenTasksOrgListIds(setOf(1L, 2L)) }

        coordinator.setListShown(1L, shown = true)
        coVerify { dataStore.setHiddenTasksOrgListIds(emptySet()) }
        verify(exactly = 2) { manager.notifyChanged() }
    }

    @Test
    fun `completing writes now, reopening writes null, and both re-query`() = runTest {
        coEvery { repository.setCompleted(any(), any()) } returns true
        val before = System.currentTimeMillis()

        val completed = coordinator.setCompleted(task(), completed = true)
        coVerify { repository.setCompleted(5L, match { it != null && it >= before }) }
        // Undo reopens it.
        assertEquals(TaskCompletionResult.Written(TaskCompletionUndo.SetCompletedAt(5L, null)), completed)

        val reopened = coordinator.setCompleted(task(completedAt = 1234L), completed = false)
        coVerify { repository.setCompleted(5L, null) }
        // Undo completes it again at its old time.
        assertEquals(TaskCompletionResult.Written(TaskCompletionUndo.SetCompletedAt(5L, 1234L)), reopened)
        verify(exactly = 2) { manager.notifyChanged() }
    }

    @Test
    fun `a refused write is reported`() = runTest {
        every { manager.notifyChanged() } returns Unit
        coEvery { repository.setCompleted(any(), any()) } returns false

        assertEquals(TaskCompletionResult.Refused, coordinator.setCompleted(task(), completed = true))
    }

    @Test
    fun `completing a repeating task keeps its schedule, which undo writes back before reopening`() = runTest {
        val schedule = TasksOrgSchedule(
            dueDate = 1000L, dueAllDay = false, startDate = 0L, startAllDay = false, recurrence = "FREQ=DAILY;COUNT=3"
        )
        coEvery { repository.getSchedule(5L) } returns schedule
        coEvery { repository.setCompleted(any(), any()) } returns true
        coEvery { repository.restoreSchedule(any(), any()) } returns true

        val result = coordinator.setCompleted(task(recurrence = "FREQ=DAILY;COUNT=3"), completed = true)
        val undo = (result as TaskCompletionResult.Written).undo
        assertEquals(TaskCompletionUndo.RestoreSchedule(5L, schedule), undo)

        assertTrue(coordinator.undo(undo!!))
        coVerifyOrder {
            repository.restoreSchedule(5L, schedule)
            repository.setCompleted(5L, null)
        }
    }

    @Test
    fun `a repeating task whose schedule can't be read completes without undo`() = runTest {
        coEvery { repository.getSchedule(5L) } returns null
        coEvery { repository.setCompleted(any(), any()) } returns true

        assertEquals(
            TaskCompletionResult.Written(null),
            coordinator.setCompleted(task(recurrence = "FREQ=WEEKLY"), completed = true)
        )
    }

    @Test
    fun `a refused undo is reported`() = runTest {
        coEvery { repository.setCompleted(any(), any()) } returns false

        assertFalse(coordinator.undo(TaskCompletionUndo.SetCompletedAt(5L, null)))
    }

    @Test
    fun `getWritableLists drops read-only lists`() = runTest {
        coEvery { repository.getLists() } returns listOf(
            TasksOrgList(id = 1L, title = "Inbox", color = 0, isReadOnly = false),
            TasksOrgList(id = 2L, title = "Shared", color = 0, isReadOnly = true)
        )
        assertEquals(listOf(1L), coordinator.getWritableLists().map { it.id })
    }

    @Test
    fun `deleteTask delegates and re-queries`() = runTest {
        coEvery { repository.deleteTask(5L) } returns TasksOrgWriteResult.Ok(5L)

        assertEquals(TasksOrgWriteResult.Ok(5L), coordinator.deleteTask(5L))
        coVerify { repository.deleteTask(5L) }
        verify { manager.notifyChanged() }
    }

    private fun task(completedAt: Long? = null, recurrence: String? = null) = TasksOrgTask(
        id = 5L, title = "Task", notes = "", dueTs = 1000L, isAllDay = false, startTs = 1000L, endTs = 1000L,
        startDay = 20261029, endDay = 20261029, completedAt = completedAt, recurrence = recurrence,
        listId = 1L, listTitle = "Inbox", listColor = 0, isReadOnly = false
    )
}

/** The coordinator's atomic save and the read-back that tells whether Tasks.org adjusted a rule. */
class TasksOrgCoordinatorSaveTest {

    private val repository: TasksOrgRepository = mockk()
    private val manager: TasksOrgManager = mockk(relaxed = true)
    private val dataStore: KashCalDataStore = mockk(relaxed = true)
    private val coordinator = TasksOrgCoordinator(repository, manager, dataStore)

    private fun task(recurrence: String? = null) = TasksOrgTask(
        id = 5L, title = "T", notes = "", dueTs = 1000L, isAllDay = false,
        startTs = 1000L, endTs = 1000L, startDay = 20261029, endDay = 20261029,
        completedAt = null, recurrence = recurrence, listId = 1L, listTitle = "", listColor = 0, isReadOnly = false
    )

    private val moveDue = TasksOrgTaskPatch(due = TasksOrgTaskPatch.Field.Value(org.onekash.kashcal.data.tasks_org.TasksOrgDate(9_000L, false)))
    private val extras = org.onekash.kashcal.data.tasks_org.TasksOrgTaskExtras()

    @Test
    fun `create saves in one batch and re-queries`() = runTest {
        coEvery { repository.createTaskWithExtras(any(), any()) } returns TasksOrgWriteResult.Ok(7L)

        val result = coordinator.createTask(TasksOrgTaskDraft(title = "T"), extras)

        assertEquals(TasksOrgWriteResult.Ok(7L), result)
        coVerify(exactly = 1) { repository.createTaskWithExtras(any(), extras) }
        verify { manager.notifyChanged() }
    }

    @Test
    fun `an edit that moves the date of a repeating task reports a rule Tasks_org changed`() = runTest {
        coEvery { repository.updateTaskWithExtras(5L, moveDue, extras) } returns TasksOrgWriteResult.Ok(5L)
        // Read back: the monthly 2nd-Tuesday rule was re-anchored.
        coEvery { repository.getTask(5L) } returns task(recurrence = "FREQ=MONTHLY;BYDAY=3WE")

        val outcome = coordinator.updateTask(task(recurrence = "FREQ=MONTHLY;BYDAY=2TU"), moveDue, extras)

        assertEquals(TasksOrgWriteResult.Ok(5L), outcome.result)
        assertTrue(outcome.recurrenceAdjusted)
    }

    @Test
    fun `an edit that leaves the rule as it was reports no adjustment`() = runTest {
        coEvery { repository.updateTaskWithExtras(5L, moveDue, extras) } returns TasksOrgWriteResult.Ok(5L)
        // The stored form may drop the RRULE prefix; that is not a change.
        coEvery { repository.getTask(5L) } returns task(recurrence = "FREQ=DAILY")

        val outcome = coordinator.updateTask(task(recurrence = "RRULE:FREQ=DAILY"), moveDue, extras)

        assertFalse(outcome.recurrenceAdjusted)
    }

    @Test
    fun `no read-back is made when the date did not move, the task doesn't repeat or the rule was edited`() = runTest {
        coEvery { repository.updateTaskWithExtras(any(), any(), any()) } returns TasksOrgWriteResult.Ok(5L)

        val titleOnly = TasksOrgTaskPatch(title = TasksOrgTaskPatch.Field.Value("x"))
        assertFalse(coordinator.updateTask(task(recurrence = "FREQ=DAILY"), titleOnly, extras).recurrenceAdjusted)
        assertFalse(coordinator.updateTask(task(recurrence = null), moveDue, extras).recurrenceAdjusted)
        val ruleEdited = moveDue.copy(recurrence = TasksOrgTaskPatch.Field.Value("FREQ=WEEKLY"))
        assertFalse(coordinator.updateTask(task(recurrence = "FREQ=DAILY"), ruleEdited, extras).recurrenceAdjusted)

        coVerify(exactly = 0) { repository.getTask(any()) }
    }

    @Test
    fun `a read-only target is surfaced to the caller`() = runTest {
        coEvery { repository.updateTaskWithExtras(any(), any(), any()) } returns TasksOrgWriteResult.ReadOnly

        val outcome = coordinator.updateTask(task(), moveDue, extras)

        assertEquals(TasksOrgWriteResult.ReadOnly, outcome.result)
        assertFalse(outcome.recurrenceAdjusted)
    }

    @Test
    fun `a refused edit is not read back`() = runTest {
        coEvery { repository.updateTaskWithExtras(any(), any(), any()) } returns TasksOrgWriteResult.Refused

        val outcome = coordinator.updateTask(task(recurrence = "FREQ=DAILY"), moveDue, extras)

        assertEquals(TasksOrgWriteResult.Refused, outcome.result)
        assertFalse(outcome.recurrenceAdjusted)
        coVerify(exactly = 0) { repository.getTask(any()) }
    }

    @Test
    fun `list, tag and place changes re-query the views`() = runTest {
        coEvery { repository.updateList(any(), any(), any()) } returns TasksOrgWriteResult.Ok(1L)
        coEvery { repository.deleteList(any()) } returns TasksOrgWriteResult.Ok(1L)
        coEvery { repository.updateTag(any(), any(), any()) } returns TasksOrgWriteResult.Ok(1L)
        coEvery { repository.deleteTag(any()) } returns TasksOrgWriteResult.Ok(1L)
        coEvery { repository.deletePlace(any()) } returns TasksOrgWriteResult.Ok(1L)

        coordinator.updateList(1L, "y")
        coordinator.deleteList(1L)
        coordinator.updateTag(1L, "y")
        coordinator.deleteTag(1L)
        coordinator.deletePlace(1L)

        verify(exactly = 5) { manager.notifyChanged() }
    }
}
