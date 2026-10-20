package initiative.core

import java.time.DayOfWeek
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

    /** Open floats, oldest first. Anchors already have their times. */
    fun queue(items: List<Item>): List<Item> =
        items.filter { !it.isTemplate && !it.isAnchor && it.isOpen }.sortedBy { it.createdAt }

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
