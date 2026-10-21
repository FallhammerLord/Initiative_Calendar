package initiative.core

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlacementTest {
    private val day = LocalDate.of(2026, 10, 19)
    private fun at(h: Int, m: Int = 0) = day.atTime(h, m)
    private val engine = Placement()
    private val created = at(7)

    private val pickup = Item(id = 1, title = "pickup", kind = Kind.ANCHOR, start = at(15), wall = WallStrength.HARD, createdAt = created)
    private fun float(id: Long, title: String, mins: Int = 30, deadline: LocalDate? = null, createdAt: LocalDateTime = created) =
        Item(id = id, title = title, kind = Kind.FLOAT, durationMinutes = mins, deadline = deadline, createdAt = createdAt)

    @Test fun freeTimeSaysWhyHonestly() {
        // Skipped until 3:30, and one for tomorrow: nothing is due yet, so not "Nothing fits".
        val skipped = float(2, "taxes", mins = 15).copy(notBefore = at(15, 30))
        val later = float(3, "dentist", mins = 15).copy(earliestStart = day.plusDays(1))
        assertEquals("Up next at 3:30 PM: taxes", engine.board(listOf(pickup, skipped, later), at(12)).now.reason)
        assertEquals("Nothing else is due today", engine.board(listOf(pickup, later), at(12)).now.reason)
    }

    @Test fun aDoneAnchorStaysNowUntilItEnds() {
        // Pickup 2:30 to 4:00, marked Done on arrival.
        val pickupDone = pickup.copy(start = at(14, 30), end = at(16), status = Status.DONE, doneAt = at(14, 35))
        val b = engine.board(listOf(pickupDone, float(2, "call", mins = 15)), at(15, 3))
        assertEquals(pickupDone.id, b.now.item?.id)
        assertEquals("Done · under way until 4:00 PM", b.now.reason)
        // After it ends, the float gets its turn.
        assertEquals(2L, engine.board(listOf(pickupDone, float(2, "call", mins = 15)), at(16, 5)).now.item?.id)
    }

    @Test fun anchorInProgressIsNow() {
        val b = engine.board(listOf(pickup, float(2, "call")), at(15, 10))
        assertEquals(pickup.id, b.now.item?.id)
        assertEquals("Happening now", b.now.reason)
    }

    @Test fun leadTimeMakesAnchorNow() {
        val b = engine.board(listOf(pickup, float(2, "call")), at(14, 50))
        assertEquals(pickup.id, b.now.item?.id)
        assertEquals("Starts in 10 min", b.now.reason)
    }

    @Test fun deadlineWinsAndReasonNamesTheWall() {
        val items = listOf(pickup, float(2, "call landlord"), float(3, "taxes", deadline = day.plusDays(1)))
        val b = engine.board(items, at(13))
        assertEquals("taxes", b.now.item?.title)
        assertEquals("Due tomorrow, fits before pickup", b.now.reason)
        // Next is the next thing in order, as in the day panel: the other task, at its time.
        assertEquals("call landlord", b.next?.title)
        assertEquals(at(13, 30), b.nextAt)
    }

    @Test fun nextFollowsTheDayNotJustTheWalls() {
        // School dropoff is Now in its warning time; laundry comes after it, then pickup.
        val dropoff = Item(id = 5, title = "dropoff", kind = Kind.ANCHOR, start = at(8, 30), end = at(9), wall = WallStrength.HARD, createdAt = created)
        val b = engine.board(listOf(dropoff, pickup, float(2, "laundry")), at(8, 20))
        assertEquals("dropoff", b.now.item?.title)
        assertEquals("laundry", b.next?.title)
        assertEquals(at(9), b.nextAt)
        // With no tasks left, Next is the next wall, with no task time.
        val walls = engine.board(listOf(dropoff, pickup), at(8, 20))
        assertEquals(pickup.id, walls.next?.id)
        assertNull(walls.nextAt)
    }

    @Test fun olderWinsAmongEquals() {
        val items = listOf(float(2, "new", createdAt = at(9)), float(3, "old", createdAt = at(8)))
        val b = engine.board(items, at(10))
        assertEquals("old", b.now.item?.title)
        assertEquals("Oldest on the list", b.now.reason)
    }

    @Test fun tooLongForThisGapWaits() {
        // 30 minutes before pickup's warning; a 60-minute float doesn't fit,
        // and a longer gap comes later in the day.
        val items = listOf(pickup, float(2, "deep clean", mins = 60))
        val b = engine.board(items, at(14, 15))
        assertNull(b.now.item)
        // The card names what comes next instead.
        assertEquals("Up next at 3:30 PM: deep clean", b.now.reason)
    }

    @Test fun longerThanEveryGapTakesTheLongest() {
        // After pickup, the rest of the day is the longest gap left.
        val items = listOf(pickup, float(2, "taxes", mins = 600))
        assertNull(engine.board(items, at(9)).now.item)
        val b = engine.board(items, at(15, 30))
        assertEquals("taxes", b.now.item?.title)
        assertEquals("Only thing on the list, longest gap today", b.now.reason)
    }

    @Test fun anotherTaskLaterMeansNotTheOnlyThing() {
        // Water plants is pinned for later, so read mail isn't the only thing on the list.
        val items = listOf(float(2, "read mail"), float(3, "water plants").copy(pinnedSlot = at(16)))
        val b = engine.board(items, at(10))
        assertEquals("read mail", b.now.item?.title)
        assertTrue(!b.now.reason.startsWith("Only thing"), b.now.reason)
    }

    @Test fun emptyBoardIsFreeTime() {
        val b = engine.board(emptyList(), at(10))
        assertNull(b.now.item)
        assertEquals("Nothing needs doing", b.now.reason)
    }

    @Test fun outsideWindowIsFreeTime() {
        assertEquals("Day's done", engine.board(listOf(float(2, "call")), at(23)).now.reason)
    }

    @Test fun skipLandsAfterTheNextWall() {
        val call = float(2, "call")
        val moved = engine.skip(call, listOf(pickup, call), at(13))
        assertEquals(at(15, 30), moved.item.notBefore)
        assertEquals(1, moved.item.skipCount)
        assertEquals("Back at 3:30 PM, after pickup", moved.message)
        // It stays off the board until then.
        assertNull(engine.board(listOf(pickup, moved.item), at(14)).now.item)
    }

    @Test fun skipWithNoLaterGapMovesAnHour() {
        val call = float(2, "call")
        val moved = engine.skip(call, listOf(call), at(10, 2))
        assertEquals(at(11, 5), moved.item.notBefore)
        assertEquals("Back at 11:05 AM", moved.message)
    }

    @Test fun skipLateInTheDayGoesToTomorrow() {
        val call = float(2, "call")
        val moved = engine.skip(call, listOf(call), at(21, 30))
        assertEquals(day.plusDays(1).atTime(8, 0), moved.item.notBefore)
        assertEquals("Back tomorrow at 8:00 AM", moved.message)
    }

    @Test fun twoSkipsRaiseAutoPriority() {
        val items = listOf(float(2, "older", createdAt = at(7)), float(3, "skipped", createdAt = at(8)).copy(skipCount = 2))
        val b = engine.board(items, at(10))
        assertEquals("skipped", b.now.item?.title)
        assertEquals("Moved up after a few skips", b.now.reason)
    }

    @Test fun nextChangeIsTheNextBoundary() {
        assertEquals(at(14, 45), engine.nextChange(listOf(pickup), at(14)))
        assertEquals(at(15), engine.nextChange(listOf(pickup), at(14, 50)))
        assertEquals(at(11), engine.nextChange(emptyList(), at(10)))
        assertEquals(at(22), engine.nextChange(emptyList(), at(21, 30)))
    }

    @Test fun focusEndsAtTheNextFirmOrHardWall() {
        val soft = Item(id = 5, title = "coffee", kind = Kind.ANCHOR, start = at(13), wall = WallStrength.SOFT, createdAt = created)
        assertEquals(pickup.id, engine.nextWall(listOf(soft, pickup), at(12))?.id)
        assertEquals(at(15), engine.focusEnd(listOf(soft, pickup), at(12)))
        // No walls left: the end of the day window.
        assertEquals(at(22), engine.focusEnd(listOf(soft), at(16)))
        assertEquals(at(22), engine.focusEnd(emptyList(), at(21, 30)))
        // Past the window: two hours on.
        assertEquals(at(22, 30).plusHours(2), engine.focusEnd(emptyList(), at(22, 30)))
    }

    @Test fun ledgerMarksWallsAndDone() {
        val done = float(2, "call").copy(status = Status.DONE, doneAt = at(9))
        val marks = engine.ledger(listOf(pickup, done), day)
        assertEquals(listOf(MarkKind.DONE, MarkKind.HARD), marks.map { it.kind })
    }
}
