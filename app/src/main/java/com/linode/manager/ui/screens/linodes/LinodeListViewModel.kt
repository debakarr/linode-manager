package com.linode.manager.ui.screens.linodes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linode.manager.AppContainer
import com.linode.manager.data.remote.LinodeInstance
import com.linode.manager.data.repository.ApiResult
import com.linode.manager.data.repository.isAuthFailure
import com.linode.manager.ui.components.AuthFailure
import kotlinx.coroutines.launch

data class LinodeListState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val authFailure: AuthFailure? = null,
    val linodes: List<LinodeInstance> = emptyList(),
    val query: String = "",
    val statusFilter: String? = null,
    val actionBusy: Int? = null,
    val actionError: String? = null,
)

class LinodeListViewModel(
    private val container: AppContainer,
) : ViewModel() {
    var state by mutableStateOf(LinodeListState())
        private set

    init {
        load()
    }

    fun load(refreshing: Boolean = false) {
        viewModelScope.launch {
            state = state.copy(loading = !refreshing && state.linodes.isEmpty(), refreshing = refreshing, error = null, authFailure = null)
            when (val r = container.repository.linodes()) {
                is ApiResult.Ok -> state = state.copy(loading = false, refreshing = false, linodes = r.value.data)
                is ApiResult.Err ->
                    if (r.isAuthFailure()) {
                        state = state.copy(loading = false, refreshing = false, authFailure = AuthFailure(r.code, r.message))
                    } else {
                        state = state.copy(loading = false, refreshing = false, error = r.message)
                    }
            }
        }
    }

    fun clearAuthFailure() {
        state = state.copy(authFailure = null)
    }

    fun clearActionError() {
        state = state.copy(actionError = null)
    }

    fun setQuery(q: String) {
        state = state.copy(query = q)
    }

    fun setStatusFilter(f: String?) {
        state = state.copy(statusFilter = f)
    }

    fun powerAction(
        id: Int,
        action: String,
    ) {
        viewModelScope.launch {
            state = state.copy(actionBusy = id, actionError = null)
            val repo = container.repository
            val r =
                when (action) {
                    "boot" -> repo.boot(id)
                    "reboot" -> repo.reboot(id)
                    "shutdown" -> repo.shutdown(id)
                    else -> null
                }
            if (r is ApiResult.Err) {
                state =
                    if (r.isAuthFailure()) {
                        state.copy(actionBusy = null, authFailure = AuthFailure(r.code, "$action: ${r.message}"))
                    } else {
                        state.copy(actionBusy = null, actionError = r.message)
                    }
            } else {
                state = state.copy(actionBusy = null, actionError = "${action.replaceFirstChar { it.uppercase() }} requested")
                // optimistic status update
                val updated =
                    state.linodes.map {
                        if (it.id == id) {
                            it.copy(
                                status =
                                    when (action) {
                                        "boot" -> "booting"
                                        "reboot" -> "rebooting"
                                        else -> "shutting_down"
                                    },
                            )
                        } else {
                            it
                        }
                    }
                state = state.copy(linodes = updated)
                load(refreshing = true)
            }
        }
    }

    fun filtered(): List<LinodeInstance> {
        val q = state.query.trim().lowercase()
        return state.linodes.filter { l ->
            (state.statusFilter == null || l.status == state.statusFilter) &&
                (
                    q.isBlank() ||
                        l.label.lowercase().contains(q) ||
                        (l.ipv4.firstOrNull()?.contains(q) == true) ||
                        (l.region?.lowercase()?.contains(q) == true) ||
                        l.tags.any { it.lowercase().contains(q) }
                )
        }
    }
}
