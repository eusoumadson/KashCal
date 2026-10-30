package org.onekash.kashcal.data.tasks_org

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.onekash.kashcal.util.DateTimeUtils
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [TasksOrgRepository] over the Tasks.org ContentProvider.
 *
 * This class owns only the ContentResolver mechanics — permissions, IO dispatch, error
 * handling and cursor paging. Every mapping to and from the `v0` columns and URIs lives in
 * [TasksOrgV0Mapper], so a contract change in the unstable API is absorbed there (report §6).
 */
@Singleton
class AndroidTasksOrgRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mapper: TasksOrgV0Mapper
) : TasksOrgRepository {

    companion object {
        private const val TAG = "TasksOrgRepo"
    }

    private val contentResolver get() = context.contentResolver

    // resolveContentProvider rather than the package: an old Tasks.org without the API has
    // nothing to read. Needs the <queries> entry for org.tasks on Android 11+.
    override fun isInstalled(): Boolean =
        context.packageManager.resolveContentProvider(TasksOrgContract.AUTHORITY, 0) != null

    override fun hasReadPermission(): Boolean = granted(TasksOrgContract.PERMISSION_READ)

    override fun hasWritePermission(): Boolean = granted(TasksOrgContract.PERMISSION_WRITE)

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    override suspend fun getLists(): List<TasksOrgList> = withContext(Dispatchers.IO) {
        if (!hasReadPermission()) return@withContext emptyList()
        readPages(TasksOrgContract.Lists.CONTENT_URI) { cursor -> mapper.readList(cursor) }
    }

    override suspend fun getTasksDueInRange(
        startMs: Long,
        endMs: Long,
        hiddenListIds: Set<Long>,
        search: String?
    ): List<TasksOrgTask> = withContext(Dispatchers.IO) {
        if (!hasReadPermission()) return@withContext emptyList()
        val lists = getLists().associateBy { it.id }
        // The provider's bounds are exclusive.
        val uri = TasksOrgContract.Tasks.CONTENT_URI.buildUpon()
            .appendQueryParameter(TasksOrgContract.Tasks.PARAM_DUE_AFTER, (startMs - 1).toString())
            .appendQueryParameter(TasksOrgContract.Tasks.PARAM_DUE_BEFORE, (endMs + 1).toString())
            .apply { search?.let { appendQueryParameter(TasksOrgContract.Tasks.PARAM_SEARCH, it) } }
            .build()
        readPages(uri) { cursor -> mapper.readTask(cursor, lists) }
            // Offset pages aren't a snapshot: a row that moves during the read can come twice.
            .distinctBy { it.id }
            .filter { it.listId !in hiddenListIds }
            .sortedBy { it.startTs }
    }

    override suspend fun setCompleted(taskId: Long, completedAt: Long?): Boolean =
        withContext(Dispatchers.IO) {
            if (!hasWritePermission()) return@withContext false
            guarded("update task $taskId", false) {
                update(taskId, mapper.completionValues(completedAt)) > 0
            }
        }

    override suspend fun getSchedule(taskId: Long): TasksOrgSchedule? = withContext(Dispatchers.IO) {
        if (!hasReadPermission()) return@withContext null
        guarded("read task $taskId", null) {
            contentResolver.query(TasksOrgContract.Tasks.itemUri(taskId), null, null, null, null)
                ?.use { mapper.readSchedule(it) }
        }
    }

    override suspend fun restoreSchedule(taskId: Long, schedule: TasksOrgSchedule): Boolean =
        withContext(Dispatchers.IO) {
            if (!hasWritePermission()) return@withContext false
            guarded("restore task $taskId", false) {
                update(taskId, mapper.scheduleValues(schedule)) > 0
            }
        }

    override suspend fun createTask(draft: TasksOrgTaskDraft): TasksOrgWriteResult =
        withContext(Dispatchers.IO) {
            if (!hasWritePermission()) return@withContext TasksOrgWriteResult.Refused
            writeGuarded("create task") {
                val uri = contentResolver.insert(
                    TasksOrgContract.Tasks.CONTENT_URI, mapper.insertValues(draft)
                ) ?: return@writeGuarded TasksOrgWriteResult.Refused
                TasksOrgWriteResult.Ok(ContentUris.parseId(uri))
            }
        }

    override suspend fun updateTask(taskId: Long, patch: TasksOrgTaskPatch): TasksOrgWriteResult =
        withContext(Dispatchers.IO) {
            if (patch.isEmpty) return@withContext TasksOrgWriteResult.Ok(taskId)
            if (!hasWritePermission()) return@withContext TasksOrgWriteResult.Refused
            writeGuarded("update task $taskId") {
                if (update(taskId, mapper.updateValues(patch)) > 0) {
                    TasksOrgWriteResult.Ok(taskId)
                } else {
                    TasksOrgWriteResult.Refused
                }
            }
        }

    override suspend fun deleteTask(taskId: Long): TasksOrgWriteResult = withContext(Dispatchers.IO) {
        if (!hasWritePermission()) return@withContext TasksOrgWriteResult.Refused
        writeGuarded("delete task $taskId") {
            if (contentResolver.delete(TasksOrgContract.Tasks.itemUri(taskId), null, null) > 0) {
                TasksOrgWriteResult.Ok(taskId)
            } else {
                TasksOrgWriteResult.Refused
            }
        }
    }

    override suspend fun getTags(): List<TasksOrgTag> = withContext(Dispatchers.IO) {
        if (!hasReadPermission()) return@withContext emptyList()
        readPages(TasksOrgContract.Tags.CONTENT_URI) { cursor -> mapper.readTag(cursor) }
    }

    override suspend fun getTagIdsForTask(taskId: Long): List<Long> = withContext(Dispatchers.IO) {
        if (!hasReadPermission()) return@withContext emptyList()
        guarded("read tags of task $taskId", emptyList()) {
            contentResolver.query(
                TasksOrgContract.Tasks.itemUri(taskId),
                arrayOf(TasksOrgContract.Tasks.TAG_IDS), null, null, null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) emptyList() else mapper.readTagIds(cursor)
            } ?: emptyList()
        }
    }

    override suspend fun ensureTag(name: String): Long? = withContext(Dispatchers.IO) {
        if (!hasWritePermission()) return@withContext null
        guarded("create tag", null) {
            // Insert is idempotent: an existing name returns that tag's URI.
            val uri = contentResolver.insert(TasksOrgContract.Tags.CONTENT_URI, mapper.tagValues(name))
            uri?.let { ContentUris.parseId(it) }
        }
    }

    override suspend fun addTagToTask(taskId: Long, tagId: Long): Boolean = withContext(Dispatchers.IO) {
        if (!hasWritePermission()) return@withContext false
        guarded("tag task $taskId", false) {
            // Inserting an existing pair returns the existing row; treat that as success.
            contentResolver.insert(
                TasksOrgContract.TaskTags.CONTENT_URI, mapper.taskTagValues(taskId, tagId)
            ) != null
        }
    }

    override suspend fun removeTagFromTask(taskId: Long, tagId: Long): Boolean = withContext(Dispatchers.IO) {
        if (!hasWritePermission()) return@withContext false
        guarded("untag task $taskId", false) {
            contentResolver.delete(TasksOrgContract.TaskTags.deleteUri(taskId, tagId), null, null) > 0
        }
    }

    override suspend fun getReminders(taskId: Long): List<TasksOrgReminder> = withContext(Dispatchers.IO) {
        if (!hasReadPermission()) return@withContext emptyList()
        readPages(TasksOrgContract.Reminders.forTask(taskId)) { cursor -> mapper.readReminder(cursor) }
    }

    override suspend fun addReminder(taskId: Long, reminder: TasksOrgReminder): Long? = withContext(Dispatchers.IO) {
        if (!hasWritePermission()) return@withContext null
        guarded("add reminder to task $taskId", null) {
            val uri = contentResolver.insert(
                TasksOrgContract.Reminders.CONTENT_URI,
                mapper.reminderInsertValues(taskId, reminder)
            )
            uri?.let { ContentUris.parseId(it) }
        }
    }

    override suspend fun deleteReminder(reminderId: Long): Boolean = withContext(Dispatchers.IO) {
        if (!hasWritePermission()) return@withContext false
        guarded("delete reminder $reminderId", false) {
            contentResolver.delete(TasksOrgContract.Reminders.itemUri(reminderId), null, null) > 0
        }
    }

    private fun update(taskId: Long, values: ContentValues): Int =
        contentResolver.update(TasksOrgContract.Tasks.itemUri(taskId), values, null, null)

    /**
     * Reads every row of the collection [uri], paging by [TasksOrgContract.MAX_LIMIT]; empty
     * when the provider refuses the query.
     */
    private fun <T> readPages(uri: Uri, map: (Cursor) -> T?): List<T> =
        guarded("query $uri", emptyList()) {
            val results = mutableListOf<T>()
            var offset = 0
            while (true) {
                val page = uri.buildUpon()
                    .appendQueryParameter(TasksOrgContract.PARAM_LIMIT, TasksOrgContract.MAX_LIMIT.toString())
                    .appendQueryParameter(TasksOrgContract.PARAM_OFFSET, offset.toString())
                    .build()
                val count = contentResolver.query(page, null, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) map(cursor)?.let(results::add)
                    cursor.count
                } ?: 0
                if (count < TasksOrgContract.MAX_LIMIT) break
                offset += count
            }
            results
        }

    /**
     * Runs [block], returning [fallback] when Tasks.org refuses the call: a revoked permission
     * (SecurityException), a query it doesn't support (IllegalArgumentException), or the app
     * going away mid-call.
     */
    private inline fun <T> guarded(what: String, fallback: T, block: () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Tasks.org refused $what: ${e.javaClass.simpleName}: ${e.message}")
            fallback
        }

    /**
     * Runs a write [block], turning an [UnsupportedOperationException] (a read-only list or
     * task) into [TasksOrgWriteResult.ReadOnly] and any other refusal into
     * [TasksOrgWriteResult.Refused]. Separating the two lets the UI explain a read-only target
     * instead of a generic failure (report §4.4).
     */
    private inline fun writeGuarded(
        what: String,
        block: () -> TasksOrgWriteResult
    ): TasksOrgWriteResult =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: UnsupportedOperationException) {
            Log.w(TAG, "Tasks.org refused $what: read-only target")
            TasksOrgWriteResult.ReadOnly
        } catch (e: Exception) {
            Log.w(TAG, "Tasks.org refused $what: ${e.javaClass.simpleName}: ${e.message}")
            TasksOrgWriteResult.Refused
        }
}

