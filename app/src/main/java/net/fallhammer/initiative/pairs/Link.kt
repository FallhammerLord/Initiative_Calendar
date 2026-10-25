package net.fallhammer.initiative.pairs

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

/**
 * An open stream to another Initiative, over Bluetooth or the home Wi-Fi: what
 * a sync needs, whichever way the phones met. [initiator] is the side that
 * made the connection.
 */
class Link(val input: InputStream, val output: OutputStream, val initiator: Boolean, private val close: () -> Unit) : Closeable {
    override fun close() = close.invoke()
}
