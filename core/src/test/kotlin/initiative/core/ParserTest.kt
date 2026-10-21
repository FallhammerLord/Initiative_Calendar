package initiative.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParserTest {
    // Monday, 10:00.
    private val now = LocalDateTime.of(2026, 10, 19, 10, 0)
    private fun p(s: String) = Parser.parse(s, now)!!

    @Test fun dentistThursday() {
        val r = p("dentist thu 3pm").item
        assertEquals("dentist", r.title)
        assertEquals(Kind.ANCHOR, r.kind)
        assertEquals(LocalDateTime.of(2026, 10, 22, 15, 0), r.start)
        assertEquals(WallStrength.FIRM, r.wall)
    }

    @Test fun monthDates() {
        // Today is Monday, October 19, 2026.
        assertEquals(LocalDateTime.of(2026, 11, 14, 15, 0), p("dentist nov 14 3pm").item.start)
        assertEquals("dentist", p("dentist nov 14 3pm").item.title)
        assertEquals(LocalDate.of(2026, 11, 14), p("dentist november 14th").item.earliestStart)
        assertEquals(LocalDate.of(2026, 11, 14), p("dentist 14 nov").item.earliestStart)
        assertEquals(LocalDate.of(2026, 11, 14), p("dentist 11/14").item.earliestStart)
        assertEquals(LocalDate.of(2027, 1, 5), p("dentist on jan 5").item.earliestStart)
        assertEquals("dentist", p("dentist on jan 5").item.title)
        // Passed this year: next year.
        assertEquals(LocalDate.of(2027, 3, 2), p("renew 3/2").item.earliestStart)
        assertEquals(LocalDate.of(2026, 12, 1), p("taxes by dec 1").item.deadline)
        assertEquals("taxes", p("taxes by dec 1").item.title)
        // A month word with no day stays in the title.
        assertEquals("march band", p("march band").item.title)
        assertEquals("may call", p("may call").item.title)
    }

    @Test fun aDayFromTheLogIsTheDefault() {
        val friday = LocalDate.of(2026, 10, 23)
        // No day typed: the Log's day.
        assertEquals(friday.atTime(15, 0), Parser.parse("dentist 3pm", now, on = friday)!!.item.start)
        val float = Parser.parse("pack lunch", now, on = friday)!!
        assertEquals(friday, float.item.earliestStart)
        assertEquals(false, float.unsureKind)
        // A day typed still wins.
        assertEquals(LocalDate.of(2026, 10, 22), Parser.parse("dentist thu 3pm", now, on = friday)!!.item.start?.toLocalDate())
    }

    @Test fun plainFloat() {
        val r = p("call landlord")
        assertEquals(Kind.FLOAT, r.item.kind)
        assertEquals("call landlord", r.item.title)
        assertEquals(Priority.AUTO, r.item.priority)
        assertEquals(15, r.item.durationMinutes)
        assertEquals(false, r.unsureKind)
    }

    @Test fun focusTasksStartAtHalfAnHour() {
        assertEquals(Effort.FOCUS, p("taxes focus").item.effort)
        assertEquals(30, p("taxes focus").item.durationMinutes)
        assertEquals(15, p("taxes quick").item.durationMinutes)
    }

    @Test fun recurringHardPickup() {
        val r = p("pickup girls weekdays 3pm hard").item
        assertEquals("pickup girls", r.title)
        assertEquals(Kind.ANCHOR, r.kind)
        assertEquals(WallStrength.HARD, r.wall)
        assertEquals(Freq.WEEKDAYS, r.recurrence?.freq)
        assertEquals(LocalDateTime.of(2026, 10, 19, 15, 0), r.start)
    }

    @Test fun taxesByFriday() {
        val r = p("taxes by fri high")
        assertEquals("taxes", r.item.title)
        assertEquals(Kind.FLOAT, r.item.kind)
        assertEquals(LocalDate.of(2026, 10, 23), r.item.deadline)
        assertEquals(Priority.HIGH, r.item.priority)
        assertTrue(r.explicitPriority)
    }

    @Test fun softGameNight() {
        val r = p("game night tue 7pm soft").item
        assertEquals("game night", r.title)
        assertEquals(LocalDateTime.of(2026, 10, 20, 19, 0), r.start)
        assertEquals(WallStrength.SOFT, r.wall)
    }

    @Test fun everyOtherMonday() {
        val r = p("bins every other mon 7am").item
        assertEquals("bins", r.title)
        assertEquals(Recurrence(Freq.WEEKLY, 2, setOf(DayOfWeek.MONDAY)), r.recurrence)
        // 7am today has passed, so the series starts next Monday.
        assertEquals(LocalDateTime.of(2026, 10, 26, 7, 0), r.start)
    }

    @Test fun dayWithoutTimeAsks() {
        val r = p("dentist thu")
        assertTrue(r.unsureKind)
        assertEquals(LocalDate.of(2026, 10, 22), r.item.earliestStart)
    }

    @Test fun durationAndAt() {
        val r = p("call mom at 4:30pm for 45 min").item
        assertEquals("call mom", r.title)
        assertEquals(LocalDateTime.of(2026, 10, 19, 16, 30), r.start)
        assertEquals(LocalDateTime.of(2026, 10, 19, 17, 15), r.end)
        assertEquals(60, p("vacuum 1h").item.durationMinutes)
    }

    @Test fun passedTimeMovesToTomorrow() {
        assertEquals(LocalDateTime.of(2026, 10, 20, 9, 0), p("standup 9am").item.start)
    }

    @Test fun recurrenceWords() {
        assertEquals(Recurrence(Freq.MONTHLY_DATE, 2), p("haircut bimonthly").item.recurrence)
        assertEquals(Recurrence(Freq.YEARLY, 1), p("renew registration yearly").item.recurrence)
        assertEquals(Recurrence(Freq.DAILY, 2), p("water plants every other day").item.recurrence)
        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            p("gym every mon and thu 6pm").item.recurrence?.days,
        )
    }

    @Test fun emptyIsNull() {
        assertNull(Parser.parse("   ", now))
        assertNull(Parser.parse("thu 3pm", now))
    }
}
