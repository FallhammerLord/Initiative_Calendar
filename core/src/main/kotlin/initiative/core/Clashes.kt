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

    data class Clash(val mine: Item, val theirs: Item) {
        val at: LocalDateTime get() = maxOf(mine.start!!, theirs.start!!)
        val day: LocalDate get() = minOf(mine.start!!, theirs.start!!).toLocalDate()

        /** Holds both items and both times, so moving either one makes it a new clash. */
        val key: String get() = "${mine.id}@${mine.start}|${theirs.id}@${theirs.start}"
    }

    /** Clashes from [now] through [AHEAD_DAYS], soonest first, leaving out the [kept] ones. */
    fun find(items: List<Item>, now: LocalDateTime, kept: Set<String> = emptySet()): List<Clash> {
        val last = now.toLocalDate().plusDays(AHEAD_DAYS)
        val live = items.filter {
            it.isAnchor && !it.isTemplate && it.isOpen && !it.forThem && it.start != null &&
                it.anchorEnd!!.isAfter(now) && !it.start.toLocalDate().isAfter(last)
        }
        val mine = live.filter { !it.fromThem }
        val theirs = live.filter { it.fromThem }
        return mine.flatMap { m ->
            theirs.filter { t -> m.start!!.isBefore(t.anchorEnd!!) && t.start!!.isBefore(m.anchorEnd!!) }.map { Clash(m, it) }
        }
            .filter { it.key !in kept }
            .sortedWith(compareBy({ it.at }, { it.mine.title.lowercase() }))
    }
}
