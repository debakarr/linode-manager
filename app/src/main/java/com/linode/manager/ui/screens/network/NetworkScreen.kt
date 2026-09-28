package com.linode.manager.ui.screens.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linode.manager.AppContainer
import com.linode.manager.data.remote.Domain
import com.linode.manager.data.remote.DomainRecord
import com.linode.manager.data.remote.Firewall
import com.linode.manager.data.remote.LkeCluster
import com.linode.manager.data.remote.NodeBalancer
import com.linode.manager.data.repository.ApiResult
import com.linode.manager.data.repository.isAuthFailure
import com.linode.manager.ui.components.AccessDeniedDialog
import com.linode.manager.ui.components.AuthFailure
import com.linode.manager.ui.components.ContentWidth
import com.linode.manager.ui.components.EmptyState
import com.linode.manager.ui.components.ErrorState
import com.linode.manager.ui.components.InfoRow
import com.linode.manager.ui.components.LoadingState
import com.linode.manager.ui.components.MenuAction
import com.linode.manager.ui.components.MessageEffect
import com.linode.manager.ui.components.OverflowMenu
import com.linode.manager.ui.components.PagePadding
import com.linode.manager.ui.components.Pill
import com.linode.manager.ui.components.ResourceCard
import com.linode.manager.ui.components.ScreenScaffold
import com.linode.manager.ui.components.StatusBadge
import com.linode.manager.ui.components.TypeToConfirmDialog
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class NetworkViewModel(
    private val container: AppContainer,
) : ViewModel() {
    var loading by mutableStateOf(true)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var authFailure by mutableStateOf<AuthFailure?>(null)
        private set
    var firewalls by mutableStateOf<List<Firewall>>(emptyList())
        private set
    var domains by mutableStateOf<List<Domain>>(emptyList())
        private set
    var nodeBalancers by mutableStateOf<List<NodeBalancer>>(emptyList())
        private set
    var clusters by mutableStateOf<List<LkeCluster>>(emptyList())
        private set
    var records by mutableStateOf<Map<Int, List<DomainRecord>>>(emptyMap())
        private set

    init {
        load()
    }

    fun load(refresh: Boolean = false) {
        viewModelScope.launch {
            if (refresh) refreshing = true else loading = firewalls.isEmpty() && domains.isEmpty()
            error = null
            val fw = async { container.repository.firewalls() }
            val dm = async { container.repository.domains() }
            val nb = async { container.repository.nodeBalancers() }
            val lke = async { container.repository.lkeClusters() }
            val fwR = fw.await()
            val dmR = dm.await()
            val nbR = nb.await()
            val lkeR = lke.await()
            // Only surface an error when nothing at all could be read: a token
            // scoped to firewalls only should still see its firewalls.
            val errs = listOf<ApiResult<*>>(fwR, dmR, nbR, lkeR).filterIsInstance<ApiResult.Err>()
            if (errs.size == 4) {
                val auth = errs.firstOrNull { it.isAuthFailure() }
                if (auth != null) authFailure = AuthFailure(auth.code, auth.message) else error = errs.first().message
            }
            firewalls = (fwR as? ApiResult.Ok)?.value?.data ?: emptyList()
            domains = (dmR as? ApiResult.Ok)?.value?.data ?: emptyList()
            nodeBalancers = (nbR as? ApiResult.Ok)?.value?.data ?: emptyList()
            clusters = (lkeR as? ApiResult.Ok)?.value?.data ?: emptyList()
            loading = false
            refreshing = false
        }
    }

    fun loadRecords(domainId: Int) {
        viewModelScope.launch {
            (container.repository.domainRecords(domainId) as? ApiResult.Ok)?.let {
                records = records + (domainId to it.value.data)
            }
        }
    }

    fun clearAuthFailure() {
        authFailure = null
    }

    fun clearMessage() {
        message = null
    }

    fun mutate(
        success: String,
        fn: suspend () -> ApiResult<*>,
    ) {
        viewModelScope.launch {
            when (val r = fn()) {
                is ApiResult.Ok -> {
                    message = success
                    load(true)
                }
                is ApiResult.Err -> if (r.isAuthFailure()) authFailure = AuthFailure(r.code, r.message) else message = r.message
            }
        }
    }

    suspend fun createFirewall(label: String): String? =
        when (val r = container.repository.createFirewall(label)) {
            is ApiResult.Ok -> {
                message = "Firewall $label created"
                load(true)
                null
            }
            is ApiResult.Err -> r.message
        }

    suspend fun createDomain(
        domain: String,
        email: String,
    ): String? =
        when (val r = container.repository.createDomain(domain, email)) {
            is ApiResult.Ok -> {
                message = "Domain $domain created"
                load(true)
                null
            }
            is ApiResult.Err -> r.message
        }
}

