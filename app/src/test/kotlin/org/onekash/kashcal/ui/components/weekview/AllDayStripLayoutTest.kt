package org.onekash.kashcal.ui.components.weekview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onekash.kashcal.data.tasks_org.TasksOrgTask
import org.onekash.kashcal.domain.model.DisplayEvent
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Unit tests for the all-day strip's spanning-bar layout, [computeAllDaySpans] and
 * [computeAllDayStripRender]. Both are pure functions over dayCodes and [DisplayEvent]s, so they
 * run without Robolectric.
 *
 * The tests cover the arithmetic that is easy to get subtly wrong: column mapping, flush edges
 * when an event runs off the visible window, greedy lane packing, dedup by stableKey, the
 * maxLanes cap, month-boundary (non-contiguous) dayCodes, multi-day timed events as spans, and
 * the overflow counts.
 */
class AllDayStripLayoutTest {

    private val monday = LocalDate.of(2026, 3, 2)
    // A Mon–Sun visible window of real, consecutive dayCodes.
    private val week: List<Int> = (0..6).map { i ->
        val d = monday.plusDays(i.toLong())
        d.year * 10000 + d.monthValue * 100 + d.dayOfMonth
    }

    private fun dayCodeOf(date: LocalDate) =
        date.year * 10000 + date.monthValue * 100 + date.dayOfMonth

    // ==================== computeAllDaySpans ====================

    @Test
    fun `empty visible window yields empty layout`() {
        val layout = computeAllDaySpans(
            visibleDayCodes = emptyList(),
            allDayEvents = listOf(multiDayDisplayEvent(1, "x", monday, monday.plusDays(2))),
            maxLanes = 3
        )
        assertTrue(layout.lanes.isEmpty())
        assertTrue(layout.placedEventKeys.isEmpty())
    }

    @Test
    fun `single-day events are not treated as spans`() {
        val layout = computeAllDaySpans(
            visibleDayCodes = week,
            allDayEvents = listOf(allDayDisplayEvent(1, "single", monday)),
            maxLanes = 3
        )
        assertTrue("single-day events must not become spanning bars", layout.lanes.isEmpty())
    }

    @Test
    fun `span fully inside window maps to the correct columns with no flush`() {
        // Tue -> Thu  == columns 1..3
        val e = multiDayDisplayEvent(1, "trip", monday.plusDays(1), monday.plusDays(3))
        val layout = computeAllDaySpans(week, listOf(e), maxLanes = 3)
        assertEquals(1, layout.lanes.size)
        val span = layout.lanes[0].single()
        assertEquals(1, span.startCol)
        assertEquals(3, span.endCol)
        assertFalse(span.leftFlush)
        assertFalse(span.rightFlush)
    }

    @Test
    fun `span starting before the window is left-flush and clamps to column 0`() {
        // Previous Sat -> Tue: starts two days before the window.
        val e = multiDayDisplayEvent(1, "ongoing", monday.minusDays(2), monday.plusDays(1))
        val layout = computeAllDaySpans(week, listOf(e), maxLanes = 3)
        val span = layout.lanes[0].single()
        assertTrue(span.leftFlush)
        assertEquals(0, span.startCol)
        assertEquals(1, span.endCol)
        assertFalse(span.rightFlush)
    }

    @Test
    fun `span ending after the window is right-flush and clamps to the last column`() {
        // Fri -> next Tue: ends after the window.
        val e = multiDayDisplayEvent(1, "ongoing", monday.plusDays(4), monday.plusDays(9))
        val layout = computeAllDaySpans(week, listOf(e), maxLanes = 3)
        val span = layout.lanes[0].single()
        assertEquals(4, span.startCol)
        assertEquals(6, span.endCol)
        assertTrue(span.rightFlush)
        assertFalse(span.leftFlush)
    }

    @Test
    fun `span entirely outside the window is excluded`() {
        val before = multiDayDisplayEvent(1, "past", monday.minusDays(10), monday.minusDays(8))
        val after = multiDayDisplayEvent(2, "future", monday.plusDays(8), monday.plusDays(10))
        val layout = computeAllDaySpans(week, listOf(before, after), maxLanes = 3)
        assertTrue(layout.lanes.isEmpty())
    }

    @Test
    fun `non-overlapping spans pack into one lane`() {
        val a = multiDayDisplayEvent(1, "a", monday, monday.plusDays(1))            // cols 0..1
        val b = multiDayDisplayEvent(2, "b", monday.plusDays(3), monday.plusDays(4)) // cols 3..4
        val layout = computeAllDaySpans(week, listOf(a, b), maxLanes = 3)
        assertEquals("disjoint spans share a lane", 1, layout.lanes.size)
        assertEquals(2, layout.lanes[0].size)
    }

