package com.linode.manager.ui.screens.ssh

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewQuilt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.linode.manager.data.ssh.Multiplexer
import com.linode.manager.data.ssh.RemoteSession
import com.linode.manager.data.ssh.TerminalSession
import com.linode.manager.data.ssh.sanitizeTmuxName

/**
 * Browse, attach, create and detach server-side sessions (tmux or herdr) on
 * the connected server. Attaching reopens the channel on the live
 * connection; detaching leaves the session running.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsSheet(
    session: TerminalSession,
    onClose: () -> Unit,
) {
    val activeKind = session.profile.multiplexer
    val activeName = session.profile.sessionName
    var kind by remember { mutableStateOf(activeKind ?: Multiplexer.TMUX) }
    var list by remember { mutableStateOf<List<RemoteSession>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }

    LaunchedEffect(kind) {
        list = null
        error = null
        session
            .listSessions(kind)
            .onSuccess { list = it }
            .onFailure {
                error = it.message
                list = emptyList()
            }
    }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            Text("Server sessions", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 8.dp))
            Text(
                "Sessions keep running on the server when the phone disconnects. Reconnecting re-attaches automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Multiplexer.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = kind == m,
                        onClick = { kind = m },
                        shape = SegmentedButtonDefaults.itemShape(i, Multiplexer.entries.size),
                    ) { Text(m.label) }
                }
            }
            when {
                list == null ->
                    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                list!!.isEmpty() ->
                    Text(
                        error ?: "No ${kind.label} sessions yet (or ${kind.label} isn't installed).",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp),
                    )
                else ->
                    list!!.forEach { s ->
                        val attached = activeKind == kind && s.name == activeName
                        ListItem(
                            headlineContent = { Text(s.name) },
                            supportingContent = {
                                Text(
                                    when {
                                        attached -> "Attached"
                                        kind == Multiplexer.HERDR && !s.running -> "Stopped — opening starts it"
                                        else -> "Running"
                                    },
                                )
                            },
                            leadingContent = { Icon(Icons.AutoMirrored.Filled.ViewQuilt, contentDescription = null) },
                            trailingContent =
                                if (attached) {
                                    (
                                        {
                                            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    )
                                } else {
                                    null
                                },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier =
                                Modifier.clickable {
                                    if (!attached) session.switchSession(kind, s.name)
                                    onClose()
                                },
                        )
                    }
            }
            Spacer(Modifier.size(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    newName,
                    { newName = it },
                    label = { Text("New ${kind.label} session") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        session.switchSession(kind, newName)
                        onClose()
                    },
                    enabled = sanitizeTmuxName(newName).isNotBlank(),
                ) { Text("Open") }
            }
            if (activeKind != null) {
                Spacer(Modifier.size(8.dp))
                OutlinedButton(
                    onClick = {
                        session.detachSession()
                        onClose()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Detach from ${activeKind.label} '$activeName' (keeps running)") }
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}
