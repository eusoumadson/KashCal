package org.onekash.kashcal.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.onekash.kashcal.R
import org.onekash.kashcal.data.tasks_org.TasksOrgList
import org.onekash.kashcal.domain.model.DEFAULT_TASK_LIST_COLOR
import org.onekash.kashcal.ui.components.SettingsTopAppBar

/**
 * Shows the Tasks.org settings; stateless, like [DeviceCalendarsScreen]. The host owns the
 * permission launcher, so turning Enable on without the Tasks.org permission through [onToggle]
 * prompts for it, and the host enables the source once it is granted.
 *
 * Without Tasks.org installed only the description and an install hint show.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksOrgScreen(
    isInstalled: Boolean,
    isEnabled: Boolean,
    hasReadPermission: Boolean,
    hasWritePermission: Boolean,
    lists: List<TasksOrgList>,
    hiddenListIds: Set<Long>,
    onNavigateBack: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRequestPermission: () -> Unit,
    onToggleList: (Long, Boolean) -> Unit,
    onRefresh: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            SettingsTopAppBar(
                title = stringResource(R.string.settings_title),
                onNavigateBack = onNavigateBack,
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NestedSettingsHeading(text = stringResource(R.string.settings_tasks_org), modifier = Modifier)
                if (isInstalled && isEnabled && hasReadPermission) {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.cd_refresh_tasks_org_lists),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Text(
                stringResource(R.string.tasks_org_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isInstalled) {
                Text(
                    stringResource(R.string.tasks_org_not_installed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                return@Column
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.action_enable), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = isEnabled, onCheckedChange = onToggle)
            }

            // On, but the permission is missing (revoked after it was granted, or Tasks.org
            // reinstalled); tapping asks for it again.
            AnimatedVisibility(
                visible = isEnabled && !hasReadPermission,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Text(
                    stringResource(R.string.tasks_org_permission_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onRequestPermission)
                        .padding(vertical = 8.dp)
                )
            }

            // Readable but not writable: tasks show, their checkboxes stay disabled. Tapping
            // asks for the write permission.
            AnimatedVisibility(
                visible = isEnabled && hasReadPermission && !hasWritePermission,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small)
                        .clickable(onClick = onRequestPermission)
                        .padding(12.dp)
                ) {
                    Text(
                        stringResource(R.string.tasks_org_read_only_access),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        stringResource(R.string.tasks_org_grant_write),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            AnimatedVisibility(
                visible = isEnabled && hasReadPermission,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.tasks_org_lists_header),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    if (lists.isEmpty()) {
                        Text(
                            stringResource(R.string.tasks_org_no_lists),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    lists.forEach { list ->
                        val isShown = list.id !in hiddenListIds
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleList(list.id, !isShown) }
                                .padding(vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color(list.color.takeIf { it != 0 } ?: DEFAULT_TASK_LIST_COLOR))
                                )
                                Text(
                                    list.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = isShown,
                                    onCheckedChange = { checked -> onToggleList(list.id, checked) }
                                )
                            }
                            if (list.isReadOnly) {
                                Text(
                                    stringResource(R.string.tasks_org_list_read_only),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 24.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Fork: version notice, account health, and managing lists, tags and places. Only
            // while the integration is on, like everything else of the fork.
            if (isEnabled && hasReadPermission) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                TasksOrgManagementSection()
            }
        }
    }
}