    @Test
    fun `overlapping spans take separate lanes`() {
        // a covers cols 0..3 and b cols 2..4, overlapping a.
        val a = multiDayDisplayEvent(1, "a", monday, monday.plusDays(3))
        val b = multiDayDisplayEvent(2, "b", monday.plusDays(2), monday.plusDays(4))
        val layout = computeAllDaySpans(week, listOf(a, b), maxLanes = 3)
        assertEquals(2, layout.lanes.size)
    }

    @Test
    fun `spans beyond maxLanes are left unplaced`() {
        // Three mutually overlapping spans, but only 2 lanes available.
        val a = multiDayDisplayEvent(1, "a", monday, monday.plusDays(4))
        val b = multiDayDisplayEvent(2, "b", monday, monday.plusDays(4))
        val c = multiDayDisplayEvent(3, "c", monday, monday.plusDays(4))
        val layout = computeAllDaySpans(week, listOf(a, b, c), maxLanes = 2)
        assertEquals(2, layout.lanes.size)
        assertEquals(2, layout.placedEventKeys.size)
        // Exactly one of the three is unplaced.
        val placed = layout.placedEventKeys
        assertEquals(1, listOf(a, b, c).count { it.stableKey !in placed })
    }

    @Test
    fun `duplicate events are collapsed by stable key`() {
        val e = multiDayDisplayEvent(1, "dup", monday, monday.plusDays(2))
        val layout = computeAllDaySpans(week, listOf(e, e), maxLanes = 3)
        assertEquals(1, layout.lanes.flatten().size)
    }

    @Test
    fun `span across a month boundary maps columns correctly despite non-contiguous dayCodes`() {
        // Jan 30 -> Feb 2, 2026. dayCodes: 20260130, 20260131, 20260201, 20260202.
        // endDay - startDay is 72, not 3, so code that assumed contiguous dayCodes would
        // mis-map the columns.
        val janStart = LocalDate.of(2026, 1, 30)
        val visible = (0..4).map { dayCodeOf(janStart.plusDays(it.toLong())) } // Jan30..Feb3
        val e = multiDayDisplayEvent(1, "cross-month", janStart, LocalDate.of(2026, 2, 2))
        val layout = computeAllDaySpans(visible, listOf(e), maxLanes = 3)
        val span = layout.lanes[0].single()
        assertEquals(0, span.startCol)
        assertEquals(3, span.endCol) // Feb 2 is the 4th visible day -> index 3
    }

    // ==================== computeAllDayStripRender ====================

    @Test
    fun `zero columns or zero rows renders nothing`() {
        assertTrue(
            computeAllDayStripRender(emptyList(), listOf(allDayDisplayEvent(1, "x", monday)), maxRows = 3)
                .slots.isEmpty()
        )
        assertTrue(
            computeAllDayStripRender(week, listOf(allDayDisplayEvent(1, "x", monday)), maxRows = 0)
                .slots.isEmpty()
        )
    }

    @Test
    fun `single-day event lands in its own column as a cell`() {
        val render = computeAllDayStripRender(
            week,
            listOf(allDayDisplayEvent(1, "solo", monday.plusDays(2))), // col 2
            maxRows = 3
        )
        val slot = render.slots[0][2]
        assertTrue(slot is AllDaySlot.CellEvent)
        assertEquals(1L, (slot as AllDaySlot.CellEvent).event.let { (it as DisplayEvent.Room).event.id })
        // Other columns in row 0 stay empty.
        assertTrue(render.slots[0][0] is AllDaySlot.Empty)
    }

    @Test
    fun `multi-day span occupies lane row across every covered column`() {
        val e = multiDayDisplayEvent(1, "span", monday.plusDays(1), monday.plusDays(3)) // cols 1..3
        val render = computeAllDayStripRender(week, listOf(e), maxRows = 3)
        for (col in 1..3) {
            val slot = render.slots[0][col]
            assertTrue("col $col should carry the bar segment", slot is AllDaySlot.BarSegment)
        }
        assertTrue(render.slots[0][0] is AllDaySlot.Empty)
        assertTrue(render.slots[0][4] is AllDaySlot.Empty)
    }

