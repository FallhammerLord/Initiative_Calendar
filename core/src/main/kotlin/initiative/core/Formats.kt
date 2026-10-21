package initiative.core

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
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
    private val LONG_DAY = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)
    private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)
    private val ICS_LOCAL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.US)
    private val ICS_UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.US).withZone(ZoneOffset.UTC)

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

    /** "Tuesday, October 20" */
    fun longDay(d: LocalDate): String = d.format(LONG_DAY)

    /** "October 2026" */
    fun month(d: LocalDate): String = d.format(MONTH)

    /** "October" */
    fun monthName(d: LocalDate): String = d.month.getDisplayName(TextStyle.FULL, Locale.US)

    /** "M" for Monday, for a calendar's column heads. */
    fun weekdayLetter(d: DayOfWeek): String = d.getDisplayName(TextStyle.NARROW, Locale.US)

    /** "Mon" */
    fun weekdayShort(d: DayOfWeek): String = d.getDisplayName(TextStyle.SHORT, Locale.US)

    /** "Oct 19 to 25", or "Oct 26 to Nov 1" across a month's end. */
    fun span(from: LocalDate, to: LocalDate): String =
        if (from.month == to.month) "${shortDate(from)} to ${to.dayOfMonth}" else "${shortDate(from)} to ${shortDate(to)}"

    /** iCalendar floating local time: "20261020T150000". */
    fun icsLocal(t: LocalDateTime): String = t.format(ICS_LOCAL)

    /** iCalendar UTC time: "20261020T190000Z". */
    fun icsUtc(t: Instant): String = ICS_UTC.format(t)

    fun wall(w: WallStrength): String = when (w) {
        WallStrength.SOFT -> "Soft"
        WallStrength.FIRM -> "Firm"
        WallStrength.HARD -> "Hard"
    }

    /** "3:00 PM · Hard", the line under an anchor's title everywhere. */
    fun anchorLine(a: Item): String = listOfNotNull(a.start?.let(::clock), wall(a.wall)).joinToString(" · ")
}
