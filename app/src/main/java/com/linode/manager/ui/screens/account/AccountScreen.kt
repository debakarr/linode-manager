package com.linode.manager.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linode.manager.AppContainer
import com.linode.manager.BuildConfig
import com.linode.manager.data.DeviceSshKey
import com.linode.manager.data.SshKeyGen
import com.linode.manager.data.ThemeMode
import com.linode.manager.data.remote.Account
import com.linode.manager.data.remote.Invoice
import com.linode.manager.data.remote.LinodeEvent
import com.linode.manager.data.remote.Profile
import com.linode.manager.data.remote.SshKey
import com.linode.manager.data.remote.SupportTicket
import com.linode.manager.data.remote.TransferUsage
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
import com.linode.manager.ui.components.ScreenScaffold
import com.linode.manager.ui.components.SectionCard
import com.linode.manager.ui.components.StatusBadge
import com.linode.manager.ui.components.copyToClipboard
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class AccountState(
    val loading: Boolean = true,
    val error: String? = null,
    val authFailure: AuthFailure? = null,
    val profile: Profile? = null,
    val account: Account? = null,
    val transfer: TransferUsage? = null,
    val invoices: List<Invoice> = emptyList(),
    val sshKeys: List<SshKey> = emptyList(),
    val tickets: List<SupportTicket> = emptyList(),
    val events: List<LinodeEvent> = emptyList(),
)

