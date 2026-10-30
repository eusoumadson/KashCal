package org.onekash.kashcal.data.tasks_org

import android.app.Application
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Tests [AndroidTasksOrgRepository] against a fake of the Tasks.org provider ([FakeTasksOrgProvider]):
 * the query parameters it sends, the row mapping, paging, hidden lists, the permission checks and
 * a refused query.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class AndroidTasksOrgRepositoryTest {

    private lateinit var provider: FakeTasksOrgProvider
    private lateinit var repository: AndroidTasksOrgRepository
    private val zone = ZoneId.systemDefault()

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Before
    fun setup() {
        provider = Robolectric.setupContentProvider(FakeTasksOrgProvider::class.java, TasksOrgContract.AUTHORITY)
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(TasksOrgContract.PERMISSION_READ, TasksOrgContract.PERMISSION_WRITE)
        repository = AndroidTasksOrgRepository(app, TasksOrgV0Mapper().apply { zone = this@AndroidTasksOrgRepositoryTest.zone })
        provider.lists += FakeTasksOrgProvider.ListRow(id = 1, title = "Inbox", color = 0xFF4CAF50.toInt(), access = "owner")
        provider.lists += FakeTasksOrgProvider.ListRow(id = 2, title = "Shared", color = 0, access = "read_only")
    }

    @Test
    fun `timed task is a point at its due time with its list`() = runTest {
        val due = at(2026, 10, 29, 17, 0)
        provider.tasks += FakeTasksOrgProvider.TaskRow(id = 10, title = "Teste 2", due = due, listId = 1, recurrence = "FREQ=DAILY")

        val task = repository.getTasksDueInRange(at(2026, 10, 29, 0), at(2026, 10, 29, 23), emptySet()).single()

        assertEquals(10L, task.id)
        assertEquals("Teste 2", task.title)
        assertFalse(task.isAllDay)
        assertEquals(due, task.startTs)
        assertEquals(due, task.endTs)
        assertEquals(20261029, task.startDay)
        assertEquals(20261029, task.endDay)
        assertEquals("Inbox", task.listTitle)
        assertEquals(0xFF4CAF50.toInt(), task.listColor)
        assertEquals("FREQ=DAILY", task.recurrence)
        assertNull(task.completedAt)
        assertFalse(task.isReadOnly)
    }

    @Test
    fun `query sends the range as exclusive bounds and pages by the provider limit`() = runTest {
        repository.getTasksDueInRange(1000L, 2000L, emptySet())

        val uri = provider.queries.single { it.pathSegments.last() == "tasks" }
        assertEquals("999", uri.getQueryParameter(TasksOrgContract.Tasks.PARAM_DUE_AFTER))
        assertEquals("2001", uri.getQueryParameter(TasksOrgContract.Tasks.PARAM_DUE_BEFORE))
        assertEquals("1000", uri.getQueryParameter(TasksOrgContract.PARAM_LIMIT))
        assertEquals("0", uri.getQueryParameter(TasksOrgContract.PARAM_OFFSET))
    }

    @Test
    fun `a full page is followed by the next one`() = runTest {
        val due = at(2026, 10, 29, 9)
        repeat(TasksOrgContract.MAX_LIMIT + 5) { i ->
            provider.tasks += FakeTasksOrgProvider.TaskRow(id = i.toLong() + 1, title = "T$i", due = due, listId = 1)
        }

        val tasks = repository.getTasksDueInRange(due - 1, due + 1, emptySet())

        assertEquals(TasksOrgContract.MAX_LIMIT + 5, tasks.size)
        val offsets = provider.queries.filter { it.pathSegments.last() == "tasks" }
            .map { it.getQueryParameter(TasksOrgContract.PARAM_OFFSET) }
        assertEquals(listOf("0", "1000"), offsets)
    }

    @Test
    fun `a task read twice across pages is returned once`() = runTest {
        val due = at(2026, 10, 29, 9)
        provider.tasks += FakeTasksOrgProvider.TaskRow(id = 1, title = "Once", due = due, listId = 1)
        provider.tasks += FakeTasksOrgProvider.TaskRow(id = 1, title = "Once", due = due, listId = 1)

        assertEquals(listOf(1L), repository.getTasksDueInRange(due - 1, due + 1, emptySet()).map { it.id })
    }

    @Test
    fun `undated tasks, hidden lists and completion are mapped`() = runTest {
        val due = at(2026, 10, 29, 9)
        provider.tasks += FakeTasksOrgProvider.TaskRow(id = 1, title = "No date", due = 0, listId = 1)
        provider.tasks += FakeTasksOrgProvider.TaskRow(id = 2, title = "Hidden", due = due, listId = 2)
        provider.tasks += FakeTasksOrgProvider.TaskRow(id = 3, title = "Done", due = due, listId = 1, completedAt = due + 60_000)

        val tasks = repository.getTasksDueInRange(due - 1, due + 1, hiddenListIds = setOf(2L))

        assertEquals(listOf(3L), tasks.map { it.id })
        assertEquals(due + 60_000, tasks.single().completedAt)
        assertTrue(tasks.single().isCompleted)
    }

    @Test
    fun `a task in a read-only list is read-only`() = runTest {
        val due = at(2026, 10, 29, 9)
        provider.tasks += FakeTasksOrgProvider.TaskRow(id = 1, title = "Shared", due = due, listId = 2)

        assertTrue(repository.getTasksDueInRange(due - 1, due + 1, emptySet()).single().isReadOnly)
    }

    @Test
    fun `lists are read with their access`() = runTest {
        val lists = repository.getLists()

        assertEquals(listOf("Inbox", "Shared"), lists.map { it.title })
        assertEquals(listOf(false, true), lists.map { it.isReadOnly })
    }

    @Test
    fun `setCompleted writes completed_at, and 0 to reopen`() = runTest {
        assertTrue(repository.setCompleted(7L, 1234L))
        assertTrue(repository.setCompleted(7L, null))

        assertEquals(listOf("7", "7"), provider.updates.map { it.first.lastPathSegment })
        assertEquals(listOf(1234L, 0L), provider.updates.map { it.second.getAsLong(TasksOrgContract.Tasks.COMPLETED_AT) })
    }

    @Test
    fun `getSchedule reads the task's dates, flags and rule`() = runTest {
        provider.tasks += FakeTasksOrgProvider.TaskRow(
            id = 4, title = "Water plants", due = 5000L, listId = 1, recurrence = "FREQ=DAILY;COUNT=2",
            allDay = true, startDate = 4000L, startAllDay = true
        )

        assertEquals(
            TasksOrgSchedule(dueDate = 5000L, dueAllDay = true, startDate = 4000L, startAllDay = true, recurrence = "FREQ=DAILY;COUNT=2"),
            repository.getSchedule(4L)
        )
        assertNull(repository.getSchedule(99L))
    }

    @Test
    fun `restoreSchedule writes the dates with their flags and the rule in one update`() = runTest {
        val schedule = TasksOrgSchedule(dueDate = 5000L, dueAllDay = false, startDate = 0L, startAllDay = false, recurrence = "FREQ=WEEKLY")

        assertTrue(repository.restoreSchedule(4L, schedule))

        val (uri, values) = provider.updates.single()
        assertEquals("4", uri.lastPathSegment)
        assertEquals(5000L, values.getAsLong(TasksOrgContract.Tasks.DUE_DATE))
        assertEquals(0, values.getAsInteger(TasksOrgContract.Tasks.DUE_ALL_DAY))
        assertEquals(0L, values.getAsLong(TasksOrgContract.Tasks.START_DATE))
        assertEquals(0, values.getAsInteger(TasksOrgContract.Tasks.START_ALL_DAY))
        assertEquals("FREQ=WEEKLY", values.getAsString(TasksOrgContract.Tasks.RECURRENCE))
        assertFalse(values.containsKey(TasksOrgContract.Tasks.COMPLETED_AT))
    }

    @Test
    fun `without permissions nothing is queried or written`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).denyPermissions(TasksOrgContract.PERMISSION_READ, TasksOrgContract.PERMISSION_WRITE)

        assertTrue(repository.getTasksDueInRange(0L, Long.MAX_VALUE / 2, emptySet()).isEmpty())
        assertTrue(repository.getLists().isEmpty())
        assertFalse(repository.setCompleted(1L, 1L))
        assertNull(repository.getSchedule(1L))
        assertFalse(repository.restoreSchedule(1L, TasksOrgSchedule(1L, false, 0L, false, "")))
        assertTrue(provider.queries.isEmpty())
        assertTrue(provider.updates.isEmpty())
    }

    @Test
    fun `a refused query returns empty instead of throwing`() = runTest {
        provider.failWith = SecurityException("revoked")

        assertTrue(repository.getTasksDueInRange(0L, 1L, emptySet()).isEmpty())
        assertFalse(repository.setCompleted(1L, 1L))
        assertNull(repository.getSchedule(1L))
        assertFalse(repository.restoreSchedule(1L, TasksOrgSchedule(1L, false, 0L, false, "")))
    }

    @Test
    fun `createTask inserts the draft and returns the new id`() = runTest {
        provider.nextInsertId = 42L
        val due = at(2026, 11, 1, 9)

        val result = repository.createTask(
            TasksOrgTaskDraft(
                title = "Renew passport",
                notes = "office hours",
                listId = 1L,
                due = TasksOrgDate(due, allDay = false),
                priority = TasksOrgPriority.HIGH,
                recurrence = "FREQ=MONTHLY"
            )
        )

        assertEquals(TasksOrgWriteResult.Ok(42L), result)
        val (uri, values) = provider.inserts.single()
        assertEquals("tasks", uri.lastPathSegment)
        assertEquals("Renew passport", values.getAsString(TasksOrgContract.Tasks.TITLE))
        assertEquals("office hours", values.getAsString(TasksOrgContract.Tasks.NOTES))
        assertEquals(1L, values.getAsLong(TasksOrgContract.Tasks.LIST_ID))
        assertEquals(due, values.getAsLong(TasksOrgContract.Tasks.DUE_DATE))
        assertEquals(0, values.getAsInteger(TasksOrgContract.Tasks.DUE_ALL_DAY))
        assertEquals("high", values.getAsString(TasksOrgContract.Tasks.PRIORITY))
        assertEquals("FREQ=MONTHLY", values.getAsString(TasksOrgContract.Tasks.RECURRENCE))
        assertEquals("due_date", values.getAsString(TasksOrgContract.Tasks.REPEAT_FROM))
    }

    @Test
    fun `createTask writes an all-day due date at local midnight with the flag`() = runTest {
        val saoPaulo = ZoneId.of("America/Sao_Paulo")
        repository = AndroidTasksOrgRepository(
            ApplicationProvider.getApplicationContext(),
            TasksOrgV0Mapper().apply { zone = saoPaulo }
        )
        // Any instant on 2026-11-01 in the device zone.
        val someMomentThatDay = LocalDateTime.of(2026, 11, 1, 15, 30).atZone(saoPaulo).toInstant().toEpochMilli()
        val expectedLocalMidnight = LocalDateTime.of(2026, 11, 1, 0, 0).atZone(saoPaulo).toInstant().toEpochMilli()

        repository.createTask(TasksOrgTaskDraft(title = "Pay rent", due = TasksOrgDate(someMomentThatDay, allDay = true)))

        val values = provider.inserts.single().second
        assertEquals(expectedLocalMidnight, values.getAsLong(TasksOrgContract.Tasks.DUE_DATE))
        assertEquals(1, values.getAsInteger(TasksOrgContract.Tasks.DUE_ALL_DAY))
    }

    @Test
    fun `updateTask only writes the set fields`() = runTest {
        val result = repository.updateTask(
            9L,
            TasksOrgTaskPatch(
                title = TasksOrgTaskPatch.Field.Value("New title"),
                recurrence = TasksOrgTaskPatch.Field.Value(null) // clear
            )
        )

        assertEquals(TasksOrgWriteResult.Ok(9L), result)
        val (uri, values) = provider.updates.single()
        assertEquals("9", uri.lastPathSegment)
        assertEquals("New title", values.getAsString(TasksOrgContract.Tasks.TITLE))
        assertEquals("", values.getAsString(TasksOrgContract.Tasks.RECURRENCE))
        // Untouched fields are not written.
        assertFalse(values.containsKey(TasksOrgContract.Tasks.NOTES))
        assertFalse(values.containsKey(TasksOrgContract.Tasks.LIST_ID))
        assertFalse(values.containsKey(TasksOrgContract.Tasks.DUE_DATE))
    }

    @Test
    fun `updateTask sends the all-day flag whenever the date changes`() = runTest {
        repository.updateTask(
            9L,
            TasksOrgTaskPatch(due = TasksOrgTaskPatch.Field.Value(TasksOrgDate(123L, allDay = false)))
        )
        val values = provider.updates.single().second
        assertEquals(123L, values.getAsLong(TasksOrgContract.Tasks.DUE_DATE))
        assertEquals(0, values.getAsInteger(TasksOrgContract.Tasks.DUE_ALL_DAY))
    }

    @Test
    fun `an empty patch is a success with no write`() = runTest {
        assertEquals(TasksOrgWriteResult.Ok(9L), repository.updateTask(9L, TasksOrgTaskPatch()))
        assertTrue(provider.updates.isEmpty())
    }

    @Test
    fun `a write to a read-only task is reported as ReadOnly`() = runTest {
        provider.readOnlyTaskIds += 9L

        assertEquals(
            TasksOrgWriteResult.ReadOnly,
            repository.updateTask(9L, TasksOrgTaskPatch(title = TasksOrgTaskPatch.Field.Value("x")))
        )
        assertEquals(TasksOrgWriteResult.ReadOnly, repository.deleteTask(9L))
    }

    @Test
    fun `deleteTask deletes the task item`() = runTest {
        assertEquals(TasksOrgWriteResult.Ok(9L), repository.deleteTask(9L))
        assertEquals("9", provider.deletes.single().lastPathSegment)
    }

    @Test
    fun `without write permission create, update and delete are refused`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).denyPermissions(TasksOrgContract.PERMISSION_WRITE)

        assertEquals(TasksOrgWriteResult.Refused, repository.createTask(TasksOrgTaskDraft(title = "x")))
        assertEquals(
            TasksOrgWriteResult.Refused,
            repository.updateTask(9L, TasksOrgTaskPatch(title = TasksOrgTaskPatch.Field.Value("x")))
        )
        assertEquals(TasksOrgWriteResult.Refused, repository.deleteTask(9L))
        assertTrue(provider.inserts.isEmpty())
        assertTrue(provider.updates.isEmpty())
        assertTrue(provider.deletes.isEmpty())
    }

    @Test
    fun `tags are read, created idempotently, and joined to or removed from a task`() = runTest {
        provider.tags += FakeTasksOrgProvider.TagRow(id = 1L, name = "admin")
        provider.tasks += FakeTasksOrgProvider.TaskRow(id = 7L, title = "T", due = 1000L, listId = 1)
        provider.nextInsertId = 50L

        assertEquals(listOf("admin"), repository.getTags().map { it.name })

        // Existing name is idempotent.
        assertEquals(1L, repository.ensureTag("admin"))
        // New name creates a row.
        val opsId = repository.ensureTag("ops")
        assertEquals(50L, opsId)

        assertTrue(repository.addTagToTask(7L, 1L))
        assertTrue(repository.addTagToTask(7L, opsId!!))
        assertEquals(setOf(1L, 50L), provider.taskTags[7L])
        assertEquals(listOf(1L, 50L), repository.getTagIdsForTask(7L).sorted())

        assertTrue(repository.removeTagFromTask(7L, 1L))
        assertEquals(setOf(50L), provider.taskTags[7L])
    }

    @Test
    fun `without read permission tags come back empty`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).denyPermissions(TasksOrgContract.PERMISSION_READ)

        assertTrue(repository.getTags().isEmpty())
        assertTrue(repository.getTagIdsForTask(7L).isEmpty())
    }

    @Test
    fun `reminders are read by type, added with their columns, and deleted`() = runTest {
        provider.reminders += FakeTasksOrgProvider.ReminderRow(id = 1, taskId = 7, type = "date_time", triggerAt = 5000L)
        provider.reminders += FakeTasksOrgProvider.ReminderRow(id = 2, taskId = 7, type = "relative_due", offsetMs = -600_000L)
        // A reminder type this version doesn't know is skipped, not an error.
        provider.reminders += FakeTasksOrgProvider.ReminderRow(id = 3, taskId = 7, type = "some_future_type")
        provider.nextInsertId = 80L

        val read = repository.getReminders(7L)
        assertEquals(2, read.size)
        assertTrue(read.any { it is TasksOrgReminder.AtTime && it.triggerAtMs == 5000L })
        assertTrue(read.any { it is TasksOrgReminder.Relative && it.offsetMs == -600_000L && it.anchor == TasksOrgReminder.Relative.Anchor.DUE })

        // Add a relative-due reminder.
        val newId = repository.addReminder(7L, TasksOrgReminder.Relative(anchor = TasksOrgReminder.Relative.Anchor.DUE, offsetMs = -3_600_000L))
        assertEquals(80L, newId)
        val added = provider.inserts.single { it.first.lastPathSegment == "reminders" }.second
        assertEquals("relative_due", added.getAsString(TasksOrgContract.Reminders.TYPE))
        assertEquals(-3_600_000L, added.getAsLong(TasksOrgContract.Reminders.OFFSET_MS))
        assertEquals(7L, added.getAsLong(TasksOrgContract.Reminders.TASK_ID))

        // Delete one.
        assertTrue(repository.deleteReminder(1L))
        assertTrue(provider.reminders.none { it.id == 1L })
    }

    @Test
    fun `an at-time reminder sends only trigger_at`() = runTest {
        repository.addReminder(7L, TasksOrgReminder.AtTime(triggerAtMs = 9999L))
        val v = provider.inserts.single { it.first.lastPathSegment == "reminders" }.second
        assertEquals("date_time", v.getAsString(TasksOrgContract.Reminders.TYPE))
        assertEquals(9999L, v.getAsLong(TasksOrgContract.Reminders.TRIGGER_AT))
        assertFalse(v.containsKey(TasksOrgContract.Reminders.OFFSET_MS))
    }

    @Test
    fun `without write permission reminders can't be added or deleted`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).denyPermissions(TasksOrgContract.PERMISSION_WRITE)

        assertNull(repository.addReminder(7L, TasksOrgReminder.AtTime(triggerAtMs = 1L)))
        assertFalse(repository.deleteReminder(1L))
    }

    @Test
    fun `an all-day task is placed on its local date with the all-day conventions`() {
        val saoPaulo = ZoneId.of("America/Sao_Paulo")
        // Tasks.org keeps an all-day due date at noon in the device zone.
        val due = LocalDateTime.of(2026, 10, 30, 12, 0).atZone(saoPaulo).toInstant().toEpochMilli()

        val task = taskAt(
            id = 1, title = "Pay rent", notes = "", dueTs = due, isAllDay = true, completedAt = null,
            recurrence = null, listId = 1, listTitle = "", listColor = 0, isReadOnly = false, zone = saoPaulo
        )

        // 2026-10-30 00:00 UTC through the day's last millisecond.
        assertEquals(1793318400000L, task.startTs)
        assertEquals(1793318400000L + 86_400_000L - 1, task.endTs)
        assertEquals(20261030, task.startDay)
        assertEquals(20261030, task.endDay)
    }
}

