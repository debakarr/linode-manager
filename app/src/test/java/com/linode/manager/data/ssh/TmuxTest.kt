package com.linode.manager.data.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TmuxTest {
    @Test
    fun parseNormalList() {
        val out =
            "main: 1 windows (created Mon Sep  8 10:00:00 2026) [80x24]\n" +
                "opencode-proxysql: 2 windows (created Mon Sep  8 11:00:00 2026) [120x40]\n"
        assertEquals(listOf("main", "opencode-proxysql"), parseTmuxLs(out))
    }

    @Test
    fun parseAbsenceMarkers() {
        assertEquals(emptyList<String>(), parseTmuxLs(""))
        assertEquals(emptyList<String>(), parseTmuxLs("no server running on /tmp/tmux-1000/default\n"))
        assertEquals(emptyList<String>(), parseTmuxLs("error connecting to /tmp/tmux-1000/default (No such file or directory)\n"))
        assertEquals(emptyList<String>(), parseTmuxLs("/bin/sh: 1: tmux: not found\n"))
        assertEquals(emptyList<String>(), parseTmuxLs("no sessions\n"))
    }

    @Test
    fun parseRejectsWeirdNamesAndDedupes() {
        val out = "ok-name: 1 windows (created x)\nok-name: 1 windows (created x)\nbad name: 1 windows\n"
        assertEquals(listOf("ok-name"), parseTmuxLs(out))
    }

    @Test
    fun sanitizeKillsInjection() {
        assertEquals("myproj", sanitizeTmuxName("my proj!"))
        assertEquals("....etc", sanitizeTmuxName("../../etc"))
        assertEquals("foo", sanitizeTmuxName("--foo"))
        assertEquals("", sanitizeTmuxName("   "))
        assertEquals("rm-rf", sanitizeTmuxName("; rm -rf /"))
        assertEquals("a".repeat(32), sanitizeTmuxName("a".repeat(60)))
        assertEquals("opencode-pmm_1.2", sanitizeTmuxName("opencode-pmm_1.2"))
    }

    @Test
    fun attachCommandFormat() {
        val cmd = tmuxChannelCommand("main")
        assertTrue(cmd, cmd.contains("tmux new-session -A -s main"))
        // Falls back to a login shell when tmux is missing or on detach.
        assertTrue(cmd, cmd.endsWith("exec \"\${SHELL:-/bin/sh}\" -l"))
    }

    @Test
    fun backoffSchedule() {
        assertEquals(2L, reconnectDelaySec(1))
        assertEquals(4L, reconnectDelaySec(2))
        assertEquals(8L, reconnectDelaySec(3))
        assertEquals(16L, reconnectDelaySec(4))
        assertEquals(30L, reconnectDelaySec(5))
        assertEquals(30L, reconnectDelaySec(9))
        assertTrue(reconnectDelaySec(0) >= 2L)
    }
}
