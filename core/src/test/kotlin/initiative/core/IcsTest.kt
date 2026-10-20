package initiative.core

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IcsTest {
    private val day = LocalDate.of(2026, 10, 20)
    private fun at(h: Int, m: Int = 0) = day.atTime(h, m)
    private val eastern = ZoneId.of("America/New_York")

    @Test fun writesAnchorsOnly() {
        val items = listOf(
            Item(id = 1, title = "pickup", kind = Kind.ANCHOR, start = at(15), wall = WallStrength.HARD, createdAt = at(7)),
            Item(id = 2, title = "call landlord", kind = Kind.FLOAT, createdAt = at(7)),
            Item(id = 3, title = "gone", kind = Kind.ANCHOR, start = at(16), status = Status.DROPPED, createdAt = at(7)),
        )
        val text = Ics.calendar(items, at(3), eastern)
        assertTrue(text.startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n"))
        assertTrue(text.endsWith("END:VCALENDAR\r\n"))
        assertEquals(1, Regex("BEGIN:VEVENT").findAll(text).count())
        assertTrue("UID:initiative-1\r\n" in text)
        assertTrue("DTSTART:20261020T150000\r\n" in text)
        // No end set: the default half hour.
        assertTrue("DTEND:20261020T153000\r\n" in text)
        // 3 AM Eastern daylight time is 7 AM UTC.
        assertTrue("DTSTAMP:20261020T070000Z\r\n" in text)
        assertTrue("CATEGORIES:Hard\r\n" in text)
        assertFalse("call landlord" in text)
    }

    @Test fun escapesText() {
        assertEquals("a\\, b\\; c\\\\d\\ne", Ics.escape("a, b; c\\d\ne"))
    }

    @Test fun foldsLongLines() {
        val line = "SUMMARY:" + "x".repeat(100)
        val folded = Ics.fold(line)
        val parts = folded.split("\r\n")
        assertEquals(75, parts[0].length)
        assertTrue(parts[1].startsWith(" "))
        assertTrue(parts.all { it.toByteArray().size <= 75 })
        assertEquals(line, parts[0] + parts.drop(1).joinToString("") { it.drop(1) })
    }
}
