package com.linode.manager.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linode.manager.AppContainer
import com.linode.manager.data.remote.Profile
import com.linode.manager.data.repository.ApiResult
import kotlinx.coroutines.launch

class SessionViewModel(
    private val container: AppContainer,
) : ViewModel() {
    var loggedIn by mutableStateOf(container.tokenStore.hasToken())
        private set
    var profile by mutableStateOf<Profile?>(null)
        private set
    var checking by mutableStateOf(loggedIn)
        private set

    init {
        if (loggedIn) refreshProfile()
    }

    fun refreshProfile() {
        viewModelScope.launch {
            checking = true
            when (val r = container.repository.profile()) {
                is ApiResult.Ok -> {
                    profile = r.value
                    loggedIn = true
                }
                is ApiResult.Err -> {
                    if (r.unauthorized) {
                        container.tokenStore.clear()
                        loggedIn = false
                        profile = null
                    }
                }
            }
            checking = false
        }
    }

    fun onLoggedIn() {
        loggedIn = true
        refreshProfile()
    }

    fun logout() {
        container.tokenStore.clear()
        profile = null
        loggedIn = false
    }

    fun handleUnauthorized() {
        container.tokenStore.clear()
        profile = null
        loggedIn = false
    }
}
