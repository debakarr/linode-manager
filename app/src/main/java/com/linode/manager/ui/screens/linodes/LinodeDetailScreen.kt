package com.linode.manager.ui.screens.linodes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linode.manager.AppContainer
import com.linode.manager.data.remote.LinodeInstance
import com.linode.manager.data.ssh.SshStatus
import com.linode.manager.ui.components.AccessDeniedDialog
import com.linode.manager.ui.components.ContentWidth
import com.linode.manager.ui.components.ErrorState
import com.linode.manager.ui.components.Fmt
import com.linode.manager.ui.components.InfoRow
import com.linode.manager.ui.components.LoadingState
import com.linode.manager.ui.components.MenuAction
import com.linode.manager.ui.components.MessageEffect
import com.linode.manager.ui.components.MetricChart
import com.linode.manager.ui.components.OverflowMenu
import com.linode.manager.ui.components.PagePadding
import com.linode.manager.ui.components.PickerField
import com.linode.manager.ui.components.PickerOption
import com.linode.manager.ui.components.Pill
import com.linode.manager.ui.components.ScreenScaffold
import com.linode.manager.ui.components.SectionCard
import com.linode.manager.ui.components.StatusBadge
import com.linode.manager.ui.components.TypeToConfirmDialog
import java.util.Locale

