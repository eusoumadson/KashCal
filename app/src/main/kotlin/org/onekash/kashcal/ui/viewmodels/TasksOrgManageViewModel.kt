package org.onekash.kashcal.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.onekash.kashcal.data.tasks_org.TasksOrgAccount
import org.onekash.kashcal.data.tasks_org.TasksOrgList
import org.onekash.kashcal.data.tasks_org.TasksOrgPlace
import org.onekash.kashcal.data.tasks_org.TasksOrgTag
import org.onekash.kashcal.data.tasks_org.TasksOrgVersion
import org.onekash.kashcal.data.tasks_org.TasksOrgWriteResult
import org.onekash.kashcal.di.IoDispatcher
import org.onekash.kashcal.domain.tasks.TasksOrgCoordinator
import javax.inject.Inject

/**
 * Backs the Tasks.org management section of the settings screen (fork): the installed version,
 * the sync accounts' health, and creating, renaming, recoloring and deleting lists, tags and
 * places. It is a ViewModel of its own so the core settings ViewModel stays untouched, which
 * keeps rebases on the upstream simple (FORK_INTEGRATION.md).
 *
 * Tasks.org owns all of this data; every action goes straight to its provider and the state is
 * re-read afterwards, so what shows is what Tasks.org holds.
 */
@HiltViewModel
class TasksOrgManageViewModel @Inject constructor(
    private val coordinator: TasksOrgCoordinator,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    /** Outcome of the last action, shown inline until the next one. */
    enum class Message { DONE, FAILED, READ_ONLY }

    data class UiState(
        val version: TasksOrgVersion? = null,
        val accounts: List<TasksOrgAccount> = emptyList(),
        val lists: List<TasksOrgList> = emptyList(),
        val tags: List<TasksOrgTag> = emptyList(),
        val places: List<TasksOrgPlace> = emptyList(),
        val busy: Boolean = false,
        val message: Message? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Reads everything again. Cheap, and called on entry and after every action. */
    fun refresh() {
        viewModelScope.launch {
            val snapshot = try {
                withContext(ioDispatcher) {
                    UiState(
                        version = coordinator.installedVersion(),
                        accounts = coordinator.getAccounts(),
                        lists = coordinator.getLists(),
                        tags = coordinator.getTags(),
                        places = coordinator.getPlaces()
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (snapshot != null) _state.update { snapshot.copy(busy = it.busy, message = it.message) }
        }
    }

    fun updateList(listId: Long, title: String, color: Int) =
        act { coordinator.updateList(listId, title.trim(), color) }

    fun deleteList(listId: Long) = act { coordinator.deleteList(listId) }

    fun updateTag(tagId: Long, name: String, color: Int) =
        act { coordinator.updateTag(tagId, name.trim(), color) }

    fun deleteTag(tagId: Long) = act { coordinator.deleteTag(tagId) }

    fun updatePlace(placeId: Long, name: String, radius: Int) =
        act { coordinator.updatePlace(placeId, name = name.trim(), radius = radius) }

    fun deletePlace(placeId: Long) = act { coordinator.deletePlace(placeId) }

    /** Runs a write, maps its result to a [Message], and re-reads the state. */
    private fun act(block: suspend () -> TasksOrgWriteResult) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val message = try {
                when (withContext(ioDispatcher) { block() }) {
                    is TasksOrgWriteResult.Ok -> Message.DONE
                    TasksOrgWriteResult.ReadOnly -> Message.READ_ONLY
                    TasksOrgWriteResult.Refused -> Message.FAILED
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Message.FAILED
            }
            _state.update { it.copy(busy = false, message = message) }
            refresh()
        }
    }
}