/**
 * Stands in for the Tasks.org provider (`org.tasks.api`): serves [tasks] and [lists] the way
 * the real one does (filtered by due range, paged by limit and offset, columns by name) and
 * records [queries] and [updates]. A non-null [failWith] is thrown by every call.
 */
class FakeTasksOrgProvider : ContentProvider() {

    data class TaskRow(
        val id: Long,
        val title: String,
        val due: Long,
        val listId: Long,
        val completedAt: Long = 0,
        val recurrence: String = "",
        val allDay: Boolean = false,
        val notes: String = "",
        val startDate: Long = 0,
        val startAllDay: Boolean = false,
        val priority: String = "none",
        val placeId: Long = 0,
        val childCount: Int = 0,
        val uncompletedChildCount: Int = 0,
        val repeatFrom: String = "due_date",
    )

    data class ListRow(val id: Long, val title: String, val color: Int, val access: String, val accountId: Long = 0)

    data class PlaceRow(
        val id: Long, val name: String, val latitude: Double, val longitude: Double,
        val address: String = "", val radius: Int = 250,
    )

    data class AccountRow(val id: Long, val name: String, val type: String, val error: String = "", val repeatsOnServer: Boolean = false)

    data class TagRow(val id: Long, val name: String, val color: Int = 0)

