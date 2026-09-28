package com.linode.manager.data.ssh

/**
 * Monotonic session generations. Every connect() takes a new generation;
 * late callbacks from dead readers or superseded handshakes carry an old
 * one and must be dropped. Without this, a stale Closed from a previous
 * reader retriggers reconnect and kills the healthy session — a self-kill
 * loop. Touch only from one thread (Main in the app).
 */
class SessionGate {
    private var current = 0

    fun next(): Int = ++current

    fun isCurrent(generation: Int): Boolean = generation == current

    fun current(): Int = current
}
