package initiative.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Which holiday's look a day wears. */
class HolidayTest {
    private val both = Holiday.entries.toSet()

    @Test fun eachWindowIncludesBothEnds() {
        assertNull(Holiday.on(LocalDate.of(2026, 9, 30), both))
        assertEquals(Holiday.HALLOWEEN, Holiday.on(LocalDate.of(2026, 10, 1), both))
        assertEquals(Holiday.HALLOWEEN, Holiday.on(LocalDate.of(2026, 11, 1), both))
        assertNull(Holiday.on(LocalDate.of(2026, 11, 2), both))
        assertEquals(Holiday.CHRISTMAS, Holiday.on(LocalDate.of(2026, 12, 1), both))
        assertEquals(Holiday.CHRISTMAS, Holiday.on(LocalDate.of(2026, 12, 26), both))
        assertNull(Holiday.on(LocalDate.of(2026, 12, 27), both))
    }

    @Test fun onlyChosenHolidaysCount() {
        assertNull(Holiday.on(LocalDate.of(2026, 10, 31), setOf(Holiday.CHRISTMAS)))
        assertNull(Holiday.on(LocalDate.of(2026, 12, 25), emptySet()))
    }
}
