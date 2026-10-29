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
import org.onekash.kashcal.data.tasks_org.TasksOrgManager
import org.onekash.kashcal.data.tasks_org.TasksOrgRepository
import org.onekash.kashcal.data.tasks_org.TasksOrgSchedule
import org.onekash.kashcal.data.tasks_org.TasksOrgTask

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

    private fun task(completedAt: Long? = null, recurrence: String? = null) = TasksOrgTask(
        id = 5L, title = "Task", notes = "", dueTs = 1000L, isAllDay = false, startTs = 1000L, endTs = 1000L,
        startDay = 20261029, endDay = 20261029, completedAt = completedAt, recurrence = recurrence,
        listId = 1L, listTitle = "Inbox", listColor = 0, isReadOnly = false
    )
}
