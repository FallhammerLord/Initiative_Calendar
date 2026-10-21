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

/** [until] is when a float's window as Now closes: the end of its gap. Null for anchors and Free time. */
/** What the board shows: Now, what comes next, and, for a task that's Next, when it's set to start. */
data class Board(
    val now: NowPick,
    val next: Item?,
    val until: LocalDateTime? = null,
    val nextAt: LocalDateTime? = null,
    /** Items stepped aside for a wait, soonest first: the waitmarks on the Now card. */
    val waits: List<Item> = emptyList(),
)

enum class MarkKind { DONE, SOFT, FIRM, HARD }

data class LedgerMark(val at: LocalDateTime, val kind: MarkKind, val item: Item)

/** One entry in today's forecast: an anchor at its start, or a task where it should begin. */
data class Slot(val item: Item, val at: LocalDateTime) {
    /** When this entry's time is over: an anchor's end, or a task's start plus its length. */
    val end: LocalDateTime get() = if (item.isAnchor) item.anchorEnd!! else at.plusMinutes(item.durationMinutes.toLong())
}

/** The rest of today in order, and the tasks that don't fit today ([later]). */
data class Forecast(val slots: List<Slot>, val later: List<Item>)

/** Result of Skip or Tomorrow: the updated item and where it landed. */
data class Moved(val item: Item, val message: String)

/**
 * Picks the Now card with a fixed, explainable rule. See docs/spec.md, Placement logic.
 */
class Placement(private val window: DayWindow = DayWindow()) {
    private companion object {
        const val FOCUS_FALLBACK_HOURS = 2L
    }

    /** [order] is the person's own task order, by id: a tie breaker when the rules rank tasks equally. */
    /**
     * Now, and Next. Next is the next thing in the day's order, task or anchor, the
     * same as the day panel shows; the walls stay in view on the dial. With nothing
     * left in today's order, Next is the next anchor, if there is one.
     */
    fun board(items: List<Item>, now: LocalDateTime, order: List<Long> = emptyList()): Board {
        var b = pick(items, now, order)
        // A task back from a wait says so, rather than why it ranks.
        b.now.item?.takeIf { it.ready(now) }?.let { back ->
            b = b.copy(now = NowPick(back, "Ready since ${Formats.clock(back.waitUntil!!)}"))
        }
        val waits = items.filter { !it.isTemplate && it.isOpen && it.waitUntil != null && it.id != b.now.item?.id }.sortedBy { it.waitUntil }
        b = b.copy(waits = waits)
        val slot = forecast(items, now, order).slots.firstOrNull { it.item.id != b.now.item?.id } ?: return b
        return b.copy(next = slot.item, nextAt = slot.at.takeIf { !slot.item.isAnchor })
    }

