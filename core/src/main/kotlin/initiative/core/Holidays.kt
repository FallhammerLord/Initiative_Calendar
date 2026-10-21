package initiative.core

import java.time.LocalDate
import java.time.MonthDay

/**
 * Holidays with a look of their own, each worn over a window of days when the
 * person turns holiday looks on. Off by default: nothing changes unasked.
 */
enum class Holiday(private val from: MonthDay, private val until: MonthDay) {
    HALLOWEEN(MonthDay.of(10, 1), MonthDay.of(11, 1)),
    CHRISTMAS(MonthDay.of(12, 1), MonthDay.of(12, 26)),
    ;

    /** Whether [date] falls in this holiday's window, both ends included. */
    fun covers(date: LocalDate): Boolean {
        val day = MonthDay.from(date)
        return !day.isBefore(from) && !day.isAfter(until)
    }

    companion object {
        /** The holiday whose look [date] wears, among the ones [chosen]; null for none. */
        fun on(date: LocalDate, chosen: Set<Holiday>): Holiday? = entries.firstOrNull { it in chosen && it.covers(date) }
    }
}
