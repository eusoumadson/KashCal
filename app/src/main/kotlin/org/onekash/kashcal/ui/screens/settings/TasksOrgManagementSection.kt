package org.onekash.kashcal.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.onekash.kashcal.R
import org.onekash.kashcal.data.tasks_org.TasksOrgAccount
import org.onekash.kashcal.data.tasks_org.TasksOrgContract
import org.onekash.kashcal.data.tasks_org.TasksOrgList
import org.onekash.kashcal.data.tasks_org.TasksOrgPlace
import org.onekash.kashcal.data.tasks_org.TasksOrgTag
import org.onekash.kashcal.data.tasks_org.TasksOrgVersion
import org.onekash.kashcal.ui.viewmodels.TasksOrgManageViewModel

/** The colors offered for lists and tags; 0 is Tasks.org's "no color". */
private val PALETTE: List<Int> = listOf(
    0,
    0xFFE53935.toInt(), 0xFFFB8C00.toInt(), 0xFFFDD835.toInt(), 0xFF43A047.toInt(),
    0xFF1E88E5.toInt(), 0xFF8E24AA.toInt(), 0xFF6D4C41.toInt()
)

/**
 * The fork's Tasks.org management section: a notice when the installed Tasks.org is too old,
 * accounts that need attention, and managing lists, tags and places. Self-contained (it owns its
 * ViewModel), so the settings screen needs only one call to show it.
 */
@Composable
fun TasksOrgManagementSection(viewModel: TasksOrgManageViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }

    var listDialog by remember { mutableStateOf<ListDialog?>(null) }
    var tagDialog by remember { mutableStateOf<TasksOrgTag?>(null) }
    var deleting by remember { mutableStateOf<Deletion?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        state.version?.let { VersionNotice(it) }
        state.accounts.filter { it.needsAttention }.forEach { AccountNotice(it) }

        TextButton(onClick = { expanded = !expanded }) {
            Text(stringResource(R.string.tasks_org_manage_lists) + " / " +
                stringResource(R.string.tasks_org_manage_tags) + " / " +
                stringResource(R.string.tasks_org_manage_places))
        }
        if (!expanded) return@Column

        state.message?.let {
            Text(
                stringResource(
                    when (it) {
                        TasksOrgManageViewModel.Message.DONE -> R.string.snackbar_tasks_org_manage_done
                        TasksOrgManageViewModel.Message.FAILED -> R.string.snackbar_tasks_org_manage_failed
                        TasksOrgManageViewModel.Message.READ_ONLY -> R.string.snackbar_tasks_org_manage_read_only
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (it == TasksOrgManageViewModel.Message.DONE) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
        }

        SectionLabel(stringResource(R.string.tasks_org_manage_lists))
        state.lists.forEach { list ->
            ManageRow(
                title = list.title,
                color = list.color,
                readOnly = list.isReadOnly,
                onEdit = { listDialog = ListDialog.Edit(list) },
                onDelete = { deleting = Deletion.OfList(list) }
            )
        }
        // Tasks.org's provider can't create a list on every account type (CalDAV ones fail inside
        // Tasks.org), so new lists are created in Tasks.org itself.
        val context = LocalContext.current
        TextButton(onClick = { launchTasksOrgApp(context) }) {
            Text(stringResource(R.string.tasks_org_create_list_in_app))
        }

        SectionLabel(stringResource(R.string.tasks_org_manage_tags))
        if (state.tags.isEmpty()) Muted(stringResource(R.string.tasks_org_no_tags))
        state.tags.forEach { tag ->
            ManageRow(
                title = tag.name,
                color = tag.color,
                readOnly = false,
                onEdit = { tagDialog = tag },
                onDelete = { deleting = Deletion.OfTag(tag) }
            )
        }

        SectionLabel(stringResource(R.string.tasks_org_manage_places))
        if (state.places.isEmpty()) Muted(stringResource(R.string.tasks_org_no_places))
        state.places.forEach { place ->
            ManageRow(
                title = place.displayName.ifBlank { place.name },
                color = 0,
                readOnly = false,
                onEdit = null,
                onDelete = { deleting = Deletion.OfPlace(place) }
            )
        }
    }

    when (val dialog = listDialog) {
        is ListDialog.Edit -> NameColorDialog(
            title = stringResource(R.string.tasks_org_rename),
            label = stringResource(R.string.tasks_org_list_name),
            initialName = dialog.list.title,
            initialColor = dialog.list.color,
            confirmLabel = stringResource(R.string.tasks_org_save),
            onDismiss = { listDialog = null },
            onConfirm = { name, color ->
                viewModel.updateList(dialog.list.id, name, color)
                listDialog = null
            }
        )
        null -> Unit
    }

    tagDialog?.let { tag ->
        NameColorDialog(
            title = stringResource(R.string.tasks_org_rename),
            label = stringResource(R.string.tasks_org_tag_name),
            initialName = tag.name,
            initialColor = tag.color,
            confirmLabel = stringResource(R.string.tasks_org_save),
            onDismiss = { tagDialog = null },
            onConfirm = { name, color ->
                viewModel.updateTag(tag.id, name, color)
                tagDialog = null
            }
        )
    }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(target.titleRes)) },
            text = { Text(stringResource(target.messageRes)) },
            confirmButton = {
                TextButton(onClick = {
                    when (target) {
                        is Deletion.OfList -> viewModel.deleteList(target.list.id)
                        is Deletion.OfTag -> viewModel.deleteTag(target.tag.id)
                        is Deletion.OfPlace -> viewModel.deletePlace(target.place.id)
                    }
                    deleting = null
                }) { Text(stringResource(R.string.tasks_org_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.tasks_org_cancel)) }
            }
        )
    }
}

