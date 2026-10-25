package initiative.core

import java.time.Duration
import java.time.LocalDateTime

/** How much one person shares with someone they've paired with. */
enum class ShareLevel { EVERYTHING, TICKED, NOTHING }

/** One shared entry as another phone sees it: its kind, title, and time; never notes or steps. */
data class Shared(val kind: Kind, val title: String, val start: LocalDateTime, val end: LocalDateTime)

/** A shareable entry and the item it came from, for the What they see list. */
data class Shareable(val item: Item, val shared: Shared)

/** One line of someone's day report as it travels: what it was, when, and what became of it. */
data class SharedLine(val title: String, val time: LocalDateTime?, val outcome: Outcome)

/**
 * What a peek brought back: the other person's shared days, as of [at]. Read-only.
 * [report] is their day report for [at]'s day, when they share it with you.
 * [tasks] are the shared tasks they own, and [done] which shared times they've
 * done; see [SharedTasks].
 */
data class Peek(
    val at: LocalDateTime,
    val items: List<Shared>,
    val report: List<SharedLine>? = null,
    val tasks: List<Item> = emptyList(),
    val done: Map<String, LocalDateTime> = emptyMap(),
) {
    /** What they're probably doing at [time]: the shared entry whose time covers it, the latest started first. */
    fun probably(time: LocalDateTime): Shared? =
        items.filter { it.kind != Kind.MARK && !it.start.isAfter(time) && it.end.isAfter(time) }.maxByOrNull { it.start }

    /** Fresh enough to light the pairmark: fetched within the last [LIT_MINUTES]. */
    fun lit(time: LocalDateTime): Boolean = !time.isBefore(at) && Duration.between(at, time).toMinutes() < LIT_MINUTES

    companion object {
        const val LIT_MINUTES = 15L
    }
}

/**
 * What leaves the phone. Only titles and times of anchors, today's placed tasks,
 * and markers, for today and the next [DAYS] days (a month); never notes or
 * steps. An item marked never share stays home whatever the level.
 */
object Sharing {
    const val DAYS = 31L

    /**
     * What a tick is kept against: a repeating item's series, so every time of it is
     * shared, not just the one ticked; any other item, itself.
     */
    fun tickKey(item: Item): Long = item.templateId ?: item.id

    fun isTicked(item: Item, ticked: Set<Long>): Boolean = item.id in ticked || item.templateId in ticked

    /** Everything that could be shared, in time order, before anyone's choices. */
    fun shareable(items: List<Item>, forecast: Forecast, now: LocalDateTime): List<Shareable> {
        val today = now.toLocalDate()
        val from = today.atStartOfDay()
        val until = today.plusDays(DAYS + 1).atStartOfDay()
        // A task just for someone is theirs to show, not this phone's.
        val live = items.filter { !it.isTemplate && !it.neverShare && !it.forThem && it.status != Status.DROPPED }
        val anchors = live.filter { it.isAnchor && it.start != null && !it.start.isBefore(from) && it.start.isBefore(until) }
            .map { Shareable(it, Shared(Kind.ANCHOR, it.title, it.start!!, it.anchorEnd!!)) }
        val marks = live.mapNotNull { m ->
            val days = m.markDays ?: return@mapNotNull null
            if (days.endInclusive.isBefore(today) || !days.start.isBefore(today.plusDays(DAYS + 1))) return@mapNotNull null
            Shareable(m, Shared(Kind.MARK, m.title, days.start.atStartOfDay(), days.endInclusive.plusDays(1).atStartOfDay()))
        }
        val tasks = forecast.slots.filter { !it.item.isAnchor && !it.item.neverShare }
            .map { Shareable(it.item, Shared(Kind.FLOAT, it.item.title, it.at, it.end)) }
        return (anchors + marks + tasks).sortedBy { it.shared.start }
    }

    /** What goes to one person: all of it, only what's ticked for them, or nothing. */
    fun outgoing(shareable: List<Shareable>, level: ShareLevel, ticked: Set<Long>): List<Shared> = when (level) {
        ShareLevel.EVERYTHING -> shareable.map { it.shared }
        ShareLevel.TICKED -> shareable.filter { isTicked(it.item, ticked) }.map { it.shared }
        ShareLevel.NOTHING -> emptyList()
    }

