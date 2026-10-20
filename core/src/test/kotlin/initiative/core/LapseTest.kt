package initiative.core

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LapseTest {
    // Monday.
    private val day = LocalDate.of(2026, 10, 19)
    private fun at(d: Long, h: Int, m: Int = 0) = day.plusDays(d).atTime(h, m)
    private fun float(id: Long, policy: MissPolicy = MissPolicy.REROLL) =
        Item(id = id, title = "f$id", kind = Kind.FLOAT, missPolicy = policy, createdAt = at(0, 8))

    @Test fun lapseStartsAfterADayOrThreeAutoSkips() {
        assertFalse(Lapse.started(at(0, 9), 0, at(0, 20)))
        assertTrue(Lapse.started(at(0, 9), 0, at(1, 9)))
        assertTrue(Lapse.started(at(0, 9), 3, at(0, 12)))
        assertFalse(Lapse.started(null, 0, at(5, 9)))
    }

    @Test fun autoSkipOnlyWhenUntouched() {
        val f = float(1)
        val shown = Shown(1, at(0, 14), 0, null)
        assertNull(AutoSkip.due(shown, listOf(f), at(0, 13)))
        assertEquals(1L, AutoSkip.due(shown, listOf(f), at(0, 14))?.id)
        // Skipped or moved by hand since: no automatic skip.
        assertNull(AutoSkip.due(shown, listOf(f.copy(skipCount = 1)), at(0, 15)))
        assertNull(AutoSkip.due(shown, listOf(f.copy(notBefore = at(1, 8))), at(0, 15)))
        assertNull(AutoSkip.due(shown, listOf(f.copy(status = Status.DONE)), at(0, 15)))
    }

    @Test fun recapSortsByPolicyAndTime() {
        val pickup = Item(id = 10, title = "pickup", kind = Kind.ANCHOR, start = at(1, 15), status = Status.MISSED, createdAt = at(0, 8))
        val dentist = Item(id = 11, title = "dentist", kind = Kind.ANCHOR, start = at(3, 10), createdAt = at(0, 8))
        val far = Item(id = 12, title = "far", kind = Kind.ANCHOR, start = at(5, 10), createdAt = at(0, 8))
        val items = listOf(pickup, dentist, far, float(1), float(2, MissPolicy.DROP), float(3, MissPolicy.ASK), float(4))
        val r = Lapse.recap(items, autoSkipped = setOf(1, 2, 3), since = at(0, 20), now = at(2, 12))
        assertEquals(listOf("pickup"), r.passed.map { it.title })
        assertEquals(listOf(1L), r.rerolled.map { it.id })
        assertEquals(listOf(2L), r.dropped.map { it.id })
        assertEquals(listOf(3L), r.ask.map { it.id })
        // Dentist is 22 hours away; "far" is beyond the next day.
        assertEquals(listOf("dentist"), r.ahead.map { it.title })
    }

    @Test fun recopyWeeklyOldestFirst() {
        assertFalse(Recopy.due(null, at(9, 9)))
        assertFalse(Recopy.due(at(0, 9), at(6, 9)))
        assertTrue(Recopy.due(at(0, 9), at(7, 9)))
        val old = float(1).copy(createdAt = at(0, 7))
        val new = float(2).copy(createdAt = at(0, 9))
        val anchor = Item(id = 3, title = "a", kind = Kind.ANCHOR, start = at(1, 9), createdAt = at(0, 1))
        assertEquals(listOf(1L, 2L), Recopy.queue(listOf(new, anchor, old)).map { it.id })
        assertTrue(Recopy.nudge(old.copy(skipCount = 3)))
    }

    @Test fun recopyMoves() {
        val w = DayWindow()
        // Monday: two days on. Saturday: Sunday. Sunday: tomorrow.
        assertEquals(at(2, 8), Recopy.laterThisWeek(at(0, 12), w))
        assertEquals(at(6, 8), Recopy.laterThisWeek(at(5, 12), w))
        assertEquals(at(7, 8), Recopy.laterThisWeek(at(6, 12), w))
        assertEquals(at(7, 8), Recopy.nextWeek(at(0, 12), w))
    }

    @Test fun boardKnowsWhenNowCloses() {
        val pickup = Item(id = 1, title = "pickup", kind = Kind.ANCHOR, start = at(0, 15), createdAt = at(0, 7))
        val b = Placement().board(listOf(pickup, float(2)), at(0, 13))
        assertEquals(at(0, 14, 45), b.until)
    }
}
