package initiative.core

import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Two Initiatives talking over a pipe, as they would over Bluetooth. */
class PairWireTest {
    private class Line {
        val aOut = PipedOutputStream(); val bIn = PipedInputStream(aOut, 1 shl 16)
        val bOut = PipedOutputStream(); val aIn = PipedInputStream(bOut, 1 shl 16)
    }

    private fun <A, B> both(a: () -> A, b: () -> B): Pair<A, B> {
        val pool = Executors.newFixedThreadPool(2)
        val fa = pool.submit(Callable { a() })
        val fb = pool.submit(Callable { b() })
        return (fa.get() to fb.get()).also { pool.shutdown() }
    }

    @Test fun pairingAgreesOnACodeAndKeys() {
        val l = Line()
        val (a, b) = both(
            { PairWire.agree(l.aIn, l.aOut, "phone-a", initiator = true).let { it to PairWire.confirm(l.aIn, l.aOut, true) } },
            { PairWire.agree(l.bIn, l.bOut, "phone-b", initiator = false).let { it to PairWire.confirm(l.bIn, l.bOut, true) } },
        )
        assertEquals("phone-b", a.first.theirId)
        assertEquals("phone-a", b.first.theirId)
        assertEquals(a.first.code, b.first.code)
        assertEquals(6, a.first.code.length)
        assertTrue(a.first.macKey.contentEquals(b.first.macKey))
        assertTrue(a.first.sealKey.contentEquals(b.first.sealKey))
        assertFalse(a.first.macKey.contentEquals(a.first.sealKey))
        assertTrue(a.second && b.second)
    }

    @Test fun oneNoMeansNoPair() {
        val l = Line()
        val (yesA, yesB) = both(
            { PairWire.agree(l.aIn, l.aOut, "a", true); PairWire.confirm(l.aIn, l.aOut, true) },
            { PairWire.agree(l.bIn, l.bOut, "b", false); PairWire.confirm(l.bIn, l.bOut, false) },
        )
        assertFalse(yesA)
        assertFalse(yesB)
    }

    @Test fun eachPairingGetsFreshKeys() {
        val l1 = Line()
        val (a1, _) = both({ PairWire.agree(l1.aIn, l1.aOut, "a", true) }, { PairWire.agree(l1.bIn, l1.bOut, "b", false) })
        val l2 = Line()
        val (a2, _) = both({ PairWire.agree(l2.aIn, l2.aOut, "a", true) }, { PairWire.agree(l2.bIn, l2.bOut, "b", false) })
        assertFalse(a1.macKey.contentEquals(a2.macKey))
        assertNotEquals(a1.sealKey.toList(), a2.sealKey.toList())
    }

    @Test fun pingSwapsWhatEachShares() {
        val mac = PairWire.macKey(ByteArray(32) { 1 })
        val seal = PairWire.sealKey(ByteArray(32) { 2 })
        val other = PairWire.PairKeys(9, PairWire.macKey(ByteArray(32) { 3 }), PairWire.sealKey(ByteArray(32) { 4 }))
        val l = Line()
        val (a, b) = both(
            { PairWire.ping(l.aIn, l.aOut, listOf(other, PairWire.PairKeys(5, mac, seal)), true) { "from a to $it" } },
            { PairWire.ping(l.bIn, l.bOut, listOf(PairWire.PairKeys(7, mac, seal)), false) { "from b to $it" } },
        )
        assertEquals(5L, a?.peer)
        assertEquals("from b to 7", a?.text)
        assertEquals(7L, b?.peer)
        assertEquals("from a to 5", b?.text)
    }

    @Test fun strangersLearnNothing() {
        val l = Line()
        val (a, b) = both(
            { PairWire.ping(l.aIn, l.aOut, listOf(PairWire.PairKeys(1, PairWire.macKey(ByteArray(32) { 1 }), PairWire.sealKey(ByteArray(32) { 2 }))), true) { "secret" } },
            { PairWire.ping(l.bIn, l.bOut, listOf(PairWire.PairKeys(2, PairWire.macKey(ByteArray(32) { 5 }), PairWire.sealKey(ByteArray(32) { 6 }))), false) { "secret" } },
        )
        assertNull(a)
        assertNull(b)
    }
}
