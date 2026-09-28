package com.linode.manager.ui.screens.ssh

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linode.manager.AppContainer
import com.linode.manager.data.DeviceSshKey
import com.linode.manager.data.repository.ApiResult
import com.linode.manager.data.repository.isAuthFailure
import com.linode.manager.data.ssh.Multiplexer
import com.linode.manager.data.ssh.SshAuth
import com.linode.manager.data.ssh.SshProfile
import com.linode.manager.data.ssh.sanitizeTmuxName
import com.linode.manager.ui.components.AccessDeniedDialog
import com.linode.manager.ui.components.AuthFailure
import com.linode.manager.ui.components.ContentWidth
import com.linode.manager.ui.components.ErrorState
import com.linode.manager.ui.components.LoadingState
import com.linode.manager.ui.components.PagePadding
import com.linode.manager.ui.components.PickerField
import com.linode.manager.ui.components.PickerOption
import com.linode.manager.ui.components.ScreenScaffold
import com.linode.manager.ui.components.SectionCard
import kotlinx.coroutines.launch

/** Connection form state. The live session itself lives in SshSessionManager. */
class SshSetupViewModel(
    private val container: AppContainer,
    private val linodeId: Int,
) : ViewModel() {
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var authFailure by mutableStateOf<AuthFailure?>(null)
        private set
    var label by mutableStateOf("Linode #$linodeId")
        private set
    var addresses by mutableStateOf<List<String>>(emptyList())
        private set
    val deviceKeys: List<DeviceSshKey> = container.deviceKeys.list()

    var host by mutableStateOf("")
    var port by mutableStateOf("22")
    var username by mutableStateOf("root")
    var useKey by mutableStateOf(true)
    var keyFingerprint by mutableStateOf<String?>(null)
    var password by mutableStateOf("")

    /** Server-side session manager to run inside (null = plain shell). */
    var multiplexer by mutableStateOf<Multiplexer?>(null)
    var sessionName by mutableStateOf("main")
    var autoReconnect by mutableStateOf(true)
    var formError by mutableStateOf<String?>(null)

    init {
        val saved = container.sshProfiles.load(linodeId)
        if (saved != null) {
            host = saved.host
            port = saved.port.toString()
            username = saved.username
            useKey = saved.useKey
            keyFingerprint = saved.keyFingerprint
            multiplexer = saved.multiplexer
            sessionName = saved.sessionName ?: "main"
            autoReconnect = saved.autoReconnect
        }
        if (deviceKeys.isEmpty()) useKey = false
        if (keyFingerprint == null || deviceKeys.none { it.fingerprint == keyFingerprint }) {
            keyFingerprint = deviceKeys.firstOrNull()?.fingerprint
        }
        viewModelScope.launch {
            when (val r = container.repository.linode(linodeId)) {
                is ApiResult.Ok -> {
                    label = r.value.label
                    addresses = r.value.ipv4 + listOfNotNull(r.value.ipv6?.substringBefore('/'))
                    if (host.isBlank()) host = r.value.ipv4.firstOrNull { !it.startsWith("192.168.") } ?: r.value.ipv4.firstOrNull() ?: ""
                }
                is ApiResult.Err ->
                    if (r.isAuthFailure()) {
                        authFailure = AuthFailure(r.code, r.message)
                    } else if (host.isBlank()) {
                        error =
                            r.message
                    }
            }
            loading = false
        }
    }

    fun clearAuthFailure() {
        authFailure = null
    }

    /** Validates, persists the non-secret settings and starts the session. */
    fun connect(): Boolean {
        val h = host.trim()
        val p = port.toIntOrNull()
        val u = username.trim()
        formError =
            when {
                h.isBlank() -> "Enter the server address."
                p == null || p !in 1..65535 -> "Port must be between 1 and 65535."
                u.isBlank() -> "Enter a username."
                useKey &&
                    deviceKeys.none {
                        it.fingerprint == keyFingerprint
                    } -> "Choose a device key, or generate one under Account → SSH keys."
                !useKey && password.isEmpty() -> "Enter the password."
                multiplexer != null && sanitizeTmuxName(sessionName).isBlank() -> "Session names may use letters, digits, - _ ."
                else -> null
            }
        if (formError != null) return false
        val profile =
            SshProfile(
                linodeId = linodeId,
                label = label,
                host = h,
                port = p!!,
                username = u,
                useKey = useKey,
                keyFingerprint = keyFingerprint,
                autoReconnect = autoReconnect,
            ).withSession(multiplexer, multiplexer?.let { sanitizeTmuxName(sessionName) })
        container.sshProfiles.save(profile)
        val auth =
            if (useKey) {
                SshAuth.Key(deviceKeys.first { it.fingerprint == keyFingerprint }.privatePem)
            } else {
                SshAuth.Password(password)
            }
        container.sshSessions.start(profile, auth)
        return true
    }
}

@Composable
fun SshScreen(
    container: AppContainer,
    linodeId: Int,
    onBack: () -> Unit,
    onUnauthorized: () -> Unit,
) {
    val session = container.sshSessions.sessions[linodeId]
    if (session != null) {
        TerminalScreen(
            session = session,
            onBack = onBack,
            onEndSession = { container.sshSessions.close(linodeId) },
        )
    } else {
        SetupScreen(container, linodeId, onBack, onUnauthorized)
    }
}

