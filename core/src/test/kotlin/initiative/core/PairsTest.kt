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
    private val farOff = Item(id = 5, title = "dentist", kind = Kind.ANCHOR, start = at(9).plusDays(40), createdAt = at(7))

    private fun shareable(now: java.time.LocalDateTime = at(14)): List<Shareable> {
        val items = listOf(pickup, taxes, secret, trip, farOff)
        return Sharing.shareable(items, engine.forecast(items, now), now)
    }

    @Test fun onlyTitlesAndTimesForTheMonthAhead() {
        val titles = shareable().map { it.shared.title }
        assertEquals(listOf("taxes", "pick up the girls", "trip"), titles)
        assertFalse("gift shopping" in titles, "never share stays home")
        assertFalse("dentist" in titles, "past the month")
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

    @Test fun aMonthAheadIsShared() {
        val soon = farOff.copy(id = 6, start = at(9).plusDays(25))
        val items = listOf(pickup, soon)
        assertTrue("dentist" in Sharing.shareable(items, engine.forecast(items, at(14)), at(14)).map { it.shared.title })
    }

    @Test fun aTickOnARepeatingItemSharesEveryTime() {
        // Ticked on Monday's pickup, kept against the series: Tuesday's goes too.
        val monday = pickup.copy(id = 10, templateId = 9)
        val tuesday = pickup.copy(id = 11, templateId = 9, start = at(15).plusDays(1), end = at(15, 30).plusDays(1))
        val key = Sharing.tickKey(monday)
        assertEquals(9L, key)
        assertTrue(Sharing.isTicked(tuesday, setOf(key)))
        assertEquals(10L, Sharing.tickKey(monday.copy(templateId = null)))
        assertFalse(Sharing.isTicked(tuesday, setOf(10L)))
    }

    @Test fun theReportGoesBySameRules() {
        val lines = listOf(
            ReportLine(pickup, at(15), Outcome.DONE),
            ReportLine(taxes, null, Outcome.OPEN),
            ReportLine(secret, null, Outcome.DONE),
        )
        assertEquals(listOf("pick up the girls", "taxes"), Sharing.report(lines, ShareLevel.EVERYTHING, emptySet()).map { it.title })
        assertEquals(listOf("pick up the girls"), Sharing.report(lines, ShareLevel.TICKED, setOf(1L)).map { it.title })
        assertTrue(Sharing.report(lines, ShareLevel.NOTHING, setOf(1L)).isEmpty())
    }

    @Test fun theReportTravelsAndIsTold() {
        val report = listOf(SharedLine("pick up the girls", at(15), Outcome.DONE), SharedLine("taxes", null, Outcome.OPEN))
        val back = PeekFormat.decode(PeekFormat.encode(Peek(at(14), emptyList(), report)))
        assertEquals(report, back?.report)
        // Shared but empty is not the same as not shared.
        assertEquals(emptyList(), PeekFormat.decode(PeekFormat.encode(Peek(at(14), emptyList(), emptyList())))?.report)
        assertNull(PeekFormat.decode(PeekFormat.encode(Peek(at(14), emptyList())))?.report)
    }

    @Test fun anOlderPeekStillReadsAndUnknownLinesAreSkipped() {
        val v1 = "initiative-peek 1\nat\t2026-10-21T14:00\nANCHOR\t2026-10-21T15:00\t2026-10-21T15:30\tpick up the girls\n"
        assertEquals(listOf("pick up the girls"), PeekFormat.decode(v1)?.items?.map { it.title })
        val later = "initiative-peek 2\nat\t2026-10-21T14:00\nSOMETHING\tnew\nFLOAT\t2026-10-21T16:00\t2026-10-21T16:30\ttaxes\n"
        assertEquals(listOf("taxes"), PeekFormat.decode(later)?.items?.map { it.title })
    }
}