    @Test
    fun `span in row 0 leaves single-day events on a covered column to lower rows`() {
        // The span takes cols 0..2 in row 0, so solo (col 0) must go to row 1.
        val span = multiDayDisplayEvent(1, "span", monday, monday.plusDays(2))
        val solo = allDayDisplayEvent(2, "solo", monday)
        val render = computeAllDayStripRender(week, listOf(span, solo), maxRows = 3)
        assertTrue(render.slots[0][0] is AllDaySlot.BarSegment)
        assertTrue(render.slots[1][0] is AllDaySlot.CellEvent)
        assertEquals(2L, ((render.slots[1][0] as AllDaySlot.CellEvent).event as DisplayEvent.Room).event.id)
    }

    @Test
    fun `multiple single-day events consolidate into group summary without consuming extra rows`() {
        // FORK:tasks-org — 5 single-day events on the same day consolidate into 1 EventGroupSummary
        // in row 0, leaving rows 1 and 2 empty with null overflow.
        val events = (1..5).map { allDayDisplayEvent(it.toLong(), "e$it", monday.plusDays(1)) }
        val render = computeAllDayStripRender(week, events, maxRows = 3)
        val col1 = render.slots.map { it[1] }
        assertTrue(col1[0] is AllDaySlot.EventGroupSummary)
        assertEquals(5, (col1[0] as AllDaySlot.EventGroupSummary).events.size)
        assertTrue(col1[1] is AllDaySlot.Empty)
        assertTrue(col1[2] is AllDaySlot.Empty)
        assertNull(render.overflowByColumn[1])
    }

    @Test
    fun `column with multi-day span, multiple 1-day events and multiple all-day tasks consolidates into summary slots`() {
        // 1 multi-day event + 3 1-day events + 2 all-day tasks on col 1 (Tuesday), maxRows = 3:
        // row 0: BarSegment (span covering col 1)
        // row 1: EventGroupSummary (count = 3)
        // row 2: TaskGroupSummary (count = 2)
        // overflow: null
        val span = multiDayDisplayEvent(1, "span", monday, monday.plusDays(2))
        val e1 = allDayDisplayEvent(2, "e1", monday.plusDays(1))
        val e2 = allDayDisplayEvent(3, "e2", monday.plusDays(1))
        val e3 = allDayDisplayEvent(4, "e3", monday.plusDays(1))
        val t1 = taskAllDay(10, monday.plusDays(1), "t1")
        val t2 = taskAllDay(11, monday.plusDays(1), "t2")
        val render = computeAllDayStripRender(week, listOf(span, e1, e2, e3, t1, t2), maxRows = 3)

        val col1 = render.slots.map { it[1] }
        assertTrue("row 0 carries multi-day span", col1[0] is AllDaySlot.BarSegment)
        assertTrue("row 1 carries event group summary", col1[1] is AllDaySlot.EventGroupSummary)
        assertEquals(3, (col1[1] as AllDaySlot.EventGroupSummary).events.size)
        assertTrue("row 2 carries task group summary", col1[2] is AllDaySlot.TaskGroupSummary)
        assertEquals(2, (col1[2] as AllDaySlot.TaskGroupSummary).tasks.size)
        assertNull("no overflow when summaries fit within maxRows", render.overflowByColumn[1])
    }

    @Test
    fun `column with single 1-day event and single all-day task renders individual cell events`() {
        // 1 1-day event + 1 all-day task on col 1, maxRows = 3:
        // row 0: CellEvent (event)
        // row 1: CellEvent (task)
        // row 2: Empty
        // overflow: null
        val e = allDayDisplayEvent(1, "single-event", monday.plusDays(1))
        val t = taskAllDay(10, monday.plusDays(1), "single-task")
        val render = computeAllDayStripRender(week, listOf(e, t), maxRows = 3)

        val col1 = render.slots.map { it[1] }
        assertTrue("row 0 is single event CellEvent", col1[0] is AllDaySlot.CellEvent)
        assertEquals(1L, ((col1[0] as AllDaySlot.CellEvent).event as DisplayEvent.Room).event.id)
        assertTrue("row 1 is single task CellEvent", col1[1] is AllDaySlot.CellEvent)
        assertEquals("task:10", (col1[1] as AllDaySlot.CellEvent).event.stableKey)
        assertTrue("row 2 is Empty", col1[2] is AllDaySlot.Empty)
        assertNull(render.overflowByColumn[1])
    }

