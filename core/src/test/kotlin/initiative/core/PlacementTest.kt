package initiative.core

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlacementTest {
    private val day = LocalDate.of(2026, 10, 19)
    private fun at(h: Int, m: Int = 0) = day.atTime(h, m)
    private val engine = Placement()
    private val created = at(7)

    private val pickup = Item(id = 1, title = "pickup", kind = Kind.ANCHOR, start = at(15), wall = WallStrength.HARD, createdAt = created)
    private fun float(id: Long, title: String, mins: Int = 30, deadline: LocalDate? = null, createdAt: LocalDateTime = created) =
        Item(id = id, title = title, kind = Kind.FLOAT, durationMinutes = mins, deadline = deadline, createdAt = createdAt)

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
        assertEquals(pickup.id, b.next?.id)
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
        assertEquals("Nothing fits before pickup", b.now.reason)
    }

    @Test fun longerThanEveryGapTakesTheLongest() {
        // After pickup, the rest of the day is the longest gap left.
        val items = listOf(pickup, float(2, "taxes", mins = 600))
        assertNull(engine.board(items, at(9)).now.item)
        val b = engine.board(items, at(15, 30))
        assertEquals("taxes", b.now.item?.title)
        assertEquals("Only thing on the list, longest gap today", b.now.reason)
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
