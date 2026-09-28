package com.linode.manager.data.ssh

import com.google.gson.JsonParser

/**
 * A server-side session manager the terminal can run inside, so work keeps
 * running when the phone disconnects and reconnecting re-attaches.
 */
enum class Multiplexer(
    val label: String,
) {
    TMUX("tmux"),
    HERDR("herdr"),
    ;

    /**
     * Channel command: attach to [name] (creating it if needed). When the
     * tool is missing, or the user detaches, drop into a login shell instead
     * of ending the connection. [name] must be sanitized.
     */
    fun channelCommand(name: String): String =
        when (this) {
            TMUX -> tmuxChannelCommand(name)
            HERDR -> herdrChannelCommand(name)
        }

    /** One-shot command that lists sessions; parse with [parseSessions]. */
    val listCommand: String
        get() =
            when (this) {
                TMUX -> "tmux ls 2>&1 || true"
                HERDR -> "$USER_BIN_PATH; herdr session list --json 2>/dev/null || true"
            }

    fun parseSessions(output: String): List<RemoteSession> =
        when (this) {
            TMUX -> parseTmuxLs(output).map { RemoteSession(it, running = true) }
            HERDR -> parseHerdrSessions(output)
        }

    /** Keystrokes that detach the client but leave the session running. */
    val detachKeys: String
        get() =
            when (this) {
                TMUX -> "\u0002d" // prefix (Ctrl+B) d
                HERDR -> "\u0002q" // prefix (Ctrl+B) q
            }
}

data class RemoteSession(
    val name: String,
    val running: Boolean,
)

/**
 * User-level tools (herdr's installer uses ~/.local/bin) aren't on PATH for
 * the non-login shell sshd uses to run a channel command.
 */
private const val USER_BIN_PATH = "PATH=\"\$HOME/.local/bin:\$HOME/bin:\$PATH\""

/** herdr: `--session NAME` uses or creates a named persistent session. */
fun herdrChannelCommand(name: String): String =
    "$USER_BIN_PATH; if command -v herdr >/dev/null 2>&1; then herdr --session $name; " +
        "else echo '[herdr is not installed on this server - opening a normal shell]'; fi; " +
        "exec \"\${SHELL:-/bin/sh}\" -l"

/** Parse `herdr session list --json`. Never throws. */
fun parseHerdrSessions(output: String): List<RemoteSession> =
    try {
        val json = output.substring(output.indexOf('{').coerceAtLeast(0))
        JsonParser
            .parseString(json)
            .asJsonObject
            .getAsJsonArray("sessions")
            .mapNotNull { el ->
                val o = el.asJsonObject
                val name = o.get("name")?.asString?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                RemoteSession(name, running = o.get("running")?.asBoolean ?: false)
            }.distinctBy { it.name }
    } catch (_: Exception) {
        emptyList()
    }
