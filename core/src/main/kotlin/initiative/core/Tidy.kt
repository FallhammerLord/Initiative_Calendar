package initiative.core

import java.time.LocalDate

/**
 * Keeping the ledger tidy, as a calendar's trash empties itself. A removed or
 * passed item leaves the Log's days at once; search still finds it for
 * [KEEP_DAYS] after the later of its day and the day it was removed or passed,
 * and then it goes for good. Done items stay: they are the record.
 */
object Tidy {
    const val KEEP_DAYS = 30L

    /** The last day search finds a removed or passed item; null for anything else. */
    fun keptUntil(item: Item, today: LocalDate): LocalDate? {
        if (item.status != Status.DROPPED && !(item.status == Status.MISSED && !item.isTemplate)) return null
        val settled = item.settledAt?.toLocalDate() ?: today
        // A stopped series sits on no date: it's kept from the day it stopped.
        val planned = if (item.isTemplate) null else Log.dateOf(item, today)
        val keep = maxOf(settled, planned ?: settled).plusDays(KEEP_DAYS)
        // A shared one stays while a sync still looks at its day, so it isn't sent back.
        val day = item.occurrenceDate
        return if (item.isShared && day != null) maxOf(keep, day.plusDays(SharedTasks.WINDOW_DAYS + 1)) else keep
    }

    /**
     * What a tidy-up does: [gone] go for good, with their history; [pruned] are
     * series whose skipped dates already past are dropped, as (before, after).
     */
    data class Sweep(val gone: List<Item>, val pruned: List<Pair<Item, Item>>)

    fun sweep(items: List<Item>, today: LocalDate): Sweep {
        // The weekly recopy asks about a series from its last few times, so those stay.
        val live = items.filter { it.isTemplate && it.status != Status.DROPPED }.map { it.id }.toSet()
        val recent = items.filter { it.templateId in live && it.occurrenceDate?.isBefore(today) == true }
            .groupBy { it.templateId }.values
            .flatMap { times -> times.sortedByDescending { it.occurrenceDate }.take(Series.STALE_TIMES) }
            .map { it.id }.toSet()
        // A series with times, or a marker with prep tasks, is still pointed at.
        val pointedAt = items.flatMap { listOfNotNull(it.templateId, it.markerId) }.toSet()
        val gone = items.filter { item ->
            !item.isTemplate && item.id !in recent && item.id !in pointedAt &&
                keptUntil(item, today)?.isBefore(today) == true
        }
        // Gone times may leave their stopped series pointed at by nothing.
        val goneIds = gone.map { it.id }.toSet()
        val stillPointed = items.filter { it.id !in goneIds }.flatMap { listOfNotNull(it.templateId, it.markerId) }.toSet()
        val series = items.filter { t ->
            t.isTemplate && t.owned && t.id !in goneIds && t.id !in stillPointed && keptUntil(t, today)?.isBefore(today) == true
        }
        val pruned = items.filter { it.isTemplate && it.id in live && it.skipDates.any { d -> d.isBefore(today) } }
            .map { it to it.copy(skipDates = it.skipDates.filterTo(mutableSetOf()) { d -> !d.isBefore(today) }) }
        return Sweep(gone + series, pruned)
    }
}
