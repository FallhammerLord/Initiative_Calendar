package initiative.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Removed and passed items leave the Log's days, and go for good after a while. */
class TidyTest {
    // Tuesday.
    private val today = LocalDate.of(2026, 11, 17)
    private fun ago(days: Long) = today.minusDays(days)

    private fun task(id: Long, status: Status, day: LocalDate, settled: LocalDate? = day) = Item(
        id = id, title = "task $id", kind = Kind.FLOAT, status = status, earliestStart = day,
        createdAt = day.atStartOfDay(), settledAt = settled?.atTime(9, 0),
        doneAt = if (status == Status.DONE) day.atTime(10, 0) else null,
    )

    private val daily = Item(id = 100, title = "water plants", kind = Kind.FLOAT, recurrence = Recurrence(Freq.DAILY), earliestStart = ago(60), createdAt = ago(60).atStartOfDay())

    private fun time(id: Long, day: LocalDate, status: Status) =
        daily.instancesBetween(day, day).single().copy(id = id, status = status, settledAt = day.atTime(23, 0))

    private fun gone(items: List<Item>) = Tidy.sweep(items, today).gone.map { it.id }.toSet()

    @Test fun removedAndPassedLeaveTheDaysButSearchFindsThem() {
        val items = listOf(task(1, Status.DROPPED, today), task(2, Status.MISSED, today), task(3, Status.DONE, today), task(4, Status.PENDING, today))
        assertEquals(setOf(3L, 4L), Log.entries(items, today, today, today).map { it.item.id }.toSet())
        assertEquals(4, Log.search(items, "task", today).size)
    }

    @Test fun aRemovedItemIsKeptThirtyDaysFromTheDayItWasRemoved() {
        // Planned long ago, removed today: kept from today.
        assertEquals(today.plusDays(30), Tidy.keptUntil(task(1, Status.DROPPED, ago(90), settled = today), today))
        // Planned next month, removed today: kept from its day.
        assertEquals(today.plusDays(40), Tidy.keptUntil(task(2, Status.DROPPED, today.plusDays(10), settled = today), today))
        assertEquals(null, Tidy.keptUntil(task(3, Status.DONE, ago(90)), today))
    }

    @Test fun oldRemovedAndPassedGoDoneStays() {
        val items = listOf(
            task(1, Status.DROPPED, ago(31)),
            task(2, Status.MISSED, ago(31)),
            task(3, Status.DROPPED, ago(29)),
            task(4, Status.DONE, ago(400)),
            task(5, Status.PENDING, ago(400)),
        )
        assertEquals(setOf(1L, 2L), gone(items))
    }

    @Test fun aSeriesKeepsItsLastThreeTimesForTheRecopy() {
        val times = (31L..36L).map { time(it, ago(it), Status.MISSED) }
        // The newest three past times are the ones from 1 to 3 days ago, kept by the recopy rule.
        val recent = (1L..3L).map { time(it, ago(it), Status.MISSED) }
        assertEquals((31L..36L).toSet(), gone(listOf(daily) + times + recent))
        // With nothing newer, the three latest of the old ones stay.
        assertEquals((34L..36L).toSet(), gone(listOf(daily) + times))
    }

    @Test fun aRemovedTimeStillToComeStaysSoItIsNotMadeAgain() {
        val ahead = time(1, today.plusDays(5), Status.DROPPED).copy(settledAt = ago(40).atStartOfDay())
        assertTrue(gone(listOf(daily, ahead)).isEmpty())
    }

    @Test fun aStoppedSeriesGoesOnceNothingPointsAtIt() {
        val stopped = daily.copy(status = Status.DROPPED, settledAt = ago(31).atStartOfDay())
        assertEquals(setOf(100L), gone(listOf(stopped)))
        // Its done time still points at it, so it stays.
        assertEquals(emptySet(), gone(listOf(stopped, time(1, ago(40), Status.DONE))))
        // Its old passed time goes, and takes the series with it.
        assertEquals(setOf(1L, 100L), gone(listOf(stopped, time(1, ago(40), Status.MISSED))))
        // Stopped just now: kept, so Undo can bring it back.
        assertEquals(emptySet(), gone(listOf(stopped.copy(settledAt = today.atStartOfDay()))))
    }

    @Test fun aSharedItemStaysWhileASyncStillLooksAtItsDay() {
        val shared = task(1, Status.DROPPED, ago(31)).copy(uid = "u", pairId = 7)
        assertEquals(today.plusDays(1), Tidy.keptUntil(shared, today))
        assertEquals(emptySet(), gone(listOf(shared)))
    }

    @Test fun aSkippedDateIsNeverMadeAndPastOnesArePruned() {
        val skipped = daily.copy(skipDates = setOf(today.plusDays(1), ago(2)))
        assertEquals(listOf(today, today.plusDays(2)), skipped.instancesBetween(today, today.plusDays(2)).map { it.occurrenceDate })
        val (before, after) = Tidy.sweep(listOf(skipped), today).pruned.single()
        assertEquals(skipped, before)
        assertEquals(setOf(today.plusDays(1)), after.skipDates)
    }

    @Test fun aThisAndLaterEditKeepsTheSkippedDates() {
        val skipped = daily.copy(skipDates = setOf(today.plusDays(3)))
        val time = skipped.instancesBetween(today, today).single().copy(id = 5, title = "water the plants")
        assertEquals(setOf(today.plusDays(3)), Series.from(skipped, time, Recurrence(Freq.DAILY)).skipDates)
    }

    @Test fun skippedDatesTravelWithASharedSeries() {
        val shared = daily.copy(uid = "u", skipDates = setOf(today.plusDays(3), today.plusDays(9)))
        val back = SharedTasks.decode(SharedTasks.encode(shared), today.atStartOfDay())!!
        assertEquals(shared.skipDates, back.skipDates)
    }
}
