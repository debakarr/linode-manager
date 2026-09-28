package com.linode.manager.ui.screens.linodes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linode.manager.AppContainer
import com.linode.manager.data.remote.LinodeType
import com.linode.manager.ui.components.AccessDeniedDialog
import com.linode.manager.ui.components.ContentWidth
import com.linode.manager.ui.components.ErrorState
import com.linode.manager.ui.components.Fmt
import com.linode.manager.ui.components.LoadingState
import com.linode.manager.ui.components.PagePadding
import com.linode.manager.ui.components.PickerField
import com.linode.manager.ui.components.PickerOption
import com.linode.manager.ui.components.ScreenScaffold
import com.linode.manager.ui.components.SectionCard
import java.security.SecureRandom

private fun generatePassword(): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#%^*-_=+"
    val rnd = SecureRandom()
    return (1..24).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
}

@Composable
fun CreateLinodeScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onCreated: (Int) -> Unit,
    onUnauthorized: () -> Unit,
) {
    val vm: CreateLinodeViewModel = viewModel { CreateLinodeViewModel(container) }
    val s = vm.state
    var planClass by remember(s.types.isEmpty()) {
        mutableStateOf(s.types.firstOrNull { it.id == s.type }?.clazz ?: "standard")
    }
    var showPass by remember { mutableStateOf(false) }

    s.authFailure?.let { af ->
        AccessDeniedDialog(af, "linodes:read_write", container, { vm.clearAuthFailure() }, onUnauthorized)
    }
    LaunchedEffect(s.createdId) { s.createdId?.let { onCreated(it) } }

    val plan = s.types.firstOrNull { it.id == s.type }
    val monthly =
        (plan?.price?.monthly ?: 0.0) +
            if (s.backups) {
                plan
                    ?.addons
                    ?.backups
                    ?.price
                    ?.monthly ?: 0.0
            } else {
                0.0
            }

    ScreenScaffold(
        title = "Create Linode",
        onBack = onBack,
        bottomBar = {
            if (!s.loadingOptions) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = PagePadding, vertical = 12.dp),
                    ) {
                        s.error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (plan !=
                                        null
                                    ) {
                                        Fmt.money(monthly) + " / month"
                                    } else {
                                        "Choose a plan"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    plan?.label ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Button(onClick = { vm.create() }, enabled = !s.busy && plan != null, modifier = Modifier.height(48.dp)) {
                                if (s.busy) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(if (s.busy) "Creating…" else "Create")
                            }
                        }
                    }
                }
            }
        },
    ) { pad ->
        when {
            s.loadingOptions -> LoadingState("Loading regions, plans and images…", Modifier.padding(pad))
            s.optionsError != null && s.types.isEmpty() -> ErrorState(s.optionsError, Modifier.padding(pad)) { vm.loadOptions() }
            else ->
                ContentWidth(Modifier.padding(pad)) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .imePadding()
                            .padding(PagePadding),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        SectionCard("Location & image") {
                            PickerField(
                                label = "Region",
                                options =
                                    s.regions
                                        .map { PickerOption(it.id, it.label.ifBlank { it.id }, it.id, it.country?.uppercase()) }
                                        .sortedWith(compareBy({ it.group }, { it.title })),
                                selectedId = s.region,
                                onPick = { vm.update(region = it.id) },
                            )
                            Spacer(Modifier.height(8.dp))
                            PickerField(
                                label = "Image",
                                options =
                                    s.images
                                        .map { PickerOption(it.id, it.label.ifBlank { it.id }, it.id, it.vendor ?: "Other") }
                                        .sortedWith(compareBy({ it.group }, { it.title })),
                                selectedId = s.image,
                                onPick = { vm.update(image = it.id) },
                            )
                        }

                        SectionCard("Plan") {
                            val classes = remember(s.types) { s.types.mapNotNull { it.clazz }.distinct() }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                                items(classes) { c ->
                                    FilterChip(selected = planClass == c, onClick = { planClass = c }, label = { Text(Fmt.label(c)) })
                                }
                            }
                            val plans = s.types.filter { it.clazz == planClass }.ifEmpty { s.types }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                plans.take(40).forEach { t -> PlanCard(t, t.id == s.type) { vm.update(type = t.id) } }
                            }
                        }

                        SectionCard("Details") {
                            OutlinedTextField(
                                s.label,
                                { vm.update(label = it) },
                                label = { Text("Label (optional)") },
                                singleLine = true,
                                supportingText = { Text("Leave empty for an auto-generated name") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                s.tags,
                                { vm.update(tags = it) },
                                label = { Text("Tags (optional)") },
                                singleLine = true,
                                supportingText = { Text("Separate with commas") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        SectionCard("Access") {
                            OutlinedTextField(
                                s.rootPass,
                                { vm.update(rootPass = it) },
                                label = { Text("Root password") },
                                singleLine = true,
                                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                textStyle =
                                    if (showPass) {
                                        MaterialTheme.typography.bodyLarge.copy(
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    } else {
                                        MaterialTheme.typography.bodyLarge
                                    },
                                supportingText = { Text("At least 11 characters. Save it somewhere safe.") },
                                trailingIcon = {
                                    Row {
                                        IconButton(onClick = {
                                            vm.update(rootPass = generatePassword())
                                            showPass = true
                                        }) {
                                            Icon(Icons.Filled.Casino, contentDescription = "Generate password")
                                        }
                                        IconButton(onClick = { showPass = !showPass }) {
                                            Icon(
                                                if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                contentDescription = if (showPass) "Hide" else "Show",
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (s.sshKeys.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text("SSH keys to install", style = MaterialTheme.typography.titleSmall)
                                s.sshKeys.forEach { k ->
                                    val id = k.ssh_key ?: k.label
                                    Row(
                                        Modifier.fillMaxWidth().toggleable(
                                            value = id in s.selectedKeys,
                                            role = Role.Checkbox,
                                        ) { vm.toggleKey(id) },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(checked = id in s.selectedKeys, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                                        Text(k.label, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            }
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            Row(
                                Modifier.fillMaxWidth().toggleable(value = s.backups, role = Role.Switch) { vm.update(backups = it) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Backups", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        plan
                                            ?.addons
                                            ?.backups
                                            ?.price
                                            ?.monthly
                                            ?.let { "+${Fmt.money(it)} / month" } ?: "Paid add-on",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(checked = s.backups, onCheckedChange = null)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
        }
    }
}

@Composable
private fun PlanCard(
    t: LinodeType,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(
                            alpha = 0.5f,
                        )
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
            ),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${t.vcpus ?: "?"} vCPU · ${Fmt.mb(t.memory)} RAM · ${Fmt.mb(t.disk)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(t.price?.monthly?.let { Fmt.money(it) } ?: "—", style = MaterialTheme.typography.titleSmall)
                Text("/ month", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
