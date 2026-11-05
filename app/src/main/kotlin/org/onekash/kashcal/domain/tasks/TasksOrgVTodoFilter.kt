package org.onekash.kashcal.domain.tasks

import org.onekash.kashcal.data.calendar_provider.DeviceCalendar
import org.onekash.kashcal.data.tasks_org.TasksOrgAccount
import org.onekash.kashcal.data.tasks_org.TasksOrgList

/**
 * Pure domain component that filters out Android CalendarProvider collections (DeviceCalendar)
 * that correspond to Tasks.org VTODO task lists (e.g. synced via DAVx5 / CalDAV).
 *
 * Matching is performed strictly on normalized (accountName, displayName) tuples to prevent
 * accidental collisions with similarly named calendars across different accounts.
 */
object TasksOrgVTodoFilter {

    /**
     * Extracts normalized (accountName.trim().lowercase(), listTitle.trim().lowercase()) tuples
     * from Tasks.org accounts and lists.
     */
    fun extractVTodoTuples(
        accounts: List<TasksOrgAccount>,
        lists: List<TasksOrgList>
    ): Set<Pair<String, String>> {
        if (accounts.isEmpty() || lists.isEmpty()) return emptySet()
        val accountsById = accounts.associateBy { it.id }
        return lists.mapNotNull { list ->
            val account = accountsById[list.accountId] ?: return@mapNotNull null
            val accountName = account.name.trim().lowercase()
            val listTitle = list.title.trim().lowercase()
            if (accountName.isNotEmpty() && listTitle.isNotEmpty()) {
                accountName to listTitle
            } else {
                null
            }
        }.toSet()
    }

    /**
     * Filters out [DeviceCalendar] entries whose (accountName, displayName) tuple matches
     * any of the provided [vtodoTuples] (case-insensitive).
     *
     * Returns the original list intact if [vtodoTuples] is empty.
     */
    fun filterDeviceCalendars(
        calendars: List<DeviceCalendar>,
        vtodoTuples: Set<Pair<String, String>>
    ): List<DeviceCalendar> {
        if (vtodoTuples.isEmpty() || calendars.isEmpty()) return calendars
        return calendars.filterNot { calendar ->
            val key = calendar.accountName.trim().lowercase() to calendar.displayName.trim().lowercase()
            key in vtodoTuples
        }
    }

    /**
     * Convenience overload that extracts VTODO tuples from [accounts] and [lists] and filters [calendars].
     */
    fun filterDeviceCalendars(
        calendars: List<DeviceCalendar>,
        accounts: List<TasksOrgAccount>,
        lists: List<TasksOrgList>
    ): List<DeviceCalendar> {
        val tuples = extractVTodoTuples(accounts, lists)
        return filterDeviceCalendars(calendars, tuples)
    }
}
