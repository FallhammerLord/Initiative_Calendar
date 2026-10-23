package initiative.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertIs
import kotlin.test.assertNull

/** A drag to the top: "I want to do this now". */
class DragNowTest {
    private val day = LocalDate.of(2026, 10, 19)
    private fun at(h: Int, m: Int = 0) = day.atTime(h, m)
    private val engine = Placement()
    private val created = at(7)

    // Pickup 3:00 to 3:30, warning from 2:45.
    private val pickup = Item(id = 1, title = "pickup", kind = Kind.ANCHOR, start = at(15), end = at(15, 30), wall = WallStrength.HARD, createdAt = created)
    private fun float(id: Long, title: String, mins: Int = 30) =
        Item(id = id, title = title, kind = Kind.FLOAT, durationMinutes = mins, createdAt = created)

    @Test fun takesOverFromTheTaskThatWasNow() {
        val taxes = float(2, "taxes")
        val call = float(3, "call landlord", mins = 15)
        val now = at(12)
        val current = engine.board(listOf(pickup, taxes, call), now, listOf(2, 3)).now.item
        assertEquals(taxes.id, current?.id)
        val drag = assertIs<DragNow.Now>(engine.dragNow(call, listOf(pickup, taxes, call), now, listOf(2, 3)))
        assertEquals(now, drag.at)
        assertEquals(taxes.id, drag.setAside?.id)
        // Pinned to now, it's Now.
        val pinned = call.copy(pinnedSlot = drag.at)
        assertEquals(call.id, engine.board(listOf(pickup, taxes, pinned), now).now.item?.id)
    }

    @Test fun unpinsATaskInTheWay() {
        val laundry = float(2, "laundry").copy(pinnedSlot = at(12, 10))
        val later = float(3, "bills").copy(pinnedSlot = at(14))
        val call = float(4, "call landlord")
        val drag = assertIs<DragNow.Now>(engine.dragNow(call, listOf(pickup, laundry, later, call), at(12)))
        // Laundry's 12:10 slot falls inside the call's half hour; bills at 2:00 doesn't.
        assertEquals(listOf(laundry.id), drag.unpin.map { it.id })
    }

    @Test fun anAppointmentHoldingNowMakesASideTask() {
        val call = float(2, "call landlord", mins = 15)
        // During pickup, and in its warning.
        listOf(at(15, 10), at(14, 50)).forEach { now ->
            val drag = assertIs<DragNow.Side>(engine.dragNow(call, listOf(pickup, call), now))
            assertEquals(at(15, 30), drag.until)
            assertEquals(pickup.id, drag.anchor.id)
        }
    }

    @Test fun aSideTaskWaitsThenBecomesNowWhenTheAppointmentEnds() {
        val side = float(2, "call landlord", mins = 15).copy(side = true, waitUntil = at(15, 30), notBefore = at(15, 30))
        val during = engine.board(listOf(pickup, side), at(15, 10))
        assertEquals(pickup.id, during.now.item?.id)
        assertEquals(listOf(side.id), during.waits.map { it.id })
        val after = engine.board(listOf(pickup, side), at(15, 31))
        assertEquals(side.id, after.now.item?.id)
        assertEquals("Side task", after.now.reason)
    }

    @Test fun aSideTaskBecomesNowWhenTheAppointmentIsMarkedDone() {
        val done = pickup.copy(status = Status.DONE, doneAt = at(15, 5))
        val side = float(2, "call landlord", mins = 15).copy(side = true, waitUntil = at(15, 30), notBefore = at(15, 30))
        val b = engine.board(listOf(done, side), at(15, 10))
        assertEquals(side.id, b.now.item?.id)
        assertEquals("Side task", b.now.reason)
    }

    @Test fun runningIntoAWarningIsSaidPlainly() {
        // 2:30 plus 30 minutes runs past pickup's 2:45 warning.
        val call = float(2, "call landlord").copy(pinnedSlot = at(14, 30))
        assertEquals("Runs into pickup's warning at 2:45 PM", engine.board(listOf(pickup, call), at(14, 30)).now.reason)
        val early = float(3, "bills", mins = 15).copy(pinnedSlot = at(13))
        assertEquals("Pinned to this slot", engine.board(listOf(pickup, early), at(13)).now.reason)
    }

    @Test fun headsUpsStayOnceOnItAndRankingReasonsGo() {
        // A heads-up still matters after On it; why a task ranks doesn't.
        val call = float(2, "call landlord").copy(pinnedSlot = at(14, 30))
        assertTrue(engine.board(listOf(pickup, call), at(14, 30)).now.keep)
        val early = float(3, "bills", mins = 15).copy(pinnedSlot = at(13))
        assertFalse(engine.board(listOf(pickup, early), at(13)).now.keep)
        assertFalse(engine.board(listOf(pickup, float(4, "taxes")), at(12)).now.keep)
        val side = float(5, "call landlord", mins = 15).copy(side = true, waitUntil = at(15, 30), notBefore = at(15, 30))
        assertTrue(engine.board(listOf(pickup, side), at(15, 31)).now.keep)
    }

    @Test fun nothingToSetAsideWhenNothingWasNow() {
        val call = float(2, "call landlord")
        val drag = assertIs<DragNow.Now>(engine.dragNow(call, listOf(call), at(12)))
        assertNull(drag.setAside)
    }
}
