package org.onekash.kashcal.data.tasks_org

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.onekash.kashcal.data.calendar_provider.CalendarProviderObserver
import org.onekash.kashcal.data.preferences.KashCalDataStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the Tasks.org source in step with its switch ([KashCalDataStore.tasksOrgEnabled]):
 * observes Tasks.org's tasks while it is on and bumps [changeSignal] when they change, so the
 * views re-query. Mirrors [org.onekash.kashcal.data.calendar_provider.CalendarProviderManager].
 *
 * The switch is turned back off when the read permission is gone (revoked, or Tasks.org was
 * uninstalled, which drops the permission with it).
 */
@Singleton
class TasksOrgManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TasksOrgRepository,
    private val dataStore: KashCalDataStore
) {
    companion object {
        private const val TAG = "TasksOrgManager"

        /** How long the observer waits for a burst of Tasks.org changes (a sync) to settle. */
        internal const val OBSERVER_DEBOUNCE_MS = 1000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var observer: CalendarProviderObserver? = null

    private val _changeSignal = MutableStateFlow(0)

    /**
     * Increments when Tasks.org's tasks change or the source is switched on or off.
     * DisplayEventRepository's flows combine it and re-query tasks on each change.
     */
    val changeSignal: StateFlow<Int> = _changeSignal.asStateFlow()

    /** Observes at app startup when the switch is on, or turns it off without the permission. */
    fun initialize() {
        scope.launch {
            if (dataStore.tasksOrgEnabled.first()) registerObserver()
        }
    }

    /** Starts observing and re-queries once the source is switched on. */
    fun onEnabled() {
        registerObserver()
        _changeSignal.value++
    }

    /** Stops observing and re-queries, so tasks leave the views, once it is switched off. */
    fun onDisabled() {
        unregisterObserver()
        _changeSignal.value++
    }

    /**
     * Bumps [changeSignal] right after KashCal writes to Tasks.org or changes which lists are
     * shown, so the views don't wait for the debounced observer.
     */
    fun notifyChanged() {
        _changeSignal.value++
    }

    private fun registerObserver() {
        if (observer != null) return
        if (!repository.hasReadPermission()) {
            Log.w(TAG, "Tasks.org permission missing, turning the Tasks.org source off")
            scope.launch { dataStore.setTasksOrgEnabled(false) }
            return
        }
        val newObserver = CalendarProviderObserver(
            handler = handler,
            scope = scope,
            debounceMs = OBSERVER_DEBOUNCE_MS
        ) {
            // Widgets don't show tasks (WidgetDataRepository), so only the views re-query.
            _changeSignal.value++
        }
        try {
            context.contentResolver.registerContentObserver(
                TasksOrgContract.Tasks.CONTENT_URI,
                true, // notifyForDescendants
                newObserver
            )
            observer = newObserver
            Log.i(TAG, "Registered Tasks.org observer")
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException registering Tasks.org observer, turning the source off", e)
            scope.launch { dataStore.setTasksOrgEnabled(false) }
        }
    }

    private fun unregisterObserver() {
        observer?.let {
            it.cancelPending()
            context.contentResolver.unregisterContentObserver(it)
            observer = null
            Log.i(TAG, "Unregistered Tasks.org observer")
        }
    }
}
