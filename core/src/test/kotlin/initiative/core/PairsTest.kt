package initiative.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What leaves the phone for someone paired, and how a peek reads back. */
class PairsTest {
    private val day = LocalDate.of(2026, 10, 21)
    private fun at(h: Int, m: Int = 0) = day.atTime(h, m)
    private val engine = Placement()
    private val pickup = Item(id = 1, title = "pick up the girls", kind = Kind.ANCHOR, start = at(15), end = at(15, 30), createdAt = at(7))
    private val taxes = Item(id = 2, title = "taxes", kind = Kind.FLOAT, durationMinutes = 30, notes = "private note", createdAt = at(7))
    private val secret = Item(id = 3, title = "gift shopping", kind = Kind.FLOAT, durationMinutes = 30, neverShare = true, createdAt = at(8))
    private val trip = Item(id = 4, title = "trip", kind = Kind.MARK, start = day.plusDays(3).atStartOfDay(), createdAt = at(7))
    private val farOff = Item(id = 5, title = "dentist", kind = Kind.ANCHOR, start = at(9).plusDays(10), createdAt = at(7))

    private fun shareable(now: java.time.LocalDateTime = at(14)): List<Shareable> {
        val items = listOf(pickup, taxes, secret, trip, farOff)
        return Sharing.shareable(items, engine.forecast(items, now), now)
    }

    @Test fun onlyTitlesAndTimesForTheWeekAhead() {
        val titles = shareable().map { it.shared.title }
        assertEquals(listOf("taxes", "pick up the girls", "trip"), titles)
        assertFalse("gift shopping" in titles, "never share stays home")
        assertFalse("dentist" in titles, "past the week")
    }

    @Test fun levelsDecideWhatGoes() {
        val all = shareable()
        assertEquals(3, Sharing.outgoing(all, ShareLevel.EVERYTHING, emptySet()).size)
        assertEquals(listOf("pick up the girls"), Sharing.outgoing(all, ShareLevel.TICKED, setOf(1L)).map { it.title })
        assertTrue(Sharing.outgoing(all, ShareLevel.NOTHING, setOf(1L, 2L)).isEmpty())
    }

    @Test fun probablyIsWhatCoversTheTime() {
        val peek = Peek(at(14), Sharing.outgoing(shareable(), ShareLevel.EVERYTHING, emptySet()))
        assertEquals("pick up the girls", peek.probably(at(15, 10))?.title)
        assertNull(peek.probably(day.plusDays(3).atTime(12, 0)), "a marker is context, not a doing")
        assertNull(peek.probably(at(20)))
    }

    @Test fun litForFifteenMinutes() {
        val peek = Peek(at(14), emptyList())
        assertTrue(peek.lit(at(14, 14)))
        assertFalse(peek.lit(at(14, 15)))
    }

    @Test fun formatRoundTripsAndRefusesNewer() {
        val peek = Peek(at(14), Sharing.outgoing(shareable(), ShareLevel.EVERYTHING, emptySet()) +
            Shared(Kind.FLOAT, "odd\ttitle", at(16), at(16, 30)))
        val back = PeekFormat.decode(PeekFormat.encode(peek))
        assertEquals(peek.at, back?.at)
        assertEquals("odd title", back?.items?.last()?.title)
        assertEquals(peek.items.dropLast(1), back?.items?.dropLast(1))
        assertNull(PeekFormat.decode("initiative-peek 99\nat\t2026-10-21T14:00\n"))
        assertNull(PeekFormat.decode("hello"))
    }
}
