package com.linode.manager.ui.screens.ssh

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ViewQuilt
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import com.linode.manager.R
import com.linode.manager.data.ssh.SshStatus
import com.linode.manager.data.ssh.TerminalSession
import com.linode.manager.ui.components.ConfirmDialog
import com.linode.manager.ui.components.Fmt
import com.linode.manager.ui.components.copyToClipboard
import org.connectbot.terminal.ModifierManager
import org.connectbot.terminal.Terminal
import org.connectbot.terminal.VTermKey

private val TermBg = Color(TerminalSession.TERM_BG)
private val BarBg = Color(0xFF161B22)
private val BarFg = Color(0xFFE6EDF3)
private val BarDim = Color(0xFF8B949E)
private val KeyBg = Color(0xFF21262D)
private val KeyActive = Color(0xFF3DDC97)

// libvterm VTermModifier bits (vterm_keycodes.h).
private const val MOD_SHIFT = 1
private const val MOD_ALT = 2
private const val MOD_CTRL = 4

/**
 * Sticky Ctrl/Alt for the on-screen keys: tap = next key only, tap again =
 * locked, third tap = off. termlib consults this for IME keystrokes too, so
 * "Ctrl" then typing "c" on the keyboard sends ^C.
 */
@Stable
class StickyModifiers : ModifierManager {
    enum class State { OFF, ONCE, LOCKED }

    var ctrl by mutableStateOf(State.OFF)
    var alt by mutableStateOf(State.OFF)

    override fun isCtrlActive() = ctrl != State.OFF

    override fun isAltActive() = alt != State.OFF

    override fun isShiftActive() = false

    override fun clearTransients() {
        if (ctrl == State.ONCE) ctrl = State.OFF
        if (alt == State.ONCE) alt = State.OFF
    }

    fun bits(): Int = (if (isCtrlActive()) MOD_CTRL else 0) or (if (isAltActive()) MOD_ALT else 0)

    fun cycle(s: State) =
        when (s) {
            State.OFF -> State.ONCE
            State.ONCE -> State.LOCKED
            State.LOCKED -> State.OFF
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    session: TerminalSession,
    onBack: () -> Unit,
    onEndSession: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity
    val uri = LocalUriHandler.current
    val status = session.status
    val mods = remember { StickyModifiers() }
    // Bundled font, not Typeface.MONOSPACE: some OEM skins (MIUI/HyperOS
    // font themes) map "monospace" to a proportional font. termlib sizes
    // cells from measureText("M"), so that spreads text out ("p e r m i t").
    val terminalTypeface =
        remember {
            runCatching { ResourcesCompat.getFont(context, R.font.jetbrains_mono_regular) }.getOrNull()
                ?: android.graphics.Typeface.MONOSPACE
        }
    val focus = remember { FocusRequester() }
    var keyboardShown by remember { mutableStateOf(true) }
    var menuOpen by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var showFiles by remember { mutableStateOf(false) }
    var showSessions by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var orientation by remember { mutableIntStateOf(0) } // 0 auto, 1 landscape, 2 portrait

    // Dark chrome regardless of app theme; keep the screen awake while typing.
    DisposableEffect(Unit) {
        val window = activity?.window
        val ctl = window?.let { WindowCompat.getInsetsController(it, view) }
        val prevStatus = ctl?.isAppearanceLightStatusBars
        val prevNav = ctl?.isAppearanceLightNavigationBars
        ctl?.isAppearanceLightStatusBars = false
        ctl?.isAppearanceLightNavigationBars = false
        view.keepScreenOn = true
        onDispose {
            prevStatus?.let { ctl.isAppearanceLightStatusBars = it }
            prevNav?.let { ctl.isAppearanceLightNavigationBars = it }
            view.keepScreenOn = false
            try {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            } catch (_: Exception) {
            }
        }
    }

    fun leave() {
        if (session.isLive) {
            Toast.makeText(context, "Session keeps running — reopen it from the Linode", Toast.LENGTH_SHORT).show()
        }
        onBack()
    }
    BackHandler { leave() }

    fun paste() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text =
            cm.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()
        if (text.isNullOrEmpty()) {
            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
        } else {
            session.emulator.pasteText(text)
        }
    }