private val TABS = listOf("Firewalls", "Domains", "Balancers", "Kubernetes")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NetworkScreen(
    container: AppContainer,
    onUnauthorized: () -> Unit,
    onOpenFirewall: (Int) -> Unit,
) {
    val vm: NetworkViewModel = viewModel { NetworkViewModel(container) }
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var create by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf<Int?>(null) }
    var typeConfirm by remember { mutableStateOf<Triple<String, String, () -> Unit>?>(null) }

    vm.authFailure?.let { af ->
        AccessDeniedDialog(af, "firewall:read_only, domains:read_only (changes need :read_write)", container, {
            vm.clearAuthFailure()
        }, onUnauthorized)
    }
    MessageEffect(vm.message, snackbar) { vm.clearMessage() }

    ScreenScaffold(
        title = "Network",
        snackbarHostState = snackbar,
        floatingActionButton = {
            if (tab == 0 || tab == 1) {
                ExtendedFloatingActionButton(
                    onClick = { create = if (tab == 0) "firewall" else "domain" },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(if (tab == 0) "Firewall" else "Domain") },
                )
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
                TABS.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }
            }
            when {
                vm.loading -> LoadingState("Loading network…")
                vm.error != null -> ErrorState(vm.error!!) { vm.load() }
                else ->
                    PullToRefreshBox(vm.refreshing, { vm.load(true) }, Modifier.fillMaxSize()) {
                        ContentWidth {
                            LazyColumn(
                                contentPadding = PaddingValues(start = PagePadding, end = PagePadding, top = 12.dp, bottom = 96.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                when (tab) {
                                    0 ->
                                        firewallItems(vm.firewalls, onOpenFirewall) { f ->
                                            typeConfirm =
                                                Triple(f.label, "Linodes and NodeBalancers using this firewall lose its protection.") {
                                                    vm.mutate("Deleted ${f.label}") { container.repository.deleteFirewall(f.id) }
                                                }
                                        }
                                    1 ->
                                        domainItems(
                                            vm.domains,
                                            vm.records,
                                            expanded,
                                            onToggle = { d ->
                                                expanded = if (expanded == d.id) null else d.id
                                                if (expanded == d.id) vm.loadRecords(d.id)
                                            },
                                            onDelete = { d ->
                                                typeConfirm =
                                                    Triple(
                                                        d.domain,
                                                        "All DNS records for this domain are deleted and it stops resolving.",
                                                    ) {
                                                        vm.mutate("Deleted ${d.domain}") { container.repository.deleteDomain(d.id) }
                                                    }
                                            },
                                        )
                                    2 ->
                                        if (vm.nodeBalancers.isEmpty()) {
                                            item {
                                                EmptyState(
                                                    "No NodeBalancers",
                                                    message = "Load balancers you create in Cloud Manager show up here.",
                                                    icon = Icons.Filled.Balance,
                                                )
                                            }
                                        } else {
                                            items(vm.nodeBalancers, key = { it.id }) { n ->
                                                ResourceCard(
                                                    title = n.label ?: "NodeBalancer ${n.id}",
                                                    subtitle = n.region,
                                                    icon = Icons.Filled.Balance,
                                                    trailing = {
                                                        OverflowMenu(
                                                            listOf(
                                                                MenuAction("Delete", Icons.Filled.Delete, destructive = true) {
                                                                    val name = n.label ?: "NodeBalancer ${n.id}"
                                                                    typeConfirm =
                                                                        Triple(name, "Traffic to this NodeBalancer stops immediately.") {
                                                                            vm.mutate(
                                                                                "Deleted $name",
                                                                            ) { container.repository.deleteNodeBalancer(n.id) }
                                                                        }
                                                                },
                                                            ),
                                                        )
                                                    },
                                                ) {
                                                    n.ipv4?.let { InfoRow("IPv4", it, copyable = true, monospace = true) }
                                                    n.hostname?.let { InfoRow("Hostname", it, copyable = true) }
                                                }
                                            }
                                        }
                                    3 ->
                                        if (vm.clusters.isEmpty()) {
                                            item {
                                                EmptyState(
                                                    "No Kubernetes clusters",
                                                    message = "LKE clusters you create in Cloud Manager show up here.",
                                                    icon = Icons.Filled.AccountTree,
                                                )
                                            }
                                        } else {
                                            items(vm.clusters, key = { it.id }) { c ->
                                                ResourceCard(
                                                    title = c.label,
                                                    subtitle =
                                                        listOfNotNull(
                                                            c.region,
                                                            c.k8sVersion?.let { "Kubernetes $it" },
                                                        ).joinToString(" · "),
                                                    icon = Icons.Filled.AccountTree,
                                                    trailing = {
                                                        OverflowMenu(
                                                            listOf(
                                                                MenuAction("Delete", Icons.Filled.Delete, destructive = true) {
                                                                    typeConfirm =
                                                                        Triple(
                                                                            c.label,
                                                                            "The cluster and all of its node pools are destroyed.",
                                                                        ) {
                                                                            vm.mutate(
                                                                                "Deleted ${c.label}",
                                                                            ) { container.repository.deleteLkeCluster(c.id) }
                                                                        }
                                                                },
                                                            ),
                                                        )
                                                    },
                                                ) {
                                                    StatusBadge(c.status)
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

    typeConfirm?.let { (name, msg, action) ->
        TypeToConfirmDialog("Delete $name?", msg, name, "Delete", onConfirm = action, onDismiss = { typeConfirm = null })
    }
    when (create) {
        "firewall" ->
            CreateDialog(
                title = "Create firewall",
                fields = listOf("Label" to "3–32 characters"),
                onDismiss = { create = null },
            ) { values -> vm.createFirewall(values[0].trim()) }
        "domain" ->
            CreateDialog(
                title = "Add domain",
                fields = listOf("Domain (example.com)" to "", "SOA email" to "Contact address for the zone"),
                onDismiss = { create = null },
            ) { values -> vm.createDomain(values[0].trim(), values[1].trim()) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.firewallItems(
    list: List<Firewall>,
    onOpen: (Int) -> Unit,
    onDelete: (Firewall) -> Unit,
) {
    if (list.isEmpty()) {
        item {
            EmptyState(
                "No firewalls",
                message = "Cloud Firewalls filter traffic before it reaches your Linodes.",
                icon = Icons.Filled.Security,
            )
        }
        return
    }
    items(list, key = { it.id }) { f ->
        ResourceCard(
            title = f.label,
            subtitle =
                "Inbound ${f.rules?.inbound?.size ?: 0} rules (default ${f.rules?.inboundPolicy?.lowercase() ?: "?"}) · " +
                    "Outbound ${f.rules?.outbound?.size ?: 0} (default ${f.rules?.outboundPolicy?.lowercase() ?: "?"})",
            icon = Icons.Filled.Security,
            onClick = { onOpen(f.id) },
            trailing = {
                OverflowMenu(
                    listOf(
                        MenuAction("View & edit", Icons.Filled.Edit) { onOpen(f.id) },
                        MenuAction("Delete", Icons.Filled.Delete, destructive = true) { onDelete(f) },
                    ),
                )
            },
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusBadge(f.status)
                if (f.entities.isEmpty()) Pill("Not attached")
                f.entities.take(3).forEach { Pill(it.label ?: it.type ?: "service") }
                if (f.entities.size > 3) Pill("+${f.entities.size - 3}")
            }
        }
    }
}

private fun LazyListScope.domainItems(
    list: List<Domain>,
    records: Map<Int, List<DomainRecord>>,
    expanded: Int?,
    onToggle: (Domain) -> Unit,
    onDelete: (Domain) -> Unit,
) {
    if (list.isEmpty()) {
        item { EmptyState("No domains", message = "Host DNS for your domains on Linode's nameservers.", icon = Icons.Filled.Language) }
        return
    }
    items(list, key = { it.id }) { d ->
        ResourceCard(
            title = d.domain,
            subtitle = listOfNotNull(d.type, d.soaEmail).joinToString(" · "),
            icon = Icons.Filled.Language,
            onClick = { onToggle(d) },
            trailing = {
                IconButton(onClick = { onToggle(d) }) {
                    Icon(if (expanded == d.id) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = "Records")
                }
                OverflowMenu(listOf(MenuAction("Delete", Icons.Filled.Delete, destructive = true) { onDelete(d) }))
            },
        ) {
            StatusBadge(d.status)
            if (expanded == d.id) {
                val recs = records[d.id]
                when {
                    recs == null -> CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                    recs.isEmpty() ->
                        Text(
                            "No records.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    else ->
                        Column(Modifier.padding(top = 8.dp)) {
                            recs.forEach { r ->
                                InfoRow(
                                    "${r.type} ${r.name?.ifBlank { "@" } ?: "@"}",
                                    r.target ?: "",
                                    copyable = true,
                                    monospace = true,
                                )
                            }
                        }
                }
            }
        }
    }
}

@Composable
private fun CreateDialog(
    title: String,
    fields: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onSubmit: suspend (List<String>) -> String?,
) {
    val scope = rememberCoroutineScope()
    val values = remember { androidx.compose.runtime.mutableStateListOf(*Array(fields.size) { "" }) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fields.forEachIndexed { i, (label, hint) ->
                    OutlinedTextField(
                        values[i],
                        { values[i] = it },
                        label = { Text(label) },
                        singleLine = true,
                        supportingText = if (hint.isNotBlank()) ({ Text(hint) }) else null,
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = if (label.contains("email", true)) KeyboardType.Email else KeyboardType.Text,
                                autoCorrectEnabled = false,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    busy = true
                    err = null
                    scope.launch {
                        err = onSubmit(values.toList())
                        busy = false
                        if (err == null) onDismiss()
                    }
                },
                enabled = !busy && values.all { it.isNotBlank() },
            ) { Text(if (busy) "Creating…" else "Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}
