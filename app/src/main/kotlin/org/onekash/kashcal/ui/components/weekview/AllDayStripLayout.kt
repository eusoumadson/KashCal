package org.onekash.kashcal.ui.components.weekview

import org.onekash.kashcal.domain.model.DisplayEvent
import org.onekash.kashcal.ui.shared.packSpansIntoLanes

/**
 * Layout model for the week and day all-day strip. A multi-day event, all-day or a timed one the
 * caller routes to the strip, is laid out as one bar across the day columns it covers, not as a
 * chip in every column. The full month grid uses the same lane packing
 * ([org.onekash.kashcal.ui.screens.monthfull.computeWeekSpans]).
 */

data class AllDaySpan(
    val displayEvent: DisplayEvent,
    val startCol: Int,
    val endCol: Int,
    /** True when the event starts before the visible window (no left cap on the bar). */
    val leftFlush: Boolean,
    /** True when the event ends after the visible window (no right cap on the bar). */
    val rightFlush: Boolean,
)

internal data class AllDaySpanLayout(
    val lanes: List<List<AllDaySpan>>,
    val placedEventKeys: Set<String>,
)

sealed interface AllDaySlot {
    data object Empty : AllDaySlot
    data class BarSegment(val span: AllDaySpan) : AllDaySlot
    data class CellEvent(val event: DisplayEvent) : AllDaySlot
    // FORK:tasks-org — group summaries for single-day all-day events and tasks
    data class EventGroupSummary(val events: List<DisplayEvent>) : AllDaySlot
    data class TaskGroupSummary(val tasks: List<DisplayEvent.Task>) : AllDaySlot
}

/**
 * Events for one day column that didn't fit in the grid's rows, shown as a "+N" badge overlaid
 * on that column ([AllDayStripRender.overflowByColumn]). Every row of a column can be taken by
 * spanning bars, so the badge must never depend on a free row existing.
 */
data class ColumnOverflow(val count: Int, val events: List<DisplayEvent>)

data class AllDayStripRender(
    val slots: List<List<AllDaySlot>>, // [rowIndex][col]
    /** Per-column overflow, indexed like [slots]' columns; null = nothing hidden. */
    val overflowByColumn: List<ColumnOverflow?>,
)

/**
 * Packs multi-day events (startDay != endDay) that overlap [visibleDayCodes] into up to
 * [maxLanes] lanes with [packSpansIntoLanes]. An event beyond capacity is left out of
 * [AllDaySpanLayout.placedEventKeys] and [computeAllDayStripRender] shows it as a per-day cell.
 */
internal fun computeAllDaySpans(
    visibleDayCodes: List<Int>,
    allDayEvents: List<DisplayEvent>,
    maxLanes: Int,
): AllDaySpanLayout {
    if (visibleDayCodes.isEmpty()) return AllDaySpanLayout(emptyList(), emptySet())
    val rangeStart = visibleDayCodes.first()
    val rangeEnd = visibleDayCodes.last()

    val seen = mutableMapOf<String, DisplayEvent>()
    for (e in allDayEvents) {
        if (e.startDay == e.endDay) continue
        if (e.endDay < rangeStart || e.startDay > rangeEnd) continue
        seen.putIfAbsent(e.stableKey, e)
    }

    val rawSpans = seen.values.map { e ->
        val leftFlush = e.startDay < rangeStart
        val rightFlush = e.endDay > rangeEnd
        val startCol = if (leftFlush) 0 else visibleDayCodes.indexOf(e.startDay)
        val endCol = if (rightFlush) visibleDayCodes.lastIndex else visibleDayCodes.indexOf(e.endDay)
        AllDaySpan(e, startCol, endCol, leftFlush, rightFlush)
    }

    val lanes = packSpansIntoLanes(rawSpans, maxLanes, startCol = { it.startCol }, endCol = { it.endCol })

    val placedKeys = lanes.flatten().map { it.displayEvent.stableKey }.toSet()
    return AllDaySpanLayout(lanes = lanes, placedEventKeys = placedKeys)
}

