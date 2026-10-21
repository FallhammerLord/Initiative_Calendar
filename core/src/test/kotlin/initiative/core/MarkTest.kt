package initiative.core

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Markers are context for a day, never work: no rule of the day sees them, but the Log and the HUD do. */
class MarkTest {
    // Saturday, October 31, 2026, 10:00.
    private val day = LocalDate.of(2026, 10, 31)
    private val now = day.atTime(10, 0)
    private val engine = Placement()
    private val halloween = Item(id = 1, title = "halloween", kind = Kind.MARK, start = day.atStartOfDay(), leadMinutes = 0, icon = MarkIcon.STAR, createdAt = now)
    private val pumpkins = Item(id = 2, title = "carve pumpkins", kind = Kind.FLOAT, createdAt = now)

    @Test fun quickAddAllDayMakesAMarker() {
        val p = Parser.parse("halloween oct 31 all day", now)!!
        assertEquals(Kind.MARK, p.item.kind)
        assertEquals(day.atStartOfDay(), p.item.start)
        assertEquals(MarkIcon.STAR, p.item.icon)
        assertEquals(false, p.unsureKind)
        assertEquals(MarkIcon.CAKE, Parser.parse("mom's birthday nov 2 all day yearly", now)!!.item.icon)
    }

    @Test fun neverNowNeverInTheForecastNeverRings() {
        val b = engine.board(listOf(halloween), now)
        assertNull(b.now.item)
        assertEquals("pumpkins", engine.board(listOf(halloween, pumpkins), now).now.item?.title?.substringAfter("carve "))
        assertTrue(engine.forecast(listOf(halloween), now).slots.isEmpty())
        assertTrue(engine.ledger(listOf(halloween), day).isEmpty())
        val plan = Pass.plan(PassInput(items = listOf(halloween), states = emptyMap(), now = now), engine)
        assertTrue(plan.fired.isEmpty())
        assertTrue(Recopy.queue(listOf(halloween)).isEmpty())
    }

    @Test fun aSpanShowsOnEachOfItsDays() {
        val trip = Item(id = 3, title = "trip", kind = Kind.MARK, start = day.atStartOfDay(), end = day.plusDays(2).atStartOfDay(), leadMinutes = 0, createdAt = now)
        val days = Log.days(listOf(trip, pumpkins), day, day.plusDays(3), day)
        assertEquals(listOf(true, true, true, false), days.map { (_, list) -> list.any { it.item.isMark } })
        // A marker leads its day.
        assertTrue(days.first().second.first().item.isMark)
    }

    @Test fun headsUpShowsAheadThenInEffect() {
        val birthday = Item(id = 4, title = "mom's birthday", kind = Kind.MARK, start = day.plusDays(2).atStartOfDay(), leadMinutes = 3 * MINUTES_A_DAY, createdAt = now)
        val shown = Marks.on(listOf(halloween, birthday), day)
        assertEquals(listOf("halloween" to 0, "mom's birthday" to 2), shown.map { it.item.title to it.ahead })
        // Outside its heads-up, not shown.
        assertTrue(Marks.on(listOf(birthday.copy(leadMinutes = MINUTES_A_DAY)), day).isEmpty())
        // Not in the day's report lines, but listed as the day's markers.
        assertTrue(Report.of(listOf(halloween, pumpkins), day, day).none { it.item.isMark })
        assertEquals(listOf("halloween"), Report.marks(listOf(halloween, pumpkins), day, day).map { it.title })
    }
}
