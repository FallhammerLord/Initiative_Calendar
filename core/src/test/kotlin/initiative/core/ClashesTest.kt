package initiative.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Double-bookings: your anchor against a shared one from someone else. */
class ClashesTest {
    private val day = LocalDate.of(2026, 10, 25)
    private fun at(h: Int, m: Int = 0) = day.atTime(h, m)
    private val now = at(12)

    private fun anchor(id: Long, title: String, h: Int, until: Int = h + 1, theirs: Boolean = false) = Item(
        id = id, title = title, kind = Kind.ANCHOR, start = at(h), end = at(until), createdAt = at(8),
        uid = if (theirs) "u-$id" else null, pairId = if (theirs) 7L else null, owned = !theirs,
    )

    private val dentist = anchor(1, "Dentist", 15)
    private val pickup = anchor(2, "School pickup", 15, theirs = true)

    @Test fun mineAgainstTheirsAtTheSameTimeClashes() {
        val clash = Clashes.find(listOf(dentist, pickup), now).single()
        assertEquals("Dentist", clash.mine.title)
        assertEquals("School pickup", clash.theirs.title)
        assertEquals(at(15), clash.at)
    }

    @Test fun touchingEndsAndTwoOfMineDoNotClash() {
        val later = anchor(3, "Later", 16, 17, theirs = true)
        val alsoMine = anchor(4, "Call", 15)
        assertTrue(Clashes.find(listOf(dentist, later, alsoMine), now).isEmpty())
    }

    @Test fun keptClearsItAndMovingMakesItNew() {
        val key = Clashes.find(listOf(dentist, pickup), now).single().key
        assertTrue(Clashes.find(listOf(dentist, pickup), now, setOf(key)).isEmpty())
        val moved = dentist.copy(start = at(15, 30), end = at(16, 30))
        val again = Clashes.find(listOf(moved, pickup), now, setOf(key)).single()
        assertNotEquals(key, again.key)
    }

    @Test fun skippedPastFarOffAndJustForThemDoNotClash() {
        val skipped = pickup.copy(status = Status.DROPPED)
        val past = anchor(5, "Breakfast", 8, theirs = true).copy(start = at(8), end = at(9))
        val farOff = pickup.copy(id = 6, start = at(15).plusDays(10), end = at(16).plusDays(10))
        val forThem = dentist.copy(id = 8, forThem = true, uid = "u-8", pairId = 7L)
        assertTrue(Clashes.find(listOf(dentist, skipped, past, farOff), now).isEmpty())
        assertTrue(Clashes.find(listOf(forThem, pickup), now).isEmpty())
    }
}
