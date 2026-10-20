package initiative.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class RecurrenceTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test fun everyOtherWeek() {
        val r = Recurrence(Freq.WEEKLY, 2, setOf(DayOfWeek.MONDAY))
        assertEquals(
            listOf(d(2026, 10, 26), d(2026, 11, 9), d(2026, 11, 23)),
            r.occurrences(d(2026, 10, 26), d(2026, 10, 19), d(2026, 11, 30)),
        )
    }

    @Test fun weekdays() {
        val r = Recurrence(Freq.WEEKDAYS)
        assertEquals(5, r.occurrences(d(2026, 10, 19), d(2026, 10, 19), d(2026, 10, 25)).size)
    }

    @Test fun monthlyDateClampsToMonthEnd() {
        val r = Recurrence(Freq.MONTHLY_DATE)
        assertEquals(
            listOf(d(2027, 1, 31), d(2027, 2, 28), d(2027, 3, 31)),
            r.occurrences(d(2027, 1, 31), d(2027, 1, 1), d(2027, 3, 31)),
        )
    }

    @Test fun secondTuesdayEveryOtherMonth() {
        val r = Recurrence(Freq.MONTHLY_WEEKDAY, 2)
        assertEquals(
            listOf(d(2026, 10, 13), d(2026, 12, 8)),
            r.occurrences(d(2026, 10, 13), d(2026, 10, 1), d(2027, 1, 31)),
        )
    }

    @Test fun yearlyOnLeapDay() {
        val r = Recurrence(Freq.YEARLY)
        assertEquals(listOf(d(2029, 2, 28)), r.occurrences(d(2028, 2, 29), d(2029, 1, 1), d(2029, 12, 31)))
    }

    @Test fun instancesShiftTimesAndLinkToTemplate() {
        val t = Item(
            id = 9, title = "pickup", kind = Kind.ANCHOR,
            start = LocalDateTime.of(2026, 10, 19, 15, 0),
            recurrence = Recurrence(Freq.WEEKDAYS),
            createdAt = LocalDateTime.of(2026, 10, 19, 9, 0),
        )
        val out = t.instancesBetween(d(2026, 10, 23), d(2026, 10, 26))
        assertEquals(listOf(LocalDateTime.of(2026, 10, 23, 15, 0), LocalDateTime.of(2026, 10, 26, 15, 0)), out.map { it.start })
        assertEquals(setOf(9L), out.map { it.templateId }.toSet())
        assertEquals(setOf(null), out.map { it.recurrence }.toSet())
    }

    @Test fun learningAveragesAndDropsOutliers() {
        assertEquals(30, Learning.updatedEstimate(30, emptyList()))
        assertEquals(20, Learning.updatedEstimate(30, listOf(20, 18, 22)))
        assertEquals(20, Learning.updatedEstimate(30, listOf(20, 500)))
        assertEquals(5, Learning.updatedEstimate(30, listOf(1)))
    }
}

class FormatsTest {
    @Test fun oneWayToWriteTimes() {
        assertEquals("3:00 PM", Formats.clock(LocalDateTime.of(2026, 10, 19, 15, 0)))
        assertEquals("1h 40m", Formats.countdown(LocalDateTime.of(2026, 10, 19, 13, 20), LocalDateTime.of(2026, 10, 19, 15, 0)))
        assertEquals("25m", Formats.countdown(LocalDateTime.of(2026, 10, 19, 14, 35), LocalDateTime.of(2026, 10, 19, 15, 0)))
        assertEquals("2h", Formats.countdown(java.time.Duration.ofMinutes(120)))
        assertEquals("Tue, Oct 20", Formats.day(java.time.LocalDate.of(2026, 10, 20)))
        val pickup = Item(title = "pickup", kind = Kind.ANCHOR, start = LocalDateTime.of(2026, 10, 19, 15, 0), wall = WallStrength.HARD, createdAt = LocalDateTime.of(2026, 10, 19, 8, 0))
        assertEquals("3:00 PM · Hard", Formats.anchorLine(pickup))
    }
}