/**
 * Builds the `[rowIndex][col]` render grid for the all-day strip. Each placed span fills its
 * lane's row across the columns it covers; each column's free rows take that day's other events,
 * single-day ones and multi-day ones that didn't get a lane, by start time. The rest go to
 * [AllDayStripRender.overflowByColumn] instead of a row, so the "+N" badge shows even when a
 * column has no free row.
 */
fun computeAllDayStripRender(
    visibleDayCodes: List<Int>,
    allDayEvents: List<DisplayEvent>,
    maxRows: Int,
): AllDayStripRender {
    val numCols = visibleDayCodes.size
    if (numCols == 0 || maxRows == 0) return AllDayStripRender(emptyList(), emptyList())

    val layout = computeAllDaySpans(visibleDayCodes, allDayEvents, maxRows)
    val grid: Array<Array<AllDaySlot>> = Array(maxRows) { Array(numCols) { AllDaySlot.Empty } }

    for ((laneIndex, lane) in layout.lanes.withIndex()) {
        for (span in lane) {
            for (col in span.startCol..span.endCol) {
                grid[laneIndex][col] = AllDaySlot.BarSegment(span)
            }
        }
    }

    val overflowByColumn = arrayOfNulls<ColumnOverflow>(numCols)
    for (col in 0 until numCols) {
        val dayCode = visibleDayCodes[col]
        val allEventsForDay = allDayEvents
            .filter { it.startDay <= dayCode && it.endDay >= dayCode }
            .sortedBy { it.startTs }
        val columnEvents = allEventsForDay.filter { it.stableKey !in layout.placedEventKeys }

        // FORK:tasks-org — partition into single-day events, all-day tasks, and others (e.g. unlaned multi-day)
        val singleDayEvents = columnEvents.filter { it !is DisplayEvent.Task && it.isAllDay && it.startDay == it.endDay }
        val allDayTasks = columnEvents.filterIsInstance<DisplayEvent.Task>().filter { it.isAllDay }
        val otherEvents = columnEvents.filter { it !in singleDayEvents && it !in allDayTasks }

        val candidateSlots = mutableListOf<AllDaySlot>()
        if (singleDayEvents.size > 1) {
            candidateSlots.add(AllDaySlot.EventGroupSummary(singleDayEvents))
        } else if (singleDayEvents.size == 1) {
            candidateSlots.add(AllDaySlot.CellEvent(singleDayEvents[0]))
        }

        if (allDayTasks.size > 1) {
            candidateSlots.add(AllDaySlot.TaskGroupSummary(allDayTasks))
        } else if (allDayTasks.size == 1) {
            candidateSlots.add(AllDaySlot.CellEvent(allDayTasks[0]))
        }

        for (other in otherEvents) {
            candidateSlots.add(AllDaySlot.CellEvent(other))
        }

        val freeSlots = (0 until maxRows).filter { grid[it][col] === AllDaySlot.Empty }
        val visibleCount = minOf(candidateSlots.size, freeSlots.size)
        for (i in 0 until visibleCount) {
            grid[freeSlots[i]][col] = candidateSlots[i]
        }
        val hiddenCandidates = candidateSlots.drop(visibleCount)
        if (hiddenCandidates.isNotEmpty()) {
            val hiddenEvents = mutableListOf<DisplayEvent>()
            for (candidate in hiddenCandidates) {
                when (candidate) {
                    is AllDaySlot.EventGroupSummary -> hiddenEvents.addAll(candidate.events)
                    is AllDaySlot.TaskGroupSummary -> hiddenEvents.addAll(candidate.tasks)
                    is AllDaySlot.CellEvent -> hiddenEvents.add(candidate.event)
                    is AllDaySlot.BarSegment -> hiddenEvents.add(candidate.span.displayEvent)
                    AllDaySlot.Empty -> Unit
                }
            }
            overflowByColumn[col] = ColumnOverflow(hiddenEvents.size, hiddenEvents)
        }
    }

    return AllDayStripRender(
        slots = grid.map { it.toList() },
        overflowByColumn = overflowByColumn.toList(),
    )
}