    Box(Modifier.fillMaxSize().background(TermBg)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            // ---- status bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(BarBg)
                    .heightIn(min = 52.dp)
                    .padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { leave() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BarFg)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        session.profile.label,
                        color = BarFg,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (dot, text) = statusLine(session)
                        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text,
                            color = BarDim,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = {
                    keyboardShown = !keyboardShown
                    if (keyboardShown) runCatching { focus.requestFocus() }
                }) {
                    Icon(
                        if (keyboardShown) Icons.Filled.KeyboardHide else Icons.Filled.Keyboard,
                        contentDescription = if (keyboardShown) "Hide keyboard" else "Show keyboard",
                        tint = BarFg,
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Menu", tint = BarFg) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        val connected = status is SshStatus.Connected
                        MenuItem("Paste", Icons.Filled.ContentPaste) {
                            menuOpen = false
                            paste()
                        }
                        MenuItem("Upload file", Icons.Filled.Upload, enabled = connected) {
                            menuOpen = false
                            showFiles = true
                        }
                        MenuItem("Server sessions", Icons.AutoMirrored.Filled.ViewQuilt, enabled = connected) {
                            menuOpen = false
                            showSessions =
                                true
                        }
                        MenuItem(
                            when (orientation) {
                                0 -> "Lock landscape"
                                1 -> "Lock portrait"
                                else -> "Unlock rotation"
                            },
                            Icons.Filled.ScreenRotation,
                        ) {
                            menuOpen = false
                            orientation = (orientation + 1) % 3
                            activity?.requestedOrientation =
                                when (orientation) {
                                    1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                    else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                }
                        }
                        MenuItem("Session log", Icons.AutoMirrored.Filled.ListAlt) {
                            menuOpen = false
                            showLog = true
                        }
                        HorizontalDivider()
                        if (session.isLive) {
                            MenuItem("Disconnect", Icons.Filled.LinkOff) {
                                menuOpen = false
                                session.disconnect()
                            }
                        } else {
                            MenuItem("Reconnect", Icons.Filled.Refresh) {
                                menuOpen = false
                                session.retryNow()
                            }
                        }
                        MenuItem("Close session", Icons.AutoMirrored.Filled.Logout, destructive = true) {
                            menuOpen = false
                            confirmClose = true
                        }
                    }
                }
            }

            // ---- terminal
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Terminal(
                    terminalEmulator = session.emulator,
                    typeface = terminalTypeface,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                    initialFontSize = 12.sp,
                    minFontSize = 7.sp,
                    maxFontSize = 26.sp,
                    backgroundColor = TermBg,
                    foregroundColor = Color(TerminalSession.TERM_FG),
                    keyboardEnabled = true,
                    showSoftKeyboard = keyboardShown,
                    focusRequester = focus,
                    onTerminalTap = {
                        keyboardShown = true
                        runCatching { focus.requestFocus() }
                    },
                    onImeVisibilityChanged = { keyboardShown = it },
                    modifierManager = mods,
                    onHyperlinkClick = { runCatching { uri.openUri(it) } },
                    onPasteRequest = { paste() },
                )
                StatusOverlay(
                    session,
                    onShowLog = { showLog = true },
                    // Never logged in (bad password, unknown host…): nothing to
                    // lose, go straight back to the form to fix the settings.
                    onClose = { if (session.everConnected) confirmClose = true else onEndSession() },
                )
                session.upload?.let { up ->
                    UploadChip(up.fileName, up.sent, up.total, up.done, up.error, Modifier.align(Alignment.BottomCenter)) {
                        if (up.done || up.error != null) session.clearUpload() else session.cancelUpload()
                    }
                }
            }

            // ---- extra keys
            ExtraKeys(session, mods, onPaste = { paste() })
        }
    }

    (status as? SshStatus.HostKeyPrompt)?.let { prompt ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Trust this server?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "First connection to ${session.profile.hostId}. Compare this fingerprint with the one the server shows (e.g. in Lish: ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub).",
                    )
                    SelectionContainer {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
                            Text(
                                "${prompt.key.algorithm}\n${prompt.key.fingerprint}",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { session.trustHostKey() }) { Text("Trust & connect") } },
            dismissButton = { TextButton(onClick = { session.rejectHostKey() }) { Text("Cancel") } },
        )
    }

    if (confirmClose) {
        ConfirmDialog(
            title = "Close this session?",
            message =
                if (session.profile.multiplexer != null) {
                    "Disconnects and closes the terminal. Your ${session.profile.multiplexer!!.label} session '${session.profile.sessionName}' keeps running on the server."
                } else {
                    "Disconnects and closes the terminal. Programs running in this shell will be stopped."
                },
            confirmLabel = "Close",
            destructive = true,
            onConfirm = {
                onEndSession()
                onBack()
            },
            onDismiss = { confirmClose = false },
        )
    }

    if (showLog) {
        AlertDialog(
            onDismissRequest = { showLog = false },
            title = { Text("Session log") },
            text = {
                Column {
                    session.connectionInfo?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                    }
                    Box(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                        SelectionContainer {
                            Text(
                                session.log.joinToString("\n").ifBlank { "No events yet." },
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLog = false }) { Text("Close") } },
            dismissButton = {
                TextButton(onClick = {
                    copyToClipboard(context, "Session log", (listOfNotNull(session.connectionInfo) + session.log).joinToString("\n"))
                }) { Text("Copy") }
            },
        )
    }

    if (showFiles) {
        ModalBottomSheet(
            onDismissRequest = { showFiles = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            FilesSheet(session = session, onClose = { showFiles = false })
        }
    }

    if (showSessions) {
        SessionsSheet(session = session, onClose = { showSessions = false })
    }
}

@Composable
private fun MenuItem(
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = { Text(label, color = if (enabled) tint else tint.copy(alpha = 0.38f)) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = if (enabled) tint else tint.copy(alpha = 0.38f)) },
        enabled = enabled,
        onClick = onClick,
    )
}

