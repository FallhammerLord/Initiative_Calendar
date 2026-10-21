package initiative.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/**
 * One item on one date in the Log. [time] is when it starts, or when it was
 * done; null for a task with no set time. [projected] marks a later time of a
 * series that hasn't been made yet, shown so far-off plans are visible.
 */
data class LogEntry(val item: Item, val date: LocalDate, val time: LocalDateTime?, val projected: Boolean = false)

/**
 * The Log: every item by date, past and coming, for planning beyond today.
 * Lists stay flat: one date, then its items in time order.
 */
object Log {
    /**
     * The date an item sits on. An anchor sits on its start; a done task on the
     * day it was done; an open task on the first day it can show, and never
     * before today. Templates sit nowhere: their times stand for them.
     */
    fun dateOf(item: Item, today: LocalDate): LocalDate? = when {
        item.isTemplate -> null
        item.isMark -> item.start?.toLocalDate()
        item.isAnchor -> item.start?.toLocalDate()
        item.status == Status.DONE -> item.doneAt?.toLocalDate() ?: item.earliestStart ?: item.createdAt.toLocalDate()
        item.isOpen -> listOfNotNull(item.pinnedSlot?.toLocalDate(), item.notBefore?.toLocalDate(), item.earliestStart, today).max()
        else -> item.earliestStart ?: item.createdAt.toLocalDate()
    }

    private fun timeOf(item: Item, date: LocalDate): LocalDateTime? = when {
        item.isMark -> null
        item.isAnchor -> item.start
        item.status == Status.DONE -> item.doneAt
        else -> item.pinnedSlot?.takeIf { it.toLocalDate() == date }
    }

    /** Everything from [from] to [to], inclusive, in date and time order. */
    fun entries(items: List<Item>, from: LocalDate, to: LocalDate, today: LocalDate): List<LogEntry> {
        val real = items.flatMap { i ->
            val span = i.markDays
            // A marker shows on each day it spans.
            if (span != null && !i.isTemplate) {
                generateSequence(maxOf(span.start, from)) { it.plusDays(1) }.takeWhile { it <= minOf(span.endInclusive, to) }
                    .map { LogEntry(i, it, null) }.toList()
            } else {
                listOfNotNull(dateOf(i, today)?.takeIf { it in from..to }?.let { LogEntry(i, it, timeOf(i, it)) })
            }
        }
        // A series' times beyond those made already, from today on.
        val made = items.filter { it.templateId != null }.groupBy { it.templateId }
            .mapValues { (_, list) -> list.mapNotNull { it.occurrenceDate }.toSet() }
        val start = maxOf(from, today)
        val projected = if (start > to) {
            emptyList()
        } else {
            items.filter { it.isTemplate && it.status != Status.DROPPED }.flatMap { t ->
                t.instancesBetween(start, to)
                    .filter { it.occurrenceDate !in made[t.id].orEmpty() }
                    .mapNotNull { i -> i.occurrenceDate?.let { LogEntry(i, it, if (i.isMark) null else i.start, projected = true) } }
            }
        }
        return (real + projected).sortedWith(ORDER)
    }

    /** Each date from [from] to [to], with its entries, empty dates included. */
    fun days(items: List<Item>, from: LocalDate, to: LocalDate, today: LocalDate): List<Pair<LocalDate, List<LogEntry>>> {
        val byDate = entries(items, from, to, today).groupBy { it.date }
        return generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.map { it to byDate[it].orEmpty() }.toList()
    }

    /**
     * Items whose title, notes, or steps hold [query], done and dropped ones
     * included. Coming ones first, soonest first; then past ones, latest first.
     */
    fun search(items: List<Item>, query: String, today: LocalDate): List<LogEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val found = items
            .filter { i -> q in i.title.lowercase() || q in i.notes.lowercase() || i.steps.any { q in it.title.lowercase() } }
            .mapNotNull { i -> dateOf(i, today)?.let { LogEntry(i, it, timeOf(i, it)) } }
        val (coming, past) = found.partition { it.date >= today }
        return coming.sortedWith(ORDER) + past.sortedWith(ORDER).reversed()
    }

    /** The Monday of [date]'s week. */
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    // Timed entries first, by time; then the rest, by title.
    // Markers lead their day: context first, then the day's items.
    private val ORDER = compareBy<LogEntry>({ it.date }, { !it.item.isMark }, { it.time == null }, { it.time }, { it.item.title.lowercase() })
}