    @Test
    fun `when maxRows is 1 multi-day span occupies row and group summaries expand into column overflow`() {
        // maxRows = 1:
        // 1 multi-day span covering col 1 (occupies row 0)
        // 3 1-day events + 2 all-day tasks on col 1 cannot fit in row 0
        // overflowByColumn[1] receives all 5 items (expanded from groups) with count = 5
        val span = multiDayDisplayEvent(1, "span", monday, monday.plusDays(2))
        val e1 = allDayDisplayEvent(2, "e1", monday.plusDays(1))
        val e2 = allDayDisplayEvent(3, "e2", monday.plusDays(1))
        val e3 = allDayDisplayEvent(4, "e3", monday.plusDays(1))
        val t1 = taskAllDay(10, monday.plusDays(1), "t1")
        val t2 = taskAllDay(11, monday.plusDays(1), "t2")
        val render = computeAllDayStripRender(week, listOf(span, e1, e2, e3, t1, t2), maxRows = 1)

        val col1 = render.slots.map { it[1] }
        assertTrue("row 0 is BarSegment", col1[0] is AllDaySlot.BarSegment)
        val overflow = render.overflowByColumn[1]
        assertEquals(5, overflow?.count)
        val overflowKeys = overflow?.events?.map { it.stableKey }?.toSet()
        assertEquals(
            setOf(e1.stableKey, e2.stableKey, e3.stableKey, t1.stableKey, t2.stableKey),
            overflowKeys
        )
    }

    @Test
    fun `collapsed strip surfaces a span-covered column's own event as overflow, not a silent drop`() {
        // In the 1-row collapsed strip (the default), a day covered by a spanning bar shows only
        // the bar; that day's own single-day event has no free row, so it must surface in
        // overflowByColumn instead of vanishing with no "+N" badge.
        val span = multiDayDisplayEvent(1, "span", monday, monday.plusDays(2)) // covers col 0
        val solo = allDayDisplayEvent(2, "hidden", monday)                     // col 0
        val render = computeAllDayStripRender(week, listOf(span, solo), maxRows = 1)
        assertEquals(1, render.slots.size)
        assertTrue(render.slots[0][0] is AllDaySlot.BarSegment)
        // The solo event doesn't appear as a row slot...
        val soloShown = render.slots.flatten().any {
            it is AllDaySlot.CellEvent && (it.event as DisplayEvent.Room).event.id == 2L
        }
        assertFalse(soloShown)
        // ...but it's this column's overflow, and only the solo event is, not the visible bar,
        // so the badge count and its sheet agree.
        val overflow = render.overflowByColumn[0]
        assertEquals(1, overflow?.count)
        assertEquals(listOf(2L), overflow?.events?.map { (it as DisplayEvent.Room).event.id })
    }

    @Test
    fun `multi-day timed event (not all-day) is placed as a span`() {
        val timed = multiDayDisplayEvent(1, "overnight", monday, monday.plusDays(1), allDay = false)
        val render = computeAllDayStripRender(week, listOf(timed), maxRows = 3)
        assertTrue(render.slots[0][0] is AllDaySlot.BarSegment)
        assertTrue(render.slots[0][1] is AllDaySlot.BarSegment)
        val seg = render.slots[0][0] as AllDaySlot.BarSegment
        assertFalse("timed span must keep isAllDay=false so the UI can show its start time", seg.span.displayEvent.isAllDay)
    }

    @Test
    fun `span beyond lane capacity falls back to per-day cells`() {
        // maxRows = 1 -> maxLanes = 1. Two overlapping spans: one is placed as a bar; the other
        // gets no lane and falls back to per-day handling in each column it covers.
        val a = multiDayDisplayEvent(1, "placed", monday, monday.plusDays(2))
        val b = multiDayDisplayEvent(2, "fallback", monday, monday.plusDays(2))
        val render = computeAllDayStripRender(week, listOf(a, b), maxRows = 1)
        // Row 0 is the placed bar. The fallback event has no free row, so it goes to
        // overflowByColumn (not asserted here); only the placed bar is asserted.
        assertTrue(render.slots[0][0] is AllDaySlot.BarSegment)
    }

    private fun taskAllDay(id: Long, date: LocalDate, title: String): DisplayEvent.Task {
        val dayCode = date.year * 10000 + date.monthValue * 100 + date.dayOfMonth
        val ms = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val task = TasksOrgTask(
            id = id,
            title = title,
            notes = "",
            dueTs = ms,
            isAllDay = true,
            startTs = ms,
            endTs = ms,
            startDay = dayCode,
            endDay = dayCode,
            completedAt = null,
            recurrence = null,
            listId = 1L,
            listTitle = "List",
            listColor = 0xFF2196F3.toInt(),
            isReadOnly = false
        )
        return DisplayEvent.Task(task, ZoneOffset.UTC)
    }
}
