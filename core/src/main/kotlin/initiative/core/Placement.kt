package initiative.core

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** Free time between two walls. [before] is the anchor that ends it, [after] the one it leads into. */
data class Gap(
    val start: LocalDateTime,
    val end: LocalDateTime,
    val before: Item?,
    val after: Item?,
) {
    val minutes: Long get() = Duration.between(start, end).toMinutes()
    operator fun contains(t: LocalDateTime): Boolean = !t.isBefore(start) && t.isBefore(end)
}

data class NowPick(val item: Item?, val reason: String)

data class Board(val now: NowPick, val next: Item?)

enum class MarkKind { DONE, SOFT, FIRM, HARD }

data class LedgerMark(val at: LocalDateTime, val kind: MarkKind, val item: Item)

/** Result of Skip or Tomorrow: the updated item and where it landed. */
data class Moved(val item: Item, val message: String)

/**
 * Picks the Now card with a fixed, explainable rule. See docs/spec.md, Placement logic.
 */
class Placement(private val window: DayWindow = DayWindow()) {
    private companion object {
        const val FOCUS_FALLBACK_HOURS = 2L
    }

    fun board(items: List<Item>, now: LocalDateTime): Board {
        val today = now.toLocalDate()
        val live = items.filter { !it.isTemplate }
        val upcoming = openAnchors(live).filter { it.start!!.isAfter(now) }

        openAnchors(live).firstOrNull { !it.start!!.isAfter(now) && now.isBefore(it.anchorEnd!!) }?.let {
            return Board(NowPick(it, "Happening now"), upcoming.firstOrNull())
        }

        upcoming.firstOrNull()?.takeIf { !now.isBefore(it.warnAt!!) }?.let {
            val mins = Duration.between(now, it.start!!).toMinutes().coerceAtLeast(1)
            return Board(NowPick(it, "Starts in $mins min"), upcoming.getOrNull(1))
        }

        val nextAnchor = upcoming.firstOrNull()
        val floats = live.filter { !it.isAnchor && it.isOpen }

        floats.firstOrNull { f ->
            val slot = f.pinnedSlot ?: return@firstOrNull false
            !now.isBefore(slot) && now.isBefore(slot.plusMinutes(f.durationMinutes.toLong()))
        }?.let { return Board(NowPick(it, "Pinned to this slot"), nextAnchor) }

        if (now.toLocalTime() !in window) {
            val why = if (now.toLocalTime().isBefore(window.start)) "Day starts at ${Formats.clock(window.start)}" else "Day's done"
            return Board(NowPick(null, why), nextAnchor)
        }

        val gaps = gaps(today, live, now)
        val current = gaps.firstOrNull { now in it } ?: return Board(NowPick(null, "Nothing fits right now"), nextAnchor)
        val longest = gaps.maxOf { it.minutes }

        val ranked = rank(eligible(floats, now).filter { fits(it, current, longest) }, today)
        val top = ranked.firstOrNull()
            ?: return Board(NowPick(null, if (floats.isEmpty()) "Nothing needs doing" else "Nothing fits before ${current.after?.title ?: "the day ends"}"), nextAnchor)

        val reason = reasonFor(top, ranked.getOrNull(1), current, longest, today)
        return Board(NowPick(top, reason), nextAnchor ?: ranked.getOrNull(1))
    }

    /** Today's remaining walls and open floats in board order, for the expanded ledger. */
    fun dayQueue(items: List<Item>, now: LocalDateTime): List<Item> {
        val today = now.toLocalDate()
        val live = items.filter { !it.isTemplate }
        val anchors = openAnchors(live).filter { it.start!!.toLocalDate() == today && it.anchorEnd!!.isAfter(now) }
        val floats = rank(
            eligible(live.filter { !it.isAnchor && it.isOpen }, now.toLocalDate().atTime(window.end).minusMinutes(1)),
            today,
        )
        return anchors + floats
    }

