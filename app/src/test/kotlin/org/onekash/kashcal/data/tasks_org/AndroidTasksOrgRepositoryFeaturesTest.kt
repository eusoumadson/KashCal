package org.onekash.kashcal.data.tasks_org

import android.app.Application
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
 * The fork's richer repository behavior against [FakeTasksOrgProvider]: what a task read carries
 * (tags, priority, subtasks, place, start), overdue tasks left on their own day, the atomic save that
 * writes a task with its tags, reminders and place in one batch, management of lists, tags and
 * places, accounts, and the installed version.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class AndroidTasksOrgRepositoryFeaturesTest {

    private lateinit var provider: FakeTasksOrgProvider
    private lateinit var repository: AndroidTasksOrgRepository
    private val zone = ZoneId.of("America/Sao_Paulo")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0) =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Before
    fun setup() {
        provider = Robolectric.setupContentProvider(FakeTasksOrgProvider::class.java, TasksOrgContract.AUTHORITY)
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(TasksOrgContract.PERMISSION_READ, TasksOrgContract.PERMISSION_WRITE)
        repository = AndroidTasksOrgRepository(app, TasksOrgV0Mapper().apply { zone = this@AndroidTasksOrgRepositoryFeaturesTest.zone })
        provider.lists += FakeTasksOrgProvider.ListRow(id = 1, title = "Inbox", color = 0, access = "owner", accountId = 4)
        provider.lists += FakeTasksOrgProvider.ListRow(id = 2, title = "Shared", color = 0, access = "read_only", accountId = 4)
    }

    // ------------------------------------------------------------------ reading a task

    @Test
    fun `a task carries its tags, priority, subtasks, place, start and repeat basis`() = runTest {
        provider.tags += FakeTasksOrgProvider.TagRow(5, "work")
        provider.tags += FakeTasksOrgProvider.TagRow(6, "home")
        provider.taskTags[10] = mutableSetOf(5L, 6L)
        val start = at(2026, 10, 28)
        provider.tasks += FakeTasksOrgProvider.TaskRow(
            id = 10, title = "Report", due = at(2026, 10, 30, 9), listId = 1,
            recurrence = "FREQ=WEEKLY", priority = "high", placeId = 3,
            childCount = 3, uncompletedChildCount = 1,
            startDate = start, startAllDay = true, repeatFrom = "completion_date"
        )

        val task = repository.getTask(10L)!!

        assertEquals(TasksOrgPriority.HIGH, task.priority)
        assertEquals(setOf("work", "home"), task.tags.toSet())
        assertEquals(3, task.childCount)
        assertEquals(1, task.uncompletedChildCount)
        assertTrue(task.hasSubtasks)
        assertEquals(3L, task.placeId)
        assertEquals(TasksOrgDate(start, allDay = true), task.hideUntil)
        assertEquals(TasksOrgRepeatFrom.COMPLETION_DATE, task.repeatFrom)
    }

    @Test
    fun `a task that can't be read or has no due date is null`() = runTest {
        provider.tasks += FakeTasksOrgProvider.TaskRow(id = 11, title = "Someday", due = 0, listId = 1)
        assertNull(repository.getTask(11L))
        assertNull(repository.getTask(999L))
    }

    // ------------------------------------------------------------------ overdue

    private val now get() = at(2026, 10, 30, 12)

    private fun seedOverdueScenario() {
        // Overdue all-day task from last week.
        provider.tasks += FakeTasksOrgProvider.TaskRow(id = 1, title = "Late rent", due = at(2026, 10, 25), listId = 1, allDay = true)
        // Due later today.
        provider.tasks += FakeTasksOrgProvider.TaskRow(id = 2, title = "Today 5pm", due = at(2026, 10, 30, 17), listId = 1)
        // Overdue and completed.
        provider.tasks += FakeTasksOrgProvider.TaskRow(id = 3, title = "Done late", due = at(2026, 10, 20), listId = 1, completedAt = at(2026, 10, 21))
    }

    @Test
    fun `an overdue task is not shown on today`() = runTest {
        seedOverdueScenario()

        val tasks = repository.getTasksDueInRange(at(2026, 10, 30), at(2026, 10, 30, 23, 59), emptySet())

        assertEquals(listOf(2L), tasks.map { it.id })
    }

    @Test
    fun `an overdue task stays on its scheduled day`() = runTest {
        seedOverdueScenario()

        val tasks = repository.getTasksDueInRange(at(2026, 10, 25), at(2026, 10, 25, 23, 59), emptySet())

        val late = tasks.single()
        assertEquals(1L, late.id)
        assertEquals(20261025, late.startDay)
        assertEquals(at(2026, 10, 25), late.dueTs)
        assertTrue(late.isAllDay)
    }

    @Test
    fun `reading a range makes no query beyond the range itself`() = runTest {
        seedOverdueScenario()

        repository.getTasksDueInRange(at(2026, 10, 30), at(2026, 10, 30, 23, 59), emptySet())

        val taskQueries = provider.queries.filter { it.pathSegments.last() == "tasks" }
        assertEquals(1, taskQueries.size)
        assertEquals((at(2026, 10, 30) - 1).toString(), taskQueries.single().getQueryParameter(TasksOrgContract.Tasks.PARAM_DUE_AFTER))
    }

    // ------------------------------------------------------------------ atomic create

    private val draft = TasksOrgTaskDraft(title = "Pay rent", listId = 1, due = TasksOrgDate(at(2026, 11, 1, 9), allDay = false))

    @Test
    fun `create writes the task with its tags, reminders and place in order`() = runTest {
        provider.tags += FakeTasksOrgProvider.TagRow(5, "work")
        provider.nextInsertId = 200L
        val extras = TasksOrgTaskExtras(
            tagIds = setOf(5L),
            newTagNames = listOf("fresh"),
            reminders = listOf(TasksOrgReminder.Relative(anchor = TasksOrgReminder.Relative.Anchor.DUE, offsetMs = -600_000L)),
            location = TasksOrgLocation(placeId = 9L, arrival = true, departure = true)
        )

        val result = repository.createTaskWithExtras(draft, extras)

        // The new tag took id 200 and the task 201; every later row points at them.
        assertEquals(TasksOrgWriteResult.Ok(201L), result)
        assertEquals(
            listOf("tags", "tasks", "task_tags", "task_tags", "reminders", "reminders", "reminders"),
            provider.inserts.map { it.first.lastPathSegment }
        )
        assertEquals(setOf(5L, 200L), provider.taskTags[201L])
        val rows = provider.reminders.filter { it.taskId == 201L }
        assertEquals(listOf("relative_due", "location_arrival", "location_departure"), rows.map { it.type })
        assertEquals(listOf(0L, 9L, 9L), rows.map { it.placeId })
        assertEquals(-600_000L, rows.first().offsetMs)
    }

    @Test
    fun `create with a place and no trigger files the task under the place directly`() = runTest {
        val extras = TasksOrgTaskExtras(location = TasksOrgLocation(placeId = 9L, arrival = false, departure = false))

        repository.createTaskWithExtras(draft, extras)

        val taskInsert = provider.inserts.single { it.first.lastPathSegment == "tasks" }.second
        assertEquals(9L, taskInsert.getAsLong(TasksOrgContract.Tasks.PLACE_ID))
        assertTrue(provider.reminders.isEmpty())
    }

    @Test
    fun `create without write permission is refused and writes nothing`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).denyPermissions(TasksOrgContract.PERMISSION_WRITE)

        assertEquals(TasksOrgWriteResult.Refused, repository.createTaskWithExtras(draft, TasksOrgTaskExtras()))
        assertTrue(provider.inserts.isEmpty())
    }

    @Test
    fun `create on a read-only list reports read-only`() = runTest {
        provider.failWith = UnsupportedOperationException("read-only list")

        assertEquals(TasksOrgWriteResult.ReadOnly, repository.createTaskWithExtras(draft, TasksOrgTaskExtras()))
    }

    // ------------------------------------------------------------------ atomic update

    private fun seedTask10() {
        provider.tasks += FakeTasksOrgProvider.TaskRow(id = 10, title = "Old", due = at(2026, 11, 1, 9), listId = 1)
        provider.nextInsertId = 300L
    }

    private val titleChange = TasksOrgTaskPatch(title = TasksOrgTaskPatch.Field.Value("New"))

    @Test
    fun `update patches the task and diffs its tags and reminders`() = runTest {
        seedTask10()
        provider.taskTags[10L] = mutableSetOf(1L, 2L)
        provider.reminders += FakeTasksOrgProvider.ReminderRow(40, 10, "relative_due", offsetMs = -600_000L)
        provider.reminders += FakeTasksOrgProvider.ReminderRow(41, 10, "date_time", triggerAt = 5000L)
        val extras = TasksOrgTaskExtras(
            tagIds = setOf(2L, 3L),
            // 40 is kept, 41 is removed, and a start-relative reminder is added.
            reminders = listOf(
                TasksOrgReminder.Relative(id = 40, anchor = TasksOrgReminder.Relative.Anchor.DUE, offsetMs = -600_000L),
                TasksOrgReminder.Relative(anchor = TasksOrgReminder.Relative.Anchor.START, offsetMs = 0L)
            )
        )

        val result = repository.updateTaskWithExtras(10L, titleChange, extras)

        assertEquals(TasksOrgWriteResult.Ok(10L), result)
        assertEquals("New", provider.updates.single { it.first.lastPathSegment == "10" }.second.getAsString("title"))
        assertEquals(setOf(2L, 3L), provider.taskTags[10L])
        val remaining = provider.reminders.filter { it.taskId == 10L }
        assertEquals(2, remaining.size)
        assertTrue(remaining.any { it.id == 40L && it.type == "relative_due" })
        assertTrue(remaining.any { it.id != 40L && it.type == "relative_start" })
        assertTrue(remaining.none { it.id == 41L })
    }

    @Test
    fun `update with nothing to change writes nothing`() = runTest {
        seedTask10()

        val result = repository.updateTaskWithExtras(10L, TasksOrgTaskPatch(), TasksOrgTaskExtras())

        assertEquals(TasksOrgWriteResult.Ok(10L), result)
        assertTrue(provider.updates.isEmpty())
        assertTrue(provider.inserts.isEmpty())
        assertTrue(provider.deletes.isEmpty())
    }

    @Test
    fun `update of a task that is gone is refused`() = runTest {
        seedTask10()
        provider.missingTaskIds += 10L

        assertEquals(TasksOrgWriteResult.Refused, repository.updateTaskWithExtras(10L, titleChange, TasksOrgTaskExtras()))
    }

    @Test
    fun `update of a read-only task reports read-only`() = runTest {
        seedTask10()
        provider.readOnlyTaskIds += 10L

        assertEquals(TasksOrgWriteResult.ReadOnly, repository.updateTaskWithExtras(10L, titleChange, TasksOrgTaskExtras()))
    }

    @Test
    fun `moving a task to another place clears the old one first, then adds the new triggers`() = runTest {
        provider.tasks += FakeTasksOrgProvider.TaskRow(id = 10, title = "T", due = at(2026, 11, 1), listId = 1, placeId = 3)
        provider.reminders += FakeTasksOrgProvider.ReminderRow(50, 10, "location_arrival", placeId = 3)
        val extras = TasksOrgTaskExtras(location = TasksOrgLocation(placeId = 9L, arrival = true, departure = false))

        repository.updateTaskWithExtras(10L, TasksOrgTaskPatch(), extras)

        val placeWrites = provider.updates.filter { it.second.containsKey(TasksOrgContract.Tasks.PLACE_ID) }
        assertEquals(listOf(0L), placeWrites.map { it.second.getAsLong(TasksOrgContract.Tasks.PLACE_ID) })
        val inserted = provider.inserts.single { it.first.lastPathSegment == "reminders" }.second
        assertEquals("location_arrival", inserted.getAsString(TasksOrgContract.Reminders.TYPE))
        assertEquals(9L, inserted.getAsLong(TasksOrgContract.Reminders.PLACE_ID))
        // The write that clears the place precedes the insert that files the new one.
        assertTrue(provider.updates.isNotEmpty() && provider.inserts.isNotEmpty())
    }

    @Test
    fun `the same place only adds and removes the triggers that changed`() = runTest {
        provider.tasks += FakeTasksOrgProvider.TaskRow(id = 10, title = "T", due = at(2026, 11, 1), listId = 1, placeId = 3)
        provider.reminders += FakeTasksOrgProvider.ReminderRow(50, 10, "location_arrival", placeId = 3)
        provider.nextInsertId = 300L
        val extras = TasksOrgTaskExtras(location = TasksOrgLocation(placeId = 3L, arrival = false, departure = true))

        repository.updateTaskWithExtras(10L, TasksOrgTaskPatch(), extras)

        assertTrue(provider.updates.isEmpty())
        assertEquals(listOf("location_departure"), provider.reminders.filter { it.taskId == 10L }.map { it.type })
    }

    @Test
    fun `removing the place clears it, which also drops its reminders`() = runTest {
        provider.tasks += FakeTasksOrgProvider.TaskRow(id = 10, title = "T", due = at(2026, 11, 1), listId = 1, placeId = 3)
        provider.reminders += FakeTasksOrgProvider.ReminderRow(50, 10, "location_arrival", placeId = 3)

        repository.updateTaskWithExtras(10L, TasksOrgTaskPatch(), TasksOrgTaskExtras(location = null))

        val cleared = provider.updates.single().second
        assertEquals(0L, cleared.getAsLong(TasksOrgContract.Tasks.PLACE_ID))
    }

    @Test
    fun `snooze and random reminders are kept unless the editor removes them`() = runTest {
        seedTask10()
        provider.reminders += FakeTasksOrgProvider.ReminderRow(60, 10, "random", offsetMs = 86_400_000L)
        provider.reminders += FakeTasksOrgProvider.ReminderRow(61, 10, "snooze", triggerAt = 9000L)
        val keepBoth = TasksOrgTaskExtras(
            reminders = listOf(TasksOrgReminder.Random(id = 60, periodMs = 86_400_000L), TasksOrgReminder.Snooze(id = 61, triggerAtMs = 9000L))
        )

        repository.updateTaskWithExtras(10L, titleChange, keepBoth)
        assertEquals(setOf(60L, 61L), provider.reminders.map { it.id }.toSet())

        // Dropping the snooze from the editor's list deletes it (that is how it is cleared).
        repository.updateTaskWithExtras(10L, titleChange, TasksOrgTaskExtras(reminders = listOf(TasksOrgReminder.Random(id = 60, periodMs = 86_400_000L))))
        assertEquals(setOf(60L), provider.reminders.map { it.id }.toSet())
    }

    // ------------------------------------------------------------------ management

    @Test
    fun `a list is created, renamed and deleted`() = runTest {
        provider.nextInsertId = 70L

        assertEquals(TasksOrgWriteResult.Ok(70L), repository.createList(accountId = 4L, title = "Groceries", color = 0xFF00FF00.toInt()))
        val created = provider.inserts.single { it.first.lastPathSegment == "lists" }.second
        assertEquals(4L, created.getAsLong(TasksOrgContract.Lists.ACCOUNT_ID))
        assertEquals("Groceries", created.getAsString(TasksOrgContract.Lists.TITLE))

        assertEquals(TasksOrgWriteResult.Ok(70L), repository.updateList(70L, title = "Shopping"))
        assertEquals("Shopping", provider.lists.single { it.id == 70L }.title)

        assertEquals(TasksOrgWriteResult.Ok(70L), repository.deleteList(70L))
        assertTrue(provider.lists.none { it.id == 70L })
    }

    @Test
    fun `renaming or deleting a read-only list reports read-only`() = runTest {
        provider.readOnlyTaskIds += 2L

        assertEquals(TasksOrgWriteResult.ReadOnly, repository.updateList(2L, title = "nope"))
        assertEquals(TasksOrgWriteResult.ReadOnly, repository.deleteList(2L))
    }

    @Test
    fun `an update with nothing to change is not sent`() = runTest {
        assertEquals(TasksOrgWriteResult.Ok(1L), repository.updateList(1L))
        assertTrue(provider.updates.isEmpty())
    }

    @Test
    fun `a tag is renamed, recolored and deleted`() = runTest {
        provider.tags += FakeTasksOrgProvider.TagRow(5, "work")

        assertEquals(TasksOrgWriteResult.Ok(5L), repository.updateTag(5L, name = "ops", color = 0xFF112233.toInt()))
        assertEquals("ops", provider.tags.single().name)

        assertEquals(TasksOrgWriteResult.Ok(5L), repository.deleteTag(5L))
        assertTrue(provider.tags.isEmpty())
    }

    @Test
    fun `renaming a tag onto another tag's name is refused`() = runTest {
        provider.failWith = IllegalArgumentException("a tag named that exists: 6")

        assertEquals(TasksOrgWriteResult.Refused, repository.updateTag(5L, name = "home"))
    }

    @Test
    fun `places are listed, created idempotently by coordinates, edited and deleted`() = runTest {
        provider.nextInsertId = 90L
        val home = TasksOrgPlaceDraft(name = "Home", address = "221B Baker St", latitude = 51.5237, longitude = -0.1585, radius = 150)

        assertEquals(TasksOrgWriteResult.Ok(90L), repository.createPlace(home))
        // The same coordinates return the saved place instead of a second one.
        assertEquals(TasksOrgWriteResult.Ok(90L), repository.createPlace(home.copy(name = "Home again")))

        val place = repository.getPlaces().single()
        assertEquals("Home", place.name)
        assertEquals(51.5237, place.latitude, 0.0)
        assertEquals(-0.1585, place.longitude, 0.0)
        assertEquals(150, place.radius)

        assertEquals(TasksOrgWriteResult.Ok(90L), repository.updatePlace(90L, name = "Flat", radius = 300))
        assertEquals(300, repository.getPlaces().single().radius)

        assertEquals(TasksOrgWriteResult.Ok(90L), repository.deletePlace(90L))
        assertTrue(repository.getPlaces().isEmpty())
    }

    @Test
    fun `accounts report their health`() = runTest {
        provider.accounts += FakeTasksOrgProvider.AccountRow(1, "Local", "local")
        provider.accounts += FakeTasksOrgProvider.AccountRow(2, "me@nextcloud", "caldav", error = "unauthorized", repeatsOnServer = true)

        val accounts = repository.getAccounts()

        assertEquals(2, accounts.size)
        assertTrue(accounts[0].isLocal)
        assertFalse(accounts[0].needsAttention)
        assertTrue(accounts[1].needsAttention)
        assertEquals("unauthorized", accounts[1].error)
        assertTrue(accounts[1].repeatsOnServer)
    }

    @Test
    fun `management and reads are empty or refused without permission`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).denyPermissions(TasksOrgContract.PERMISSION_READ, TasksOrgContract.PERMISSION_WRITE)

        assertTrue(repository.getPlaces().isEmpty())
        assertTrue(repository.getAccounts().isEmpty())
        assertNull(repository.getTask(1L))
        assertEquals(TasksOrgWriteResult.Refused, repository.createList(1L, "x"))
        assertEquals(TasksOrgWriteResult.Refused, repository.deletePlace(1L))
    }

    // ------------------------------------------------------------------ version

    @Test
    fun `the installed version is read and checked against the first API release`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertNull(repository.installedVersion())

        Shadows.shadowOf(app.packageManager).installPackage(PackageInfo().apply {
            packageName = TasksOrgContract.PACKAGE
            versionName = "15.13"
            @Suppress("DEPRECATION")
            versionCode = 15130
        })

        val version = repository.installedVersion()
        assertNotNull(version)
        assertEquals("15.13", version!!.name)
        assertTrue(version.isSupported)
    }

    @Test
    fun `a version older than 15_12 is not supported, a beta or patch name is parsed`() {
        assertFalse(TasksOrgVersion("15.11", 1).isSupported)
        assertFalse(TasksOrgVersion("14.9.2", 1).isSupported)
        assertTrue(TasksOrgVersion("15.12", 1).isSupported)
        assertTrue(TasksOrgVersion("15.13-beta03", 1).isSupported)
        assertTrue(TasksOrgVersion("16.0", 1).isSupported)
        // A name we can't parse is not blamed.
        assertTrue(TasksOrgVersion("dev-build", 1).isSupported)
    }
}
