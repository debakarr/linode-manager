package com.linode.manager.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.linode.manager.AppContainer
import com.linode.manager.data.repository.ApiResult
import com.linode.manager.ui.components.SectionCard
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    container: AppContainer,
    onLoggedIn: () -> Unit,
) {
    var token by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current

    fun submit() {
        val t = token.trim()
        if (t.length < 20) {
            error = "That doesn't look like a Linode API token."
            return
        }
        busy = true
        error = null
        scope.launch {
            container.tokenStore.saveToken(t)
            when (val r = container.repository.validateToken()) {
                is ApiResult.Ok -> onLoggedIn()
                is ApiResult.Err -> {
                    container.tokenStore.clear()
                    error = if (r.code == 401) "The token was rejected. Check it hasn't expired or been revoked." else r.message
                }
            }
            busy = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(72.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Cloud,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("Linode Manager", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Manage your Akamai Cloud Linodes, storage and network from your phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            SectionCard(title = null) {
                Text("Sign in with an API token", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Create a Personal Access Token in Cloud Manager → Profile → API Tokens.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = {
                        token = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Personal Access Token") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (!busy) submit() }),
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(
                                if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showToken) "Hide token" else "Show token",
                            )
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { submit() }, enabled = !busy && token.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Verifying…")
                    } else {
                        Text("Sign in")
                    }
                }
                TextButton(onClick = { uri.openUri("https://cloud.linode.com/profile/tokens") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Open Cloud Manager tokens")
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp).padding(top = 2.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Stored encrypted on this device only. The app talks directly to api.linode.com. Read-only tokens work for browsing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
