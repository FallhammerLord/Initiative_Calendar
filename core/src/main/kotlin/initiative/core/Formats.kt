package initiative.core

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * The one place times, dates, durations, and wall labels are written, so the
 * board, notifications, and alarm screen always agree: "3:00 PM", "1h 40m".
 */
object Formats {
    private val CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val SHORT_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private val DAY = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)

    fun clock(t: LocalTime): String = t.format(CLOCK)
    fun clock(t: LocalDateTime): String = clock(t.toLocalTime())

    /** "1h 40m", "2h", "25m". Never negative. */
    fun countdown(d: Duration): String {
        val mins = d.toMinutes().coerceAtLeast(0)
        val h = mins / 60
        val m = mins % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    /** Real time left, so a countdown across a daylight-saving change stays right. */
    fun countdown(from: LocalDateTime, to: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): String =
        countdown(WallClock.between(from, to, zone))

    fun weekday(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
    fun shortDate(d: LocalDate): String = d.format(SHORT_DATE)

    /** "Tue, Oct 20" */
    fun day(d: LocalDate): String = d.format(DAY)

    fun wall(w: WallStrength): String = when (w) {
        WallStrength.SOFT -> "Soft"
        WallStrength.FIRM -> "Firm"
        WallStrength.HARD -> "Hard"
    }

    /** "3:00 PM · Hard", the line under an anchor's title everywhere. */
    fun anchorLine(a: Item): String = listOfNotNull(a.start?.let(::clock), wall(a.wall)).joinToString(" · ")
}
