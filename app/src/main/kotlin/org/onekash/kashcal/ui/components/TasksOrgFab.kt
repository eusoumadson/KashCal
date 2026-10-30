package org.onekash.kashcal.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.onekash.kashcal.R

/**
 * Wraps the home screen's floating action button so a Tasks.org "new task" button can sit above
 * it. While [showTaskFab] is false (the integration is off) it only calls [content], so the
 * screen is exactly the original's.
 */
@Composable
internal fun TasksOrgFabSlot(
    showTaskFab: Boolean,
    onCreateTask: () -> Unit,
    content: @Composable () -> Unit
) {
    if (!showTaskFab) {
        content()
        return
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SmallFloatingActionButton(
            onClick = onCreateTask,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = stringResource(R.string.task_editor_new_task_action)
            )
        }
        content()
    }
}
