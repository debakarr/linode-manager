package com.linode.manager.data.ssh

import java.io.ByteArrayOutputStream

/**
 * Rewrites the SCO save/restore-cursor sequences `ESC [ s` / `ESC [ u`
 * into DEC `ESC 7` / `ESC 8` before output reaches libvterm.
 *
 * libvterm doesn't implement the bare SCO forms, so they're dropped. TUIs
 * such as opencode save the cursor, probe the terminal at the home position,
 * then restore — with the restore lost, the cursor stays at the top-left,
 * `?1049h` saves *that*, and on exit the shell prompt is drawn over the first
 * line of old output. xterm and tmux treat the two forms the same way when
 * left/right margins aren't in use, which is the case for these apps.
 *
 * Only the exact three-byte forms are translated: `ESC[?u`, `ESC[>1u`,
 * `ESC[<u` (kitty keyboard protocol) and `ESC[1;5s` (DECSLRM) have
 * parameters and pass through untouched. Sequences split across reads are
 * handled by carrying up to two trailing bytes into the next chunk.
 */
class ScoCursorTranslator {
    private var carry = ByteArray(0)

    /** Translate [len] bytes of [buf]; may hold back up to 2 bytes. */
    fun feed(
        buf: ByteArray,
        len: Int,
    ): ByteArray {
        val input = if (carry.isEmpty()) buf.copyOf(len) else carry + buf.copyOf(len)
        carry = ByteArray(0)
        val out = ByteArrayOutputStream(input.size)
        var i = 0
        val n = input.size
        while (i < n) {
            val b = input[i]
            if (b != ESC) {
                out.write(b.toInt())
                i++
                continue
            }
            // Possible partial sequence at the end of the chunk: hold it.
            if (i == n - 1 || (i == n - 2 && input[i + 1] == LBRACKET)) {
                carry = input.copyOfRange(i, n)
                break
            }
            if (input[i + 1] == LBRACKET && i + 2 < n) {
                when (input[i + 2]) {
                    LOWER_S -> {
                        out.write(ESC.toInt())
                        out.write('7'.code)
                        i += 3
                        continue
                    }
                    LOWER_U -> {
                        out.write(ESC.toInt())
                        out.write('8'.code)
                        i += 3
                        continue
                    }
                }
            }
            out.write(b.toInt())
            i++
        }
        return out.toByteArray()
    }

    /** Any held-back bytes (e.g. when the stream ends mid-sequence). */
    fun flush(): ByteArray = carry.also { carry = ByteArray(0) }

    private companion object {
        const val ESC: Byte = 0x1b
        const val LBRACKET: Byte = '['.code.toByte()
        const val LOWER_S: Byte = 's'.code.toByte()
        const val LOWER_U: Byte = 'u'.code.toByte()
    }
}
