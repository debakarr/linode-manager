package com.linode.manager.data.ssh

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoCursorTranslatorTest {
    private fun run(vararg chunks: String): String {
        val t = ScoCursorTranslator()
        val sb = StringBuilder()
        for (c in chunks) {
            val b = c.toByteArray(Charsets.ISO_8859_1)
            sb.append(String(t.feed(b, b.size), Charsets.ISO_8859_1))
        }
        sb.append(String(t.flush(), Charsets.ISO_8859_1))
        return sb.toString()
    }

    @Test
    fun translatesBareScoSaveRestore() {
        assertEquals("a\u001b7b\u001b8c", run("a\u001b[sb\u001b[uc"))
    }

    @Test
    fun opencodeStartupSequence() {
        // What opencode 1.18 actually sends before entering the alt screen.
        val raw = "\u001b[s\u001b[6n\u001b[H\u001b]66;w=1; \u001b\\\u001b[6n\u001b[u\u001b[s\u001b[?1049h"
        val want = "\u001b7\u001b[6n\u001b[H\u001b]66;w=1; \u001b\\\u001b[6n\u001b8\u001b7\u001b[?1049h"
        assertEquals(want, run(raw))
    }

    @Test
    fun leavesParameterisedFormsAlone() {
        val kitty = "\u001b[?u\u001b[>1u\u001b[<u\u001b[=1;1u\u001b[1;5s\u001b[2J\u001b[?1049l"
        assertEquals(kitty, run(kitty))
    }

    @Test
    fun handlesSequencesSplitAcrossReads() {
        assertEquals("x\u001b7y", run("x\u001b", "[sy"))
        assertEquals("x\u001b8y", run("x\u001b[", "uy"))
        assertEquals("x\u001b[?uy", run("x\u001b[", "?uy"))
        assertEquals("\u001b7", run("\u001b", "[", "s"))
    }

    @Test
    fun passesPlainTextAndUtf8Through() {
        val s = "héllo ✓ 世界 \u001b[31mred\u001b[0m"
        val t = ScoCursorTranslator()
        val b = s.toByteArray()
        assertEquals(s, String(t.feed(b, b.size) + t.flush()))
    }

    @Test
    fun trailingEscIsFlushed() {
        assertEquals("abc\u001b", run("abc\u001b"))
    }
}
