package initiative.core

import java.time.DayOfWeek
import java.time.LocalDate

import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/**
 * The weekly recopy, after the old sticky pad: once a week, walk every open item
 * oldest first and keep, move, or drop each one. See docs/spec.md, Board variant B.
 */
object Recopy {
    const val EVERY_DAYS = 7L
    const val NUDGE_SKIPS = 3

    /** Due [days] after the last recopy, a week by default. */
    fun due(lastRecopy: LocalDateTime?, now: LocalDateTime, days: Long = EVERY_DAYS): Boolean =
        lastRecopy != null && !now.isBefore(lastRecopy.plusDays(days))

    /**
     * Open one-off floats, oldest first, then series gone stale (see [Series.stale]),
     * one card each. Anchors already have their times; a series' times come back
     * by themselves, so they aren't walked one by one.
     */
    fun queue(items: List<Item>, today: LocalDate): List<Item> =
        items.filter { !it.isTemplate && it.templateId == null && !it.isAnchor && !it.isMark && it.isOpen && !it.forThem && !Prep.waiting(it, today) }

            .sortedBy { it.createdAt } + Series.stale(items, today)

    /** Skipped often enough to suggest dropping it or giving it a real time. */
    fun nudge(item: Item): Boolean = item.skipCount >= NUDGE_SKIPS

    /** Two days on, but not past Sunday; at the very end of a week, tomorrow. */
    fun laterThisWeek(now: LocalDateTime, window: DayWindow): LocalDateTime {
        val today = now.toLocalDate()
        val sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        val day = minOf(today.plusDays(2), sunday).takeIf { it.isAfter(today) } ?: today.plusDays(1)
        return day.atTime(window.start)
    }

    fun nextWeek(now: LocalDateTime, window: DayWindow): LocalDateTime =
        now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atTime(window.start)
}