private fun statusLine(session: TerminalSession): Pair<Color, String> {
    val p = session.profile
    val who = "${p.username}@${p.host}" + if (p.port != 22) ":${p.port}" else ""
    val tmux = p.multiplexer?.let { " · ${it.label} ${p.sessionName}" } ?: ""
    return when (val s = session.status) {
        SshStatus.Connected -> KeyActive to "$who$tmux"
        SshStatus.Connecting -> Color(0xFFFFB86B) to "Connecting to $who…"
        is SshStatus.Reconnecting -> Color(0xFFFFB86B) to "Reconnecting in ${s.inSeconds}s (attempt ${s.attempt})"
        is SshStatus.HostKeyPrompt -> Color(0xFFFFB86B) to "Verify host key"
        is SshStatus.Disconnected -> Color(0xFFFF8A80) to "Disconnected"
    }
}

@Composable
private fun StatusOverlay(
    session: TerminalSession,
    onShowLog: () -> Unit,
    onClose: () -> Unit,
) {
    when (val s = session.status) {
        SshStatus.Connecting ->
            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.TopCenter) {
                TermBanner {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = KeyActive)
                    Spacer(Modifier.width(12.dp))
                    Text("Connecting to ${session.profile.host}…", color = BarFg, style = MaterialTheme.typography.bodyMedium)
                }
            }
        is SshStatus.Reconnecting ->
            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.TopCenter) {
                TermBanner(
                    message =
                        "${s.reason}\nReconnecting in ${s.inSeconds}s" +
                            (
                                session.profile.multiplexer?.let { " — ${it.label} '${session.profile.sessionName}' will be re-attached." }
                                    ?: "."
                            ),
                    actions = {
                        TextButton(onClick = { session.cancelReconnect() }) { Text("Stop", color = BarDim) }
                        TextButton(onClick = { session.retryNow() }) { Text("Retry now", color = KeyActive) }
                    },
                )
            }
        is SshStatus.Disconnected ->
            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.TopCenter) {
                TermBanner(
                    message = s.message ?: "Disconnected.",
                    accent = if (s.isError) Color(0xFFFF8A80) else BarDim,
                    actions = {
                        TextButton(onClick = onShowLog) { Text("Log", color = BarDim) }
                        TextButton(onClick = onClose) { Text(if (session.everConnected) "Close" else "Edit settings", color = BarDim) }
                        TextButton(onClick = { session.retryNow() }) { Text("Reconnect", color = KeyActive) }
                    },
                )
            }
        else -> Unit
    }
}

@Composable
private fun TermBanner(
    message: String? = null,
    accent: Color = KeyActive,
    actions: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    Surface(
        color = BarBg,
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 6.dp,
        modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = if (actions != null) 4.dp else 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (content != null) content()
                if (message != null) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                    Spacer(Modifier.width(10.dp))
                    Text(message, color = BarFg, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (actions != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { actions() }
            }
        }
    }
}

