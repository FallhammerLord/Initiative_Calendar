package initiative.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class Freq { DAILY, WEEKDAYS, WEEKLY, MONTHLY_DATE, MONTHLY_WEEKDAY, YEARLY }

/**
 * A repeat rule. [interval] 2 gives the every-other variants. [days] applies to
 * WEEKLY; when empty, the series start's weekday is used.
 */
data class Recurrence(
    val freq: Freq,
    val interval: Int = 1,
    val days: Set<DayOfWeek> = emptySet(),
) {
    init {
        require(interval >= 1) { "interval must be at least 1" }
    }

    /** Whether a series whose first occurrence is [seriesStart] occurs on [date]. */
    fun occursOn(seriesStart: LocalDate, date: LocalDate): Boolean {
        if (date.isBefore(seriesStart)) return false
        return when (freq) {
            Freq.DAILY -> ChronoUnit.DAYS.between(seriesStart, date) % interval == 0L
            Freq.WEEKDAYS -> date.dayOfWeek !in WEEKEND
            Freq.WEEKLY -> {
                val on = days.ifEmpty { setOf(seriesStart.dayOfWeek) }
                date.dayOfWeek in on && weeksBetween(seriesStart, date) % interval == 0L
            }
            Freq.MONTHLY_DATE ->
                monthsBetween(seriesStart, date) % interval == 0L &&
                    date.dayOfMonth == minOf(seriesStart.dayOfMonth, date.lengthOfMonth())
            Freq.MONTHLY_WEEKDAY ->
                monthsBetween(seriesStart, date) % interval == 0L &&
                    date.dayOfWeek == seriesStart.dayOfWeek &&
                    sameWeekOfMonth(seriesStart, date)
            Freq.YEARLY ->
                (date.year - seriesStart.year) % interval == 0 &&
                    date.month == seriesStart.month &&
                    date.dayOfMonth == minOf(seriesStart.dayOfMonth, date.lengthOfMonth())
        }
    }

    fun occurrences(seriesStart: LocalDate, from: LocalDate, to: LocalDate): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        var d = maxOf(from, seriesStart)
        while (!d.isAfter(to)) {
            if (occursOn(seriesStart, d)) out += d
            d = d.plusDays(1)
        }
        return out
    }

    private companion object {
        val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

        fun weeksBetween(a: LocalDate, b: LocalDate): Long {
            val monday = TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
            return ChronoUnit.WEEKS.between(a.with(monday), b.with(monday))
        }

        fun monthsBetween(a: LocalDate, b: LocalDate): Long =
            (b.year * 12L + b.monthValue) - (a.year * 12L + a.monthValue)

        fun nth(d: LocalDate): Int = (d.dayOfMonth - 1) / 7 + 1
        fun isLast(d: LocalDate): Boolean = d.plusWeeks(1).month != d.month

        // A series that starts on a 5th weekday repeats on the last one.
        fun sameWeekOfMonth(start: LocalDate, d: LocalDate): Boolean =
            if (nth(start) == 5) isLast(d) else nth(d) == nth(start)
    }
}

/** First date of the series this template describes. */
val Item.seriesStart: LocalDate
    get() = start?.toLocalDate() ?: earliestStart ?: createdAt.toLocalDate()

/** Instances of this template that fall between [from] and [to], inclusive. */
fun Item.instancesBetween(from: LocalDate, to: LocalDate): List<Item> {
    val rule = recurrence ?: return emptyList()
    val first = seriesStart
    return rule.occurrences(first, from, to).map { date ->
        val shift = ChronoUnit.DAYS.between(first, date)
        copy(
            id = 0,
            recurrence = null,
            templateId = id,
            start = start?.plusDays(shift),
            end = end?.plusDays(shift),
            earliestStart = if (isAnchor) null else date,
            deadline = deadline?.plusDays(shift),
            status = Status.PENDING,
            skipCount = 0,
            notBefore = null,
            doneAt = null,
            activeSince = null,
            createdAt = date.atStartOfDay(),
        )
    }
}

/** The date an instance stands for, used to avoid generating it twice. */
val Item.occurrenceDate: LocalDate?
    get() = start?.toLocalDate() ?: earliestStart
