package initiative.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Every stored time is a wall-clock time with no zone: "dentist at 3:00 PM"
 * stays at 3:00 PM wherever the phone is. This is the one place a wall-clock
 * time becomes a real moment, for alarms and countdowns.
 *
 * Daylight saving: a time in the skipped hour moves forward by the gap
 * (2:30 AM becomes 3:30 AM); a time in the repeated hour takes the first one.
 */
object WallClock {
    fun instant(t: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Instant = t.atZone(zone).toInstant()

    /** Real time between two wall-clock times, counting a daylight-saving change. */
    fun between(from: LocalDateTime, to: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Duration =
        Duration.between(instant(from, zone), instant(to, zone))
}
