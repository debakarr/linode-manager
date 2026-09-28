package com.linode.manager.ui.screens.linodes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linode.manager.AppContainer
import com.linode.manager.data.remote.CreateLinodeRequest
import com.linode.manager.data.remote.Image
import com.linode.manager.data.remote.LinodeType
import com.linode.manager.data.remote.Region
import com.linode.manager.data.remote.SshKey
import com.linode.manager.data.repository.ApiResult
import com.linode.manager.data.repository.isAuthFailure
import com.linode.manager.ui.components.AuthFailure
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class CreateLinodeState(
    val loadingOptions: Boolean = true,
    val optionsError: String? = null,
    val regions: List<Region> = emptyList(),
    val types: List<LinodeType> = emptyList(),
    val images: List<Image> = emptyList(),
    val sshKeys: List<SshKey> = emptyList(),
    val label: String = "",
    val region: String = "",
    val type: String = "",
    val image: String = "linode/ubuntu24.04",
    val rootPass: String = "",
    val tags: String = "",
    val backups: Boolean = false,
    val selectedKeys: Set<String> = emptySet(),
    val busy: Boolean = false,
    val error: String? = null,
    val createdId: Int? = null,
    val authFailure: AuthFailure? = null,
)

class CreateLinodeViewModel(
    private val container: AppContainer,
) : ViewModel() {
    var state by mutableStateOf(CreateLinodeState())
        private set

    init {
        loadOptions()
    }

    fun loadOptions() {
        viewModelScope.launch {
            state = state.copy(loadingOptions = true, optionsError = null)
            try {
                val regionsD = async { container.repository.regions() }
                val typesD = async { container.repository.types() }
                val imagesD = async { container.repository.images() }
                val keysD = async { container.repository.sshKeys() }
                val regions = (regionsD.await() as? ApiResult.Ok)?.value?.data?.filter { it.status == "ok" } ?: emptyList()
                val types = (typesD.await() as? ApiResult.Ok)?.value?.data ?: emptyList()
                val images =
                    (imagesD.await() as? ApiResult.Ok)
                        ?.value
                        ?.data
                        ?.filter { it.isPublic == true && it.deprecated != true }
                        ?.take(60) ?: emptyList()
                val keys = (keysD.await() as? ApiResult.Ok)?.value?.data ?: emptyList()
                state =
                    state.copy(
                        loadingOptions = false,
                        regions = regions,
                        types = types.sortedBy { it.price?.monthly ?: Double.MAX_VALUE },
                        images = images,
                        sshKeys = keys,
                        region = state.region.ifBlank { regions.firstOrNull()?.id ?: "" },
                        type = state.type.ifBlank { "g6-nanode-1" },
                    )
            } catch (e: Exception) {
                state = state.copy(loadingOptions = false, optionsError = e.message)
            }
        }
    }

    fun update(
        label: String = state.label,
        region: String = state.region,
        type: String = state.type,
        image: String = state.image,
        rootPass: String = state.rootPass,
        tags: String = state.tags,
        backups: Boolean = state.backups,
    ) {
        state = state.copy(label = label, region = region, type = type, image = image, rootPass = rootPass, tags = tags, backups = backups)
    }

    fun toggleKey(k: String) {
        val s = state.selectedKeys.toMutableSet()
        if (!s.add(k)) s.remove(k)
        state = state.copy(selectedKeys = s)
    }

    fun create() {
        viewModelScope.launch {
            if (state.region.isBlank() || state.type.isBlank() || state.image.isBlank()) {
                state = state.copy(error = "Pick a region, plan and image.")
                return@launch
            }
            if (state.rootPass.length < 11) {
                state = state.copy(error = "Root password must be at least 11 characters.")
                return@launch
            }
            state = state.copy(busy = true, error = null)
            val tags =
                state.tags
                    .split(',', ' ')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            val req =
                CreateLinodeRequest(
                    label = state.label.trim().ifBlank { null },
                    region = state.region,
                    type = state.type,
                    image = state.image,
                    rootPass = state.rootPass,
                    authorizedKeys = state.selectedKeys.toList().ifEmpty { null },
                    tags = tags.ifEmpty { null },
                    backups_enabled = if (state.backups) true else null,
                    privateIp = true,
                )
            when (val r = container.repository.createLinode(req)) {
                is ApiResult.Ok -> state = state.copy(busy = false, createdId = r.value.id)
                is ApiResult.Err ->
                    if (r.isAuthFailure()) {
                        state = state.copy(busy = false, authFailure = AuthFailure(r.code, r.message))
                    } else {
                        state = state.copy(busy = false, error = r.message)
                    }
            }
        }
    }

    fun clearAuthFailure() {
        state = state.copy(authFailure = null)
    }
}
