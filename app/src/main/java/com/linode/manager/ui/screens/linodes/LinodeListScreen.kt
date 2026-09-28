package com.linode.manager.ui.screens.linodes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linode.manager.AppContainer
import com.linode.manager.data.remote.LinodeInstance
import com.linode.manager.data.ssh.SshStatus
import com.linode.manager.ui.components.AccessDeniedDialog
import com.linode.manager.ui.components.ConfirmDialog
import com.linode.manager.ui.components.ContentWidth
import com.linode.manager.ui.components.EmptyState
import com.linode.manager.ui.components.ErrorState
import com.linode.manager.ui.components.Fmt
import com.linode.manager.ui.components.LoadingState
import com.linode.manager.ui.components.MenuAction
import com.linode.manager.ui.components.MessageEffect
import com.linode.manager.ui.components.OverflowMenu
import com.linode.manager.ui.components.PagePadding
import com.linode.manager.ui.components.Pill
import com.linode.manager.ui.components.ResourceCard
import com.linode.manager.ui.components.ScreenScaffold
import com.linode.manager.ui.components.SearchField
import com.linode.manager.ui.components.StatusBadge

/** Power actions that need a confirmation, with copy shared by list and detail. */
internal data class PowerRequest(
    val id: Int,
    val label: String,
    val action: String,
)

@Composable
internal fun PowerConfirm(
    req: PowerRequest,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val (title, msg, cta) =
        when (req.action) {
            "reboot" ->
                Triple(
                    "Reboot ${req.label}?",
                    "Running programs are stopped and the Linode restarts. Open SSH sessions will drop.",
                    "Reboot",
                )
            "shutdown" ->
                Triple(
                    "Power off ${req.label}?",
                    "The Linode stops until you boot it again. You're still billed while it's off.",
                    "Power off",
                )
            else -> Triple("Boot ${req.label}?", "Starts the Linode with its current configuration.", "Boot")
        }
    ConfirmDialog(title, msg, cta, onConfirm, onDismiss, destructive = req.action != "boot")
}

internal fun linodeSpecs(l: LinodeInstance): String =
    listOfNotNull(
        l.specs?.vcpus?.let { "$it vCPU" },
        l.specs?.memory?.let { Fmt.mb(it) },
        l.specs?.disk?.let { Fmt.mb(it) + " SSD" },
    ).joinToString(" · ")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LinodeListScreen(
    container: AppContainer,
    onUnauthorized: () -> Unit,
    onOpen: (Int) -> Unit,
    onOpenSsh: (Int) -> Unit,
    onCreate: () -> Unit,
) {
    val vm: LinodeListViewModel = viewModel { LinodeListViewModel(container) }
    val s = vm.state
    val snackbar = remember { SnackbarHostState() }
    var confirm by remember { mutableStateOf<PowerRequest?>(null) }

    s.authFailure?.let { af ->
        AccessDeniedDialog(
            af,
            "linodes:read_only (power actions need linodes:read_write)",
            container,
            { vm.clearAuthFailure() },
            onUnauthorized,
        )
    }
    MessageEffect(s.actionError, snackbar) { vm.clearActionError() }
    confirm?.let { req ->
        PowerConfirm(req, onConfirm = { vm.powerAction(req.id, req.action) }, onDismiss = { confirm = null })
    }

    ScreenScaffold(
        title = "Linodes",
        subtitle = if (s.linodes.isNotEmpty()) "${s.linodes.size} total · ${s.linodes.count { it.status == "running" }} running" else null,
        snackbarHostState = snackbar,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Create") },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            ContentWidth {
                Column(Modifier.padding(horizontal = PagePadding)) {
                    SearchField(s.query, vm::setQuery, placeholder = "Search label, IP, region or tag")
                    val statuses =
                        remember(s.linodes) {
                            s.linodes
                                .mapNotNull { it.status }
                                .distinct()
                                .sorted()
                        }
                    if (statuses.size > 1) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            item {
                                FilterChip(
                                    selected = s.statusFilter == null,
                                    onClick = { vm.setStatusFilter(null) },
                                    label = { Text("All") },
                                )
                            }
                            items(statuses) { st ->
                                FilterChip(
                                    selected = s.statusFilter == st,
                                    onClick = { vm.setStatusFilter(if (s.statusFilter == st) null else st) },
                                    label = { Text(Fmt.label(st)) },
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.size(8.dp))
                    }
                }
            }
            when {
                s.loading -> LoadingState("Loading Linodes…")
                s.error != null && s.linodes.isEmpty() -> ErrorState(s.error) { vm.load() }
                s.linodes.isEmpty() ->
                    EmptyState(
                        "No Linodes yet",
                        message = "Deploy your first Linode in about a minute.",
                        icon = Icons.Filled.Dns,
                        actionLabel = "Create Linode",
                        onAction = onCreate,
                    )
                else -> {
                    val list = vm.filtered()
                    PullToRefreshBox(isRefreshing = s.refreshing, onRefresh = { vm.load(true) }, modifier = Modifier.fillMaxSize()) {
                        if (list.isEmpty()) {
                            EmptyState("No matches", message = "Nothing matches your search or filter.", icon = Icons.Filled.SearchOff)
                        } else {
                            ContentWidth {
                                LazyColumn(
                                    contentPadding = PaddingValues(start = PagePadding, end = PagePadding, bottom = 96.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    items(list, key = { it.id }) { l ->
                                        val terminal = container.sshSessions.sessions[l.id]
                                        ResourceCard(
                                            title = l.label,
                                            subtitle = listOfNotNull(l.region, linodeSpecs(l).ifBlank { null }).joinToString(" · "),
                                            icon = Icons.Filled.Dns,
                                            onClick = { onOpen(l.id) },
                                            trailing = {
                                                if (s.actionBusy == l.id) {
                                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                                } else {
                                                    OverflowMenu(
                                                        buildList {
                                                            add(MenuAction("Open terminal", Icons.Filled.Terminal) { onOpenSsh(l.id) })
                                                            when (l.status) {
                                                                "running" -> {
                                                                    add(
                                                                        MenuAction("Reboot", Icons.Filled.RestartAlt) {
                                                                            confirm =
                                                                                PowerRequest(l.id, l.label, "reboot")
                                                                        },
                                                                    )
                                                                    add(
                                                                        MenuAction(
                                                                            "Power off",
                                                                            Icons.Filled.PowerSettingsNew,
                                                                            destructive = true,
                                                                        ) {
                                                                            confirm =
                                                                                PowerRequest(l.id, l.label, "shutdown")
                                                                        },
                                                                    )
                                                                }
                                                                "offline", "stopped" ->
                                                                    add(
                                                                        MenuAction("Boot", Icons.Filled.PlayArrow) {
                                                                            confirm =
                                                                                PowerRequest(l.id, l.label, "boot")
                                                                        },
                                                                    )
                                                            }
                                                        },
                                                    )
                                                }
                                            },
                                        ) {
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                                itemVerticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                StatusBadge(l.status)
                                                if (terminal != null) {
                                                    Pill(if (terminal.status is SshStatus.Connected) "Terminal open" else "Terminal idle")
                                                }
                                                l.tags.take(3).forEach { Pill(it) }
                                            }
                                            l.ipv4.firstOrNull()?.let {
                                                Spacer(Modifier.size(6.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        it,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
