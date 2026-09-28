package com.linode.manager.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linode.manager.AppContainer
import com.linode.manager.data.ssh.SshStatus
import com.linode.manager.ui.components.AccessDeniedDialog
import com.linode.manager.ui.components.ContentWidth
import com.linode.manager.ui.components.EmptyState
import com.linode.manager.ui.components.ErrorState
import com.linode.manager.ui.components.Fmt
import com.linode.manager.ui.components.IconTile
import com.linode.manager.ui.components.InfoRow
import com.linode.manager.ui.components.LoadingState
import com.linode.manager.ui.components.PagePadding
import com.linode.manager.ui.components.ResourceCard
import com.linode.manager.ui.components.ScreenScaffold
import com.linode.manager.ui.components.SectionCard
import com.linode.manager.ui.components.StatusBadge
import com.linode.manager.ui.components.StatusDot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    container: AppContainer,
    onUnauthorized: () -> Unit,
    onOpenLinodes: () -> Unit,
    onOpenLinode: (Int) -> Unit,
    onOpenSsh: (Int) -> Unit,
    onOpenEvents: () -> Unit,
) {
    val vm: DashboardViewModel = viewModel { DashboardViewModel(container) }
    val s = vm.state

    s.authFailure?.let { af ->
        AccessDeniedDialog(af, "linodes:read_only, account:read_only", container, { vm.clearAuthFailure() }, onUnauthorized)
    }

    ScreenScaffold(
        title = "Hello, ${s.profile?.username ?: "there"}",
        subtitle = s.account?.email ?: s.profile?.email,
        actions = { IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") } },
    ) { pad ->
        when {
            s.loading -> LoadingState("Loading your cloud…", Modifier.padding(pad))
            s.error != null && s.profile == null && s.linodes.isEmpty() -> ErrorState(s.error, Modifier.padding(pad)) { vm.refresh() }
            else ->
                PullToRefreshBox(
                    isRefreshing = s.refreshing,
                    onRefresh = { vm.refresh() },
                    modifier = Modifier.padding(pad).fillMaxSize(),
                ) {
                    ContentWidth {
                        LazyColumn(
                            contentPadding = PaddingValues(start = PagePadding, end = PagePadding, top = 4.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            val terminals =
                                container.sshSessions.sessions.values
                                    .toList()
                            if (terminals.isNotEmpty()) {
                                item {
                                    SectionCard("Terminals") {
                                        terminals.forEach { t ->
                                            val live = t.status is SshStatus.Connected
                                            Row(
                                                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                IconTile(Icons.Filled.Terminal)
                                                Spacer(Modifier.width(12.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(t.profile.label, style = MaterialTheme.typography.titleSmall)
                                                    Text(
                                                        if (live) {
                                                            "Connected"
                                                        } else if (t.isLive) {
                                                            "Connecting…"
                                                        } else {
                                                            "Disconnected"
                                                        },
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                                TextButton(onClick = { onOpenSsh(t.profile.linodeId) }) { Text("Open") }
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    StatTile(Icons.Filled.Dns, s.linodes.size, "Linodes", Modifier.weight(1f), onOpenLinodes)
                                    StatTile(Icons.Filled.Storage, s.volumeCount, "Volumes", Modifier.weight(1f))
                                }
                            }
                            item {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    StatTile(Icons.Filled.Security, s.firewallCount, "Firewalls", Modifier.weight(1f))
                                    StatTile(Icons.Filled.Language, s.domainCount, "Domains", Modifier.weight(1f))
                                }
                            }

                            if (s.notifications.isNotEmpty()) {
                                item {
                                    SectionCard("Notifications") {
                                        s.notifications.take(3).forEachIndexed { i, n ->
                                            if (i > 0) Spacer(Modifier.height(12.dp))
                                            Row(verticalAlignment = Alignment.Top) {
                                                Icon(
                                                    Icons.Filled.Notifications,
                                                    contentDescription = null,
                                                    tint =
                                                        if (n.severity == "critical" ||
                                                            n.severity == "major"
                                                        ) {
                                                            MaterialTheme.colorScheme.error
                                                        } else {
                                                            MaterialTheme.colorScheme.secondary
                                                        },
                                                )
                                                Spacer(Modifier.width(12.dp))
                                                Column {
                                                    Text(n.label ?: Fmt.label(n.type), style = MaterialTheme.typography.titleSmall)
                                                    Text(
                                                        n.message ?: n.body ?: "",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                val running = s.linodes.count { it.status == "running" }
                                val stopped = s.linodes.count { it.status == "offline" || it.status == "stopped" }
                                SectionCard("Account") {
                                    InfoRow("Running Linodes", "$running")
                                    InfoRow("Stopped Linodes", "$stopped")
                                    s.account?.balance?.let { InfoRow("Balance", Fmt.money(it, s.account.currency)) }
                                    s.account?.balanceUninvoiced?.let { InfoRow("Uninvoiced", Fmt.money(it, s.account.currency)) }
                                    val t = s.transfer
                                    if (t?.quota != null && t.quota > 0) {
                                        val used = t.used ?: 0L
                                        Spacer(Modifier.height(8.dp))
                                        Row(Modifier.fillMaxWidth()) {
                                            Text(
                                                "Network transfer",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text("$used / ${t.quota} GB", style = MaterialTheme.typography.bodyMedium)
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = { (used.toFloat() / t.quota).coerceIn(0f, 1f) },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }

                            item {
                                SectionCard("Linodes", action = { TextButton(onClick = onOpenLinodes) { Text("View all") } }) {
                                    if (s.linodes.isEmpty()) {
                                        EmptyState("No Linodes yet", message = "Create one from the Linodes tab.", icon = Icons.Filled.Dns)
                                    } else {
                                        s.linodes.take(5).forEachIndexed { i, l ->
                                            if (i > 0) Spacer(Modifier.height(8.dp))
                                            ResourceCard(
                                                title = l.label,
                                                subtitle = listOfNotNull(l.region, l.ipv4.firstOrNull()).joinToString(" · "),
                                                icon = Icons.Filled.Dns,
                                                onClick = { onOpenLinode(l.id) },
                                                trailing = {
                                                    StatusBadge(l.status)
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                            }

                            if (s.events.isNotEmpty()) {
                                item {
                                    SectionCard("Recent activity", action = { TextButton(onClick = onOpenEvents) { Text("View all") } }) {
                                        s.events.forEachIndexed { i, e ->
                                            if (i > 0) Spacer(Modifier.height(10.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                StatusDot(e.status)
                                                Spacer(Modifier.width(12.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(
                                                        Fmt.label(e.action),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                    Text(
                                                        listOfNotNull(e.entity?.label, e.username).joinToString(" · "),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                                Text(
                                                    Fmt.relative(e.created),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
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

@Composable
private fun StatTile(
    icon: ImageVector,
    value: Int,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    val border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    val body: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("$value", style = MaterialTheme.typography.titleLarge)
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.large, colors = colors, border = border) { body() }
    } else {
        Card(modifier = modifier, shape = MaterialTheme.shapes.large, colors = colors, border = border) { body() }
    }
}
