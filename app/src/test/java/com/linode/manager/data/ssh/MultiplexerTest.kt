package com.linode.manager.data.ssh

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiplexerTest {
    @Test
    fun herdrCommandAttachesOrCreatesAndFallsBackToAShell() {
        val cmd = Multiplexer.HERDR.channelCommand("work")
        assertTrue(cmd, cmd.contains("herdr --session work"))
        // ~/.local/bin isn't on PATH for sshd's non-login command shell.
        assertTrue(cmd, cmd.startsWith("PATH=\"\$HOME/.local/bin:"))
        assertTrue(cmd, cmd.endsWith("exec \"\${SHELL:-/bin/sh}\" -l"))
    }

    @Test
    fun detachKeysUseEachToolsBinding() {
        assertEquals("\u0002d", Multiplexer.TMUX.detachKeys)
        assertEquals("\u0002q", Multiplexer.HERDR.detachKeys)
    }

    @Test
    fun parsesHerdrSessionListJson() {
        // Shape printed by `herdr session list --json` (herdr 0.9).
        val out =
            """{"sessions":[{"default":true,"name":"default","running":true,""" +
                """"session_dir":"/h/.config/herdr","socket_path":"/h/.config/herdr/herdr.sock"},""" +
                """{"default":false,"name":"agents","running":false,"session_dir":"/h/x","socket_path":"/h/x.sock"}]}"""
        assertEquals(
            listOf(RemoteSession("default", true), RemoteSession("agents", false)),
            parseHerdrSessions(out),
        )
    }

    @Test
    fun herdrParsingNeverThrows() {
        assertEquals(emptyList<RemoteSession>(), parseHerdrSessions(""))
        assertEquals(emptyList<RemoteSession>(), parseHerdrSessions("sh: herdr: not found"))
        assertEquals(emptyList<RemoteSession>(), parseHerdrSessions("""{"sessions":[{"running":true}]}"""))
        // Leading noise (e.g. a login banner) before the JSON is tolerated.
        assertEquals(listOf("a"), parseHerdrSessions("motd\n{\"sessions\":[{\"name\":\"a\",\"running\":true}]}").map { it.name })
    }

    @Test
    fun profilesSavedBy20xStillLoadAsTmux() {
        val old =
            """{"linodeId":1,"label":"x","host":"h","port":22,"username":"root","useKey":true,""" +
                """"tmuxName":"main","autoReconnect":true}"""
        val p = Gson().fromJson(old, SshProfile::class.java)
        assertEquals(Multiplexer.TMUX, p.multiplexer)
        assertEquals("main", p.sessionName)
        assertTrue(p.channelCommand()!!.contains("tmux new-session -A -s main"))
    }

    @Test
    fun withSessionKeepsExactlyOneMultiplexer() {
        val base = SshProfile(linodeId = 1, label = "x", host = "h", tmuxName = "t")
        val h = base.withSession(Multiplexer.HERDR, "agents")
        assertNull(h.tmuxName)
        assertEquals(Multiplexer.HERDR, h.multiplexer)
        assertTrue(h.channelCommand()!!.contains("herdr --session agents"))
        val none = h.withSession(null, null)
        assertNull(none.multiplexer)
        assertNull(none.channelCommand())
    }
}