    fun ledger(items: List<Item>, date: LocalDate): List<LedgerMark> {
        val live = items.filter { !it.isTemplate }
        val done = live.mapNotNull { i -> i.doneAt?.takeIf { it.toLocalDate() == date }?.let { LedgerMark(it, MarkKind.DONE, i) } }
        val walls = openAnchors(live).filter { it.start!!.toLocalDate() == date }.map {
            val kind = when (it.wall) {
                WallStrength.SOFT -> MarkKind.SOFT
                WallStrength.FIRM -> MarkKind.FIRM
                WallStrength.HARD -> MarkKind.HARD
            }
            LedgerMark(it.start!!, kind, it)
        }
        return (done + walls).sortedBy { it.at }
    }

    /** Gaps on [date] from [from] onward, between anchor lead-time warnings and anchor ends. */
    fun gaps(date: LocalDate, items: List<Item>, from: LocalDateTime): List<Gap> {
        val dayStart = date.atTime(window.start)
        val dayEnd = date.atTime(window.end)
        val walls = items
            .filter { it.isAnchor && !it.isTemplate && it.start != null && it.status != Status.DROPPED }
            .filter { it.start!!.toLocalDate() == date }
            .sortedBy { it.start }
        val out = mutableListOf<Gap>()
        var cursor = maxOf(dayStart, from)
        var prev: Item? = null
        for (a in walls) {
            val wallStart = minOf(a.warnAt!!, dayEnd)
            if (wallStart.isAfter(cursor)) out += Gap(cursor, wallStart, prev, a)
            if (a.anchorEnd!!.isAfter(cursor)) {
                cursor = a.anchorEnd!!
                prev = a
            }
        }
        if (dayEnd.isAfter(cursor)) out += Gap(cursor, dayEnd, prev, null)
        return out
    }

    /**
     * The next moment the board could pick something different: an anchor's warning,
     * start or end, a float coming back, a pinned slot, or the day window. Never more
     * than an hour away, as a safety net.
     */
    fun nextChange(items: List<Item>, now: LocalDateTime): LocalDateTime {
        val live = items.filter { !it.isTemplate && it.isOpen }
        val today = now.toLocalDate()
        val times = mutableListOf<LocalDateTime>()
        live.filter { it.isAnchor && it.start != null }.forEach { times += listOf(it.warnAt!!, it.start!!, it.anchorEnd!!) }
        live.filter { !it.isAnchor }.forEach { f ->
            f.notBefore?.let { times += it }
            f.pinnedSlot?.let { times += listOf(it, it.plusMinutes(f.durationMinutes.toLong())) }
        }
        times += listOf(today.atTime(window.start), today.atTime(window.end), today.plusDays(1).atTime(window.start))
        val cap = now.plusHours(1)
        return times.filter { it.isAfter(now) }.minOrNull()?.let { minOf(it, cap) } ?: cap
    }

    /** The next Firm or Hard anchor still to start: the wall that ends a focus session. */
    fun nextWall(items: List<Item>, now: LocalDateTime): Item? =
        items.filter { !it.isTemplate && it.isAnchor && it.isOpen && it.start != null && it.wall != WallStrength.SOFT }
            .filter { it.start!!.isAfter(now) }
            .minByOrNull { it.start!! }

    /**
     * When a focus session started [now] ends: at the next Firm or Hard wall, or
     * else at the end of the day window. Past the window, two hours from now.
     */
    fun focusEnd(items: List<Item>, now: LocalDateTime): LocalDateTime {
        nextWall(items, now)?.start?.let { return it }
        val dayEnd = now.toLocalDate().atTime(window.end)
        return if (dayEnd.isAfter(now)) dayEnd else now.plusHours(FOCUS_FALLBACK_HOURS)
    }

