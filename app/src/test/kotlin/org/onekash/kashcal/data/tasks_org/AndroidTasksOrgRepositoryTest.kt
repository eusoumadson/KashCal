package org.onekash.kashcal.data.tasks_org

import android.app.Application
import android.content.ContentProvider
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
        repository = AndroidTasksOrgRepository(app)
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
    fun `without permissions nothing is queried or written`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).denyPermissions(TasksOrgContract.PERMISSION_READ, TasksOrgContract.PERMISSION_WRITE)

        assertTrue(repository.getTasksDueInRange(0L, Long.MAX_VALUE / 2, emptySet()).isEmpty())
        assertTrue(repository.getLists().isEmpty())
        assertFalse(repository.setCompleted(1L, 1L))
        assertTrue(provider.queries.isEmpty())
        assertTrue(provider.updates.isEmpty())
    }

    @Test
    fun `a refused query returns empty instead of throwing`() = runTest {
        provider.failWith = SecurityException("revoked")

        assertTrue(repository.getTasksDueInRange(0L, 1L, emptySet()).isEmpty())
        assertFalse(repository.setCompleted(1L, 1L))
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
    )

    data class ListRow(val id: Long, val title: String, val color: Int, val access: String)

    val tasks = mutableListOf<TaskRow>()
    val lists = mutableListOf<ListRow>()
    val queries = mutableListOf<Uri>()
    val updates = mutableListOf<Pair<Uri, ContentValues>>()
    var failWith: RuntimeException? = null

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
        return when (uri.pathSegments.last()) {
            "tasks" -> {
                val after = uri.getQueryParameter(TasksOrgContract.Tasks.PARAM_DUE_AFTER)?.toLong()
                val before = uri.getQueryParameter(TasksOrgContract.Tasks.PARAM_DUE_BEFORE)?.toLong()
                MatrixCursor(
                    arrayOf(
                        TasksOrgContract.ID, TasksOrgContract.Tasks.TITLE, TasksOrgContract.Tasks.NOTES,
                        TasksOrgContract.Tasks.DUE_DATE, TasksOrgContract.Tasks.DUE_ALL_DAY,
                        TasksOrgContract.Tasks.COMPLETED_AT, TasksOrgContract.Tasks.RECURRENCE,
                        TasksOrgContract.Tasks.LIST_ID, TasksOrgContract.Tasks.IS_READ_ONLY,
                    )
                ).apply {
                    // The real provider filters with dueDate > after AND dueDate < before, so an
                    // undated task (0) falls out of any positive range; keep it to test the mapping.
                    tasks.filter { t -> t.due == 0L || ((after == null || t.due > after) && (before == null || t.due < before)) }
                        .drop(offset).take(limit)
                        .forEach { t ->
                            addRow(arrayOf<Any>(t.id, t.title, t.notes, t.due, if (t.allDay) 1 else 0,
                                t.completedAt, t.recurrence, t.listId, 0))
                        }
                }
            }
            "lists" -> MatrixCursor(
                arrayOf(TasksOrgContract.ID, TasksOrgContract.Lists.TITLE, TasksOrgContract.Lists.COLOR, TasksOrgContract.Lists.ACCESS)
            ).apply {
                lists.drop(offset).take(limit).forEach { addRow(arrayOf<Any>(it.id, it.title, it.color, it.access)) }
            }
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        failWith?.let { throw it }
        updates += uri to ContentValues(values)
        return 1
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
}
