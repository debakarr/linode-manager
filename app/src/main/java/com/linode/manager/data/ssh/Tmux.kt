package com.linode.manager.data.ssh

// tmux helpers. Sessions keep remote work (opencode, editors, builds) alive
// across dead phone connections: reconnect, reattach, carry on.

/** Parse `tmux ls` output into session names. Never throws. */
fun parseTmuxLs(output: String): List<String> {
    return output
        .lines()
        .mapNotNull { line ->
            val t = line.trim()
            if (t.isEmpty()) return@mapNotNull null
            val low = t.lowercase()
            // Absence markers across tmux versions and shells.
            if (low.startsWith("no ") ||
                low.startsWith("error") ||
                low.startsWith("failed") ||
                low.contains("command not found") ||
                low.contains("no such file")
            ) {
                return@mapNotNull null
            }
            // Format: "<name>: 1 windows (created ...) [dims]".
            val name = t.substringBefore(':').trim()
            if (name.isEmpty() || name.any { it.isWhitespace() || it == '/' }) null else name
        }.distinct()
}

/**
 * Keep only tmux-safe chars (kills shell metachars so the name can't
 * inject), cap length, and strip leading dashes (tmux would parse them
 * as flags).
 */
fun sanitizeTmuxName(raw: String): String =
    raw
        .trim()
        .filter { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
        .trimStart('-')
        .take(32)

/**
 * Channel command for a tmux-backed terminal: attach if the session exists,
 * create it otherwise. Detaching (or tmux missing) drops into a login shell
 * instead of ending the connection. Name must be sanitized.
 */
fun tmuxChannelCommand(name: String): String =
    "if command -v tmux >/dev/null 2>&1; then tmux new-session -A -s $name; " +
        "else echo '[tmux is not installed on this server - opening a normal shell]'; fi; " +
        "exec \"\${SHELL:-/bin/sh}\" -l"

/** Backoff seconds for reconnect attempt n (1-based): 2, 4, 8, 16, 30, 30… */
fun reconnectDelaySec(attempt: Int): Long {
    if (attempt <= 1) return 2L
    return minOf(2L shl (attempt - 1).coerceAtMost(4), 30L)
}