class AccountViewModel(
    private val container: AppContainer,
) : ViewModel() {
    var state by mutableStateOf(AccountState())
        private set
    var deviceKeys by mutableStateOf<List<DeviceSshKey>>(emptyList())
        private set
    var generating by mutableStateOf(false)
        private set
    var lastGenerated by mutableStateOf<DeviceSshKey?>(null)
        private set
    var uploadingFp by mutableStateOf<String?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
        private set

    init {
        deviceKeys = container.deviceKeys.list()
        load()
    }

    fun load() {
        viewModelScope.launch {
            state = state.copy(loading = state.profile == null, error = null)
            try {
                val p = async { container.repository.profile() }
                val a = async { container.repository.account() }
                val t = async { container.repository.transfer() }
                val inv = async { container.repository.invoices() }
                val keys = async { container.repository.sshKeys() }
                val tick = async { container.repository.tickets() }
                val ev = async { container.repository.events() }
                val pr = p.await()
                if (pr is ApiResult.Err && pr.isAuthFailure()) {
                    state = state.copy(loading = false, authFailure = AuthFailure(pr.code, "Profile: ${pr.message}"))
                    return@launch
                }
                state =
                    AccountState(
                        loading = false,
                        profile = (pr as? ApiResult.Ok)?.value,
                        account = (a.await() as? ApiResult.Ok)?.value,
                        transfer = (t.await() as? ApiResult.Ok)?.value,
                        invoices = (inv.await() as? ApiResult.Ok)?.value?.data?.take(10) ?: emptyList(),
                        sshKeys = (keys.await() as? ApiResult.Ok)?.value?.data ?: emptyList(),
                        tickets = (tick.await() as? ApiResult.Ok)?.value?.data?.take(10) ?: emptyList(),
                        events = (ev.await() as? ApiResult.Ok)?.value?.data?.take(15) ?: emptyList(),
                        error = (pr as? ApiResult.Err)?.message,
                    )
            } catch (e: Exception) {
                state = state.copy(loading = false, error = e.message)
            }
        }
    }

    fun clearAuthFailure() {
        state = state.copy(authFailure = null)
    }

    fun clearNotice() {
        notice = null
    }

    fun deleteAccountKey(
        id: Int,
        label: String,
    ) {
        viewModelScope.launch {
            when (val r = container.repository.deleteSshKey(id)) {
                is ApiResult.Ok -> {
                    notice = "Removed $label from your profile"
                    load()
                }
                is ApiResult.Err ->
                    if (r.isAuthFailure()) {
                        state = state.copy(authFailure = AuthFailure(r.code, r.message))
                    } else {
                        notice = r.message
                    }
            }
        }
    }

    suspend fun addAccountKey(
        label: String,
        key: String,
    ): String? =
        when (val r = container.repository.createSshKey(label, key)) {
            is ApiResult.Ok -> {
                notice = "Added $label"
                load()
                null
            }
            is ApiResult.Err -> r.message
        }

    /** Generate an RSA keypair on-device (runs off the main thread). */
    fun generateDeviceKey(label: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            generating = true
            notice = null
            try {
                val key = SshKeyGen.generateRsa(label.ifBlank { "device-key" })
                container.deviceKeys.add(key)
                deviceKeys = container.deviceKeys.list()
                lastGenerated = key
            } catch (e: Exception) {
                notice = "Key generation failed: ${e.message}"
            }
            generating = false
        }
    }

    fun clearLastGenerated() {
        lastGenerated = null
    }

    fun deleteDeviceKey(fingerprint: String) {
        container.deviceKeys.remove(fingerprint)
        deviceKeys = container.deviceKeys.list()
        notice = "Device key deleted. Servers that trust it still list it in authorized_keys."
    }

    /** Upload a device public key to the Linode account so new Linodes get it. */
    fun uploadDeviceKey(key: DeviceSshKey) {
        viewModelScope.launch {
            uploadingFp = key.fingerprint
            notice = null
            when (val r = container.repository.createSshKey(key.label, key.publicKey)) {
                is ApiResult.Ok -> {
                    notice = "Public key added to your Linode account — pick it when creating Linodes."
                    load()
                }
                is ApiResult.Err ->
                    if (r.isAuthFailure()) {
                        state = state.copy(authFailure = AuthFailure(r.code, r.message))
                    } else {
                        notice = "Upload failed: ${r.message}"
                    }
            }
            uploadingFp = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    container: AppContainer,
    onUnauthorized: () -> Unit,
    onLogout: () -> Unit,
) {
    val vm: AccountViewModel = viewModel { AccountViewModel(container) }
    val s = vm.state
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showLogout by remember { mutableStateOf(false) }
    var showAddKey by remember { mutableStateOf(false) }
    var showGenerate by remember { mutableStateOf(false) }
    var viewKey by remember { mutableStateOf<DeviceSshKey?>(null) }
    var deleteDeviceKey by remember { mutableStateOf<DeviceSshKey?>(null) }
    var deleteAccountKey by remember { mutableStateOf<SshKey?>(null) }

    s.authFailure?.let { af ->
        AccessDeniedDialog(af, "account:read_only", container, { vm.clearAuthFailure() }, onUnauthorized)
    }
    MessageEffect(vm.notice, snackbar) { vm.clearNotice() }

    ScreenScaffold(
        title = "Account",
        snackbarHostState = snackbar,
        actions = {
            IconButton(
                onClick = { showLogout = true },
            ) { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out") }
        },
    ) { pad ->
        when {
            s.loading -> LoadingState("Loading account…", Modifier.padding(pad))
            s.error != null && s.profile == null -> ErrorState(s.error, Modifier.padding(pad)) { vm.load() }
            else ->
                PullToRefreshBox(isRefreshing = false, onRefresh = { vm.load() }, modifier = Modifier.padding(pad).fillMaxSize()) {
                    ContentWidth {
                        LazyColumn(
                            contentPadding = PaddingValues(start = PagePadding, end = PagePadding, top = 4.dp, bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            item { ProfileHeader(s.profile, s.account) }

                            item {
                                SectionCard("Billing") {
                                    InfoRow("Balance", Fmt.money(s.account?.balance, s.account?.currency))
                                    InfoRow("Uninvoiced", Fmt.money(s.account?.balanceUninvoiced, s.account?.currency))
                                    s.transfer?.let { InfoRow("Transfer this month", "${it.used ?: 0} / ${it.quota ?: 0} GB") }
                                    if (s.invoices.isNotEmpty()) {
                                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                        Text(
                                            "Recent invoices",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        s.invoices.take(5).forEach { i ->
                                            InfoRow(Fmt.date(i.date), Fmt.money(i.total, s.account?.currency))
                                        }
                                    }
                                }
                            }

                            item {
                                SectionCard(
                                    "SSH keys on this device",
                                    action = {
                                        TextButton(
                                            onClick = { showGenerate = true },
                                            enabled = !vm.generating,
                                        ) { Text("Generate") }
                                    },
                                ) {
                                    Text(
                                        "Private keys never leave this phone. Upload the public key to your profile so new Linodes trust it, or add it to a server's ~/.ssh/authorized_keys.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (vm.generating) {
                                        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                            Spacer(Modifier.width(12.dp))
                                            Text("Generating a 3072-bit RSA key…", style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                    if (vm.deviceKeys.isEmpty() && !vm.generating) {
                                        Text("No device keys yet.", modifier = Modifier.padding(top = 12.dp))
                                    }
                                    vm.deviceKeys.forEach { k ->
                                        KeyRow(k.label, k.fingerprint, busy = vm.uploadingFp == k.fingerprint) {
                                            OverflowMenu(
                                                listOf(
                                                    MenuAction("View public key", Icons.Filled.Visibility) { viewKey = k },
                                                    MenuAction(
                                                        "Copy public key",
                                                        Icons.Filled.ContentCopy,
                                                    ) { copyToClipboard(context, "Public key", k.publicKey) },
                                                    MenuAction(
                                                        "Upload to Linode profile",
                                                        Icons.Filled.CloudUpload,
                                                        enabled =
                                                            vm.uploadingFp == null,
                                                    ) { vm.uploadDeviceKey(k) },
                                                    MenuAction("Delete", Icons.Filled.Delete, destructive = true) { deleteDeviceKey = k },
                                                ),
                                            )
                                        }
                                    }
                                }
                            }

                            item {
                                SectionCard(
                                    "SSH keys on your profile",
                                    action = { TextButton(onClick = { showAddKey = true }) { Text("Add") } },
                                ) {
                                    Text(
                                        "Offered when creating Linodes and installed as root's authorized keys.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (s.sshKeys.isEmpty()) Text("No keys on your profile.", modifier = Modifier.padding(top = 12.dp))
                                    s.sshKeys.forEach { k ->
                                        KeyRow(k.label, k.ssh_key?.let { keySummary(it) } ?: "", busy = false) {
                                            OverflowMenu(
                                                listOf(
                                                    MenuAction("Copy", Icons.Filled.ContentCopy) {
                                                        copyToClipboard(
                                                            context,
                                                            "Public key",
                                                            k.ssh_key ?: "",
                                                        )
                                                    },
                                                    MenuAction("Remove", Icons.Filled.Delete, destructive = true) { deleteAccountKey = k },
                                                ),
                                            )
                                        }
                                    }
                                }
                            }

                            if (s.tickets.isNotEmpty()) {
                                item {
                                    SectionCard("Support tickets") {
                                        s.tickets.forEach { t ->
                                            Row(
                                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                                    Text(t.summary ?: "Ticket #${t.id}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text(
                                                        "#${t.id} · ${Fmt.relative(t.updated ?: t.opened)}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                                StatusBadge(t.status)
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                SectionCard("Appearance") {
                                    val mode = container.settings.themeMode
                                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                        ThemeMode.entries.forEachIndexed { i, m ->
                                            SegmentedButton(
                                                selected = mode == m,
                                                onClick = { container.settings.updateThemeMode(m) },
                                                shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                                            ) { Text(Fmt.label(m.name.lowercase())) }
                                        }
                                    }
                                }
                            }

                            item {
                                OutlinedButton(
                                    onClick = { showLogout = true },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Sign out")
                                }
                                Text(
                                    "Linode Manager ${BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    }
                }
        }
    }

    if (showLogout) {
        ConfirmDialog(
            "Sign out?",
            "Your token is removed from this device and open terminals are closed.",
            "Sign out",
            destructive = true,
            onConfirm = onLogout,
            onDismiss = { showLogout = false },
        )
    }
    deleteDeviceKey?.let { k ->
        ConfirmDialog(
            "Delete ${k.label}?",
            "The private key is erased from this phone. You won't be able to use it to sign in anywhere again.",
            "Delete",
            destructive = true,
            onConfirm = { vm.deleteDeviceKey(k.fingerprint) },
            onDismiss = { deleteDeviceKey = null },
        )
    }
    deleteAccountKey?.let { k ->
        ConfirmDialog(
            "Remove ${k.label}?",
            "New Linodes won't get this key. Existing servers keep it until you remove it there.",
            "Remove",
            destructive = true,
            onConfirm = { vm.deleteAccountKey(k.id, k.label) },
            onDismiss = { deleteAccountKey = null },
        )
    }
    if (showAddKey) AddKeyDialog(vm) { showAddKey = false }
    if (showGenerate) {
        var label by remember {
            mutableStateOf(
                android.os.Build.MODEL
                    .replace(Regex("\\s+"), "-")
                    .lowercase(),
            )
        }
        AlertDialog(
            onDismissRequest = { showGenerate = false },
            title = { Text("Generate SSH key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Creates a 3072-bit RSA keypair on this device.")
                    OutlinedTextField(
                        label,
                        { label = it },
                        label = { Text("Label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showGenerate = false
                    vm.generateDeviceKey(label)
                }, enabled = label.isNotBlank()) { Text("Generate") }
            },
            dismissButton = { TextButton(onClick = { showGenerate = false }) { Text("Cancel") } },
        )
    }
    (vm.lastGenerated ?: viewKey)?.let { k ->
        val fresh = vm.lastGenerated != null
        AlertDialog(
            onDismissRequest = {
                vm.clearLastGenerated()
                viewKey = null
            },
            title = { Text(if (fresh) "Key ready" else k.label) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(k.fingerprint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SelectionContainer {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
                            Text(
                                k.publicKey,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                    Text(
                        "Upload it to your Linode profile to have it installed on new Linodes, or append it to ~/.ssh/authorized_keys on an existing server.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { copyToClipboard(context, "Public key", k.publicKey) }) { Text("Copy") }
                        OutlinedButton(onClick = {
                            vm.uploadDeviceKey(k)
                            vm.clearLastGenerated()
                            viewKey = null
                        }) { Text("Upload") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearLastGenerated()
                    viewKey = null
                }) { Text("Done") }
            },
        )
    }
}

@Composable
private fun ProfileHeader(
    profile: Profile?,
    account: Account?,
) {
    SectionCard(null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val name = profile?.username ?: "?"
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(
                    profile?.email ?: account?.email ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                listOfNotNull(account?.company?.ifBlank { null }, profile?.timezone).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (profile?.restricted == true) StatusBadge("restricted")
        }
    }
}

@Composable
private fun KeyRow(
    title: String,
    subtitle: String,
    busy: Boolean,
    menu: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else menu()
    }
}

private fun keySummary(key: String): String {
    val parts = key.trim().split(Regex("\\s+"))
    val type = parts.getOrNull(0) ?: ""
    val body = parts.getOrNull(1) ?: ""
    return "$type …${body.takeLast(12)}"
}

@Composable
private fun AddKeyDialog(
    vm: AccountViewModel,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var label by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val keyOk = Regex("^(ssh-(rsa|ed25519|dss)|ecdsa-sha2-nistp\\d+|sk-\\S+) \\S+.*").matches(key.trim())
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Add SSH key") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(label, { label = it }, label = { Text("Label") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    key,
                    { key = it },
                    label = { Text("Public key") },
                    placeholder = { Text("ssh-ed25519 AAAA… user@host") },
                    isError = key.isNotBlank() && !keyOk,
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                busy = true
                scope.launch {
                    err = vm.addAccountKey(label.trim(), key.trim())
                    busy = false
                    if (err == null) onDismiss()
                }
            }, enabled = !busy && label.isNotBlank() && keyOk) { Text(if (busy) "Adding…" else "Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}