    private fun pick(items: List<Item>, now: LocalDateTime, order: List<Long>): Board {
        val today = now.toLocalDate()
        val live = items.filter { !it.isTemplate }
        val upcoming = openAnchors(live).filter { it.start!!.isAfter(now) }

        openAnchors(live).firstOrNull { !it.start!!.isAfter(now) && now.isBefore(it.anchorEnd!!) }?.let {
            return Board(NowPick(it, "Happening now"), upcoming.firstOrNull())
        }

        // An anchor marked Done on arrival still holds its time: it stays Now until it ends.
        live.firstOrNull {
            it.isAnchor && it.status == Status.DONE && it.start != null && !it.start.isAfter(now) && now.isBefore(it.anchorEnd!!)
        }?.let {
            return Board(NowPick(it, "Done · under way until ${Formats.clock(it.anchorEnd!!)}"), upcoming.firstOrNull())
        }

        upcoming.firstOrNull()?.takeIf { !now.isBefore(it.warnAt!!) }?.let {
            val mins = Duration.between(now, it.start!!).toMinutes().coerceAtLeast(1)
            return Board(NowPick(it, "Starts in $mins min"), upcoming.getOrNull(1))
        }

        val nextAnchor = upcoming.firstOrNull()
        val floats = live.filter { workable(it, now) }

        floats.firstOrNull { f ->
            val slot = f.pinnedSlot ?: return@firstOrNull false
            !now.isBefore(slot) && now.isBefore(slot.plusMinutes(f.durationMinutes.toLong()))
        }?.let { pin ->
            val after = rank(eligible(floats - pin, now), today, order).firstOrNull()
            return Board(NowPick(pin, "Pinned to this slot"), nextAnchor ?: after)
        }

        if (now.toLocalTime() !in window) {
            val why = if (now.toLocalTime().isBefore(window.start)) "Day starts at ${Formats.clock(window.start)}" else "Day's done"
            return Board(NowPick(null, why), nextAnchor)
        }

        // The forecast leads, so the Now card and the day panel always agree.
        val forecast = forecast(items, now, order)
        val tasks = forecast.slots.filter { !it.item.isAnchor }
        val upNext = tasks.firstOrNull()

        val gaps = gaps(today, live, now)
        val current = gaps.firstOrNull { now in it } ?: return Board(NowPick(null, upNextReason(upNext) ?: "Nothing fits right now"), nextAnchor)
        val longest = gaps.maxOf { it.minutes }

        if (upNext != null && !upNext.at.isAfter(now)) {
            val second = tasks.getOrNull(1)?.item
            // A task's window closes at the gap's end, or sooner at a pinned task's start.
            val until = (listOf(current.end) + tasks.filter { it.item.pinnedSlot != null && it.at.isAfter(now) }.map { it.at }).min()
            return Board(NowPick(upNext.item, reasonFor(upNext.item, second, current, longest, today, alone = floats.size == 1)), nextAnchor ?: second, until = until)
        }

        val ready = eligible(floats, now)
        val ranked = rank(ready.filter { fits(it, current, longest) }, today, order)
        val top = ranked.firstOrNull()
            ?: return Board(NowPick(null, upNextReason(upNext) ?: idleReason(floats, ready, current, now)), nextAnchor ?: upNext?.item)

        val reason = reasonFor(top, ranked.getOrNull(1), current, longest, today, alone = floats.size == 1)
        return Board(NowPick(top, reason), nextAnchor ?: ranked.getOrNull(1), until = current.end)
    }

