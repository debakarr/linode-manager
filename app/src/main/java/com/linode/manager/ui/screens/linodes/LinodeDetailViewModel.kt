package com.linode.manager.ui.screens.linodes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linode.manager.AppContainer
import com.linode.manager.data.remote.Firewall
import com.linode.manager.data.remote.Image
import com.linode.manager.data.remote.LabelUpdate
import com.linode.manager.data.remote.LinodeConfig
import com.linode.manager.data.remote.LinodeDisk
import com.linode.manager.data.remote.LinodeInstance
import com.linode.manager.data.remote.LinodeStats
import com.linode.manager.data.remote.LinodeType
import com.linode.manager.data.remote.NetworkingInfo
import com.linode.manager.data.remote.Volume
import com.linode.manager.data.repository.ApiResult
import com.linode.manager.data.repository.isAuthFailure
import com.linode.manager.ui.components.AuthFailure
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class LinodeDetailState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val authFailure: AuthFailure? = null,
    val linode: LinodeInstance? = null,
    val networking: NetworkingInfo? = null,
    val disks: List<LinodeDisk> = emptyList(),
    val configs: List<LinodeConfig> = emptyList(),
    val volumes: List<Volume> = emptyList(),
    val firewalls: List<Firewall> = emptyList(),
    val stats: LinodeStats? = null,
    val busyAction: String? = null,
    val message: String? = null,
    val types: List<LinodeType> = emptyList(),
    val images: List<Image> = emptyList(),
)

class LinodeDetailViewModel(
    private val container: AppContainer,
    private val linodeId: Int,
) : ViewModel() {
    var state by mutableStateOf(LinodeDetailState())
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            state = state.copy(loading = state.linode == null, refreshing = state.linode != null, error = null)
            try {
                val repo = container.repository
                val main = async { repo.linode(linodeId) }
                val net = async { repo.networking(linodeId) }
                val disks = async { repo.disks(linodeId) }
                val configs = async { repo.configs(linodeId) }
                val vols = async { repo.linodeVolumes(linodeId) }
                val fws = async { repo.linodeFirewalls(linodeId) }
                val stats = async { repo.stats(linodeId) }

                val m = main.await()
                if (m is ApiResult.Err) {
                    state =
                        if (m.isAuthFailure()) {
                            state.copy(loading = false, refreshing = false, authFailure = AuthFailure(m.code, m.message))
                        } else {
                            state.copy(loading = false, refreshing = false, error = m.message)
                        }
                    return@launch
                }
                state =
                    state.copy(
                        loading = false,
                        refreshing = false,
                        linode = (m as ApiResult.Ok).value,
                        networking = (net.await() as? ApiResult.Ok)?.value,
                        disks = (disks.await() as? ApiResult.Ok)?.value?.data ?: emptyList(),
                        configs = (configs.await() as? ApiResult.Ok)?.value?.data ?: emptyList(),
                        volumes = (vols.await() as? ApiResult.Ok)?.value?.data ?: emptyList(),
                        firewalls = (fws.await() as? ApiResult.Ok)?.value?.data ?: emptyList(),
                        stats = (stats.await() as? ApiResult.Ok)?.value,
                    )
            } catch (e: Exception) {
                state = state.copy(loading = false, refreshing = false, error = e.message ?: "Failed to load Linode")
            }
        }
    }

    /** Plans and images for resize/rebuild pickers; fetched on first use. */
    fun loadCatalog() {
        if (state.types.isNotEmpty() && state.images.isNotEmpty()) return
        viewModelScope.launch {
            val t = async { container.repository.types() }
            val i = async { container.repository.images() }
            state =
                state.copy(
                    types = (t.await() as? ApiResult.Ok)?.value?.data?.sortedBy { it.price?.monthly ?: Double.MAX_VALUE } ?: emptyList(),
                    images =
                        (i.await() as? ApiResult.Ok)?.value?.data?.filter {
                            it.deprecated != true && it.status != "deleted"
                        } ?: emptyList(),
                )
        }
    }

    fun clearMessage() {
        state = state.copy(message = null)
    }

    fun runAction(
        name: String,
        success: String,
        block: suspend () -> ApiResult<*>,
    ) {
        viewModelScope.launch {
            state = state.copy(busyAction = name, message = null)
            when (val r = block()) {
                is ApiResult.Ok -> {
                    state = state.copy(busyAction = null, message = success)
                    load()
                }
                is ApiResult.Err ->
                    state =
                        if (r.isAuthFailure()) {
                            state.copy(busyAction = null, authFailure = AuthFailure(r.code, "$name: ${r.message}"))
                        } else {
                            state.copy(busyAction = null, message = r.message)
                        }
            }
        }
    }

    fun power(action: String) {
        val repo = container.repository
        when (action) {
            "boot" -> runAction("boot", "Booting…") { repo.boot(linodeId) }
            "reboot" -> runAction("reboot", "Rebooting…") { repo.reboot(linodeId) }
            "shutdown" -> runAction("shutdown", "Shutting down…") { repo.shutdown(linodeId) }
        }
    }

    fun rename(label: String) =
        runAction("rename", "Renamed to $label") {
            container.repository.updateLinode(linodeId, LabelUpdate(label = label))
        }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            state = state.copy(busyAction = "delete")
            when (val r = container.repository.deleteLinode(linodeId)) {
                is ApiResult.Ok -> {
                    container.sshSessions.close(linodeId)
                    onDeleted()
                }
                is ApiResult.Err ->
                    state =
                        if (r.isAuthFailure()) {
                            state.copy(busyAction = null, authFailure = AuthFailure(r.code, "delete: ${r.message}"))
                        } else {
                            state.copy(busyAction = null, message = r.message)
                        }
            }
        }
    }

    fun clearAuthFailure() {
        state = state.copy(authFailure = null)
    }
}
