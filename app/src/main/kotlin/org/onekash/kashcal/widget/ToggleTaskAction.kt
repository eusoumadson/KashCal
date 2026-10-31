package org.onekash.kashcal.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.onekash.kashcal.domain.tasks.TasksOrgCoordinator

private const val TAG = "ToggleTaskAction"

/**
 * Hilt EntryPoint to allow Glance ActionCallbacks (which have no constructor injection)
 * to access [TasksOrgCoordinator] and [WidgetUpdateManager].
 *
 * FORK:tasks-widget
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetTaskEntryPoint {
    fun tasksOrgCoordinator(): TasksOrgCoordinator
    fun widgetUpdateManager(): WidgetUpdateManager
}

/**
 * Handles tapping a task's checkbox on any home screen widget: toggles the task's completion
 * in Tasks.org and immediately requests a refresh across all widgets so the checked state
 * updates on the home screen.
 *
 * FORK:tasks-widget
 */
class ToggleTaskAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[EXTRA_TASK_ID_KEY] ?: run {
            Log.w(TAG, "Missing task ID parameter in ToggleTaskAction")
            return
        }
        val isCompleted = parameters[EXTRA_COMPLETED_KEY] ?: false

        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetTaskEntryPoint::class.java
            )
            val coordinator = entryPoint.tasksOrgCoordinator()
            val task = coordinator.getTask(taskId)
            if (task == null) {
                Log.w(TAG, "Task $taskId not found in Tasks.org provider")
                return
            }

            val newCompleted = !isCompleted
            Log.d(TAG, "Toggling task $taskId from widget (currently: $isCompleted -> new: $newCompleted)")
            coordinator.setCompleted(task, newCompleted)

            val updateManager = entryPoint.widgetUpdateManager()
            updateManager.updateAllWidgets(reason = "widget_task_toggled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle task $taskId from widget", e)
        }
    }

    companion object {
        val EXTRA_TASK_ID_KEY = ActionParameters.Key<Long>("extra_task_id")
        val EXTRA_COMPLETED_KEY = ActionParameters.Key<Boolean>("extra_completed")
    }
}
