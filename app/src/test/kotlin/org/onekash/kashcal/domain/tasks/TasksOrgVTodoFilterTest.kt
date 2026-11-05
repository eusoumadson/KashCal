package org.onekash.kashcal.domain.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onekash.kashcal.data.calendar_provider.DeviceCalendar
import org.onekash.kashcal.data.tasks_org.TasksOrgAccount
import org.onekash.kashcal.data.tasks_org.TasksOrgList

class TasksOrgVTodoFilterTest {

    private fun makeCalendar(
        id: Long,
        displayName: String,
        accountName: String,
        accountType: String = "org.dmfs.caldav.account",
        color: Int = 0xFF00FF00.toInt()
    ) = DeviceCalendar(
        id = id,
        displayName = displayName,
        color = color,
        accountName = accountName,
        accountType = accountType,
        visible = true,
        accessLevel = 700
    )

    private fun makeAccount(
        id: Long,
        name: String,
        type: String = "caldav"
    ) = TasksOrgAccount(
        id = id,
        name = name,
        type = type,
        error = "",
        repeatsOnServer = false
    )

    private fun makeList(
        id: Long,
        title: String,
        accountId: Long
    ) = TasksOrgList(
        id = id,
        title = title,
        color = 0xFF112233.toInt(),
        isReadOnly = false,
        accountId = accountId
    )

    @Test
    fun `omits device calendar when account and display name match tasks org list`() {
        val accounts = listOf(makeAccount(id = 1L, name = "Abacate Calendários"))
        val lists = listOf(makeList(id = 10L, title = "Lembretes Comuns", accountId = 1L))

        val vtodoCalendar = makeCalendar(id = 101L, displayName = "Lembretes Comuns", accountName = "Abacate Calendários")
        val normalCalendar = makeCalendar(id = 102L, displayName = "Trabalho", accountName = "Abacate Calendários")

        val result = TasksOrgVTodoFilter.filterDeviceCalendars(
            calendars = listOf(vtodoCalendar, normalCalendar),
            accounts = accounts,
            lists = lists
        )

        assertEquals(1, result.size)
        assertEquals(102L, result.first().id)
        assertEquals("Trabalho", result.first().displayName)
    }

    @Test
    fun `matches case-insensitively and trims whitespace`() {
        val accounts = listOf(makeAccount(id = 1L, name = "  Abacate Calendários  "))
        val lists = listOf(makeList(id = 10L, title = "  Lembretes Comuns  ", accountId = 1L))

        val vtodoCalendar = makeCalendar(id = 101L, displayName = "lembretes comuns", accountName = "ABACATE CALENDÁRIOS")
        val otherCalendar = makeCalendar(id = 102L, displayName = "Pessoal", accountName = "abacate calendários")

        val result = TasksOrgVTodoFilter.filterDeviceCalendars(
            calendars = listOf(vtodoCalendar, otherCalendar),
            accounts = accounts,
            lists = lists
        )

        assertEquals(1, result.size)
        assertEquals(102L, result.first().id)
    }

    @Test
    fun `preserves calendar with identical name under a different account`() {
        val accounts = listOf(makeAccount(id = 1L, name = "Abacate Calendários"))
        val lists = listOf(makeList(id = 10L, title = "Compras", accountId = 1L))

        val googleCalendar = makeCalendar(id = 201L, displayName = "Compras", accountName = "user@gmail.com", accountType = "com.google")
        val caldavVtodoCalendar = makeCalendar(id = 202L, displayName = "Compras", accountName = "Abacate Calendários")

        val result = TasksOrgVTodoFilter.filterDeviceCalendars(
            calendars = listOf(googleCalendar, caldavVtodoCalendar),
            accounts = accounts,
            lists = lists
        )

        assertEquals(1, result.size)
        assertEquals(201L, result.first().id)
        assertEquals("user@gmail.com", result.first().accountName)
    }

    @Test
    fun `returns calendars intact when accounts or lists are empty`() {
        val calendars = listOf(
            makeCalendar(id = 1L, displayName = "Cal 1", accountName = "Acc 1"),
            makeCalendar(id = 2L, displayName = "Cal 2", accountName = "Acc 2")
        )

        // Empty accounts
        val res1 = TasksOrgVTodoFilter.filterDeviceCalendars(calendars, emptyList(), listOf(makeList(1L, "Cal 1", 1L)))
        assertEquals(calendars, res1)

        // Empty lists
        val res2 = TasksOrgVTodoFilter.filterDeviceCalendars(calendars, listOf(makeAccount(1L, "Acc 1")), emptyList())
        assertEquals(calendars, res2)

        // Empty tuples
        val res3 = TasksOrgVTodoFilter.filterDeviceCalendars(calendars, emptySet())
        assertEquals(calendars, res3)

        // Empty input calendars
        val res4 = TasksOrgVTodoFilter.filterDeviceCalendars(emptyList(), listOf(makeAccount(1L, "Acc 1")), listOf(makeList(1L, "Cal 1", 1L)))
        assertTrue(res4.isEmpty())
    }

    @Test
    fun `ignores list when accountId does not match any known account`() {
        val accounts = listOf(makeAccount(id = 1L, name = "Acc 1"))
        val lists = listOf(makeList(id = 10L, title = "List 1", accountId = 999L)) // Unlinked account

        val calendars = listOf(makeCalendar(id = 101L, displayName = "List 1", accountName = "Acc 1"))

        val result = TasksOrgVTodoFilter.filterDeviceCalendars(calendars, accounts, lists)
        assertEquals(1, result.size)
        assertEquals(101L, result.first().id)
    }

    @Test
    fun `extractVTodoTuples normalizes and ignores blank titles or names`() {
        val accounts = listOf(
            makeAccount(id = 1L, name = "  My Account  "),
            makeAccount(id = 2L, name = "   ")
        )
        val lists = listOf(
            makeList(id = 10L, title = "  My List  ", accountId = 1L),
            makeList(id = 11L, title = "   ", accountId = 1L),
            makeList(id = 12L, title = "Valid Title", accountId = 2L)
        )

        val tuples = TasksOrgVTodoFilter.extractVTodoTuples(accounts, lists)
        assertEquals(setOf("my account" to "my list"), tuples)
    }
}
