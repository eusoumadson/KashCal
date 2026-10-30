package org.onekash.kashcal.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.onekash.kashcal.data.tasks_org.TasksOrgPriority
import org.onekash.kashcal.data.tasks_org.TasksOrgReminder
import org.onekash.kashcal.data.tasks_org.TasksOrgTask
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Pure helpers behind the editor fields and the task card. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class TaskEditorFieldsTest {

    private val saoPaulo = ZoneId.of("America/Sao_Paulo")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    // ---- the new-place form ----

    @Test
    fun `a valid place form becomes a draft`() {
        val draft = parsePlaceDraft(" Home ", " 221B Baker St ", "51.5237", "-0.1585", "150")!!
        assertEquals("Home", draft.name)
        assertEquals("221B Baker St", draft.address)
        assertEquals(51.5237, draft.latitude, 0.0)
        assertEquals(-0.1585, draft.longitude, 0.0)
        assertEquals(150, draft.radius)
    }

    @Test
    fun `a comma decimal mark is accepted`() {
        val draft = parsePlaceDraft("Casa", "", "-23,5505", "-46,6333", "250")!!
        assertEquals(-23.5505, draft.latitude, 0.0)
        assertEquals(-46.6333, draft.longitude, 0.0)
    }

    @Test
    fun `an invalid place form is rejected`() {
        assertNull("blank name", parsePlaceDraft("  ", "", "1", "1", "250"))
        assertNull("latitude out of range", parsePlaceDraft("A", "", "91", "1", "250"))
        assertNull("longitude out of range", parsePlaceDraft("A", "", "1", "181", "250"))
        assertNull("not a number", parsePlaceDraft("A", "", "x", "1", "250"))
        assertNull("empty coordinate", parsePlaceDraft("A", "", "", "1", "250"))
        assertNull("zero radius", parsePlaceDraft("A", "", "1", "1", "0"))
        assertNull("negative radius", parsePlaceDraft("A", "", "1", "1", "-5"))
        assertNull("radius not a number", parsePlaceDraft("A", "", "1", "1", "big"))
    }

    // ---- date helpers ----

    @Test
    fun `the date picker is given the local calendar day, not the UTC one`() {
        // 00:00 on Nov 1 in Tokyo is still Oct 31 in UTC; the picker must open on Nov 1.
        val localMidnight = LocalDateTime.of(2026, 11, 1, 0, 0).atZone(tokyo).toInstant().toEpochMilli()
        val picked = localDayAsUtcMidnight(localMidnight, tokyo)
        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), picked)
    }

    @Test
    fun `picking a day keeps the time of a timed task and snaps an all-day one to local midnight`() {
        val pickedUtcMidnight = LocalDateTime.of(2026, 11, 5, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val existing = LocalDateTime.of(2026, 10, 30, 17, 45).atZone(saoPaulo).toInstant().toEpochMilli()

        val timed = combineDateWithExistingTime(pickedUtcMidnight, existing, allDay = false, zone = saoPaulo)
        assertEquals(LocalDateTime.of(2026, 11, 5, 17, 45).atZone(saoPaulo).toInstant().toEpochMilli(), timed)

        val allDay = combineDateWithExistingTime(pickedUtcMidnight, existing, allDay = true, zone = saoPaulo)
        assertEquals(LocalDateTime.of(2026, 11, 5, 0, 0).atZone(saoPaulo).toInstant().toEpochMilli(), allDay)

        val fresh = combineDateWithExistingTime(pickedUtcMidnight, null, allDay = false, zone = saoPaulo)
        assertEquals(LocalDateTime.of(2026, 11, 5, 0, 0).atZone(saoPaulo).toInstant().toEpochMilli(), fresh)
    }

    @Test
    fun `turning all-day on snaps to local midnight and off leaves the value`() {
        val ms = LocalDateTime.of(2026, 10, 30, 17, 45).atZone(saoPaulo).toInstant().toEpochMilli()
        assertEquals(LocalDateTime.of(2026, 10, 30, 0, 0).atZone(saoPaulo).toInstant().toEpochMilli(), adjustForAllDay(ms, true, saoPaulo))
        assertEquals(ms, adjustForAllDay(ms, false, saoPaulo))
    }

    @Test
    fun `setting the time replaces only the time of day`() {
        val ms = LocalDateTime.of(2026, 10, 30, 8, 0).atZone(saoPaulo).toInstant().toEpochMilli()
        assertEquals(
            LocalDateTime.of(2026, 10, 30, 21, 5).atZone(saoPaulo).toInstant().toEpochMilli(),
            setTimeOfDay(ms, 21, 5, saoPaulo)
        )
    }

    // ---- reminders ----

    @Test
    fun `reminders are the same by type, anchor and timing and not by id`() {
        val due = TasksOrgReminder.Relative.Anchor.DUE
        assertTrue(sameReminder(TasksOrgReminder.Relative(id = 1, anchor = due, offsetMs = -5), TasksOrgReminder.Relative(anchor = due, offsetMs = -5)))
        assertTrue(sameReminder(TasksOrgReminder.Random(periodMs = 60_000), TasksOrgReminder.Random(id = 3, periodMs = 60_000)))
        assertTrue(sameReminder(TasksOrgReminder.AtTime(triggerAtMs = 1), TasksOrgReminder.AtTime(id = 2, triggerAtMs = 1)))
        assertTrue(!sameReminder(TasksOrgReminder.Random(periodMs = 60_000), TasksOrgReminder.Random(periodMs = 120_000)))
        assertTrue(!sameReminder(TasksOrgReminder.Relative(anchor = due, offsetMs = 0), TasksOrgReminder.Relative(anchor = TasksOrgReminder.Relative.Anchor.START, offsetMs = 0)))
        assertTrue(!sameReminder(TasksOrgReminder.AtTime(triggerAtMs = 1), TasksOrgReminder.Random(periodMs = 1)))
    }

    // ---- the task card ----

    @Test
    fun `the card's secondary line lists tags and finished subtasks`() {
        assertEquals("", formatTaskMeta(emptyList(), 0, 0))
        assertEquals("#work #home", formatTaskMeta(listOf("work", "home"), 0, 0))
        assertEquals("\u2937 2/3", formatTaskMeta(emptyList(), 3, 1))
        assertEquals("#work \u00B7 \u2937 0/2", formatTaskMeta(listOf("work"), 2, 2))
    }

    @Test
    fun `only a prioritized task gets a dot`() {
        assertNull(priorityColor(TasksOrgPriority.NONE))
        assertNotNull(priorityColor(TasksOrgPriority.HIGH))
        assertNotNull(priorityColor(TasksOrgPriority.MEDIUM))
        assertNotNull(priorityColor(TasksOrgPriority.LOW))
        assertTrue(priorityColor(TasksOrgPriority.HIGH) != priorityColor(TasksOrgPriority.LOW))
    }

    @Test
    fun `an overdue task shows its own date and any other task its calendar date`() {
        // All-day task due Oct 25 (local midnight), pinned to Oct 30.
        val due = LocalDateTime.of(2026, 10, 25, 0, 0).atZone(saoPaulo).toInstant().toEpochMilli()
        val oct25Utc = LocalDateTime.of(2026, 10, 25, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val oct30Utc = LocalDateTime.of(2026, 10, 30, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val pinned = TasksOrgTask(
            id = 1, title = "Late", notes = "", dueTs = due, isAllDay = true,
            startTs = oct30Utc, endTs = oct30Utc + 86_399_999L, startDay = 20261030, endDay = 20261030,
            completedAt = null, recurrence = null, listId = 1, listTitle = "", listColor = 0, isReadOnly = false,
            dueAllDay = true, overdueSince = due
        )
        assertEquals(oct25Utc, pinned.dueCalendarTs(saoPaulo))

        val normal = pinned.copy(overdueSince = null, startTs = oct25Utc)
        assertEquals(oct25Utc, normal.dueCalendarTs(saoPaulo))
    }
}