@Composable
private fun UploadChip(
    name: String,
    sent: Long,
    total: Long,
    done: Boolean,
    error: String?,
    modifier: Modifier,
    onAction: () -> Unit,
) {
    Surface(
        color = BarBg,
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 6.dp,
        modifier = modifier.padding(12.dp).widthIn(max = 520.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        error != null -> "$name — $error"
                        done -> "Uploaded $name (${Fmt.bytes(sent)})"
                        else -> "Uploading $name · ${Fmt.bytes(sent)}" + if (total > 0) " / ${Fmt.bytes(total)}" else ""
                    },
                    color = if (error != null) Color(0xFFFF8A80) else BarFg,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = onAction) { Text(if (done || error != null) "Dismiss" else "Cancel", color = KeyActive) }
            }
            if (!done && error == null) {
                if (total > 0) {
                    LinearProgressIndicator(progress = {
                        (sent.toFloat() / total).coerceIn(0f, 1f)
                    }, modifier = Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 6.dp), color = KeyActive)
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 6.dp), color = KeyActive)
                }
            }
        }
    }
}

private sealed interface XKey {
    val label: String

    data class Key(
        override val label: String,
        val code: Int,
    ) : XKey

    data class Char(
        override val label: String,
        val ch: kotlin.Char,
    ) : XKey

    data class Icon(
        override val label: String,
        val icon: ImageVector,
        val code: Int,
    ) : XKey
}

private val KEYS: List<XKey> =
    listOf(
        XKey.Key("Esc", VTermKey.ESCAPE),
        XKey.Key("Tab", VTermKey.TAB),
        XKey.Icon("Left", Icons.AutoMirrored.Filled.KeyboardArrowLeft, VTermKey.LEFT),
        XKey.Icon("Up", Icons.Filled.KeyboardArrowUp, VTermKey.UP),
        XKey.Icon("Down", Icons.Filled.KeyboardArrowDown, VTermKey.DOWN),
        XKey.Icon("Right", Icons.AutoMirrored.Filled.KeyboardArrowRight, VTermKey.RIGHT),
        XKey.Char("|", '|'),
        XKey.Char("/", '/'),
        XKey.Char("-", '-'),
        XKey.Char("~", '~'),
        XKey.Key("Home", VTermKey.HOME),
        XKey.Key("End", VTermKey.END),
        XKey.Key("PgUp", VTermKey.PAGEUP),
        XKey.Key("PgDn", VTermKey.PAGEDOWN),
    )

@Composable
private fun ExtraKeys(
    session: TerminalSession,
    mods: StickyModifiers,
    onPaste: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(BarBg)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModKey("Ctrl", mods.ctrl) { mods.ctrl = mods.cycle(mods.ctrl) }
        ModKey("Alt", mods.alt) { mods.alt = mods.cycle(mods.alt) }
        KEYS.forEach { k ->
            KeyCap(k) {
                val m = mods.bits()
                when (k) {
                    is XKey.Key -> session.emulator.dispatchKey(m, k.code)
                    is XKey.Icon -> session.emulator.dispatchKey(m, k.code)
                    is XKey.Char -> session.emulator.dispatchCharacter(m, k.ch.code)
                }
                mods.clearTransients()
            }
        }
        KeyCap(XKey.Icon("Paste", Icons.Filled.ContentPaste, 0)) { onPaste() }
    }
}

@Composable
private fun ModKey(
    label: String,
    state: StickyModifiers.State,
    onClick: () -> Unit,
) {
    val bg =
        when (state) {
            StickyModifiers.State.OFF -> KeyBg
            StickyModifiers.State.ONCE -> KeyActive.copy(alpha = 0.35f)
            StickyModifiers.State.LOCKED -> KeyActive
        }
    val fg = if (state == StickyModifiers.State.LOCKED) Color(0xFF002114) else BarFg
    Box(
        Modifier
            .height(
                38.dp,
            ).widthIn(min = 52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun KeyCap(
    key: XKey,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .height(
                38.dp,
            ).widthIn(min = 42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(KeyBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (key is XKey.Icon) {
            Icon(key.icon, contentDescription = key.label, tint = BarFg, modifier = Modifier.size(20.dp))
        } else {
            Text(
                key.label,
                color = BarFg,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = if (key is XKey.Char) FontFamily.Monospace else null,
            )
        }
    }
}
