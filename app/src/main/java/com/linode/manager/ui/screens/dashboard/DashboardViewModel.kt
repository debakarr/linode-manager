package com.linode.manager.ui.screens.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linode.manager.AppContainer
import com.linode.manager.data.remote.Account
import com.linode.manager.data.remote.LinodeEvent
import com.linode.manager.data.remote.LinodeInstance
import com.linode.manager.data.remote.Notification
import com.linode.manager.data.remote.Profile
import com.linode.manager.data.remote.TransferUsage
import com.linode.manager.data.repository.ApiResult
import com.linode.manager.data.repository.isAuthFailure
import com.linode.manager.ui.components.AuthFailure
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class DashboardState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val authFailure: AuthFailure? = null,
    val profile: Profile? = null,
    val account: Account? = null,
    val linodes: List<LinodeInstance> = emptyList(),
    val notifications: List<Notification> = emptyList(),
    val events: List<LinodeEvent> = emptyList(),
    val transfer: TransferUsage? = null,
    val volumeCount: Int = 0,
    val firewallCount: Int = 0,
    val domainCount: Int = 0,
)

class DashboardViewModel(
    private val container: AppContainer,
) : ViewModel() {
    var state by mutableStateOf(DashboardState())
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val hasData = state.profile != null || state.linodes.isNotEmpty()
            state = state.copy(loading = !hasData, refreshing = hasData, error = null, authFailure = null)
            try {
                val repo = container.repository
                val profileD = async { repo.profile() }
                val accountD = async { repo.account() }
                val linodesD = async { repo.linodes() }
                val notifD = async { repo.notifications() }
                val eventsD = async { repo.events() }
                val transferD = async { repo.transfer() }
                val volumesD = async { repo.volumes() }
                val fwD = async { repo.firewalls() }
                val domD = async { repo.domains() }

                val profile = profileD.await()
                if (profile is ApiResult.Err && profile.isAuthFailure()) {
                    state =
                        state.copy(
                            loading = false,
                            refreshing = false,
                            authFailure = AuthFailure(profile.code, "Profile: ${profile.message}"),
                        )
                    return@launch
                }
                val linodesR = linodesD.await()
                if (linodesR is ApiResult.Err && linodesR.isAuthFailure()) {
                    state =
                        state.copy(
                            loading = false,
                            refreshing = false,
                            authFailure = AuthFailure(linodesR.code, "Linodes: ${linodesR.message}"),
                        )
                    return@launch
                }
                val err =
                    listOf(profile, accountD.await(), linodesR)
                        .filterIsInstance<ApiResult.Err>()
                        .firstOrNull { !it.isAuthFailure() }

                state =
                    DashboardState(
                        loading = false,
                        error = err?.message,
                        profile = (profile as? ApiResult.Ok)?.value,
                        account = (accountD.await() as? ApiResult.Ok)?.value,
                        linodes = (linodesR as? ApiResult.Ok)?.value?.data ?: emptyList(),
                        notifications = (notifD.await() as? ApiResult.Ok)?.value?.data ?: emptyList(),
                        events = (eventsD.await() as? ApiResult.Ok)?.value?.data?.take(8) ?: emptyList(),
                        transfer = (transferD.await() as? ApiResult.Ok)?.value,
                        volumeCount = (volumesD.await() as? ApiResult.Ok)?.value?.results ?: 0,
                        firewallCount = (fwD.await() as? ApiResult.Ok)?.value?.results ?: 0,
                        domainCount = (domD.await() as? ApiResult.Ok)?.value?.results ?: 0,
                    )
            } catch (e: Exception) {
                state = state.copy(loading = false, refreshing = false, error = e.message ?: "Failed to load dashboard")
            }
        }
    }

    fun clearAuthFailure() {
        state = state.copy(authFailure = null)
    }
}
