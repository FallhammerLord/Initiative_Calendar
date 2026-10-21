package initiative.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** A day's report: what became of each item, removed ones left out, moved ones in. */
class ReportTest {
    // Wednesday.
    private val day = LocalDate.of(2026, 10, 21)
    private fun at(h: Int, m: Int = 0) = day.atTime(h, m)

    @Test fun eachItemWithWhatBecameOfIt() {
        val pickup = Item(id = 1, title = "pickup", kind = Kind.ANCHOR, start = at(14, 30), status = Status.DONE, doneAt = at(14, 40), createdAt = at(7))
        val dentist = Item(id = 2, title = "dentist", kind = Kind.ANCHOR, start = at(9), status = Status.MISSED, createdAt = at(7))
        val mail = Item(id = 3, title = "read mail", kind = Kind.FLOAT, status = Status.DONE, doneAt = at(10, 5), createdAt = at(7))
        val plants = Item(id = 4, title = "water plants", kind = Kind.FLOAT, createdAt = at(7))
        val removed = Item(id = 5, title = "typo", kind = Kind.FLOAT, status = Status.DROPPED, earliestStart = day, createdAt = at(7))
        val taxes = Item(id = 6, title = "taxes", kind = Kind.FLOAT, notBefore = day.plusDays(1).atTime(8, 0), createdAt = at(7))
        val lines = Report.of(listOf(pickup, dentist, mail, plants, removed, taxes), day, today = day, movedOn = listOf(taxes))
        assertEquals(
            listOf("dentist" to Outcome.PASSED, "read mail" to Outcome.DONE, "pickup" to Outcome.DONE, "water plants" to Outcome.OPEN, "taxes" to Outcome.MOVED),
            lines.map { it.item.title to it.outcome },
        )
    }
}
