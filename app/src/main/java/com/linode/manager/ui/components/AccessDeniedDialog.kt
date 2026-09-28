package com.linode.manager.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.linode.manager.AppContainer
import com.linode.manager.data.repository.ApiResult
import kotlinx.coroutines.launch

/**
 * Carries an auth failure (401 invalid/expired token, 403 missing scope or
 * insufficient permissions) from a resource endpoint so the UI can explain
 * it instead of silently signing the user out.
 */
data class AuthFailure(
    val code: Int,
    val message: String,
)

/**
 * Dialog shown when an API call fails with 401/403: explains the likely
 * missing token scope, lets the user verify whether the token itself is
 * still valid, switch tokens, or dismiss and stay signed in.
 */
@Composable
fun AccessDeniedDialog(
    failure: AuthFailure,
    scopeHint: String,
    container: AppContainer,
    onDismiss: () -> Unit,
    onLogout: () -> Unit,
) {
    var checking by remember { mutableStateOf(false) }
    var verdict by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Access denied (${failure.code})") },
        text = {
            Column {
                Text(
                    failure.message.ifBlank { "The Linode API refused this request." },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "This usually means your token doesn't include the \"$scopeHint\" scope. " +
                        "Create a token with that access in Cloud Manager → Profile → API Tokens, then sign in with it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (checking) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.padding(4.dp))
                        Text("Checking token…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (verdict != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(verdict!!, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onLogout,
                    contentPadding =
                        androidx.compose.foundation.layout
                            .PaddingValues(0.dp),
                ) {
                    Text("Sign in with a different token", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    checking = true
                    verdict = null
                    scope.launch {
                        when (val r = container.repository.profile()) {
                            is ApiResult.Ok -> {
                                checking = false
                                verdict = "Token is valid (signed in as ${r.value.username}) — it just needs more scopes."
                            }
                            is ApiResult.Err -> {
                                if (r.code == 401) {
                                    onLogout()
                                } else {
                                    checking = false
                                    verdict = "Token check failed: ${r.message}"
                                }
                            }
                        }
                    }
                },
                enabled = !checking,
            ) { Text("Check token") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Dismiss") } },
    )
}
