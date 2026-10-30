package org.onekash.kashcal.data.tasks_org

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Tests [TasksOrgV0Mapper], the one translation layer to the unstable `v0` API. Locks down the
 * two parts most likely to break silently: the all-day date conversion (report §4.1) and the
 * patch semantics of [updateValues] (report §4.3).
 *
 * Runs under Robolectric so `ContentValues` is the real Android implementation, not a stub.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class TasksOrgV0MapperTest {

    private val saoPaulo = ZoneId.of("America/Sao_Paulo")
    private val mapper = TasksOrgV0Mapper().apply { zone = saoPaulo }

    private fun momentOn(year: Int, month: Int, day: Int, hour: Int) =
        LocalDateTime.of(year, month, day, hour, 0).atZone(saoPaulo).toInstant().toEpochMilli()

    @Test
    fun `insert writes an all-day due date at local midnight with the flag`() {
        val expected = momentOn(2026, 11, 1, 0)
        val values = mapper.insertValues(
            TasksOrgTaskDraft(title = "Pay rent", due = TasksOrgDate(momentOn(2026, 11, 1, 15), allDay = true))
        )
        assertEquals(expected, values.getAsLong(TasksOrgContract.Tasks.DUE_DATE))
        assertEquals(1, values.getAsInteger(TasksOrgContract.Tasks.DUE_ALL_DAY))
    }

    @Test
    fun `insert writes a timed due date as-is with the flag off`() {
        val due = momentOn(2026, 11, 1, 15)
        val values = mapper.insertValues(TasksOrgTaskDraft(title = "Call", due = TasksOrgDate(due, allDay = false)))
        assertEquals(due, values.getAsLong(TasksOrgContract.Tasks.DUE_DATE))
        assertEquals(0, values.getAsInteger(TasksOrgContract.Tasks.DUE_ALL_DAY))
    }

    @Test
    fun `insert without recurrence omits the rule and repeat_from`() {
        val values = mapper.insertValues(TasksOrgTaskDraft(title = "One-off"))
        assertFalse(values.containsKey(TasksOrgContract.Tasks.RECURRENCE))
        assertFalse(values.containsKey(TasksOrgContract.Tasks.REPEAT_FROM))
    }

    @Test
    fun `update only puts the set fields`() {
        val values = mapper.updateValues(
            TasksOrgTaskPatch(
                title = TasksOrgTaskPatch.Field.Value("New"),
                priority = TasksOrgTaskPatch.Field.Value(TasksOrgPriority.LOW)
            )
        )
        assertEquals("New", values.getAsString(TasksOrgContract.Tasks.TITLE))
        assertEquals("low", values.getAsString(TasksOrgContract.Tasks.PRIORITY))
        assertFalse(values.containsKey(TasksOrgContract.Tasks.NOTES))
        assertFalse(values.containsKey(TasksOrgContract.Tasks.DUE_DATE))
        assertFalse(values.containsKey(TasksOrgContract.Tasks.RECURRENCE))
    }

    @Test
    fun `update clears recurrence with an empty string when set to null`() {
        val values = mapper.updateValues(TasksOrgTaskPatch(recurrence = TasksOrgTaskPatch.Field.Value(null)))
        assertEquals("", values.getAsString(TasksOrgContract.Tasks.RECURRENCE))
    }

    @Test
    fun `update clearing a due date writes 0 and flag 0`() {
        val values = mapper.updateValues(TasksOrgTaskPatch(due = TasksOrgTaskPatch.Field.Value(null)))
        assertEquals(0L, values.getAsLong(TasksOrgContract.Tasks.DUE_DATE))
        assertEquals(0, values.getAsInteger(TasksOrgContract.Tasks.DUE_ALL_DAY))
    }

    @Test
    fun `update switching to all-day always sends the flag alongside the date`() {
        val values = mapper.updateValues(
            TasksOrgTaskPatch(due = TasksOrgTaskPatch.Field.Value(TasksOrgDate(momentOn(2026, 11, 2, 9), allDay = true)))
        )
        assertTrue(values.containsKey(TasksOrgContract.Tasks.DUE_DATE))
        assertEquals(1, values.getAsInteger(TasksOrgContract.Tasks.DUE_ALL_DAY))
    }

    @Test
    fun `an empty patch produces empty values`() {
        assertEquals(0, mapper.updateValues(TasksOrgTaskPatch()).size())
        assertTrue(TasksOrgTaskPatch().isEmpty)
    }

    @Test
    fun `completion values complete and reopen`() {
        assertEquals(1234L, mapper.completionValues(1234L).getAsLong(TasksOrgContract.Tasks.COMPLETED_AT))
        assertEquals(0L, mapper.completionValues(null).getAsLong(TasksOrgContract.Tasks.COMPLETED_AT))
    }

    @Test
    fun `insert writes the priority`() {
        val values = mapper.insertValues(TasksOrgTaskDraft(title = "x", priority = TasksOrgPriority.MEDIUM))
        assertEquals("medium", values.getAsString(TasksOrgContract.Tasks.PRIORITY))
    }

    @Test
    fun `a relative reminder sends offset but not trigger_at`() {
        val values = mapper.reminderInsertValues(
            7L,
            TasksOrgReminder.Relative(anchor = TasksOrgReminder.Relative.Anchor.DUE, offsetMs = -600_000L)
        )
        assertEquals(7L, values.getAsLong(TasksOrgContract.Reminders.TASK_ID))
        assertEquals("relative_due", values.getAsString(TasksOrgContract.Reminders.TYPE))
        assertEquals(-600_000L, values.getAsLong(TasksOrgContract.Reminders.OFFSET_MS))
        assertFalse(values.containsKey(TasksOrgContract.Reminders.TRIGGER_AT))
        assertFalse(values.containsKey(TasksOrgContract.Reminders.REPEAT_COUNT))
    }

    @Test
    fun `a repeating relative reminder adds repeat_count and interval`() {
        val values = mapper.reminderInsertValues(
            7L,
            TasksOrgReminder.Relative(
                anchor = TasksOrgReminder.Relative.Anchor.START, offsetMs = 0L,
                repeatCount = 3, intervalMs = 60_000L
            )
        )
        assertEquals("relative_start", values.getAsString(TasksOrgContract.Reminders.TYPE))
        assertEquals(3, values.getAsInteger(TasksOrgContract.Reminders.REPEAT_COUNT))
        assertEquals(60_000L, values.getAsLong(TasksOrgContract.Reminders.INTERVAL_MS))
    }

    @Test
    fun `an at-time reminder sends trigger_at but not offset`() {
        val values = mapper.reminderInsertValues(7L, TasksOrgReminder.AtTime(triggerAtMs = 1234L))
        assertEquals("date_time", values.getAsString(TasksOrgContract.Reminders.TYPE))
        assertEquals(1234L, values.getAsLong(TasksOrgContract.Reminders.TRIGGER_AT))
        assertFalse(values.containsKey(TasksOrgContract.Reminders.OFFSET_MS))
    }
}
