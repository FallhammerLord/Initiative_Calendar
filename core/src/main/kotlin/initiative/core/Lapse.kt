package initiative.core

import java.time.LocalDateTime

/**
 * A float shown as Now, remembered so it can be skipped automatically if its
 * window closes untouched. [skipCount] and [notBefore] are snapshots: if either
 * changes, the person acted on it.
 */
data class Shown(val itemId: Long, val until: LocalDateTime, val skipCount: Int, val notBefore: LocalDateTime?)

/** A missed float is an automatic skip. See docs/spec.md, Skip and reslot. */
object AutoSkip {
    /** The float to skip now, if its window closed while it sat untouched as Now. */
    fun due(shown: Shown?, items: List<Item>, now: LocalDateTime): Item? {
        val s = shown ?: return null
        if (now.isBefore(s.until)) return null
        return items.firstOrNull {
            it.id == s.itemId && !it.isAnchor && it.isOpen && it.skipCount == s.skipCount && it.notBefore == s.notBefore
        }
    }
}

/** What the "Previously on" screen shows after a lapse. */
data class Recap(
    /** Anchors since the lapse began, done or passed. */
    val passed: List<Item>,
    /** Skipped floats set to Reroll: already back in the queue. */
    val rerolled: List<Item>,
    /** Skipped floats set to Drop: they go to the archive when the recap closes. */
    val dropped: List<Item>,
    /** Skipped floats set to Ask: one Keep or Drop each. */
    val ask: List<Item>,
    /** Anchors in the next 24 hours. */
    val ahead: List<Item>,
)

/** See docs/spec.md, Lapse recovery. */
object Lapse {
    const val UNTOUCHED_HOURS = 24L
    const val SKIP_STREAK = 3
    private const val AHEAD_HOURS = 24L

    /** A lapse: no action for a day, or three automatic skips in a row. */
    fun started(lastAction: LocalDateTime?, autoSkipStreak: Int, now: LocalDateTime): Boolean =
        autoSkipStreak >= SKIP_STREAK || (lastAction != null && !now.isBefore(lastAction.plusHours(UNTOUCHED_HOURS)))

    fun recap(items: List<Item>, autoSkipped: Set<Long>, since: LocalDateTime, now: LocalDateTime): Recap {
        val live = items.filter { !it.isTemplate }
        val anchors = live.filter { it.isAnchor && it.start != null }.sortedBy { it.start }
        val handled = live.filter { !it.isAnchor && it.isOpen && it.id in autoSkipped }.sortedBy { it.createdAt }
        return Recap(
            passed = anchors.filter { !it.start!!.isBefore(since) && it.start!!.isBefore(now) && it.status != Status.DROPPED },
            rerolled = handled.filter { it.missPolicy == MissPolicy.REROLL },
            dropped = handled.filter { it.missPolicy == MissPolicy.DROP },
            ask = handled.filter { it.missPolicy == MissPolicy.ASK },
            ahead = anchors.filter { it.isOpen && !it.start!!.isBefore(now) && it.start!!.isBefore(now.plusHours(AHEAD_HOURS)) },
        )
    }
}
