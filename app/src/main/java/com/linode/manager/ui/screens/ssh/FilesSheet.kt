package com.linode.manager.ui.screens.ssh

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.linode.manager.data.ssh.RemoteEntry
import com.linode.manager.data.ssh.TerminalSession
import com.linode.manager.ui.components.Fmt
import kotlinx.coroutines.launch

private fun parentDir(path: String): String {
    val t = path.trimEnd('/')
    if (t.isEmpty() || t == "/") return "/"
    val i = t.lastIndexOf('/')
    return if (i <= 0) "/" else t.substring(0, i)
}

/**
 * Phone → server upload over SFTP on the live connection (no second login).
 * Pick a local file, browse to a remote folder, send. Progress shows on the
 * terminal so the sheet can be closed while it runs.
 */
@Composable
fun FilesSheet(
    session: TerminalSession,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var localUri by remember { mutableStateOf<Uri?>(null) }
    var localName by remember { mutableStateOf("") }
    var localSize by remember { mutableStateOf(0L) }
    var dir by remember { mutableStateOf<String?>(null) }
    var home by remember { mutableStateOf("/") }
    var entries by remember { mutableStateOf<List<RemoteEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showMkdir by remember { mutableStateOf(false) }

    fun open(path: String) {
        loading = true
        error = null
        scope.launch {
            session
                .listRemote(path)
                .onSuccess {
                    entries = it
                    dir = path
                }.onFailure { error = it.message }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        home = session.remoteHome()
        open(home)
    }

    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            var name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
            var size = 0L
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (c.moveToFirst()) {
                        if (ni >= 0) c.getString(ni)?.let { name = it }
                        if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                    }
                }
            } catch (_: Exception) {
            }
            localUri = uri
            localName = name
            localSize = size
        }

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        Text("Upload a file", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 8.dp))
        Text(
            "Sent over SFTP on this SSH connection.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.size(12.dp))
        OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.AttachFile, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                if (localUri == null) "Choose a file on this phone" else "$localName · ${Fmt.bytes(localSize)}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.size(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { open(parentDir(dir ?: "/")) }, enabled = !loading && dir != "/") {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Parent folder")
            }
            Text(
                dir ?: "…",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
            )
            IconButton(onClick = { open(home) }, enabled = !loading) { Icon(Icons.Filled.Home, contentDescription = "Home folder") }
            IconButton(onClick = { showMkdir = true }, enabled = !loading && dir != null) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = "New folder")
            }
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 320.dp)) {
            when {
                loading ->
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(24.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) { CircularProgressIndicator() }
                    }
                error != null ->
                    item {
                        Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                    }
                entries.isEmpty() ->
                    item {
                        Text("Empty folder", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                    }
                else ->
                    items(entries, key = { it.path }) { e ->
                        ListItem(
                            headlineContent = { Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = if (!e.isDir) ({ Text(Fmt.bytes(e.size)) }) else null,
                            leadingContent = {
                                Icon(
                                    if (e.isDir) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                                    contentDescription = null,
                                    tint = if (e.isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.clickable(enabled = e.isDir) { open(e.path) },
                        )
                    }
            }
        }
        Spacer(Modifier.size(12.dp))
        val target = dir?.let { it.trimEnd('/') + "/" + localName }
        val overwrite = entries.any { !it.isDir && it.name == localName }
        if (overwrite && localUri != null) {
            Text(
                "$localName already exists here and will be replaced.",
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Button(
            onClick = {
                val uri = localUri ?: return@Button
                session.startUpload(localName, localSize, target!!) {
                    context.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Can't read the file")
                }
                onClose()
            },
            enabled = localUri != null && target != null && session.upload?.let { it.done || it.error != null } != false,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (localUri == null) "Upload" else "Upload to ${dir ?: ""}", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.size(16.dp))
    }

    if (showMkdir) {
        var name by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showMkdir = false },
            title = { Text("New folder") },
            text = {
                OutlinedTextField(
                    name,
                    {
                        name = it
                        err = null
                    },
                    label = { Text("Folder name") },
                    singleLine = true,
                    isError = err != null,
                    supportingText = err?.let { { Text(it) } },
                )
            },
            confirmButton = {
                Button(onClick = {
                    val n = name.trim()
                    if (n.isEmpty() || n.contains('/')) {
                        err = "Enter a name without '/'."
                        return@Button
                    }
                    val path = (dir ?: "/").trimEnd('/') + "/" + n
                    scope.launch {
                        session
                            .makeRemoteDir(path)
                            .onSuccess {
                                showMkdir = false
                                open(path)
                            }.onFailure { err = it.message }
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showMkdir = false }) { Text("Cancel") } },
        )
    }
}