private sealed interface ListDialog {
    data class Edit(val list: TasksOrgList) : ListDialog
}

private sealed interface Deletion {
    val titleRes: Int
    val messageRes: Int

    data class OfList(val list: TasksOrgList) : Deletion {
        override val titleRes = R.string.tasks_org_delete_list_title
        override val messageRes = R.string.tasks_org_delete_list_message
    }

    data class OfTag(val tag: TasksOrgTag) : Deletion {
        override val titleRes = R.string.tasks_org_delete_tag_title
        override val messageRes = R.string.tasks_org_delete_tag_message
    }

    data class OfPlace(val place: TasksOrgPlace) : Deletion {
        override val titleRes = R.string.tasks_org_delete_place_title
        override val messageRes = R.string.tasks_org_delete_place_message
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun VersionNotice(version: TasksOrgVersion) {
    if (version.isSupported) {
        Muted(stringResource(R.string.tasks_org_version_label, version.name))
    } else {
        Text(
            stringResource(R.string.tasks_org_version_too_old, version.name, TasksOrgVersion.MIN_SUPPORTED_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun AccountNotice(account: TasksOrgAccount) {
    Text(
        stringResource(R.string.tasks_org_account_attention, account.name, account.error),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}

@Composable
private fun ManageRow(
    title: String,
    color: Int,
    readOnly: Boolean,
    onEdit: (() -> Unit)?,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(if (color != 0) Color(color) else MaterialTheme.colorScheme.outlineVariant)
        )
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        // A read-only list can't be renamed or deleted; Tasks.org would refuse.
        if (!readOnly) {
            if (onEdit != null) TextButton(onClick = onEdit) { Text(stringResource(R.string.tasks_org_rename)) }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.tasks_org_delete)) }
        } else {
            Text(
                stringResource(R.string.tasks_org_list_read_only),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Name and color in one dialog shared by lists and tags. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NameColorDialog(
    title: String,
    label: String,
    initialName: String,
    initialColor: Int,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, color: Int) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(label) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.tasks_org_color_label), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PALETTE.forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(if (c == color) 32.dp else 24.dp)
                                .clip(CircleShape)
                                .background(if (c != 0) Color(c) else MaterialTheme.colorScheme.outlineVariant)
                                .clickable { color = c }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name, color) }
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.tasks_org_cancel)) } }
    )
}

/** Opens the Tasks.org app (where lists are created); does nothing when it can't be launched. */
private fun launchTasksOrgApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(TasksOrgContract.PACKAGE) ?: return
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // Uninstalled or disabled since the section opened.
    }
}
