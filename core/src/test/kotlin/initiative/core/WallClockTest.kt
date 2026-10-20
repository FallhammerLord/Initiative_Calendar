package initiative.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Daylight saving and time zones, using US Eastern: clocks jump on Mar 8 and Nov 1, 2026. */
class WallClockTest {
    private val eastern = ZoneId.of("America/New_York")
    private val pacific = ZoneId.of("America/Los_Angeles")

    private fun wall(instant: java.time.Instant, zone: ZoneId) = ZonedDateTime.ofInstant(instant, zone).toLocalDateTime()

    @Test
    fun timeInTheSkippedHourMovesForwardByTheGap() {
        val t = LocalDateTime.of(2026, 3, 8, 2, 30)
        assertEquals(LocalDateTime.of(2026, 3, 8, 3, 30), wall(WallClock.instant(t, eastern), eastern))
    }

    @Test
    fun timeInTheRepeatedHourTakesTheFirstOne() {
        val t = LocalDateTime.of(2026, 11, 1, 1, 30)
        val first = ZonedDateTime.of(t, eastern).withEarlierOffsetAtOverlap().toInstant()
        assertEquals(first, WallClock.instant(t, eastern))
    }

    @Test
    fun alarmRungsKeepTheirOrderAcrossTheSkippedHour() {
        // An anchor at 2:30 AM on the spring-forward night, warned 15 minutes before.
        val warn = WallClock.instant(LocalDateTime.of(2026, 3, 8, 2, 15), eastern)
        val start = WallClock.instant(LocalDateTime.of(2026, 3, 8, 2, 30), eastern)
        assertTrue(warn.isBefore(start))
        assertEquals(Duration.ofMinutes(15), Duration.between(warn, start))
    }

    @Test
    fun countdownCountsRealTimeAcrossSpringForward() {
        // 1:00 AM to 9:00 AM on the night the clocks jump: 7 real hours, not 8.
        val from = LocalDateTime.of(2026, 3, 8, 1, 0)
        val to = LocalDateTime.of(2026, 3, 8, 9, 0)
        assertEquals("7h 0m", Formats.countdown(from, to, eastern))
    }

    @Test
    fun countdownCountsRealTimeAcrossFallBack() {
        // 12:30 AM to 3:00 AM on the night the clocks go back: 3.5 real hours.
        val from = LocalDateTime.of(2026, 11, 1, 0, 30)
        val to = LocalDateTime.of(2026, 11, 1, 3, 0)
        assertEquals("3h 30m", Formats.countdown(from, to, eastern))
    }

    @Test
    fun ordinaryDaysAreUnchanged() {
        val from = LocalDateTime.of(2026, 10, 20, 12, 0)
        assertEquals("1h 40m", Formats.countdown(from, from.plusMinutes(100), eastern))
    }

    @Test
    fun wallTimesStayPutWhenThePhoneChangesZone() {
        // "Dentist at 3:00 PM" means 3:00 PM wherever the phone is.
        val t = LocalDateTime.of(2026, 10, 20, 15, 0)
        assertEquals(t, wall(WallClock.instant(t, eastern), eastern))
        assertEquals(t, wall(WallClock.instant(t, pacific), pacific))
        assertEquals(Duration.ofHours(3), Duration.between(WallClock.instant(t, eastern), WallClock.instant(t, pacific)))
    }
}
