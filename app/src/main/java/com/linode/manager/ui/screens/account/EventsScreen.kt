package com.linode.manager.ui.screens.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linode.manager.AppContainer
import com.linode.manager.data.remote.LinodeEvent
import com.linode.manager.data.repository.ApiResult
import com.linode.manager.data.repository.isAuthFailure
import com.linode.manager.ui.components.AccessDeniedDialog
import com.linode.manager.ui.components.AuthFailure
import com.linode.manager.ui.components.ContentWidth
import com.linode.manager.ui.components.EmptyState
import com.linode.manager.ui.components.ErrorState
import com.linode.manager.ui.components.Fmt
import com.linode.manager.ui.components.LoadingState
import com.linode.manager.ui.components.PagePadding
import com.linode.manager.ui.components.ScreenScaffold
import com.linode.manager.ui.components.SectionCard
import com.linode.manager.ui.components.StatusDot
import kotlinx.coroutines.launch

class EventsViewModel(
    private val container: AppContainer,
) : ViewModel() {
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var authFailure by mutableStateOf<AuthFailure?>(null)
        private set
    var events by mutableStateOf<List<LinodeEvent>>(emptyList())
        private set
    var page by mutableStateOf(1)
        private set
    var endReached by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            loading = true
            error = null
            page = 1
            endReached = false
            when (val r = container.repository.events(1)) {
                is ApiResult.Ok -> {
                    events = r.value.data
                    if (r.value.page >= r.value.pages) endReached = true else page = 2
                }
                is ApiResult.Err -> if (r.isAuthFailure()) authFailure = AuthFailure(r.code, r.message) else error = r.message
            }
            loading = false
        }
    }

    fun clearAuthFailure() {
        authFailure = null
    }

    fun loadMore() {
        if (loadingMore || endReached) return
        viewModelScope.launch {
            loadingMore = true
            when (val r = container.repository.events(page)) {
                is ApiResult.Ok -> {
                    events = events + r.value.data
                    if (r.value.page >= r.value.pages || r.value.data.isEmpty()) endReached = true else page += 1
                }
                is ApiResult.Err -> if (r.isAuthFailure()) authFailure = AuthFailure(r.code, r.message) else Unit
            }
            loadingMore = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onUnauthorized: () -> Unit,
) {
    val vm: EventsViewModel = viewModel { EventsViewModel(container) }
    val listState = rememberLazyListState()

    vm.authFailure?.let { af ->
        AccessDeniedDialog(af, "events:read_only", container, { vm.clearAuthFailure() }, onUnauthorized)
    }

    // Infinite scroll: fetch the next page when the end comes into view.
    val nearEnd by remember {
        derivedStateOf {
            val last =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, vm.events.size) {
        if (nearEnd && vm.events.isNotEmpty() && !vm.endReached) vm.loadMore()
    }

    ScreenScaffold(title = "Activity", onBack = onBack) { pad ->
        when {
            vm.loading -> LoadingState("Loading activity…", Modifier.padding(pad))
            vm.error != null && vm.events.isEmpty() -> ErrorState(vm.error!!, Modifier.padding(pad)) { vm.load() }
            vm.events.isEmpty() -> EmptyState("No recent activity", Modifier.padding(pad), icon = Icons.AutoMirrored.Filled.EventNote)
            else ->
                PullToRefreshBox(isRefreshing = false, onRefresh = { vm.load() }, modifier = Modifier.padding(pad).fillMaxSize()) {
                    ContentWidth {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(start = PagePadding, end = PagePadding, top = 4.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(vm.events, key = { it.id }) { e -> EventRow(e) }
                            if (!vm.endReached) {
                                item {
                                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }
}

@Composable
private fun EventRow(e: LinodeEvent) {
    SectionCard(null, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            StatusDot(e.status, Modifier.padding(top = 6.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.label(e.action), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(
                        Fmt.relative(e.created),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    listOfNotNull(e.entity?.label, e.username, Fmt.label(e.status)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!e.message.isNullOrBlank()) {
                    Text(e.message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
                val pct = e.percent_complete
                if (e.status == "started" && pct != null && pct < 100) {
                    LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
        }
    }
}
