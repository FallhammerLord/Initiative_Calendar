package initiative.core

import java.time.Duration
import java.time.LocalDateTime

/** How much one person shares with someone they've paired with. */
enum class ShareLevel { EVERYTHING, TICKED, NOTHING }

/** One shared entry as another phone sees it: its kind, title, and time; never notes or steps. */
data class Shared(val kind: Kind, val title: String, val start: LocalDateTime, val end: LocalDateTime)

/** A shareable entry and the item it came from, for the What they see list. */
data class Shareable(val item: Item, val shared: Shared)

/** What a peek brought back: the other person's shared days, as of [at]. Read-only. */
data class Peek(val at: LocalDateTime, val items: List<Shared>) {
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
 * and markers, for today and the next [DAYS] days; never notes or steps. An
 * item marked never share stays home whatever the level.
 */
object Sharing {
    const val DAYS = 7L

    /** Everything that could be shared, in time order, before anyone's choices. */
    fun shareable(items: List<Item>, forecast: Forecast, now: LocalDateTime): List<Shareable> {
        val today = now.toLocalDate()
        val from = today.atStartOfDay()
        val until = today.plusDays(DAYS + 1).atStartOfDay()
        val live = items.filter { !it.isTemplate && !it.neverShare && it.status != Status.DROPPED }
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
        ShareLevel.TICKED -> shareable.filter { it.item.id in ticked }.map { it.shared }
        ShareLevel.NOTHING -> emptyList()
    }
}

/**
 * A peek as text, one entry per line after a version line, so phones on
 * different builds can tell when they can't read each other.
 */
object PeekFormat {
    const val VERSION = 1
    private const val HEAD = "initiative-peek"
    private const val TAB = '\t'

    fun encode(peek: Peek): String = buildString {
        append("$HEAD $VERSION\n")
        append("at$TAB${peek.at}\n")
        peek.items.forEach { s ->
            append("${s.kind.name}$TAB${s.start}$TAB${s.end}$TAB${s.title.replace(TAB, ' ').replace('\n', ' ')}\n")
        }
    }

    /** The peek back; null when it isn't one, or comes from a newer version this build can't read. */
    fun decode(text: String): Peek? = runCatching {
        val lines = text.lines().filter { it.isNotEmpty() }
        val head = lines.first().split(' ')
        if (head.size != 2 || head[0] != HEAD || head[1].toInt() > VERSION) return null
        val at = LocalDateTime.parse(lines[1].removePrefix("at$TAB"))
        val items = lines.drop(2).map { line ->
            val (kind, start, end, title) = line.split(TAB, limit = 4)
            Shared(Kind.valueOf(kind), title, LocalDateTime.parse(start), LocalDateTime.parse(end))
        }
        Peek(at, items)
    }.getOrNull()
}