@Composable
private fun SetupScreen(
    container: AppContainer,
    linodeId: Int,
    onBack: () -> Unit,
    onUnauthorized: () -> Unit,
) {
    val vm: SshSetupViewModel = viewModel(key = "ssh-setup-$linodeId") { SshSetupViewModel(container, linodeId) }
    val context = LocalContext.current
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    vm.authFailure?.let { af ->
        AccessDeniedDialog(af, "linodes:read_only", container, { vm.clearAuthFailure() }, onUnauthorized)
    }

    ScreenScaffold(title = "SSH", subtitle = vm.label, onBack = onBack) { pad ->
        when {
            vm.loading -> LoadingState("Loading Linode…", Modifier.padding(pad))
            vm.error != null -> ErrorState(vm.error!!, Modifier.padding(pad)) { onBack() }
            else ->
                ContentWidth(Modifier.padding(pad)) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .imePadding()
                            .padding(horizontal = PagePadding, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        SectionCard("Server") {
                            if (vm.addresses.size > 1) {
                                PickerField(
                                    label = "Address",
                                    options = vm.addresses.map { PickerOption(it, it) },
                                    selectedId = vm.host,
                                    onPick = { vm.host = it.id },
                                    searchable = false,
                                )
                                Spacer(Modifier.height(8.dp))
                            } else {
                                OutlinedTextField(
                                    vm.host,
                                    { vm.host = it.trim() },
                                    label = { Text("Address") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    vm.username,
                                    { vm.username = it.trim() },
                                    label = { Text("Username") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                                    modifier = Modifier.weight(2f),
                                )
                                OutlinedTextField(
                                    vm.port,
                                    { vm.port = it.filter(Char::isDigit).take(5) },
                                    label = { Text("Port") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        SectionCard("Sign in with") {
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                SegmentedButton(
                                    selected = vm.useKey,
                                    onClick = { vm.useKey = true },
                                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                                    icon = { SegmentedButtonDefaults.Icon(vm.useKey) { Icon(Icons.Filled.Key, null) } },
                                ) { Text("Device key") }
                                SegmentedButton(
                                    selected = !vm.useKey,
                                    onClick = { vm.useKey = false },
                                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                                    icon = { SegmentedButtonDefaults.Icon(!vm.useKey) { Icon(Icons.Filled.Password, null) } },
                                ) { Text("Password") }
                            }
                            Spacer(Modifier.height(12.dp))
                            if (vm.useKey) {
                                if (vm.deviceKeys.isEmpty()) {
                                    Text(
                                        "No device keys yet. Generate one under Account → SSH keys, then add its public key to the server's ~/.ssh/authorized_keys (or upload it to your Linode profile before creating a Linode).",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    PickerField(
                                        label = "Key",
                                        options = vm.deviceKeys.map { PickerOption(it.fingerprint, it.label, it.fingerprint) },
                                        selectedId = vm.keyFingerprint,
                                        onPick = { vm.keyFingerprint = it.id },
                                        searchable = false,
                                    )
                                }
                            } else {
                                var show by remember { mutableStateOf(false) }
                                OutlinedTextField(
                                    vm.password,
                                    { vm.password = it },
                                    label = { Text("Password") },
                                    singleLine = true,
                                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                                    trailingIcon = {
                                        IconButton(onClick = { show = !show }) {
                                            Icon(
                                                if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                if (show) "Hide" else "Show",
                                            )
                                        }
                                    },
                                    supportingText = { Text("Kept in memory for reconnects only — never saved.") },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        SectionCard("Session") {
                            ToggleRow(
                                "Reconnect automatically",
                                "Retry with backoff when the network drops",
                                vm.autoReconnect,
                            ) { vm.autoReconnect = it }
                            Text(
                                "Keep session on the server",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            Text(
                                "Work keeps running if the phone disconnects; reconnecting re-attaches.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val choices = listOf<Multiplexer?>(null) + Multiplexer.entries
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                choices.forEachIndexed { i, m ->
                                    SegmentedButton(
                                        selected = vm.multiplexer == m,
                                        onClick = { vm.multiplexer = m },
                                        shape = SegmentedButtonDefaults.itemShape(i, choices.size),
                                    ) { Text(m?.label ?: "Off") }
                                }
                            }
                            vm.multiplexer?.let { m ->
                                OutlinedTextField(
                                    vm.sessionName,
                                    { vm.sessionName = it },
                                    label = { Text("${m.label} session") },
                                    singleLine = true,
                                    supportingText =
                                        if (m == Multiplexer.HERDR) {
                                            {
                                                Text(
                                                    "Opens or creates this herdr session. Use Ctrl then b for herdr's keys (Ctrl, b, q detaches).",
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                )
                            }
                        }

                        vm.formError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        }
                        Button(
                            onClick = {
                                if (vm.connect() &&
                                    Build.VERSION.SDK_INT >= 33 &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                                    PackageManager.PERMISSION_GRANTED
                                ) {
                                    // Lets the "SSH connected" notification show; the
                                    // session works either way.
                                    notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Icon(Icons.Filled.Terminal, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Connect")
                        }
                        Text(
                            "Port ${vm.port.ifBlank {
                                "22"
                            }} must be reachable (check the Linode's Cloud Firewall). The server's host key is verified on first use and pinned after that.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(24.dp))
                    }
                }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
