package org.onekash.kashcal.domain.tasks

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onekash.kashcal.data.preferences.KashCalDataStore
import org.onekash.kashcal.data.tasks_org.TasksOrgManager
import org.onekash.kashcal.data.tasks_org.TasksOrgRepository

/** Tests [TasksOrgCoordinator]: the switch, list visibility and completion. */
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

        assertTrue(coordinator.setCompleted(5L, completed = true))
        coVerify { repository.setCompleted(5L, match { it != null && it >= before }) }

        assertTrue(coordinator.setCompleted(5L, completed = false))
        coVerify { repository.setCompleted(5L, null) }
        verify(exactly = 2) { manager.notifyChanged() }
    }

    @Test
    fun `a refused write is reported`() = runTest {
        every { manager.notifyChanged() } returns Unit
        coEvery { repository.setCompleted(any(), any()) } returns false

        assertFalse(coordinator.setCompleted(5L, completed = true))
    }
}