private val TABS = listOf("Overview", "Network", "Storage", "Metrics", "Manage")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LinodeDetailScreen(
    container: AppContainer,
    linodeId: Int,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onOpenSsh: () -> Unit,
    onUnauthorized: () -> Unit,
) {
    val vm: LinodeDetailViewModel = viewModel(key = "detail-$linodeId") { LinodeDetailViewModel(container, linodeId) }
    val s = vm.state
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var power by remember { mutableStateOf<PowerRequest?>(null) }

    s.authFailure?.let { af ->
        AccessDeniedDialog(af, "linodes:read_only (actions need linodes:read_write)", container, { vm.clearAuthFailure() }, onUnauthorized)
    }
    MessageEffect(s.message, snackbar) { vm.clearMessage() }

    val l = s.linode
    ScreenScaffold(
        title = l?.label ?: "Linode",
        subtitle = l?.let { listOfNotNull(it.region, it.type).joinToString(" · ") },
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            IconButton(onClick = { vm.load() }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
            if (l != null) {
                OverflowMenu(
                    listOf(
                        MenuAction("Rename", Icons.Filled.Edit) { dialog = "rename" },
                        MenuAction("Delete Linode", Icons.Filled.Delete, destructive = true) { dialog = "delete" },
                    ),
                )
            }
        },
    ) { pad ->
        when {
            s.loading -> LoadingState("Loading Linode…", Modifier.padding(pad))
            l == null -> ErrorState(s.error ?: "Linode not found.", Modifier.padding(pad)) { vm.load() }
            else ->
                Column(Modifier.padding(pad).fillMaxSize()) {
                    ContentWidth {
                        Column(Modifier.padding(horizontal = PagePadding)) {
                            Header(
                                linode = l,
                                busy = s.busyAction,
                                terminalOpen = container.sshSessions.sessions[linodeId]?.status is SshStatus.Connected,
                                onSsh = onOpenSsh,
                                onPower = { power = PowerRequest(l.id, l.label, it) },
                            )
                        }
                    }
                    PrimaryScrollableTabRow(
                        selectedTabIndex = tab,
                        edgePadding = PagePadding,
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        TABS.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
                    }
                    PullToRefreshBox(isRefreshing = s.refreshing, onRefresh = { vm.load() }, modifier = Modifier.fillMaxSize()) {
                        ContentWidth {
                            Column(
                                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PagePadding),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                when (tab) {
                                    0 -> OverviewTab(s, l)
                                    1 -> NetworkTab(s, l)
                                    2 -> StorageTab(s)
                                    3 -> MetricsTab(s)
                                    4 ->
                                        ManageTab(l, s.busyAction) {
                                            dialog = it
                                            if (it == "resize" || it == "rebuild") vm.loadCatalog()
                                        }
                                }
                                Spacer(Modifier.height(24.dp))
                            }
                        }
                    }
                }
        }
    }

    power?.let { req -> PowerConfirm(req, onConfirm = { vm.power(req.action) }, onDismiss = { power = null }) }

    if (l != null) {
        when (dialog) {
            "rename" -> RenameDialog(l.label, onDismiss = { dialog = null }) { vm.rename(it) }
            "delete" ->
                TypeToConfirmDialog(
                    title = "Delete ${l.label}?",
                    message = "This permanently destroys the Linode, its disks and backups. It cannot be undone.",
                    expected = l.label,
                    confirmLabel = "Delete",
                    onConfirm = { vm.delete(onDeleted) },
                    onDismiss = { dialog = null },
                )
            "resize" ->
                ResizeDialog(s, l, onDismiss = { dialog = null }) { type ->
                    vm.runAction("resize", "Resize started — the Linode will reboot") { container.repository.resize(linodeId, type) }
                }
            "rebuild" ->
                RebuildDialog(s, l, onDismiss = { dialog = null }) { image, pass ->
                    vm.runAction("rebuild", "Rebuild started") { container.repository.rebuild(linodeId, image, pass) }
                }
            "password" ->
                PasswordDialog(onDismiss = { dialog = null }) { pass ->
                    vm.runAction("password", "Root password reset") { container.repository.resetPassword(linodeId, pass) }
                }
            "snapshot" ->
                com.linode.manager.ui.components.ConfirmDialog(
                    "Take a snapshot?",
                    "Captures a manual backup of all disks. It replaces the previous manual snapshot.",
                    "Take snapshot",
                    onConfirm = { vm.runAction("snapshot", "Snapshot started") { container.repository.snapshot(linodeId) } },
                    onDismiss = { dialog = null },
                )
            "backups" ->
                com.linode.manager.ui.components.ConfirmDialog(
                    "Enable backups?",
                    "Backups are a paid add-on billed monthly per Linode.",
                    "Enable",
                    onConfirm = { vm.runAction("backups", "Backups enabled") { container.repository.enableBackups(linodeId) } },
                    onDismiss = { dialog = null },
                )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(
    linode: LinodeInstance,
    busy: String?,
    terminalOpen: Boolean,
    onSsh: () -> Unit,
    onPower: (String) -> Unit,
) {
    Column(Modifier.padding(bottom = 12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusBadge(linode.status)
            linodeSpecs(linode).takeIf { it.isNotBlank() }?.let { Pill(it) }
        }
        Spacer(Modifier.height(12.dp))
        // One row on every phone width: the primary action takes the
        // space, power actions are labelled icon buttons.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSsh, modifier = Modifier.weight(1f).height(48.dp)) {
                Icon(Icons.Filled.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (terminalOpen) "Resume terminal" else "Open terminal")
            }
            if (busy in listOf("boot", "reboot", "shutdown")) {
                CircularProgressIndicator(Modifier.padding(horizontal = 12.dp).size(24.dp), strokeWidth = 2.dp)
            } else {
                when (linode.status) {
                    "running" -> {
                        PowerIcon(Icons.Filled.RestartAlt, "Reboot") { onPower("reboot") }
                        PowerIcon(Icons.Filled.PowerSettingsNew, "Power off") { onPower("shutdown") }
                    }
                    "offline", "stopped" -> PowerIcon(Icons.Filled.PlayArrow, "Boot") { onPower("boot") }
                }
            }
        }
    }
}

@Composable
private fun PowerIcon(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    androidx.compose.material3.FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = label)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewTab(
    s: LinodeDetailState,
    l: LinodeInstance,
) {
    SectionCard("Summary") {
        InfoRow("Plan", l.type ?: "—")
        InfoRow("Region", l.region ?: "—")
        InfoRow("Image", l.image ?: "—")
        InfoRow("vCPUs", l.specs?.vcpus?.toString() ?: "—")
        InfoRow("Memory", Fmt.mb(l.specs?.memory))
        InfoRow("Storage", Fmt.mb(l.specs?.disk))
        InfoRow("Transfer", l.specs?.transfer?.let { "$it GB / month" } ?: "—")
        InfoRow("Created", Fmt.dateTime(l.created))
        InfoRow("Linode ID", l.id.toString(), copyable = true)
    }
    SectionCard("Protection") {
        InfoRow("Backups", if (l.backups?.enabled == true) "Enabled" else "Off")
        l.backups?.lastSuccessful?.let { InfoRow("Last backup", Fmt.relative(it)) }
        InfoRow("Shutdown watchdog", if (l.watchdogEnabled == true) "On" else "Off")
        InfoRow("Disk encryption", Fmt.label(l.diskEncryption))
        InfoRow("Cloud Firewalls", if (s.firewalls.isEmpty()) "None" else s.firewalls.joinToString { it.label })
    }
    if (l.tags.isNotEmpty()) {
        SectionCard("Tags") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                l.tags.forEach { Pill(it) }
            }
        }
    }
}