/**
 * Builds a [TasksOrgTask] placed at [dueTs] with the Room event time conventions (see
 * [TasksOrgTask]). An all-day due date is read in [zone], where Tasks.org stores it.
 */
internal fun taskAt(
    id: Long,
    title: String,
    notes: String,
    dueTs: Long,
    isAllDay: Boolean,
    completedAt: Long?,
    recurrence: String?,
    listId: Long,
    listTitle: String,
    listColor: Int,
    isReadOnly: Boolean,
    priority: TasksOrgPriority = TasksOrgPriority.NONE,
    zone: ZoneId = ZoneId.systemDefault()
): TasksOrgTask {
    val (startTs, endTs) = if (isAllDay) {
        val dayStart = Instant.ofEpochMilli(dueTs).atZone(zone).toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        dayStart to dayStart + 24L * 60 * 60 * 1000 - 1
    } else {
        dueTs to dueTs
    }
    return TasksOrgTask(
        id = id,
        title = title,
        notes = notes,
        dueTs = dueTs,
        isAllDay = isAllDay,
        startTs = startTs,
        endTs = endTs,
        startDay = DateTimeUtils.eventTsToDayCode(startTs, isAllDay, zone),
        endDay = DateTimeUtils.eventTsToEndDayCode(endTs = endTs, startTs = startTs, isAllDay = isAllDay, localZone = zone),
        completedAt = completedAt,
        recurrence = recurrence,
        listId = listId,
        listTitle = listTitle,
        listColor = listColor,
        isReadOnly = isReadOnly,
        priority = priority
    )
}