    /** Skip: move to the next gap it fits, or an hour later, or tomorrow. */
    fun skip(item: Item, items: List<Item>, now: LocalDateTime): Moved {
        val today = now.toLocalDate()
        val need = item.durationMinutes.toLong()
        val todayGaps = gaps(today, items, now)
        val current = todayGaps.firstOrNull { now in it }
        val later = todayGaps.filter { current == null || it.start >= current.end }.firstOrNull { it.minutes >= need }

        val skipped = item.copy(skipCount = item.skipCount + 1, status = Status.PENDING, pinnedSlot = null, activeSince = null)
        if (later != null) {
            return Moved(skipped.copy(notBefore = later.start), landing(later, "Back at"))
        }
        val hourOn = roundUp5(now.plusHours(1))
        if (current != null && !hourOn.plusMinutes(need).isAfter(current.end)) {
            return Moved(skipped.copy(notBefore = hourOn), "Back at ${Formats.clock(hourOn)}")
        }
        val tomorrow = today.plusDays(1)
        val next = gaps(tomorrow, items, tomorrow.atStartOfDay()).let { g -> g.firstOrNull { it.minutes >= need } ?: g.firstOrNull() }
        return if (next != null) {
            Moved(skipped.copy(notBefore = next.start), landing(next, "Back tomorrow at"))
        } else {
            Moved(skipped.copy(notBefore = tomorrow.atTime(window.start)), "Back tomorrow")
        }
    }

    /** Tomorrow: off today's board entirely. Not a skip. */
    fun tomorrow(item: Item, now: LocalDateTime): Moved =
        Moved(
            item.copy(notBefore = now.toLocalDate().plusDays(1).atTime(window.start), status = Status.PENDING, pinnedSlot = null, activeSince = null),
            "Back tomorrow",
        )

    private fun openAnchors(items: List<Item>) =
        items.filter { it.isAnchor && it.isOpen && it.start != null }.sortedBy { it.start }

    private fun eligible(floats: List<Item>, now: LocalDateTime): List<Item> {
        val today = now.toLocalDate()
        return floats.filter { f ->
            f.pinnedSlot == null &&
                (f.earliestStart == null || !f.earliestStart.isAfter(today)) &&
                (f.notBefore == null || !f.notBefore.isAfter(now))
        }
    }

    // A float longer than every gap today may take the longest one.
    private fun fits(f: Item, current: Gap, longest: Long): Boolean {
        val need = f.durationMinutes.toLong()
        return need <= current.minutes || (need > longest && current.minutes == longest)
    }

    private fun rank(floats: List<Item>, today: LocalDate): List<Item> =
        floats.sortedWith(
            compareByDescending<Item> { it.resolvePriority(today).level }
                .thenBy(nullsLast()) { it.deadline }
                .thenBy { it.createdAt },
        )

    private fun reasonFor(top: Item, second: Item?, gap: Gap, longest: Long, today: LocalDate): String {
        val p = top.resolvePriority(today)
        val q = second?.resolvePriority(today)
        val why = when {
            q != null && p.level > q.level -> p.reason ?: "Higher priority"
            q != null && top.deadline != null && top.deadline != second?.deadline -> dueText(top.deadline, today)
            q != null -> p.reason ?: "Oldest on the list"
            else -> p.reason ?: top.deadline?.let { dueText(it, today) } ?: "Only thing on the list"
        }
        val fit = when {
            top.durationMinutes > gap.minutes && gap.minutes == longest -> "longest gap today"
            gap.after != null -> "fits before ${gap.after.title}"
            else -> null
        }
        return if (fit == null) why else "$why, $fit"
    }

    private fun landing(gap: Gap, prefix: String): String {
        val after = gap.before?.let { ", after ${it.title}" } ?: ""
        return "$prefix ${Formats.clock(gap.start)}$after"
    }

    private fun roundUp5(t: LocalDateTime): LocalDateTime {
        val base = t.withSecond(0).withNano(0)
        val extra = (5 - base.minute % 5) % 5
        return base.plusMinutes(extra.toLong())
    }
}
