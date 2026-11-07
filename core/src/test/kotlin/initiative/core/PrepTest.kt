package initiative.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Prep tasks wait on their marker's page until its heads-up opens, then join the board. */
class PrepTest {
    private val day = LocalDate.of(2026, 11, 2)
    private val engine = Placement()
    private val birthday = Item(id = 1, title = "Birthday", kind = Kind.MARK, start = day.plusDays(10).atStartOfDay(), leadMinutes = 0, createdAt = day.atTime(8, 0))
    private val cake = Item(id = 2, title = "order cake", kind = Kind.FLOAT, createdAt = day.atTime(9, 0))

    @Test fun theFirstPrepTaskSetsAHeadsUpAndTheTaskWaitsForIt() {
        val marker = Prep.withHeadsUp(birthday)
        assertEquals(Prep.DEFAULT_HEADS_UP_DAYS, marker.headsUpDays)
        val task = Prep.task(cake, marker)
        assertEquals(day.plusDays(7), task.earliestStart)
        assertEquals(day.plusDays(10), task.deadline)
        assertEquals(1L, task.markerId)
        val items = listOf(marker, task)
        // Before the window: on the marker's page only.
        val now = day.atTime(12, 0)
        assertTrue(Prep.waiting(task, day))
        assertNull(engine.board(items, now).now.item)
        assertTrue(Log.entries(items, day, day.plusDays(20), day).none { it.item.id == 2L })
        assertTrue(Recopy.queue(items, day).isEmpty())
        assertEquals(listOf(task), Prep.of(items, marker))
        // Once it opens: on the board, tagged with its marker.
        val later = day.plusDays(7).atTime(12, 0)
        assertEquals("order cake", engine.board(items, later).now.item?.title)
        assertEquals(marker, Prep.markerOf(items, task))
    }

    @Test fun aRepeatingMarkersPrepComesBackEachTime() {
        val yearly = birthday.copy(recurrence = Recurrence(Freq.YEARLY), leadMinutes = 3 * MINUTES_A_DAY)
        val thisTime = yearly.instancesBetween(day, day.plusDays(20)).single().copy(id = 5)
        val series = Prep.task(cake, thisTime, yearly).copy(id = 6)
        assertEquals(Recurrence(Freq.YEARLY), series.recurrence)
        assertEquals(1L, series.markerId)
        val copies = series.instancesBetween(day, day.plusYears(1).plusDays(20)).mapIndexed { i, t -> t.copy(id = 10L + i) }
        assertEquals(listOf(day.plusDays(7), day.plusDays(7).plusYears(1)), copies.map { it.earliestStart })
        val items = listOf(yearly, thisTime, series) + copies
        assertEquals(listOf(10L), Prep.of(items, thisTime).map { it.id })
        assertEquals(thisTime, Prep.markerOf(items, copies.first()))
    }
}