@Composable
private fun NetworkTab(
    s: LinodeDetailState,
    l: LinodeInstance,
) {
    val v4 = s.networking?.ipv4
    SectionCard("Public IPv4") {
        val pub = v4?.public.orEmpty()
        if (pub.isEmpty()) {
            l.ipv4.filterNot { it.startsWith("192.168.") }.forEach { InfoRow("Address", it, copyable = true, monospace = true) }
        } else {
            pub.forEach { ip ->
                InfoRow(ip.address, ip.rdns ?: "no reverse DNS", copyable = true)
            }
        }
    }
    val priv = v4?.private.orEmpty()
    if (priv.isNotEmpty()) {
        SectionCard("Private IPv4") { priv.forEach { InfoRow("Address", it.address, copyable = true, monospace = true) } }
    }
    SectionCard("IPv6") {
        InfoRow(
            "SLAAC",
            s.networking
                ?.ipv6
                ?.slaac
                ?.address ?: l.ipv6 ?: "—",
            copyable = true,
            monospace = true,
        )
        s.networking
            ?.ipv6
            ?.link_local
            ?.address
            ?.let { InfoRow("Link-local", it, copyable = true, monospace = true) }
    }
    Text(
        "Long-press an address to copy it.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StorageTab(s: LinodeDetailState) {
    SectionCard("Disks") {
        if (s.disks.isEmpty()) Text("No disks.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        s.disks.forEach { d ->
            InfoRow(d.label, listOfNotNull(Fmt.mb(d.size), d.filesystem, d.status?.takeIf { it != "ready" }).joinToString(" · "))
        }
    }
    SectionCard("Configuration profiles") {
        if (s.configs.isEmpty()) Text("No configs.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        s.configs.forEach { c -> InfoRow(c.label, c.kernel?.substringAfterLast('/') ?: "—") }
    }
    SectionCard("Block storage volumes") {
        if (s.volumes.isEmpty()) Text("No volumes attached.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        s.volumes.forEach { v -> InfoRow(v.label, "${v.size ?: "?"} GB · ${v.status ?: ""}") }
    }
}

@Composable
private fun MetricsTab(s: LinodeDetailState) {
    val data = s.stats?.data
    if (data == null) {
        SectionCard(null) {
            Text(
                "Metrics aren't available yet. New Linodes take a few minutes to report.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    SectionCard("CPU") {
        MetricChart(data.cpu.mapNotNull { it.getOrNull(1) }, { String.format(Locale.US, "%.1f%%", it) })
    }
    SectionCard("Network in (public IPv4)") {
        MetricChart(
            data.netv4
                ?.inbound
                .orEmpty()
                .mapNotNull { it.getOrNull(1) },
            ::bits,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
    SectionCard("Network out (public IPv4)") {
        MetricChart(
            data.netv4
                ?.out
                .orEmpty()
                .mapNotNull { it.getOrNull(1) },
            ::bits,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
    SectionCard("Disk I/O") {
        MetricChart(
            data.io?.io.orEmpty().mapNotNull {
                it.getOrNull(1)
            },
            { String.format(Locale.US, "%.1f blk/s", it) },
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

private fun bits(v: Double): String =
    when {
        v >= 1e9 -> String.format(Locale.US, "%.1f Gb/s", v / 1e9)
        v >= 1e6 -> String.format(Locale.US, "%.1f Mb/s", v / 1e6)
        v >= 1e3 -> String.format(Locale.US, "%.1f Kb/s", v / 1e3)
        else -> String.format(Locale.US, "%.0f b/s", v)
    }

@Composable
private fun ManageTab(
    l: LinodeInstance,
    busy: String?,
    onAction: (String) -> Unit,
) {
    SectionCard(
        null,
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(vertical = 8.dp),
    ) {
        ActionItem(Icons.Filled.OpenInFull, "Resize", "Move to a bigger or smaller plan", busy == "resize") { onAction("resize") }
        ActionItem(Icons.Filled.SettingsBackupRestore, "Rebuild", "Reinstall from an image — erases all data", busy == "rebuild") {
            onAction("rebuild")
        }
        ActionItem(
            Icons.Filled.Password,
            "Reset root password",
            "The Linode must be powered off",
            busy == "password",
        ) { onAction("password") }
        ActionItem(Icons.Filled.CameraAlt, "Take snapshot", "Manual backup of all disks", busy == "snapshot") { onAction("snapshot") }
        if (l.backups?.enabled != true) {
            ActionItem(
                Icons.Filled.Backup,
                "Enable backups",
                "Automatic daily and weekly backups (paid)",
                busy == "backups",
            ) { onAction("backups") }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        ActionItem(Icons.Filled.Delete, "Delete Linode", "Permanently destroy this Linode", busy == "delete", destructive = true) {
            onAction("delete")
        }
    }
}

@Composable
private fun ActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    busy: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        headlineContent = { Text(title, color = tint) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null, tint = if (destructive) tint else MaterialTheme.colorScheme.primary) },
        trailingContent = {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clickable(enabled = !busy, onClick = onClick),
    )
}

@Composable
private fun RenameDialog(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var label by remember { mutableStateOf(current) }
    val valid = Regex("^[a-zA-Z0-9][a-zA-Z0-9._-]{1,62}[a-zA-Z0-9]$").matches(label.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Linode") },
        text = {
            OutlinedTextField(
                label,
                { label = it },
                label = { Text("Label") },
                singleLine = true,
                isError = !valid,
                supportingText = { Text("3–64 characters: letters, digits, . _ -") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = {
                onDismiss()
                onSave(label.trim())
            }, enabled = valid && label.trim() != current) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ResizeDialog(
    s: LinodeDetailState,
    l: LinodeInstance,
    onDismiss: () -> Unit,
    onResize: (String) -> Unit,
) {
    var type by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Resize ${l.label}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("The Linode is powered off, migrated to the new plan and booted again. Expect a few minutes of downtime.")
                if (s.types.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Loading plans…")
                    }
                } else {
                    PickerField(
                        label = "New plan",
                        options = s.types.filter { it.id != l.type }.map { planOption(it) },
                        selectedId = type,
                        onPick = { type = it.id },
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onDismiss()
                onResize(type!!)
            }, enabled = type != null) { Text("Resize") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

internal fun planOption(t: com.linode.manager.data.remote.LinodeType) =
    PickerOption(
        id = t.id,
        title = "${t.label} — ${t.price?.monthly?.let { Fmt.money(it) + "/mo" } ?: ""}",
        subtitle = "${t.vcpus ?: "?"} vCPU · ${Fmt.mb(t.memory)} RAM · ${Fmt.mb(t.disk)} storage",
        group = Fmt.label(t.clazz),
    )

@Composable
private fun RebuildDialog(
    s: LinodeDetailState,
    l: LinodeInstance,
    onDismiss: () -> Unit,
    onRebuild: (String, String) -> Unit,
) {
    var image by remember { mutableStateOf(l.image) }
    var pass by remember { mutableStateOf("") }
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rebuild ${l.label}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("All data on this Linode's disks is erased and replaced by a fresh image.", color = MaterialTheme.colorScheme.error)
                PickerField(
                    label = "Image",
                    options =
                        s.images
                            .map {
                                PickerOption(
                                    it.id,
                                    it.label,
                                    it.id,
                                    if (it.isPublic ==
                                        true
                                    ) {
                                        "Public images"
                                    } else {
                                        "My images"
                                    },
                                )
                            }.sortedBy { it.group },
                    selectedId = image,
                    onPick = { image = it.id },
                )
                OutlinedTextField(
                    pass,
                    { pass = it },
                    label = { Text("New root password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = { Text("At least 11 characters, mixed types") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    typed,
                    { typed = it },
                    label = { Text("Type \"${l.label}\" to confirm") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onRebuild(image!!, pass)
                },
                enabled = image != null && pass.length >= 11 && typed.trim() == l.label,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) { Text("Rebuild") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PasswordDialog(
    onDismiss: () -> Unit,
    onReset: (String) -> Unit,
) {
    var pass by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset root password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Power the Linode off first — the password can only be changed while it's stopped.")
                OutlinedTextField(
                    pass,
                    { pass = it },
                    label = { Text("New root password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = { Text("At least 11 characters, mixed types") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onDismiss()
                onReset(pass)
            }, enabled = pass.length >= 11) { Text("Reset") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
