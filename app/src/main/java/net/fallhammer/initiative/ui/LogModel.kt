package net.fallhammer.initiative.ui

import androidx.lifecycle.SavedStateHandle
import initiative.core.Item
import initiative.core.Log
import initiative.core.LogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalDateTime

/** How far the Log looks: one day, a week, a month, or a year. */
enum class Zoom { DAY, WEEK, MONTH, YEAR }

/** One month in the Year view, with its fixed times. */
data class LogMonth(val first: LocalDate, val anchors: List<LogEntry>)

/**
 * What the Log shows. [days] fills Day and Week; Month uses [days] for its grid
 * and [date] as the picked day; Year uses [months]. [results] is non-null while
 * a search is typed, and replaces the rest.
 */
data class LogView(
    val zoom: Zoom = Zoom.DAY,
    val date: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val days: List<Pair<LocalDate, List<LogEntry>>> = emptyList(),
    val months: List<LogMonth> = emptyList(),
    val results: List<LogEntry>? = null,
)

/**
 * The Log's state: open or not, the zoom, the date it's on, and the search.
 * All of it is kept in [saved], so the Log reopens where it was after a restart.
 */
class LogModel(
    private val parts: BoardParts,
    private val saved: SavedStateHandle,
    items: Flow<List<Item>>,
    clock: StateFlow<LocalDateTime>,
) {
    val open: StateFlow<Boolean> = saved.getStateFlow(OPEN, false)
    val zoom: StateFlow<Zoom> = saved.getStateFlow(ZOOM, Zoom.DAY)

    /** The date the Log is on, as an epoch day; [TODAY] for today. */
    private val day: StateFlow<Long> = saved.getStateFlow(DAY, TODAY)
    val query: StateFlow<String> = saved.getStateFlow(QUERY, "")

    val isOpen: Boolean get() = open.value

    /** Worked out off the main thread: a year of a daily series is a few hundred entries. */
    val view: StateFlow<LogView> = combine(items, clock, zoom, day, query) { all, now, z, d, q ->
        val today = now.toLocalDate()
        val date = if (d == TODAY) today else LocalDate.ofEpochDay(d)
        if (q.isNotBlank()) return@combine LogView(z, date, today, results = Log.search(all, q, today))
        when (z) {
            Zoom.DAY -> LogView(z, date, today, days = Log.days(all, date, date, today))
            Zoom.WEEK -> {
                val start = Log.weekStart(date)
                LogView(z, date, today, days = Log.days(all, start, start.plusDays(6), today))
            }
            Zoom.MONTH -> {
                val start = date.withDayOfMonth(1)
                LogView(z, date, today, days = Log.days(all, start, start.plusMonths(1).minusDays(1), today))
            }
            Zoom.YEAR -> {
                val start = date.withDayOfYear(1)
                val anchors = Log.entries(all, start, start.plusYears(1).minusDays(1), today).filter { it.item.isAnchor }
                val months = (0L until 12L).map { m ->
                    val first = start.plusMonths(m)
                    LogMonth(first, anchors.filter { it.date.month == first.month })
                }
                LogView(z, date, today, months = months)
            }
        }
    }.flowOn(Dispatchers.Default).stateIn(parts.scope, SharingStarted.WhileSubscribed(STOP_MS), LogView())

    /** Opens on today's Day view, with no search. */
    fun show() {
        saved[ZOOM] = Zoom.DAY
        saved[DAY] = TODAY
        saved[QUERY] = ""
        saved[OPEN] = true
    }

    fun close() {
        saved[OPEN] = false
    }

    fun setZoom(zoom: Zoom) {
        saved[ZOOM] = zoom
    }

    fun setQuery(text: String) {
        saved[QUERY] = text
    }

    /** One step earlier (-1) or later (1) at the current zoom. */
    fun page(step: Long) {
        val date = view.value.date
        saved[DAY] = when (zoom.value) {
            Zoom.DAY -> date.plusDays(step)
            Zoom.WEEK -> date.plusWeeks(step)
            Zoom.MONTH -> date.plusMonths(step)
            Zoom.YEAR -> date.plusYears(step)
        }.toEpochDay()
    }

    /** A day picked in the Month grid. */
    fun pick(date: LocalDate) {
        saved[DAY] = date.toEpochDay()
    }

    /** A month picked in the Year view: that month, on today if it's this month. */
    fun openMonth(first: LocalDate) {
        val today = view.value.today
        saved[DAY] = (if (first.month == today.month && first.year == today.year) today else first).toEpochDay()
        saved[ZOOM] = Zoom.MONTH
    }

    private companion object {
        const val OPEN = "log_open"
        const val ZOOM = "log_zoom"
        const val DAY = "log_day"
        const val QUERY = "log_query"
        const val TODAY = Long.MIN_VALUE
        const val STOP_MS = 5_000L
    }
}
