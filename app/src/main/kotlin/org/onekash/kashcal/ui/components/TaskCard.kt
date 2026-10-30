package org.onekash.kashcal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay
import org.onekash.kashcal.R
import org.onekash.kashcal.data.tasks_org.TasksOrgContract
import org.onekash.kashcal.data.tasks_org.TasksOrgPriority
import org.onekash.kashcal.domain.EmojiMatcher
import org.onekash.kashcal.domain.model.DisplayEvent
import org.onekash.kashcal.util.DateTimeUtils
import java.time.ZoneId

/**
 * Completes a Tasks.org task from a list checkbox or reopens it. The last parameter reports
 * whether Tasks.org took the write, so the checkbox, which flips at once, can flip back.
 */
typealias OnTaskCheckedChange = (task: DisplayEvent.Task, completed: Boolean, onResult: (Boolean) -> Unit) -> Unit

/**
 * Whether KashCal holds the Tasks.org write permission, which completing a task needs. Re-read
 * on resume, since the permission can be granted or revoked in system settings meanwhile.
 */
@Composable
internal fun rememberCanWriteTasksOrg(): Boolean {
    val context = LocalContext.current
    val read = {
        ContextCompat.checkSelfPermission(context, TasksOrgContract.PERMISSION_WRITE) ==
            PackageManager.PERMISSION_GRANTED
    }
    var canWrite by remember { mutableStateOf(read()) }
    LifecycleResumeEffect(Unit) {
        canWrite = read()
        onPauseOrDispose { }
    }
    return canWrite
}

/** How long a [TaskCard] checkbox waits for the re-query after a write before it unlocks. */
private const val PENDING_TIMEOUT_MS = 3_000L

/**
 * Shows a Tasks.org task as a one-line card in the day and agenda lists: a checkbox that
 * completes or reopens it in Tasks.org ([onCheckedChange]), its title and, at the end, the due
 * time ([formatTaskCardTime]). Tapping elsewhere opens the task quick view ([onClick]). The
 * checkbox stands for the ☐/☑ title prefix the other views use, so the title has none.
 *
 * The checkbox is disabled for a task in a read-only list and without the write permission
 * ([rememberCanWriteTasksOrg]), and ignores taps while a change waits for Tasks.org. A completed
 * task is struck through and dimmed; an open task past its due date is not, since it still
 * needs doing.
 *
 * @param supportingText an optional line under the title, such as the search results' date;
 *   it replaces the due time at the end
 */
@Composable
internal fun TaskCard(
    displayEvent: DisplayEvent.Task,
    showEventEmojis: Boolean,
    timePattern: String,
    onCheckedChange: OnTaskCheckedChange,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    val task = displayEvent.task
    val canWrite = rememberCanWriteTasksOrg()
    // Flips at once; the re-query after the write brings the task's real state, which resets
    // both. [pending] blocks another tap until then: a repeating task's re-query moves it to its
    // next date, and a tap on the stale row would reopen nothing.
    val stateKey = Triple(displayEvent.stableKey, task.completedAt, task.dueTs)
    var checkedOverride by remember(stateKey) { mutableStateOf<Boolean?>(null) }
    var pending by remember(stateKey) { mutableStateOf(false) }
    if (pending) {
        // A write that changed nothing in the re-read row (or a re-query that never came)
        // mustn't leave the checkbox stuck.
        LaunchedEffect(stateKey) {
            delay(PENDING_TIMEOUT_MS)
            pending = false
            checkedOverride = null
        }
    }
    val checked = checkedOverride ?: task.isCompleted
    val title = remember(task.title, showEventEmojis) {
        EmojiMatcher.formatWithEmoji(task.title, showEventEmojis)
    }
    val trailing = remember(task.startTs, task.isAllDay, task.isRecurring, timePattern, supportingText) {
        if (supportingText != null) "" else formatTaskCardTime(task.startTs, task.isAllDay, task.isRecurring, timePattern)
    }
    val meta = remember(task.tags, task.childCount, task.uncompletedChildCount) {
        formatTaskMeta(task.tags, task.childCount, task.uncompletedChildCount)
    }
    val priorityColor = priorityColor(task.priority)
    val priorityDescription = when (task.priority) {
        TasksOrgPriority.HIGH -> stringResource(R.string.task_priority_dot_high)
        TasksOrgPriority.MEDIUM -> stringResource(R.string.task_priority_dot_medium)
        TasksOrgPriority.LOW -> stringResource(R.string.task_priority_dot_low)
        TasksOrgPriority.NONE -> ""
    }
    val stripeColor = Color(displayEvent.calendarColor)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (checked) 0.6f else 1f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = stripeColor.copy(alpha = displayEvent.cardFillAlpha())),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(stripeColor.copy(alpha = 0.4f))
            )
            // The Checkbox keeps its 48dp touch target. Labelled with the title, since TalkBack
            // reads it apart from the card.
            Checkbox(
                checked = checked,
                enabled = canWrite && !task.isReadOnly,
                modifier = Modifier.semantics { contentDescription = title },
                onCheckedChange = { completed ->
                    // Ignored rather than disabled while pending, so the box doesn't grey out
                    // for the moment the write takes.
                    if (pending) return@Checkbox
                    checkedOverride = completed
                    pending = true
                    onCheckedChange(displayEvent, completed) { written ->
                        if (!written) {
                            checkedOverride = null
                            pending = false
                        }
                    }
                }
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (priorityColor != null) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(priorityColor)
                                .semantics { contentDescription = priorityDescription }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (checked) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (supportingText != null) {
                    Text(
                        supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (trailing.isNotEmpty()) {
                Text(
                    trailing,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp, end = 12.dp)
                )
            }
        }
    }
}

/**
 * Formats the end of a [TaskCard]: the due time of a timed task, nothing for an all-day one,
 * and the repeat marker 🔁 for a repeating task. Empty when there is nothing to show.
 */
internal fun formatTaskCardTime(
    dueTs: Long,
    isAllDay: Boolean,
    isRecurring: Boolean,
    timePattern: String,
    zoneId: ZoneId = ZoneId.systemDefault()
): String {
    val time = DateTimeUtils.formatEventTime(dueTs, isAllDay, timePattern, zoneId)
    val repeat = if (isRecurring) "\uD83D\uDD01" else ""
    return listOf(time, repeat).filter { it.isNotEmpty() }.joinToString(" ")
}

/** The dot color of a priority, or null for none (no dot). */
internal fun priorityColor(priority: TasksOrgPriority): Color? = when (priority) {
    TasksOrgPriority.HIGH -> Color(0xFFE53935)
    TasksOrgPriority.MEDIUM -> Color(0xFFFB8C00)
    TasksOrgPriority.LOW -> Color(0xFF1E88E5)
    TasksOrgPriority.NONE -> null
}

/**
 * The secondary line of a [TaskCard]: the tags as `#name` and, when the task has subtasks, how
 * many are done. Empty when there is nothing to show. Subtasks are managed in Tasks.org; this
 * only reports them.
 */
internal fun formatTaskMeta(tags: List<String>, childCount: Int, uncompletedChildCount: Int): String {
    val tagText = tags.joinToString(" ") { "#$it" }
    val subtaskText = if (childCount > 0) "\u2937 ${childCount - uncompletedChildCount}/$childCount" else ""
    return listOf(tagText, subtaskText).filter { it.isNotEmpty() }.joinToString(" \u00B7 ")
}
