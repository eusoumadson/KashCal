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

/** The fork's later mapper pieces: every reminder type, the richer task row, places and accounts. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class TasksOrgV0MapperFeaturesTest {

    private val saoPaulo = ZoneId.of("America/Sao_Paulo")
    private val mapper = TasksOrgV0Mapper().apply { zone = saoPaulo }

    private fun cursor(columns: Array<String>, vararg row: Any): android.database.Cursor =
        android.database.MatrixCursor(columns).apply { addRow(row) }.also { it.moveToFirst() }

    private val reminderColumns = arrayOf(
        TasksOrgContract.ID, TasksOrgContract.Reminders.TYPE, TasksOrgContract.Reminders.TRIGGER_AT,
        TasksOrgContract.Reminders.OFFSET_MS, TasksOrgContract.Reminders.PLACE_ID
    )

    @Test
    fun `every reminder type is read`() {
        assertEquals(
            TasksOrgReminder.Random(id = 4, periodMs = 86_400_000L),
            mapper.readReminder(cursor(reminderColumns, 4L, "random", 0L, 86_400_000L, 0L))
        )
        assertEquals(
            TasksOrgReminder.Snooze(id = 5, triggerAtMs = 777L),
            mapper.readReminder(cursor(reminderColumns, 5L, "snooze", 777L, 0L, 0L))
        )
        assertEquals(
            TasksOrgReminder.Location(6, 9L, TasksOrgReminder.Location.Kind.ARRIVAL),
            mapper.readReminder(cursor(reminderColumns, 6L, "location_arrival", 0L, 0L, 9L))
        )
        assertEquals(
            TasksOrgReminder.Location(7, 9L, TasksOrgReminder.Location.Kind.DEPARTURE),
            mapper.readReminder(cursor(reminderColumns, 7L, "location_departure", 0L, 0L, 9L))
        )
    }

    @Test
    fun `a reminder type from a later Tasks_org is skipped`() {
        assertEquals(null, mapper.readReminder(cursor(reminderColumns, 8L, "something_new", 0L, 0L, 0L)))
    }

    @Test
    fun `each new reminder type sends only the columns that apply to it`() {
        val random = mapper.reminderInsertValues(1L, TasksOrgReminder.Random(periodMs = 120_000L))
        assertEquals("random", random.getAsString(TasksOrgContract.Reminders.TYPE))
        assertEquals(120_000L, random.getAsLong(TasksOrgContract.Reminders.OFFSET_MS))
        assertFalse(random.containsKey(TasksOrgContract.Reminders.TRIGGER_AT))
        assertFalse(random.containsKey(TasksOrgContract.Reminders.PLACE_ID))

        val snooze = mapper.reminderInsertValues(1L, TasksOrgReminder.Snooze(triggerAtMs = 55L))
        assertEquals("snooze", snooze.getAsString(TasksOrgContract.Reminders.TYPE))
        assertEquals(55L, snooze.getAsLong(TasksOrgContract.Reminders.TRIGGER_AT))
        assertFalse(snooze.containsKey(TasksOrgContract.Reminders.OFFSET_MS))

        val arrival = mapper.reminderInsertValues(1L, TasksOrgReminder.Location(placeId = 9L, kind = TasksOrgReminder.Location.Kind.ARRIVAL))
        assertEquals("location_arrival", arrival.getAsString(TasksOrgContract.Reminders.TYPE))
        assertEquals(9L, arrival.getAsLong(TasksOrgContract.Reminders.PLACE_ID))
        assertFalse(arrival.containsKey(TasksOrgContract.Reminders.TRIGGER_AT))
        assertFalse(arrival.containsKey(TasksOrgContract.Reminders.OFFSET_MS))
    }

    @Test
    fun `a task row carries tags, subtasks, place, start and repeat basis`() {
        val columns = arrayOf(
            TasksOrgContract.ID, TasksOrgContract.Tasks.TITLE, TasksOrgContract.Tasks.DUE_DATE, TasksOrgContract.Tasks.DUE_ALL_DAY,
            TasksOrgContract.Tasks.START_DATE, TasksOrgContract.Tasks.START_ALL_DAY, TasksOrgContract.Tasks.REPEAT_FROM,
            TasksOrgContract.Tasks.TAG_IDS, TasksOrgContract.Tasks.CHILD_COUNT, TasksOrgContract.Tasks.UNCOMPLETED_CHILD_COUNT,
            TasksOrgContract.Tasks.PLACE_ID, TasksOrgContract.Tasks.LIST_ID, TasksOrgContract.Tasks.PRIORITY
        )
        val tags = mapOf(1L to TasksOrgTag(1, "a", 0), 3L to TasksOrgTag(3, "c", 0))
        val task = mapper.readTask(
            cursor(columns, 7L, "T", 1_000L, 0, 500L, 1, "completion_date", "1,2,3", 4, 2, 9L, 1L, "medium"),
            mapOf(1L to TasksOrgList(1, "L", 0, false)), tags
        )!!

        // Tag 2 is unknown (deleted or not loaded) and is skipped, order is kept.
        assertEquals(listOf("a", "c"), task.tags)
        assertEquals(4, task.childCount)
        assertEquals(2, task.uncompletedChildCount)
        assertEquals(9L, task.placeId)
        assertEquals(TasksOrgDate(500L, allDay = true), task.hideUntil)
        assertEquals(TasksOrgRepeatFrom.COMPLETION_DATE, task.repeatFrom)
        assertEquals(TasksOrgPriority.MEDIUM, task.priority)
    }

    @Test
    fun `a task without a start date has none`() {
        val columns = arrayOf(TasksOrgContract.ID, TasksOrgContract.Tasks.DUE_DATE)
        val task = mapper.readTask(cursor(columns, 1L, 1_000L), emptyMap())!!
        assertEquals(null, task.hideUntil)
        assertEquals(TasksOrgRepeatFrom.DUE_DATE, task.repeatFrom)
        assertEquals(emptyList<String>(), task.tags)
    }

    @Test
    fun `a place and an account are read`() {
        val place = mapper.readPlace(
            cursor(
                arrayOf(TasksOrgContract.ID, TasksOrgContract.Places.NAME, TasksOrgContract.Places.DISPLAY_NAME,
                    TasksOrgContract.Places.ADDRESS, TasksOrgContract.Places.LATITUDE, TasksOrgContract.Places.LONGITUDE,
                    TasksOrgContract.Places.RADIUS),
                3L, "Home", "Home", "221B Baker St", 51.5, -0.1, 150
            )
        )
        assertEquals(TasksOrgPlace(3, "Home", "Home", "221B Baker St", 51.5, -0.1, 150), place)

        val account = mapper.readAccount(
            cursor(
                arrayOf(TasksOrgContract.ID, TasksOrgContract.Accounts.NAME, TasksOrgContract.Accounts.TYPE,
                    TasksOrgContract.Accounts.ERROR, TasksOrgContract.Accounts.REPEATS_ON_SERVER),
                2L, "me", "caldav", "unauthorized", 1
            )
        )
        assertEquals(TasksOrgAccount(2, "me", "caldav", "unauthorized", true), account)
        assertTrue(account.needsAttention)
    }

    @Test
    fun `list, tag and place writes send only what is given`() {
        val list = mapper.listInsertValues(4L, "Groceries", 7)
        assertEquals(4L, list.getAsLong(TasksOrgContract.Lists.ACCOUNT_ID))

        assertEquals(setOf(TasksOrgContract.Lists.TITLE), mapper.listUpdateValues("x", null).keySet())
        assertEquals(setOf(TasksOrgContract.Lists.COLOR), mapper.listUpdateValues(null, 3).keySet())
        assertEquals(setOf(TasksOrgContract.Tags.NAME), mapper.tagUpdateValues("x", null).keySet())
        assertEquals(setOf(TasksOrgContract.Places.RADIUS), mapper.placeUpdateValues(null, null, 90).keySet())
        // Coordinates are fixed after creation, so an update never sends them.
        assertFalse(mapper.placeUpdateValues("n", "a", 1).containsKey(TasksOrgContract.Places.LATITUDE))
    }

    @Test
    fun `a start date patch writes its flag with it, and clearing zeroes both`() {
        val set = mapper.updateValues(TasksOrgTaskPatch(start = TasksOrgTaskPatch.Field.Value(TasksOrgDate(
            LocalDateTime.of(2026, 11, 1, 15, 0).atZone(saoPaulo).toInstant().toEpochMilli(), allDay = true))))
        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0).atZone(saoPaulo).toInstant().toEpochMilli(), set.getAsLong(TasksOrgContract.Tasks.START_DATE))
        assertEquals(1, set.getAsInteger(TasksOrgContract.Tasks.START_ALL_DAY))

        val cleared = mapper.updateValues(TasksOrgTaskPatch(start = TasksOrgTaskPatch.Field.Value(null)))
        assertEquals(0L, cleared.getAsLong(TasksOrgContract.Tasks.START_DATE))
        assertEquals(0, cleared.getAsInteger(TasksOrgContract.Tasks.START_ALL_DAY))
    }
}
