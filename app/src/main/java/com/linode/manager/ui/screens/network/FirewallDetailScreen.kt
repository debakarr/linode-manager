package com.linode.manager.ui.screens.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linode.manager.AppContainer
import com.linode.manager.data.remote.Firewall
import com.linode.manager.data.remote.FirewallDevice
import com.linode.manager.data.remote.FirewallRule
import com.linode.manager.data.remote.FirewallRuleAddresses
import com.linode.manager.data.remote.FirewallRulesUpdate
import com.linode.manager.data.repository.ApiResult
import com.linode.manager.data.repository.isAuthFailure
import com.linode.manager.ui.components.AccessDeniedDialog
import com.linode.manager.ui.components.AuthFailure
import com.linode.manager.ui.components.ConfirmDialog
import com.linode.manager.ui.components.ContentWidth
import com.linode.manager.ui.components.ErrorState
import com.linode.manager.ui.components.Fmt
import com.linode.manager.ui.components.InfoRow
import com.linode.manager.ui.components.LoadingState
import com.linode.manager.ui.components.MenuAction
import com.linode.manager.ui.components.MessageEffect
import com.linode.manager.ui.components.OverflowMenu
import com.linode.manager.ui.components.PagePadding
import com.linode.manager.ui.components.PickerField
import com.linode.manager.ui.components.PickerOption
import com.linode.manager.ui.components.ScreenScaffold
import com.linode.manager.ui.components.SectionCard
import com.linode.manager.ui.components.TypeToConfirmDialog
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class RuleDraft(
    val label: String = "",
    val action: String = "ACCEPT",
    val protocol: String = "TCP",
    val ports: String = "",
    val ipv4: String = "",
    val ipv6: String = "",
    val description: String = "",
)

private fun FirewallRule.toDraft() =
    RuleDraft(
        label = label ?: "",
        action = action ?: "ACCEPT",
        protocol = protocol ?: "TCP",
        ports = ports ?: "",
        ipv4 = addresses?.ipv4?.joinToString(", ") ?: "",
        ipv6 = addresses?.ipv6?.joinToString(", ") ?: "",
        description = description ?: "",
    )

private fun RuleDraft.toRule(): FirewallRule {
    val v4 = ipv4.split(',').map { it.trim() }.filter { it.isNotBlank() }
    val v6 = ipv6.split(',').map { it.trim() }.filter { it.isNotBlank() }
    return FirewallRule(
        action = action,
        label = label.ifBlank { null },
        description = description.ifBlank { null },
        protocol = protocol,
        ports = if (protocol == "TCP" || protocol == "UDP") ports.ifBlank { null } else null,
        addresses =
            FirewallRuleAddresses(
                ipv4 = v4.ifEmpty { null },
                ipv6 = v6.ifEmpty { null },
            ),
    )
}

