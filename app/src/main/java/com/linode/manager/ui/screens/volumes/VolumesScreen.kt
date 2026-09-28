package com.linode.manager.ui.screens.volumes

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linode.manager.AppContainer
import com.linode.manager.data.remote.LinodeInstance
import com.linode.manager.data.remote.Region
import com.linode.manager.data.remote.Volume
import com.linode.manager.data.repository.ApiResult
import com.linode.manager.data.repository.isAuthFailure
import com.linode.manager.ui.components.AccessDeniedDialog
import com.linode.manager.ui.components.AuthFailure
import com.linode.manager.ui.components.ConfirmDialog
import com.linode.manager.ui.components.ContentWidth
import com.linode.manager.ui.components.EmptyState
import com.linode.manager.ui.components.ErrorState
import com.linode.manager.ui.components.LoadingState
import com.linode.manager.ui.components.MenuAction
import com.linode.manager.ui.components.MessageEffect
import com.linode.manager.ui.components.OverflowMenu
import com.linode.manager.ui.components.PagePadding
import com.linode.manager.ui.components.PickerField
import com.linode.manager.ui.components.PickerOption
import com.linode.manager.ui.components.Pill
import com.linode.manager.ui.components.ResourceCard
import com.linode.manager.ui.components.ScreenScaffold
import com.linode.manager.ui.components.StatusBadge
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class VolumesViewModel(
    private val container: AppContainer,
) : ViewModel() {
    var loading by mutableStateOf(true)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var authFailure by mutableStateOf<AuthFailure?>(null)
        private set
    var volumes by mutableStateOf<List<Volume>>(emptyList())
        private set
    var linodes by mutableStateOf<List<LinodeInstance>>(emptyList())
        private set
    var regions by mutableStateOf<List<Region>>(emptyList())
        private set
    var busyId by mutableStateOf<Int?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    init {
        load()
    }

    fun load(refresh: Boolean = false) {
        viewModelScope.launch {
            if (refresh) refreshing = true else loading = volumes.isEmpty()
            error = null
            val v = async { container.repository.volumes() }
            val l = async { container.repository.linodes() }
            when (val r = v.await()) {
                is ApiResult.Ok -> volumes = r.value.data
                is ApiResult.Err -> if (r.isAuthFailure()) authFailure = AuthFailure(r.code, r.message) else error = r.message
            }
            (l.await() as? ApiResult.Ok)?.let { linodes = it.value.data }
            loading = false
            refreshing = false
        }
    }

    fun loadRegions() {
        if (regions.isNotEmpty()) return
        viewModelScope.launch {
            (container.repository.regions() as? ApiResult.Ok)?.let { r ->
                regions = r.value.data.filter { it.status == "ok" && "Block Storage" in it.capabilities }
            }
        }
    }

    fun clearAuthFailure() {
        authFailure = null
    }

    fun clearMessage() {
        message = null
    }

    fun act(
        id: Int,
        success: String,
        fn: suspend () -> ApiResult<*>,
    ) {
        viewModelScope.launch {
            busyId = id
            when (val r = fn()) {
                is ApiResult.Ok -> {
                    message = success
                    load(true)
                }
                is ApiResult.Err -> if (r.isAuthFailure()) authFailure = AuthFailure(r.code, r.message) else message = r.message
            }
            busyId = null
        }
    }

    suspend fun create(
        label: String,
        region: String,
        size: Int,
        linodeId: Int?,
    ): String? =
        when (val r = container.repository.createVolume(label, region, size, linodeId)) {
            is ApiResult.Ok -> {
                message = "Volume $label created"
                load(true)
                null
            }
            is ApiResult.Err -> r.message
        }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VolumesScreen(
    container: AppContainer,
    onUnauthorized: () -> Unit,
) {
    val vm: VolumesViewModel = viewModel { VolumesViewModel(container) }
    val snackbar = remember { SnackbarHostState() }
    var showCreate by remember { mutableStateOf(false) }
    var attach by remember { mutableStateOf<Volume?>(null) }
    var resize by remember { mutableStateOf<Volume?>(null) }
    var delete by remember { mutableStateOf<Volume?>(null) }
    var detach by remember { mutableStateOf<Volume?>(null) }

    vm.authFailure?.let { af ->
        AccessDeniedDialog(af, "volumes:read_only (changes need volumes:read_write)", container, { vm.clearAuthFailure() }, onUnauthorized)
    }
    MessageEffect(vm.message, snackbar) { vm.clearMessage() }

    ScreenScaffold(
        title = "Volumes",
        subtitle = if (vm.volumes.isNotEmpty()) "${vm.volumes.size} volumes · ${vm.volumes.sumOf { it.size ?: 0 }} GB" else null,
        snackbarHostState = snackbar,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    showCreate = true
                    vm.loadRegions()
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Create") },
            )
        },
    ) { pad ->
        when {
            vm.loading -> LoadingState("Loading volumes…", Modifier.padding(pad))
            vm.error != null && vm.volumes.isEmpty() -> ErrorState(vm.error!!, Modifier.padding(pad)) { vm.load() }
            else ->
                PullToRefreshBox(vm.refreshing, { vm.load(true) }, Modifier.padding(pad).fillMaxSize()) {
                    if (vm.volumes.isEmpty()) {
                        EmptyState(
                            "No volumes",
                            message = "Block Storage adds extra disk space you can move between Linodes in the same region.",
                            icon = Icons.Filled.Storage,
                            actionLabel = "Create volume",
                            onAction = {
                                showCreate = true
                                vm.loadRegions()
                            },
                        )
                    } else {
                        ContentWidth {
                            LazyColumn(
                                contentPadding = PaddingValues(start = PagePadding, end = PagePadding, top = 4.dp, bottom = 96.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(vm.volumes, key = { it.id }) { v ->
                                    ResourceCard(
                                        title = v.label,
                                        subtitle =
                                            listOfNotNull(
                                                v.region,
                                                "${v.size ?: "?"} GB",
                                                v.hardwareType?.uppercase(),
                                            ).joinToString(" · "),
                                        icon = Icons.Filled.Storage,
                                        trailing = {
                                            if (vm.busyId == v.id) {
                                                CircularProgressIndicator(Modifier.size(20.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                                            } else {
                                                OverflowMenu(
                                                    listOfNotNull(
                                                        if (v.linodeId == null) {
                                                            MenuAction("Attach to Linode", Icons.Filled.Link) { attach = v }
                                                        } else {
                                                            MenuAction("Detach", Icons.Filled.LinkOff) { detach = v }
                                                        },
                                                        MenuAction("Resize", Icons.Filled.OpenInFull) { resize = v },
                                                        MenuAction(
                                                            "Delete",
                                                            Icons.Filled.Delete,
                                                            destructive = true,
                                                            enabled = v.linodeId == null,
                                                        ) {
                                                            delete =
                                                                v
                                                        },
                                                    ),
                                                )
                                            }
                                        },
                                    ) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            StatusBadge(v.status)
                                            Pill(
                                                if (v.linodeId !=
                                                    null
                                                ) {
                                                    "Attached · ${v.linodeLabel ?: "Linode ${v.linodeId}"}"
                                                } else {
                                                    "Unattached"
                                                },
                                            )
                                            if (v.encryption == "enabled") Pill("Encrypted")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }

    if (showCreate) CreateVolumeDialog(vm) { showCreate = false }
    attach?.let { v ->
        var target by remember(v.id) { mutableStateOf<String?>(null) }
        val candidates = vm.linodes.filter { it.region == v.region }
        AlertDialog(
            onDismissRequest = { attach = null },
            title = { Text("Attach ${v.label}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Only Linodes in ${v.region} can use this volume.")
                    if (candidates.isEmpty()) {
                        Text("You have no Linodes in ${v.region}.", color = MaterialTheme.colorScheme.error)
                    } else {
                        PickerField(
                            label = "Linode",
                            options = candidates.map { PickerOption(it.id.toString(), it.label, it.status) },
                            selectedId = target,
                            onPick = { target = it.id },
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val id = target!!.toInt()
                    attach = null
                    vm.act(v.id, "Attached ${v.label}") { container.repository.attachVolume(v.id, id) }
                }, enabled = target != null) { Text("Attach") }
            },
            dismissButton = { TextButton(onClick = { attach = null }) { Text("Cancel") } },
        )
    }
    detach?.let { v ->
        ConfirmDialog(
            "Detach ${v.label}?",
            "Unmount it inside ${v.linodeLabel ?: "the Linode"} first to avoid data loss.",
            "Detach",
            onConfirm = { vm.act(v.id, "Detaching ${v.label}") { container.repository.detachVolume(v.id) } },
            onDismiss = { detach = null },
        )
    }
    delete?.let { v ->
        ConfirmDialog(
            "Delete ${v.label}?",
            "The volume and all its data are permanently destroyed.",
            "Delete",
            destructive = true,
            onConfirm = { vm.act(v.id, "Deleted ${v.label}") { container.repository.deleteVolume(v.id) } },
            onDismiss = { delete = null },
        )
    }
    resize?.let { v ->
        var size by remember(v.id) { mutableStateOf(((v.size ?: 10) + 10).toString()) }
        val n = size.toIntOrNull()
        val valid = n != null && n > (v.size ?: 0) && n <= 16384
        AlertDialog(
            onDismissRequest = { resize = null },
            title = { Text("Resize ${v.label}") },
            text = {
                OutlinedTextField(
                    size,
                    { size = it.filter(Char::isDigit).take(5) },
                    label = { Text("New size (GB)") },
                    singleLine = true,
                    isError = !valid,
                    supportingText = { Text("Volumes can only grow. Currently ${v.size} GB.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(onClick = {
                    resize = null
                    vm.act(v.id, "Resizing ${v.label} to $n GB") { container.repository.resizeVolume(v.id, n!!) }
                }, enabled = valid) { Text("Resize") }
            },
            dismissButton = { TextButton(onClick = { resize = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CreateVolumeDialog(
    vm: VolumesViewModel,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var label by remember { mutableStateOf("") }
    var region by remember { mutableStateOf<String?>(null) }
    var linode by remember { mutableStateOf<String?>(null) }
    var size by remember { mutableStateOf("20") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(linode) {
        linode?.let { id ->
            vm.linodes
                .firstOrNull { it.id.toString() == id }
                ?.region
                ?.let { region = it }
        }
    }
    val sz = size.toIntOrNull()
    val labelOk = Regex("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,31}$").matches(label)
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Create volume") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    label,
                    { label = it.trim() },
                    label = { Text("Label") },
                    singleLine = true,
                    isError = label.isNotEmpty() && !labelOk,
                    supportingText = { Text("Letters, digits, - and _") },
                    modifier = Modifier.fillMaxWidth(),
                )
                PickerField(
                    label = "Attach to (optional)",
                    options =
                        listOf(PickerOption("", "Don't attach")) + vm.linodes.map { PickerOption(it.id.toString(), it.label, it.region) },
                    selectedId = linode ?: "",
                    onPick = { linode = it.id.ifBlank { null } },
                )
                PickerField(
                    label = "Region",
                    options = vm.regions.map { PickerOption(it.id, it.label.ifBlank { it.id }, it.id) },
                    selectedId = region,
                    onPick = { region = it.id },
                    enabled = linode == null,
                    supportingText = if (linode != null) "Same region as the Linode" else null,
                )
                OutlinedTextField(
                    size,
                    { size = it.filter(Char::isDigit).take(5) },
                    label = { Text("Size (GB)") },
                    singleLine = true,
                    isError = sz == null || sz < 10,
                    supportingText = { Text("10 GB minimum") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    busy = true
                    err = null
                    scope.launch {
                        err = vm.create(label, region!!, sz!!, linode?.toInt())
                        busy = false
                        if (err == null) onDismiss()
                    }
                },
                enabled = !busy && labelOk && region != null && sz != null && sz >= 10,
            ) { Text(if (busy) "Creating…" else "Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}