    /** Today's remaining walls and open floats in board order, for the expanded ledger. */
    fun dayQueue(items: List<Item>, now: LocalDateTime, order: List<Long> = emptyList()): List<Item> {
        val today = now.toLocalDate()
        val live = items.filter { !it.isTemplate }
        val anchors = openAnchors(live).filter { it.start!!.toLocalDate() == today && it.anchorEnd!!.isAfter(now) }
        val floats = rank(
            eligible(live.filter { workable(it, now) }, now.toLocalDate().atTime(window.end).minusMinutes(1)),
            today,
            order,
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
     * The rest of today, in order: anchors at their starts, tasks pinned to a time,
     * and the other tasks filling the free time between, best-ranked first. A task
     * can't start before it's back from a skip; when nothing ready fits, the
     * forecast waits for the next one coming back. Tasks that can't fit go in
     * [Forecast.later]. A forecast, not a promise: it reshuffles as the day goes.
     */
    fun forecast(items: List<Item>, now: LocalDateTime, order: List<Long> = emptyList()): Forecast {
        val today = now.toLocalDate()
        val live = items.filter { !it.isTemplate }
        val anchors = openAnchors(live).filter { it.start!!.toLocalDate() == today && it.anchorEnd!!.isAfter(now) }
        val floats = live.filter { workable(it, now) && (it.earliestStart == null || !it.earliestStart.isAfter(today)) }
        val pinned = floats.filter { pinHolds(it, now) && it.pinnedSlot!!.toLocalDate() == today }
        val pool = rank(floats - pinned.toSet(), today, order).toMutableList()
        val placed = mutableListOf<Slot>()
        for ((start, end) in freeTimes(today, live, now, pinned)) {
            var cursor = start
            while (true) {
                val room = Duration.between(cursor, end).toMinutes()
                val pick = pool.firstOrNull { (it.notBefore == null || !it.notBefore.isAfter(cursor)) && it.durationMinutes <= room }
                if (pick != null) {
                    placed += Slot(pick, cursor)
                    pool.remove(pick)
                    cursor = cursor.plusMinutes(pick.durationMinutes.toLong())
                    continue
                }
                // Nothing ready fits yet: wait for the next task back from a skip, if it would fit.
                cursor = pool.mapNotNull { f ->
                    f.notBefore?.takeIf { it.isAfter(cursor) && !it.plusMinutes(f.durationMinutes.toLong()).isAfter(end) }
                }.minOrNull() ?: break
            }
        }
        val slots = (anchors.map { Slot(it, it.start!!) } + pinned.map { Slot(it, it.pinnedSlot!!) } + placed).sortedBy { it.at }
        return Forecast(slots, pool)
    }

    /**
     * Whether [item] can be pinned to start at [at] today: it must fit in free
     * time, clear of every anchor's warning and time and of other pinned tasks.
     */
    fun canPin(item: Item, items: List<Item>, at: LocalDateTime, now: LocalDateTime): Boolean {
        if (item.isAnchor || at.toLocalDate() != now.toLocalDate()) return false
        val others = items.filter { !it.isTemplate && it.id != item.id }
        val pinned = others.filter { !it.isAnchor && it.isOpen && pinHolds(it, now) }
        val end = at.plusMinutes(item.durationMinutes.toLong())
        return freeTimes(now.toLocalDate(), others, now.withSecond(0).withNano(0), pinned).any { (s, e) -> !at.isBefore(s) && !end.isAfter(e) }
    }

    /**
     * The time a task dropped at [index] in [slots] would start: right after the
     * entry before it, or now when dropped first.
     */
    fun dropTime(slots: List<Slot>, index: Int, now: LocalDateTime): LocalDateTime {
        // Dropped first: it starts this minute, so it's Now straight away.
        val before = slots.getOrNull(index - 1) ?: return now.withSecond(0).withNano(0)
        return wholeMinute(maxOf(before.end, now))
    }

    // Up to the next whole minute, so the entry before keeps all of its time.
    private fun wholeMinute(t: LocalDateTime): LocalDateTime {
        val base = t.withSecond(0).withNano(0)
        return if (base == t) base else base.plusMinutes(1)
    }

    /**
     * Free time on [date] from [from], as (start, end) pairs: the day window, less
     * each anchor's warning-to-end (including ones marked Done, which still hold
     * their time) and each pinned task's slot.
     */
    private fun freeTimes(date: LocalDate, items: List<Item>, from: LocalDateTime, pinned: List<Item>): List<Pair<LocalDateTime, LocalDateTime>> {
        val dayStart = maxOf(date.atTime(window.start), from)
        val dayEnd = date.atTime(window.end)
        val blocks = items
            .filter { it.isAnchor && it.start != null && it.status != Status.DROPPED && it.start.toLocalDate() == date }
            .map { it.warnAt!! to it.anchorEnd!! } +
            pinned.map { it.pinnedSlot!! to it.pinnedSlot.plusMinutes(it.durationMinutes.toLong()) }
        val out = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
        var cursor = dayStart
        for ((s, e) in blocks.sortedBy { it.first }) {
            if (s.isAfter(cursor)) out += cursor to minOf(s, dayEnd)
            if (e.isAfter(cursor)) cursor = e
        }
        if (dayEnd.isAfter(cursor)) out += cursor to dayEnd
        return out.filter { (s, e) -> e.isAfter(s) }
    }

    /** A pin holds until its slot is over; after that the task is free to be placed again. */
    private fun pinHolds(f: Item, now: LocalDateTime): Boolean {
        val slot = f.pinnedSlot ?: return false
        return now.isBefore(slot.plusMinutes(f.durationMinutes.toLong()))
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
        live.filter { !it.isAnchor || it.waitUntil != null }.forEach { f ->
            f.notBefore?.let { times += it }
            f.waitUntil?.let { times += it }
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

        val skipped = item.copy(skipCount = item.skipCount + 1, status = Status.PENDING, pinnedSlot = null, activeSince = null, waitUntil = null)
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
            item.copy(notBefore = now.toLocalDate().plusDays(1).atTime(window.start), status = Status.PENDING, pinnedSlot = null, activeSince = null, waitUntil = null),
            "Back tomorrow",
        )

    /** What can be worked like a task: an open float, or an anchor running over with a wait. */
    private fun workable(i: Item, now: LocalDateTime): Boolean = i.isOpen && (!i.isAnchor || i.overrunning(now))

    private fun openAnchors(items: List<Item>) =
        items.filter { it.isAnchor && it.isOpen && it.start != null }.sortedBy { it.start }

    private fun eligible(floats: List<Item>, now: LocalDateTime): List<Item> {
        val today = now.toLocalDate()
        return floats.filter { f ->
            (f.pinnedSlot == null || !pinHolds(f, now)) &&
                (f.earliestStart == null || !f.earliestStart.isAfter(today)) &&
                (f.notBefore == null || !f.notBefore.isAfter(now))
        }
    }

    // A float longer than every gap today may take the longest one.
    private fun fits(f: Item, current: Gap, longest: Long): Boolean {
        val need = f.durationMinutes.toLong()
        return need <= current.minutes || (need > longest && current.minutes == longest)
    }

    private fun rank(floats: List<Item>, today: LocalDate, order: List<Long> = emptyList()): List<Item> {
        // The person's own order breaks ties; tasks they haven't placed come after, oldest first.
        val place = order.withIndex().associate { (i, id) -> id to i }
        // A task back from a wait comes first; but while a task is under way, that one
        // stays first, and the waiting task is next: it never takes over by itself.
        // Under way: the task most recently picked up, if it isn't one back from a wait.
        val waits = floats.any { it.waitUntil != null }
        val underWay = if (waits) floats.filter { it.activeSince != null && it.waitUntil == null }.maxByOrNull { it.activeSince!! }?.id else null
        return floats.sortedWith(
            compareBy<Item> { it.id != underWay }
                .thenBy { it.waitUntil == null }
                .thenBy(nullsLast()) { it.waitUntil }
                .thenByDescending { it.resolvePriority(today).level }
                .thenBy(nullsLast()) { it.deadline }
                .thenBy { place[it.id] ?: Int.MAX_VALUE }
                .thenBy { it.createdAt },
        )
    }

    /** A task coming later today: "Up next at 7:30 PM: clean upstairs". */
    private fun upNextReason(next: Slot?): String? = next?.let { "Up next at ${Formats.clock(it.at)}: ${it.item.title}" }

    /**
     * Why Now is Free time. "Nothing fits" only when a task could be shown but
     * none fits this gap; otherwise when the next one is back, or that nothing
     * else is due today.
     */
    private fun idleReason(floats: List<Item>, ready: List<Item>, current: Gap, now: LocalDateTime): String {
        if (floats.isEmpty()) return "Nothing needs doing"
        if (ready.isNotEmpty()) return "Nothing fits before ${current.after?.title ?: "the day ends"}"
        val today = now.toLocalDate()
        val back = floats
            .filter { f -> !pinHolds(f, now) && (f.earliestStart == null || !f.earliestStart.isAfter(today)) }
            .mapNotNull { f -> f.notBefore?.takeIf { it.isAfter(now) && it.toLocalDate() == today }?.let { f to it } }
            .minByOrNull { it.second }
        return back?.let { (f, at) -> "Back at ${Formats.clock(at)}: ${f.title}" } ?: "Nothing else is due today"
    }

    /** [alone]: no other open task at all, not just none that fits or is back yet. */
    private fun reasonFor(top: Item, second: Item?, gap: Gap, longest: Long, today: LocalDate, alone: Boolean): String {
        val p = top.resolvePriority(today)
        val q = second?.resolvePriority(today)
        val why = when {
            q != null && p.level > q.level -> p.reason ?: "Higher priority"
            q != null && top.deadline != null && top.deadline != second?.deadline -> dueText(top.deadline, today)
            q != null -> p.reason ?: "Oldest on the list"
            else -> p.reason ?: top.deadline?.let { dueText(it, today) } ?: if (alone) "Only thing on the list" else "First on the list"
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
