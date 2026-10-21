package initiative.core

import java.time.LocalDate
import java.time.LocalDateTime

/** What became of one item on the report's day. */
enum class Outcome {
    /** Done, at [ReportLine.time]. */
    DONE,

    /** A fixed time that went by without Done. */
    PASSED,

    /** Moved to tomorrow from this day. */
    MOVED,

    /** Still open. */
    OPEN,
}

/** One line of a day's report. [time] is when it starts or was done; null for a task with no set time. */
data class ReportLine(val item: Item, val time: LocalDateTime?, val outcome: Outcome)

/**
 * A day's report, to keep or to share: the day's items in time order, each with
 * what became of it. Plain facts, no counts or scores. Removed items stay out;
 * removing means it wasn't wanted. Later times of a series that aren't made yet
 * stay out too: a report is of what's real.
 */
object Report {
    /**
     * [movedOn] holds the items moved to tomorrow on [date], from the event log:
     * they now sit on a later day, so the Log alone wouldn't show them here.
     */
    fun of(items: List<Item>, date: LocalDate, today: LocalDate, movedOn: List<Item> = emptyList()): List<ReportLine> {
        val lines = Log.entries(items, date, date, today)
            .filter { !it.projected && it.item.status != Status.DROPPED && !it.item.isMark }
            .map { e ->
                val outcome = when (e.item.status) {
                    Status.DONE -> Outcome.DONE
                    Status.MISSED -> Outcome.PASSED
                    else -> Outcome.OPEN
                }
                ReportLine(e.item, e.time, outcome)
            }
        val listed = lines.map { it.item.id }.toSet()
        val moved = movedOn.filter { it.id !in listed && it.status != Status.DROPPED }.map { ReportLine(it, null, Outcome.MOVED) }
        return lines + moved
    }

    /** The markers on [date], made or still to be made from a series: "Today: Halloween". */
    fun marks(items: List<Item>, date: LocalDate, today: LocalDate): List<Item> =
        Log.entries(items, date, date, today).filter { it.item.isMark && it.item.status != Status.DROPPED }.map { it.item }
}

/**
 * Markers for the board's HUD on [today]: those in effect, and those coming up
 * within their heads-up days (dim on the board). In effect first, then soonest.
 */
object Marks {
    data class Shown(val item: Item, val ahead: Int)

    fun on(items: List<Item>, today: LocalDate): List<Shown> {
        val most = items.filter { it.isMark && it.status != Status.DROPPED }.maxOfOrNull { it.headsUpDays } ?: 0
        return Log.entries(items, today, today.plusDays(most.toLong()), today)
            .filter { it.item.isMark && it.item.status != Status.DROPPED }
            .groupBy { it.item.id.takeIf { id -> id != 0L } ?: (it.item.templateId ?: 0L) * 100_000 + it.date.toEpochDay() }
            .mapNotNull { (_, entries) ->
                val first = entries.minOf { it.date }
                val ahead = java.time.temporal.ChronoUnit.DAYS.between(today, first).toInt()
                val item = entries.first().item
                if (ahead == 0 || ahead <= item.headsUpDays) Shown(item, ahead) else null
            }
            .sortedWith(compareBy({ it.ahead }, { it.item.title.lowercase() }))
    }
}