    data class ReminderRow(
        val id: Long,
        val taskId: Long,
        val type: String,
        val triggerAt: Long = 0,
        val offsetMs: Long = 0,
        val repeatCount: Int = 0,
        val intervalMs: Long = 0,
        val placeId: Long = 0,
    )

    val tasks = mutableListOf<TaskRow>()
    val lists = mutableListOf<ListRow>()
    val tags = mutableListOf<TagRow>()
    val reminders = mutableListOf<ReminderRow>()
    val places = mutableListOf<PlaceRow>()
    val accounts = mutableListOf<AccountRow>()

    /** What the fake treats as "now" when it filters `overdue=1`. */
    var now: Long = Long.MAX_VALUE
    /** task id -> its tag ids, returned in the task's tag_ids column and edited via task_tags. */
    val taskTags = mutableMapOf<Long, MutableSet<Long>>()
    val queries = mutableListOf<Uri>()
    val updates = mutableListOf<Pair<Uri, ContentValues>>()
    val inserts = mutableListOf<Pair<Uri, ContentValues>>()
    val deletes = mutableListOf<Uri>()
    var failWith: RuntimeException? = null

    /** Ids of tasks whose write must throw UnsupportedOperationException (read-only target). */
    val readOnlyTaskIds = mutableSetOf<Long>()

