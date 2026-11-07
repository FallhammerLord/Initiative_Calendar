package initiative.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue


class SeriesTest {
    // Monday.
    private val day = LocalDate.of(2026, 10, 19)
    private fun at(d: Long, h: Int) = day.plusDays(d).atTime(h, 0)

    @Test fun thisAndLaterRestartsTheSeriesFromThatTime() {
        val template = Item(id = 1, title = "gym", kind = Kind.ANCHOR, start = at(0, 7), recurrence = Recurrence(Freq.DAILY), createdAt = at(-10, 9))
        // Thursday's time, moved to 6 PM and renamed.
        val instance = template.instancesBetween(day.plusDays(3), day.plusDays(3)).single().copy(id = 9)
        val edited = instance.copy(title = "evening gym", start = at(3, 18))
        val next = Series.from(template, edited, Recurrence(Freq.WEEKLY))

        assertEquals(1L, next.id)
        assertEquals(template.createdAt, next.createdAt)
        assertNull(next.templateId)
        val times = next.instancesBetween(day, day.plusDays(14)).map { it.start }
        // Weekly on Thursdays from Thursday on; nothing before it.
        assertEquals(listOf(at(3, 18), at(10, 18)), times)
        assertEquals("evening gym", next.instancesBetween(day, day.plusDays(3)).single().title)
    }

    @Test fun aOneOffFloatStartsRepeatingFromToday() {
        val f = Item(id = 5, title = "water plants", kind = Kind.FLOAT, skipCount = 2, status = Status.ACTIVE, createdAt = at(-3, 9))
        val t = Series.start(f, Recurrence(Freq.DAILY), day)
        assertEquals(0L, t.id)
        assertEquals(day, t.earliestStart)
        assertEquals(0, t.skipCount)
        assertEquals(Status.PENDING, t.status)
        assertEquals(day, Series.joinDate(f, day))
        assertEquals(listOf(day, day.plusDays(1)), t.instancesBetween(day, day.plusDays(1)).map { it.earliestStart })
    }

    @Test fun aFloatCarriedOverJoinsFromTodayAndAnAnchorKeepsItsDate() {
        val old = Item(id = 6, title = "dentist", kind = Kind.FLOAT, earliestStart = day.minusDays(8), createdAt = at(-9, 9))
        assertEquals(day, Series.joinDate(old, day))
        assertEquals(day, Series.start(old, Recurrence(Freq.DAILY), day).earliestStart)
        val later = old.copy(earliestStart = day.plusDays(2))
        assertEquals(day.plusDays(2), Series.joinDate(later, day))
        val anchor = Item(id = 7, title = "pickup", kind = Kind.ANCHOR, start = at(-1, 15), createdAt = at(-9, 9))
        assertEquals(day.minusDays(1), Series.joinDate(anchor, day))
    }

    private val dishes = Item(id = 20, title = "dishes", kind = Kind.FLOAT, earliestStart = day.minusDays(5), recurrence = Recurrence(Freq.DAILY), createdAt = at(-5, 9))
    private fun timeOf(t: Item, d: Long, id: Long) = t.instancesBetween(day.plusDays(d), day.plusDays(d)).single().copy(id = id)

    @Test fun aMissedDailyTimePassesOnceTheNextTimeComes() {
        val yesterday = timeOf(dishes, -1, 21)
        val today = timeOf(dishes, 0, 22)
        val older = timeOf(dishes, -3, 23)
        val done = timeOf(dishes, -2, 24).copy(status = Status.DONE)
        val passed = Series.passing(listOf(dishes, older, done, yesterday, today), day).map { it.id }.sorted()
        assertEquals(listOf(21L, 23L), passed)
    }

    @Test fun aWeeklyTimeStaysUntilTheNextWeek() {
        val gym = dishes.copy(id = 30, recurrence = Recurrence(Freq.WEEKLY))
        // Started five days ago: its next time is two days off.
        val last = timeOf(gym, -5, 31)
        assertTrue(Series.passing(listOf(gym, last), day).isEmpty())
        assertEquals(listOf(31L), Series.passing(listOf(gym, last), day.plusDays(2)).map { it.id })
    }

    @Test fun underWayStoppedOrAnAnchorDoesNotPass() {
        val active = timeOf(dishes, -1, 41).copy(status = Status.ACTIVE)
        assertTrue(Series.passing(listOf(dishes, active, timeOf(dishes, 0, 42)), day).isEmpty())
        // A stopped series makes no next time: its last one stays.
        val stopped = dishes.copy(status = Status.DROPPED)
        assertTrue(Series.passing(listOf(stopped, timeOf(dishes, -1, 43)), day).isEmpty())
        val pickup = Item(id = 50, title = "pickup", kind = Kind.ANCHOR, start = at(-1, 15), recurrence = Recurrence(Freq.DAILY), createdAt = at(-5, 9))
        assertTrue(Series.passing(listOf(pickup, timeOf(pickup, -1, 51), timeOf(pickup, 0, 52)), day).isEmpty())
        val theirs = timeOf(dishes, -1, 44).copy(owned = false)
        assertTrue(Series.passing(listOf(dishes, theirs, timeOf(dishes, 0, 45)), day).isEmpty())
    }

    @Test fun aSeriesWithThreeTimesPassedInARowIsStale() {
        val passed = (1L..3L).map { timeOf(dishes, -it, 60 + it).copy(status = Status.MISSED) }
        val today = timeOf(dishes, 0, 70)
        assertEquals(listOf(20L), Series.stale(listOf(dishes, today) + passed, day).map { it.id })
        val oneDone = passed.mapIndexed { i, t -> if (i == 1) t.copy(status = Status.DONE) else t }
        assertTrue(Series.stale(listOf(dishes, today) + oneDone, day).isEmpty())
        // The recopy walks one-offs, then the stale series, never a series' times.
        val chore = Item(id = 80, title = "call bank", kind = Kind.FLOAT, createdAt = at(-9, 9))
        assertEquals(listOf(80L, 20L), Recopy.queue(listOf(dishes, today, chore) + passed, day).map { it.id })
    }
}
