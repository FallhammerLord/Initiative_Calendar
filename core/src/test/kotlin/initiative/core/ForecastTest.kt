package initiative.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The rest of today, in order, and drag-to-reorder's rules. */
class ForecastTest {
    // Monday. The day runs 8 AM to 10 PM.
    private val day = LocalDate.of(2026, 10, 19)
    private fun at(h: Int, m: Int = 0) = day.atTime(h, m)
    private val engine = Placement()
    private val pickup = Item(id = 1, title = "pickup", kind = Kind.ANCHOR, start = at(15), wall = WallStrength.HARD, createdAt = at(7))
    private fun task(id: Long, mins: Int = 15, created: Int = 7) =
        Item(id = id, title = "t$id", kind = Kind.FLOAT, durationMinutes = mins, createdAt = at(created))

    private fun Forecast.line() = slots.map { it.item.id to it.at }

    @Test fun tasksFillTheGapsInRankOrder() {
        val f = engine.forecast(listOf(pickup, task(2), task(3, created = 8)), at(14))
        // From now, oldest first, then pickup at 3:00.
        assertEquals(listOf(2L to at(14), 3L to at(14, 15), 1L to at(15)), f.line())
    }

    @Test fun aSkippedTaskLandsWhenItsBack() {
        val skipped = task(2).copy(notBefore = at(16))
        val f = engine.forecast(listOf(pickup, skipped, task(3, created = 8)), at(14))
        // t3 now; t2 waits until 4:00 even though it's older.
        assertEquals(listOf(3L to at(14), 1L to at(15), 2L to at(16)), f.line())
    }

    @Test fun whatCantFitTodayGoesLater() {
        val long = task(2, mins = 600)
        val f = engine.forecast(listOf(pickup, long), at(14))
        assertEquals(listOf(1L to at(15)), f.line())
        assertEquals(listOf(2L), f.later.map { it.id })
    }

    @Test fun nothingFillsAnAnchorsWarningOrTime() {
        // 2:30 to 2:45 is free; pickup warns at 2:45 and runs to 3:30.
        val f = engine.forecast(listOf(pickup, task(2, mins = 30), task(3)), at(14, 30))
        assertEquals(listOf(3L to at(14, 30), 1L to at(15), 2L to at(15, 30)), f.line())
    }

    @Test fun yourOrderBreaksTies() {
        val items = listOf(task(2), task(3, created = 8))
        assertEquals(listOf(2L, 3L), engine.forecast(items, at(9)).slots.map { it.item.id })
        assertEquals(listOf(3L, 2L), engine.forecast(items, at(9), order = listOf(3L, 2L)).slots.map { it.item.id })
        // Priority still leads: a High task stays ahead of your order.
        val high = task(4, created = 9).copy(priority = Priority.HIGH)
        assertEquals(4L, engine.forecast(items + high, at(9), order = listOf(3L, 2L)).slots.first().item.id)
    }

    @Test fun dragRules() {
        val items = listOf(pickup, task(2, mins = 30))
        // Fits before pickup's warning at 2:45.
        assertTrue(engine.canPin(items[1], items, at(14, 15), at(14)))
        // Would run into the warning: no.
        assertFalse(engine.canPin(items[1], items, at(14, 30), at(14)))
        // Over the anchor itself: no. Anchors themselves never move.
        assertFalse(engine.canPin(items[1], items, at(15), at(14)))
        assertFalse(engine.canPin(pickup, items, at(16), at(14)))
        // After pickup ends: yes.
        assertTrue(engine.canPin(items[1], items, at(15, 30), at(14)))
    }

    @Test fun aPinnedTaskHoldsItsSlotAndOthersFlowAround() {
        val pinned = task(2).copy(pinnedSlot = at(14, 15))
        val f = engine.forecast(listOf(pickup, pinned, task(3, created = 8), task(4, created = 9)), at(14))
        assertEquals(listOf(3L to at(14), 2L to at(14, 15), 4L to at(14, 30), 1L to at(15)), f.line())
    }

    @Test fun aPinWhoseTimePassedFreesTheTask() {
        val missed = task(2).copy(pinnedSlot = at(10))
        val b = engine.board(listOf(missed), at(12))
        assertEquals(2L, b.now.item?.id)
        assertEquals(listOf(2L to at(12)), engine.forecast(listOf(missed), at(12)).line())
    }

    @Test fun dropTimeFollowsTheEntryBefore() {
        val f = engine.forecast(listOf(pickup, task(2), task(3, created = 8)), at(14))
        assertEquals(at(14), engine.dropTime(f.slots, 0, at(14)))
        assertEquals(at(14, 15), engine.dropTime(f.slots, 1, at(14)))
        // After pickup: its end.
        assertEquals(at(15, 30), engine.dropTime(f.slots, 3, at(14)))
        // Dropped first, partway through a minute: this minute, so it's Now at once.
        assertEquals(at(14), engine.dropTime(f.slots, 0, at(14).plusSeconds(20)))
        // After another entry, partway through a minute: the next whole one.
        val late = engine.forecast(listOf(pickup, task(2), task(3, created = 8)), at(14).plusSeconds(20))
        assertEquals(at(14, 16), engine.dropTime(late.slots, 1, at(14).plusSeconds(20)))
    }

    @Test fun aPinnedNowStillShowsWhatsNext() {
        val pinned = task(2).copy(pinnedSlot = at(9))
        val b = engine.board(listOf(pinned, task(3, created = 8)), at(9, 5))
        assertEquals(2L, b.now.item?.id)
        assertEquals(3L, b.next?.id)
    }

    @Test fun theNowCardAgreesWithTheForecast() {
        // A task pinned to start in a minute: not Now yet, and the card says when, not "nothing else".
        val soon = task(2).copy(pinnedSlot = at(19, 5))
        val club = Item(id = 9, title = "club", kind = Kind.ANCHOR, start = at(20), createdAt = at(7))
        val b = engine.board(listOf(soon, club), at(19, 4))
        assertEquals(null, b.now.item)
        assertEquals("Up next at 7:05 PM: t2", b.now.reason)
        // A minute later it's Now.
        assertEquals(2L, engine.board(listOf(soon, club), at(19, 5)).now.item?.id)
        // Whenever the forecast starts a task now, the card shows that same task.
        val items = listOf(club, task(3), task(4, created = 8).copy(pinnedSlot = at(19, 20)))
        assertEquals(engine.forecast(items, at(19)).slots.first().item.id, engine.board(items, at(19)).now.item?.id)
    }
}