    /**
     * A day report as it goes to one person: the same items they'd see in a peek,
     * by the same level and ticks, with what became of each. Never-share items stay home.
     */
    fun report(lines: List<ReportLine>, level: ShareLevel, ticked: Set<Long>): List<SharedLine> =
        lines.filter { !it.item.neverShare }
            .filter {
                when (level) {
                    ShareLevel.EVERYTHING -> true
                    ShareLevel.TICKED -> isTicked(it.item, ticked)
                    ShareLevel.NOTHING -> false
                }
            }
            .map { SharedLine(it.item.title, it.time, it.outcome) }
}

/**
 * A peek as text, one entry per line after a version line, so phones on
 * different builds can tell when they can't read each other. From version 2, a
 * line of a kind this build doesn't know is skipped, so later additions don't
 * stop an older build reading the rest. Version 2 adds the day report and
 * shared tasks.
 */
object PeekFormat {
    const val VERSION = 2
    private const val HEAD = "initiative-peek"
    private const val TAB = '\t'
    private const val REPORT = "REPORT"
    private const val TASK = "TASK"
    private const val DONE = "DONE"
    private const val NO_TIME = "-"

    private fun clean(text: String) = text.replace(TAB, ' ').replace('\n', ' ')

    fun encode(peek: Peek): String = buildString {
        append("$HEAD $VERSION\n")
        append("at$TAB${peek.at}\n")
        peek.items.forEach { s ->
            append("${s.kind.name}$TAB${s.start}$TAB${s.end}$TAB${clean(s.title)}\n")
        }
        peek.report?.let { lines ->
            // A header line even when empty, so "shared, nothing yet" differs from "not shared".
            append("$REPORT\n")
            lines.forEach { l -> append("$REPORT$TAB${l.time ?: NO_TIME}$TAB${l.outcome.name}$TAB${clean(l.title)}\n") }
        }
        peek.tasks.forEach { append("$TASK$TAB${SharedTasks.encode(it)}\n") }
        peek.done.forEach { (key, at) -> append("$DONE$TAB${clean(key)}$TAB$at\n") }
    }

    /** The version a peek was written in; null when it isn't one. */
    fun version(text: String): Int? = text.lineSequence().firstOrNull()?.split(' ')?.takeIf { it.size == 2 && it[0] == HEAD }?.get(1)?.toIntOrNull()

    /** The peek back; null when it isn't one, or comes from a newer version this build can't read. */
    fun decode(text: String): Peek? = runCatching {
        val lines = text.lines().filter { it.isNotEmpty() }
        val head = lines.first().split(' ')
        if (head.size != 2 || head[0] != HEAD || head[1].toInt() > VERSION) return null
        val at = LocalDateTime.parse(lines[1].removePrefix("at$TAB"))
        val items = mutableListOf<Shared>()
        var report: MutableList<SharedLine>? = null
        val tasks = mutableListOf<Item>()
        val done = mutableMapOf<String, LocalDateTime>()
        lines.drop(2).forEach { line ->
            if (line.startsWith("$TASK$TAB")) {
                SharedTasks.decode(line.removePrefix("$TASK$TAB"), at)?.let { tasks += it }
                return@forEach
            }
            val parts = line.split(TAB, limit = 4)
            val kind = Kind.entries.firstOrNull { it.name == parts[0] }
            when {
                kind != null -> items += Shared(kind, parts[3], LocalDateTime.parse(parts[1]), LocalDateTime.parse(parts[2]))
                parts[0] == REPORT -> {
                    val list = report ?: mutableListOf<SharedLine>().also { report = it }
                    if (parts.size == 4) {
                        val time = parts[1].takeIf { it != NO_TIME }?.let(LocalDateTime::parse)
                        list += SharedLine(parts[3], time, Outcome.valueOf(parts[2]))
                    }
                }
                parts[0] == DONE && parts.size >= 3 -> done[parts[1]] = LocalDateTime.parse(parts[2])
                // Anything else is from a later version: skipped.
            }
        }
        Peek(at, items, report, tasks, done)
    }.getOrNull()
}
