package initiative.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A step with a wait: the task steps aside, the gap fills, and it comes back first without taking over. */
class WaitTest {
    // Wednesday. The day runs 8 AM to 10 PM.
    private val day = LocalDate.of(2026, 10, 21)
    private fun at(h: Int, m: Int = 0) = day.atTime(h, m)
    private val engine = Placement()
    private val laundry = Item(
        id = 1, title = "laundry", kind = Kind.FLOAT, durationMinutes = 30, createdAt = at(7),
        steps = listOf(Step("gather", done = true), Step("run washer", done = true, waitMinutes = 60), Step("move to dryer")),
    )
    private val kitchen = Item(id = 2, title = "kitchen", kind = Kind.FLOAT, durationMinutes = 30, createdAt = at(7, 30))

    @Test fun stepsKeepTheirWaitsInStorage() {
        val steps = listOf(Step("run washer", waitMinutes = 60), Step("fold"))
        assertEquals(steps, Steps.decode(Steps.encode(steps)))
        assertEquals(60, Steps.parse(Steps.line(Step("run washer", done = true, waitMinutes = 60))).waitMinutes)
    }

    @Test fun aWaitBanksTheWorkAndHoldsTheTask() {
        val waiting = laundry.copy(activeSince = at(8, 40)).startWait(60, at(9))
        assertEquals(at(10), waiting.waitUntil)
        assertEquals(20, waiting.workedMinutes)
        assertNull(waiting.activeSince)
        assertEquals(at(9), waiting.waitStart)
    }

    @Test fun theGapFillsWhileTheWasherRuns() {
        val waiting = laundry.startWait(60, at(9))
        val b = engine.board(listOf(waiting, kitchen), at(9, 5))
        assertEquals("kitchen", b.now.item?.title)
        assertEquals(listOf(1L), b.waits.map { it.id })
        assertTrue(b.waits.single().waiting(at(9, 5)))
    }

    @Test fun backFromAWaitItWaitsForTheTaskUnderWay() {
        val waiting = laundry.startWait(60, at(9))
        val underWay = kitchen.copy(status = Status.ACTIVE, activeSince = at(9, 5))
        // Ready at 10, but kitchen is under way: kitchen stays Now, laundry is next.
        val b = engine.board(listOf(waiting, underWay), at(10, 5))
        assertEquals("kitchen", b.now.item?.title)
        assertEquals("laundry", b.next?.title)
        assertTrue(b.waits.single().ready(at(10, 5)))
        // With nothing under way, it comes back first, and says so.
        val free = engine.board(listOf(waiting, kitchen), at(10, 5))
        assertEquals("laundry", free.now.item?.title)
        assertEquals("Ready since 10:00 AM", free.now.reason)
    }

    @Test fun pickedUpAgainItStaysFirstUntilSetAside() {
        // Back from the washer and under way: it keeps its place ahead of kitchen.
        val back = laundry.startWait(60, at(9)).copy(activeSince = at(10, 5))
        assertEquals("laundry", engine.board(listOf(back, kitchen), at(10, 20)).now.item?.title)
        // Skipped or put off, it's no longer back from a wait.
        assertNull(engine.skip(back, listOf(back, kitchen), at(10, 20)).item.waitUntil)
        assertNull(engine.tomorrow(back, at(10, 20)).item.waitUntil)
    }

    @Test fun anAnchorWithAWaitRunsOverInsteadOfSettling() {
        val bake = Item(
            id = 3, title = "bake bread", kind = Kind.ANCHOR, start = at(14), end = at(15), createdAt = at(7),
            steps = listOf(Step("shape", done = true, waitMinutes = 60), Step("bake")),
        ).startWait(60, at(14, 30))
        // The rise ends at 3:30, after the anchor's end: it isn't settled, and runs over.
        assertNull(Escalation.settle(bake, AlarmState(), at(15, 10)))
        assertTrue(bake.overrunning(at(15, 40)))
        assertEquals("bake bread", engine.board(listOf(bake), at(15, 40)).now.item?.title)
    }
}