    /** Ids whose update matches no row (the task is gone): the provider returns 0. */
    val missingTaskIds = mutableSetOf<Long>()

    /** Next id handed out by insert. */
    var nextInsertId = 100L

    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        failWith?.let { throw it }
        require(selection == null && sortOrder == null) { "selection and sortOrder are not supported" }
        queries += uri
        val limit = uri.getQueryParameter(TasksOrgContract.PARAM_LIMIT)?.toInt() ?: 100
        val offset = uri.getQueryParameter(TasksOrgContract.PARAM_OFFSET)?.toInt() ?: 0
        // An item URI, /v0/tasks/{id}: the one task.
        val segments = uri.pathSegments
        if (segments.size == 3 && segments[1] == "tasks") {
            val id = segments[2].toLong()
            return taskCursor(tasks.filter { it.id == id })
        }
        return when (uri.pathSegments.last()) {
            "tasks" -> {
                val after = uri.getQueryParameter(TasksOrgContract.Tasks.PARAM_DUE_AFTER)?.toLong()
                val before = uri.getQueryParameter(TasksOrgContract.Tasks.PARAM_DUE_BEFORE)?.toLong()
                val overdue = uri.getQueryParameter(TasksOrgContract.Tasks.PARAM_OVERDUE) == "1"
                val completed = uri.getQueryParameter(TasksOrgContract.Tasks.PARAM_COMPLETED)
                // The real provider filters with dueDate > after AND dueDate < before, so an
                // undated task (0) falls out of any positive range; keep it to test the mapping,
                // unless the query asks for a dated task (due_after=0).
                taskCursor(
                    tasks.filter { t ->
                        val inRange = if (after == 0L) t.due > 0L && (before == null || t.due < before)
                        else t.due == 0L || ((after == null || t.due > after) && (before == null || t.due < before))
                        inRange &&
                            (!overdue || (t.due in 1 until now)) &&
                            (completed == null || (completed == "1") == (t.completedAt > 0L))
                    }.drop(offset).take(limit)
                )
            }
            "lists" -> MatrixCursor(
                arrayOf(TasksOrgContract.ID, TasksOrgContract.Lists.TITLE, TasksOrgContract.Lists.COLOR,
                    TasksOrgContract.Lists.ACCESS, TasksOrgContract.Lists.ACCOUNT_ID)
            ).apply {
                lists.drop(offset).take(limit).forEach { addRow(arrayOf<Any>(it.id, it.title, it.color, it.access, it.accountId)) }
            }
            "places" -> MatrixCursor(
                arrayOf(TasksOrgContract.ID, TasksOrgContract.Places.NAME, TasksOrgContract.Places.DISPLAY_NAME,
                    TasksOrgContract.Places.ADDRESS, TasksOrgContract.Places.LATITUDE,
                    TasksOrgContract.Places.LONGITUDE, TasksOrgContract.Places.RADIUS)
            ).apply {
                places.drop(offset).take(limit).forEach {
                    addRow(arrayOf<Any>(it.id, it.name, it.name.ifEmpty { it.address }, it.address, it.latitude, it.longitude, it.radius))
                }
            }
            "accounts" -> MatrixCursor(
                arrayOf(TasksOrgContract.ID, TasksOrgContract.Accounts.NAME, TasksOrgContract.Accounts.TYPE,
                    TasksOrgContract.Accounts.ERROR, TasksOrgContract.Accounts.REPEATS_ON_SERVER)
            ).apply {
                accounts.drop(offset).take(limit).forEach {
                    addRow(arrayOf<Any>(it.id, it.name, it.type, it.error, if (it.repeatsOnServer) 1 else 0))
                }
            }
            "tags" -> MatrixCursor(
                arrayOf(TasksOrgContract.ID, TasksOrgContract.Tags.NAME, TasksOrgContract.Tags.COLOR)
            ).apply {
                tags.drop(offset).take(limit).forEach { addRow(arrayOf<Any>(it.id, it.name, it.color)) }
            }
            "reminders" -> {
                val taskId = uri.getQueryParameter(TasksOrgContract.Reminders.TASK_ID)?.toLong()
                MatrixCursor(
                    arrayOf(
                        TasksOrgContract.ID, TasksOrgContract.Reminders.TYPE,
                        TasksOrgContract.Reminders.TRIGGER_AT, TasksOrgContract.Reminders.OFFSET_MS,
                        TasksOrgContract.Reminders.REPEAT_COUNT, TasksOrgContract.Reminders.INTERVAL_MS,
                        TasksOrgContract.Reminders.PLACE_ID,
                    )
                ).apply {
                    reminders.filter { taskId == null || it.taskId == taskId }
                        .drop(offset).take(limit)
                        .forEach { addRow(arrayOf<Any>(it.id, it.type, it.triggerAt, it.offsetMs, it.repeatCount, it.intervalMs, it.placeId)) }
                }
            }
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    private fun taskCursor(rows: List<TaskRow>): Cursor = MatrixCursor(
        arrayOf(
            TasksOrgContract.ID, TasksOrgContract.Tasks.TITLE, TasksOrgContract.Tasks.NOTES,
            TasksOrgContract.Tasks.DUE_DATE, TasksOrgContract.Tasks.DUE_ALL_DAY,
            TasksOrgContract.Tasks.START_DATE, TasksOrgContract.Tasks.START_ALL_DAY,
            TasksOrgContract.Tasks.COMPLETED_AT, TasksOrgContract.Tasks.RECURRENCE,
            TasksOrgContract.Tasks.REPEAT_FROM, TasksOrgContract.Tasks.PRIORITY,
            TasksOrgContract.Tasks.LIST_ID, TasksOrgContract.Tasks.IS_READ_ONLY,
            TasksOrgContract.Tasks.TAG_IDS, TasksOrgContract.Tasks.PLACE_ID,
            TasksOrgContract.Tasks.CHILD_COUNT, TasksOrgContract.Tasks.UNCOMPLETED_CHILD_COUNT,
        )
    ).apply {
        rows.forEach { t ->
            addRow(arrayOf<Any>(
                t.id, t.title, t.notes, t.due, if (t.allDay) 1 else 0, t.startDate, if (t.startAllDay) 1 else 0,
                t.completedAt, t.recurrence, t.repeatFrom, t.priority, t.listId, 0,
                taskTags[t.id].orEmpty().joinToString(","), t.placeId, t.childCount, t.uncompletedChildCount,
            ))
        }
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        failWith?.let { throw it }
        if (uri.lastPathSegment?.toLongOrNull() in readOnlyTaskIds) {
            throw UnsupportedOperationException("read-only task")
        }
        updates += uri to ContentValues(values)
        if (uri.lastPathSegment?.toLongOrNull() in missingTaskIds) return 0
        // Keep the in-memory rows in step for the cases the tests read back.
        val id = uri.lastPathSegment?.toLongOrNull()
        when (uri.pathSegments.getOrNull(1)) {
            "lists" -> lists.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { i ->
                lists[i] = lists[i].copy(
                    title = values?.getAsString(TasksOrgContract.Lists.TITLE) ?: lists[i].title,
                    color = values?.getAsInteger(TasksOrgContract.Lists.COLOR) ?: lists[i].color
                )
            }
            "tags" -> tags.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { i ->
                tags[i] = tags[i].copy(
                    name = values?.getAsString(TasksOrgContract.Tags.NAME) ?: tags[i].name,
                    color = values?.getAsInteger(TasksOrgContract.Tags.COLOR) ?: tags[i].color
                )
            }
            "places" -> places.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { i ->
                places[i] = places[i].copy(
                    name = values?.getAsString(TasksOrgContract.Places.NAME) ?: places[i].name,
                    radius = values?.getAsInteger(TasksOrgContract.Places.RADIUS) ?: places[i].radius
                )
            }
        }
        return 1
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        failWith?.let { throw it }
        inserts += uri to ContentValues(values)
        return when (uri.lastPathSegment) {
            "tags" -> {
                val name = values?.getAsString(TasksOrgContract.Tags.NAME).orEmpty()
                // Idempotent: an existing name returns its row.
                val existing = tags.firstOrNull { it.name.equals(name, ignoreCase = true) }
                val id = existing?.id ?: (nextInsertId++).also { tags += TagRow(it, name) }
                ContentUris.withAppendedId(TasksOrgContract.Tags.CONTENT_URI, id)
            }
            "task_tags" -> {
                val taskId = values?.getAsLong(TasksOrgContract.TaskTags.TASK_ID) ?: 0L
                val tagId = values?.getAsLong(TasksOrgContract.TaskTags.TAG_ID) ?: 0L
                taskTags.getOrPut(taskId) { mutableSetOf() }.add(tagId)
                ContentUris.withAppendedId(TasksOrgContract.TaskTags.CONTENT_URI, nextInsertId++)
            }
            "reminders" -> {
                val id = nextInsertId++
                reminders += ReminderRow(
                    id = id,
                    taskId = values?.getAsLong(TasksOrgContract.Reminders.TASK_ID) ?: 0L,
                    type = values?.getAsString(TasksOrgContract.Reminders.TYPE).orEmpty(),
                    triggerAt = values?.getAsLong(TasksOrgContract.Reminders.TRIGGER_AT) ?: 0L,
                    offsetMs = values?.getAsLong(TasksOrgContract.Reminders.OFFSET_MS) ?: 0L,
                    repeatCount = values?.getAsInteger(TasksOrgContract.Reminders.REPEAT_COUNT) ?: 0,
                    intervalMs = values?.getAsLong(TasksOrgContract.Reminders.INTERVAL_MS) ?: 0L,
                    placeId = values?.getAsLong(TasksOrgContract.Reminders.PLACE_ID) ?: 0L,
                )
                ContentUris.withAppendedId(TasksOrgContract.Reminders.CONTENT_URI, id)
            }
            "lists" -> {
                val id = nextInsertId++
                lists += ListRow(id, values?.getAsString(TasksOrgContract.Lists.TITLE).orEmpty(),
                    values?.getAsInteger(TasksOrgContract.Lists.COLOR) ?: 0, "owner",
                    values?.getAsLong(TasksOrgContract.Lists.ACCOUNT_ID) ?: 0L)
                ContentUris.withAppendedId(TasksOrgContract.Lists.CONTENT_URI, id)
            }
            "places" -> {
                val lat = values?.getAsDouble(TasksOrgContract.Places.LATITUDE) ?: 0.0
                val lon = values?.getAsDouble(TasksOrgContract.Places.LONGITUDE) ?: 0.0
                // Unique by coordinate: the same coordinates return the saved place.
                val existing = places.firstOrNull { it.latitude == lat && it.longitude == lon }
                val id = existing?.id ?: (nextInsertId++).also {
                    places += PlaceRow(it, values?.getAsString(TasksOrgContract.Places.NAME).orEmpty(), lat, lon,
                        values?.getAsString(TasksOrgContract.Places.ADDRESS).orEmpty(),
                        values?.getAsInteger(TasksOrgContract.Places.RADIUS) ?: 250)
                }
                ContentUris.withAppendedId(TasksOrgContract.Places.CONTENT_URI, id)
            }
            else -> ContentUris.withAppendedId(TasksOrgContract.Tasks.CONTENT_URI, nextInsertId++)
        }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        failWith?.let { throw it }
        if (uri.lastPathSegment?.toLongOrNull() in readOnlyTaskIds) {
            throw UnsupportedOperationException("read-only task")
        }
        deletes += uri
        // task_tags removal by natural key (?task_id=&tag_id=).
        if (uri.pathSegments.lastOrNull() == "task_tags") {
            val taskId = uri.getQueryParameter(TasksOrgContract.TaskTags.TASK_ID)?.toLong()
            val tagId = uri.getQueryParameter(TasksOrgContract.TaskTags.TAG_ID)?.toLong()
            if (taskId != null && tagId != null) {
                val removed = taskTags[taskId]?.remove(tagId) == true
                return if (removed) 1 else 0
            }
        }
        // lists, tags and places item URIs: /v0/<collection>/{id}
        val itemId = uri.lastPathSegment?.toLongOrNull()
        when (uri.pathSegments.getOrNull(1)) {
            "lists" -> return if (lists.removeAll { it.id == itemId }) 1 else 0
            "tags" -> return if (tags.removeAll { it.id == itemId }) 1 else 0
            "places" -> return if (places.removeAll { it.id == itemId }) 1 else 0
        }
        // reminders item URI: /v0/reminders/{id}
        val segs = uri.pathSegments
        if (segs.size == 3 && segs[1] == "reminders") {
            val id = segs[2].toLongOrNull()
            val removed = reminders.removeAll { it.id == id }
            return if (removed) 1 else 0
        }
        return 1
    }
}
