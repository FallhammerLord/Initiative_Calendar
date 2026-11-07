package initiative.core

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Double-bookings: one of your anchors and a shared anchor from someone else
 * at the same time. Worked out fresh each time, never saved. Each person
 * chooses for themselves: keep both, skip theirs, or move theirs. A clash
 * clears once chosen, or when either one moves, since its key holds both times.
 */
object Clashes {
    /** How many days ahead a double-booking shows, dim, before its day. */
    const val AHEAD_DAYS = 7L

    /**
     * One double-booking, at its soonest time. When two series overlap time after
     * time, it's one clash for all of them: [series] is your series, and one
     * choice covers every time.
     */
    data class Clash(val mine: Item, val theirs: Item, val series: Item? = null) {
        val at: LocalDateTime get() = maxOf(mine.start!!, theirs.start!!)
        val day: LocalDate get() = minOf(mine.start!!, theirs.start!!).toLocalDate()

        /**
         * Holds both items and both times, so moving either one makes it a new clash.
         * Two series' clash holds the two series, so keeping both covers every time.
         */
        val key: String get() = if (series != null) "series:${mine.templateId}|${theirs.templateId}" else "${mine.id}@${mine.start}|${theirs.id}@${theirs.start}"
    }

    /** Clashes from [now] through [AHEAD_DAYS], soonest first, leaving out the [kept] ones. */
    fun find(items: List<Item>, now: LocalDateTime, kept: Set<String> = emptySet()): List<Clash> {
        val raw = pairs(items, now, now.toLocalDate().plusDays(AHEAD_DAYS))
        val templates = items.filter { it.isTemplate }.associateBy { it.id }
        // Two series that overlap more than once are one clash.
        val (bySeries, single) = raw.partition { it.mine.templateId != null && it.theirs.templateId != null }
        val grouped = bySeries.groupBy { it.mine.templateId to it.theirs.templateId }.values.flatMap { group ->
            val series = templates[group.first().mine.templateId]
            if (group.size < 2 || series == null) group else listOf(group.minBy { it.at }.copy(series = series))
        }
        return (single + grouped)
            .filter { it.key !in kept }
            .sortedWith(compareBy({ it.at }, { it.mine.title.lowercase() }))
    }

    /** Every time of theirs that [clash] covers: one, or for two series, each made time that overlaps. */
    fun theirTimes(items: List<Item>, clash: Clash, now: LocalDateTime): List<Item> {
        if (clash.series == null) return listOf(clash.theirs)
        return pairs(items, now, null)
            .filter { it.mine.templateId == clash.mine.templateId && it.theirs.templateId == clash.theirs.templateId }
            .map { it.theirs }.distinctBy { it.id }
    }

    private fun pairs(items: List<Item>, now: LocalDateTime, last: LocalDate?): List<Clash> {
        val live = items.filter {
            it.isAnchor && !it.isTemplate && it.isOpen && !it.forThem && it.start != null &&
                it.anchorEnd!!.isAfter(now) && (last == null || !it.start.toLocalDate().isAfter(last))
        }
        val mine = live.filter { !it.fromThem }
        val theirs = live.filter { it.fromThem }
        return mine.flatMap { m ->
            theirs.filter { t -> m.start!!.isBefore(t.anchorEnd!!) && t.start!!.isBefore(m.anchorEnd!!) }.map { Clash(m, it) }
        }
    }
}

