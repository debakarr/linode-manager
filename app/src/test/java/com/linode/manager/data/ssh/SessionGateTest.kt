package com.linode.manager.data.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionGateTest {
    @Test
    fun generationsInvalidateOld() {
        val gate = SessionGate()
        val g1 = gate.next()
        assertTrue(gate.isCurrent(g1))
        val g2 = gate.next()
        assertTrue(g2 > g1)
        assertTrue(gate.isCurrent(g2))
        assertFalse(gate.isCurrent(g1))
        assertFalse(gate.isCurrent(g2 + 1))
        assertEquals(g2, gate.current())
    }
}
