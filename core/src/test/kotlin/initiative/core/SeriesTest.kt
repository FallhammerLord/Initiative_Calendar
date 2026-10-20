package initiative.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
