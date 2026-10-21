package initiative.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Log's dates, a series' far-off times, search, and steps. */
class LogTest {
    // Tuesday.
    private val today = LocalDate.of(2026, 10, 20)
    private fun at(d: LocalDate, h: Int, m: Int = 0) = d.atTime(h, m)
    private val dentist = Item(id = 1, title = "dentist", kind = Kind.ANCHOR, start = at(today.plusDays(1), 9), createdAt = at(today, 7))
    private val laundry = Item(id = 2, title = "laundry", kind = Kind.FLOAT, createdAt = at(today.minusDays(3), 7))
    private val landlord = Item(
        id = 3, title = "call landlord", kind = Kind.FLOAT, status = Status.DONE,
        doneAt = at(today.minusDays(2), 10), createdAt = at(today.minusDays(4), 7),
    )

    @Test fun eachItemSitsOnItsDay() {
        assertEquals(today.plusDays(1), Log.dateOf(dentist, today))
        // Carried over from earlier days: today.
        assertEquals(today, Log.dateOf(laundry, today))
        assertEquals(today.minusDays(2), Log.dateOf(landlord, today))
        // Skipped to tomorrow: tomorrow.
        assertEquals(today.plusDays(1), Log.dateOf(laundry.copy(notBefore = at(today.plusDays(1), 8)), today))
    }

    @Test fun theWeekListsEveryDayInOrder() {
        val week = Log.days(listOf(dentist, laundry, landlord), Log.weekStart(today), Log.weekStart(today).plusDays(6), today)
        assertEquals(7, week.size)
        assertEquals(LocalDate.of(2026, 10, 19), week.first().first)
        assertEquals(listOf("laundry"), week[1].second.map { it.item.title })
        assertEquals(listOf("dentist"), week[2].second.map { it.item.title })
        assertTrue(week[6].second.isEmpty())
    }

    @Test fun aSeriesShowsNextYear() {
        val yearly = Item(
            id = 5, title = "eye exam", kind = Kind.ANCHOR, start = at(LocalDate.of(2026, 2, 9), 10),
            recurrence = Recurrence(Freq.YEARLY), createdAt = at(LocalDate.of(2026, 1, 2), 7),
        )
        val next = Log.entries(listOf(yearly), LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31), today)
        assertEquals(listOf(LocalDate.of(2027, 2, 9)), next.map { it.date })
        assertTrue(next.single().projected)
        // The template itself never shows.
        assertNull(Log.dateOf(yearly, today))
    }

    @Test fun aTimeAlreadyMadeIsntShownTwice() {
        val daily = Item(id = 6, title = "water plants", kind = Kind.FLOAT, recurrence = Recurrence(Freq.DAILY), earliestStart = today, createdAt = at(today, 7))
        val made = daily.instancesBetween(today, today).single().copy(id = 7)
        val days = Log.entries(listOf(daily, made), today, today.plusDays(1), today)
        assertEquals(listOf(7L, 0L), days.map { it.item.id })
    }

    @Test fun searchFindsDoneItemsAndStepsSoonestFirst() {
        val withSteps = laundry.copy(steps = listOf(Step("fold clean laundry")))
        val found = Log.search(listOf(dentist, withSteps, landlord), "L", today)
        // Coming first (today's laundry), then the past (landlord, done).
        assertEquals(listOf("laundry", "call landlord"), found.map { it.item.title })
        assertEquals(listOf("laundry"), Log.search(listOf(withSteps, dentist), "fold", today).map { it.item.title })
        assertTrue(Log.search(listOf(dentist), "  ", today).isEmpty())
    }

    @Test fun stepsSurviveStorageAndStartFreshEachTime() {
        val steps = listOf(Step("collect dirty clothes", done = true), Step("fold clean laundry"))
        assertEquals(steps, Steps.decode(Steps.encode(steps)))
        assertTrue(Steps.decode("").isEmpty())
        val weekly = laundry.copy(recurrence = Recurrence(Freq.WEEKLY), earliestStart = today, steps = steps)
        val next = weekly.instancesBetween(today, today).single()
        assertEquals(listOf(false, false), next.steps.map { it.done })
        assertEquals("collect dirty clothes", next.nextStep?.title)
    }
}