class FirewallDetailViewModel(
    private val container: AppContainer,
    private val fwId: Int,
) : ViewModel() {
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var authFailure by mutableStateOf<AuthFailure?>(null)
        private set
    var firewall by mutableStateOf<Firewall?>(null)
        private set
    var devices by mutableStateOf<List<FirewallDevice>>(emptyList())
        private set
    var busy by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var linodes by mutableStateOf<List<com.linode.manager.data.remote.LinodeInstance>>(emptyList())
        private set

    fun clearMessage() {
        message = null
    }

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val fwD = async { container.repository.firewall(fwId) }
                val devD = async { container.repository.firewallDevices(fwId) }
                val fw = fwD.await()
                if (fw is ApiResult.Err) {
                    if (fw.isAuthFailure()) authFailure = AuthFailure(fw.code, fw.message) else error = fw.message
                    loading = false
                    return@launch
                }
                firewall = (fw as ApiResult.Ok).value
                devices = (devD.await() as? ApiResult.Ok)?.value?.data ?: emptyList()
                if (linodes.isEmpty()) {
                    (container.repository.linodes() as? ApiResult.Ok)?.let { linodes = it.value.data }
                }
            } catch (e: Exception) {
                error = e.message
            }
            loading = false
        }
    }

    fun clearAuthFailure() {
        authFailure = null
    }

    private fun failAs(
        target: String,
        r: ApiResult.Err,
    ) {
        if (r.isAuthFailure()) {
            authFailure = AuthFailure(r.code, "$target: ${r.message}")
        } else {
            message = "$target failed: ${r.message}"
        }
    }

    fun saveOverview(
        label: String,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            busy = "overview"
            message = null
            when (val r = container.repository.updateFirewall(fwId, label.ifBlank { null }, if (enabled) "enabled" else "disabled")) {
                is ApiResult.Ok -> {
                    firewall = r.value
                    message = "Firewall updated."
                }
                is ApiResult.Err -> failAs("Update", r)
            }
            busy = null
        }
    }

    fun saveRules(body: FirewallRulesUpdate) {
        viewModelScope.launch {
            busy = "rules"
            message = null
            when (val r = container.repository.updateFirewallRules(fwId, body)) {
                is ApiResult.Ok -> {
                    message = "Rules saved."
                    load()
                }
                is ApiResult.Err -> failAs("Save rules", r)
            }
            busy = null
        }
    }

    fun attach(
        entityId: Int,
        type: String,
    ) {
        viewModelScope.launch {
            busy = "attach"
            message = null
            when (val r = container.repository.attachFirewallDevice(fwId, entityId, type)) {
                is ApiResult.Ok -> {
                    message = "Device attached."
                    load()
                }
                is ApiResult.Err -> failAs("Attach", r)
            }
            busy = null
        }
    }

    fun detach(deviceId: Int) {
        viewModelScope.launch {
            busy = "detach-$deviceId"
            message = null
            when (val r = container.repository.detachFirewallDevice(fwId, deviceId)) {
                is ApiResult.Ok -> {
                    message = "Device removed."
                    load()
                }
                is ApiResult.Err -> failAs("Detach", r)
            }
            busy = null
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            busy = "delete"
            when (val r = container.repository.deleteFirewall(fwId)) {
                is ApiResult.Ok -> onDeleted()
                is ApiResult.Err -> failAs("Delete", r)
            }
            busy = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirewallDetailScreen(
    container: AppContainer,
    firewallId: Int,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onUnauthorized: () -> Unit,
) {
    val vm: FirewallDetailViewModel = viewModel(key = "fw-$firewallId") { FirewallDetailViewModel(container, firewallId) }
    val snackbar = remember { SnackbarHostState() }
    var showDelete by remember { mutableStateOf(false) }
    var detachTarget by remember { mutableStateOf<FirewallDevice?>(null) }

    vm.authFailure?.let { af ->
        AccessDeniedDialog(
            af,
            "firewall:read_only (changes need firewall:read_write)",
            container,
            { vm.clearAuthFailure() },
            onUnauthorized,
        )
    }
    MessageEffect(vm.message, snackbar) { vm.clearMessage() }

    ScreenScaffold(
        title = vm.firewall?.label ?: "Firewall",
        subtitle = vm.firewall?.let { "Cloud Firewall · ${it.status ?: ""}" },
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            OverflowMenu(listOf(MenuAction("Delete firewall", Icons.Filled.Delete, destructive = true) { showDelete = true }))
        },
    ) { pad ->
        val fw = vm.firewall
        when {
            vm.loading && fw == null -> LoadingState("Loading firewall…", Modifier.padding(pad))
            fw == null -> ErrorState(vm.error ?: "Firewall not found.", Modifier.padding(pad)) { vm.load() }
            else ->
                ContentWidth(Modifier.padding(pad)) {
                    var label by remember(fw.id, fw.label) { mutableStateOf(fw.label) }
                    var enabled by remember(fw.id, fw.status) { mutableStateOf(fw.status != "disabled") }
                    var inPolicy by remember(fw.id, fw.rules) { mutableStateOf(fw.rules?.inboundPolicy ?: "DROP") }
                    var outPolicy by remember(fw.id, fw.rules) { mutableStateOf(fw.rules?.outboundPolicy ?: "ACCEPT") }
                    val inbound =
                        remember(fw.id, fw.rules) {
                            mutableStateListOf(
                                *(
                                    fw.rules
                                        ?.inbound
                                        ?.map { it.toDraft() }
                                        ?.toTypedArray()
                                        ?: emptyArray()
                                ),
                            )
                        }
                    val outbound =
                        remember(fw.id, fw.rules) {
                            mutableStateListOf(
                                *(
                                    fw.rules
                                        ?.outbound
                                        ?.map { it.toDraft() }
                                        ?.toTypedArray()
                                        ?: emptyArray()
                                ),
                            )
                        }
                    var rulesError by remember { mutableStateOf<String?>(null) }
                    var attachId by remember { mutableStateOf<String?>(null) }

                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .imePadding()
                            .padding(PagePadding),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SectionCard("Overview") {
                            OutlinedTextField(
                                label,
                                { label = it },
                                label = { Text("Label") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(if (enabled) "Enabled" else "Disabled", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "Disabled firewalls let all traffic through",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(checked = enabled, onCheckedChange = { enabled = it })
                            }
                            Spacer(Modifier.height(8.dp))
                            FilledTonalButton(
                                onClick = { vm.saveOverview(label.trim(), enabled) },
                                enabled = vm.busy == null && (label.trim() != fw.label || enabled != (fw.status != "disabled")),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (vm.busy ==
                                    "overview"
                                ) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Save overview")
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            InfoRow("Created", Fmt.dateTime(fw.created))
                            InfoRow("Updated", Fmt.dateTime(fw.updated))
                        }

                        SectionCard("Default policies") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SmallDropdown("Inbound", listOf("ACCEPT", "DROP"), inPolicy, { inPolicy = it }, Modifier.weight(1f))
                                SmallDropdown("Outbound", listOf("ACCEPT", "DROP"), outPolicy, { outPolicy = it }, Modifier.weight(1f))
                            }
                            Text(
                                "Applied to traffic that doesn't match any rule.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }

                        RulesSection("Inbound rules", inbound, onAdd = {
                            inbound.add(RuleDraft(label = "rule-${inbound.size + 1}", ipv4 = "0.0.0.0/0", ipv6 = "::/0"))
                        })
                        RulesSection("Outbound rules", outbound, onAdd = {
                            outbound.add(RuleDraft(label = "rule-${outbound.size + 1}", ipv4 = "0.0.0.0/0", ipv6 = "::/0"))
                        })

                        rulesError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                        Button(
                            onClick = {
                                rulesError = null
                                val all = inbound + outbound
                                rulesError =
                                    when {
                                        all.any {
                                            it.label.isNotBlank() && it.label.length < 3
                                        } -> "Rule labels must be empty or at least 3 characters."
                                        all.any {
                                            it.ipv4.isBlank() && it.ipv6.isBlank()
                                        } -> "Every rule needs at least one IPv4 or IPv6 address/network."
                                        all.size > 25 -> "Firewalls allow at most 25 rules in total."
                                        else -> null
                                    }
                                if (rulesError != null) return@Button
                                vm.saveRules(
                                    FirewallRulesUpdate(
                                        inboundPolicy = inPolicy,
                                        outboundPolicy = outPolicy,
                                        inbound = inbound.map { it.toRule() },
                                        outbound = outbound.map { it.toRule() },
                                    ),
                                )
                            },
                            enabled = vm.busy == null,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            if (vm.busy ==
                                "rules"
                            ) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Save rules & policies")
                            }
                        }

                        SectionCard("Protected services") {
                            if (vm.devices.isEmpty()) {
                                Text("Not attached to any Linode or NodeBalancer.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            vm.devices.forEach { d ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            d.entity?.label ?: "${d.entity?.type ?: "service"} #${d.entity?.id}",
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Text(
                                            Fmt.label(d.entity?.type),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    TextButton(onClick = { detachTarget = d }, enabled = vm.busy == null) {
                                        Text("Remove", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            val attached = vm.devices.mapNotNull { it.entity?.id }.toSet()
                            val candidates = vm.linodes.filter { it.id !in attached }
                            if (candidates.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                PickerField(
                                    label = "Add a Linode",
                                    options = candidates.map { PickerOption(it.id.toString(), it.label, it.region) },
                                    selectedId = attachId,
                                    onPick = { attachId = it.id },
                                )
                                Spacer(Modifier.height(8.dp))
                                FilledTonalButton(
                                    onClick = {
                                        attachId?.toIntOrNull()?.let { vm.attach(it, "linode") }
                                        attachId = null
                                    },
                                    enabled = vm.busy == null && attachId != null,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Attach") }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
        }
    }

    val fw = vm.firewall
    if (showDelete && fw != null) {
        TypeToConfirmDialog(
            title = "Delete ${fw.label}?",
            message = "Services using this firewall lose its protection immediately.",
            expected = fw.label,
            confirmLabel = "Delete",
            onConfirm = { vm.delete(onDeleted) },
            onDismiss = { showDelete = false },
        )
    }
    detachTarget?.let { d ->
        ConfirmDialog(
            "Remove ${d.entity?.label ?: "service"}?",
            "It will no longer be protected by this firewall.",
            "Remove",
            destructive = true,
            onConfirm = { vm.detach(d.id) },
            onDismiss = { detachTarget = null },
        )
    }
}

@Composable
private fun RulesSection(
    title: String,
    rules: MutableList<RuleDraft>,
    onAdd: () -> Unit,
) {
    SectionCard(title, action = { TextButton(onClick = onAdd) { Text("Add rule") } }) {
        if (rules.isEmpty()) {
            Text("No rules — the default policy applies to all traffic.", style = MaterialTheme.typography.bodyMedium)
        }
        rules.forEachIndexed { i, r ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Rule ${i + 1}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { rules.removeAt(i) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove rule", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    OutlinedTextField(r.label, {
                        rules[i] = r.copy(label = it)
                    }, label = { Text("Label") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallDropdown("Action", listOf("ACCEPT", "DROP"), r.action, { rules[i] = r.copy(action = it) }, Modifier.weight(1f))
                        SmallDropdown(
                            "Protocol",
                            listOf("TCP", "UDP", "ICMP", "IPENCAP"),
                            r.protocol,
                            { rules[i] = r.copy(protocol = it) },
                            Modifier.weight(1f),
                        )
                    }
                    if (r.protocol == "TCP" || r.protocol == "UDP") {
                        OutlinedTextField(r.ports, {
                            rules[i] = r.copy(ports = it)
                        }, label = { Text("Ports (e.g. 22, 80, 443 or 3000-4000)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedTextField(r.ipv4, {
                        rules[i] = r.copy(ipv4 = it)
                    }, label = { Text("IPv4 (comma separated, e.g. 0.0.0.0/0)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(r.ipv6, {
                        rules[i] = r.copy(ipv6 = it)
                    }, label = { Text("IPv6 (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(r.description, {
                        rules[i] = r.copy(description = it)
                    }, label = { Text("Description (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmallDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = modifier) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier =
                Modifier.fillMaxWidth().menuAnchor(
                    androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    true,
                ),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(o) }, onClick = {
                    onPick(o)
                    expanded = false
                })
            }
        }
    }
}
