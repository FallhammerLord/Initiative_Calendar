package initiative.core

import kotlin.test.Test
import kotlin.test.assertEquals

class NamesTest {
    private val history = listOf("Dinner with Sam", "Make dinner", "Dinner", "Dentist", "Call landlord", "dishes")

    @Test fun startsWithComesFirstThenAWordInside() {
        assertEquals(listOf("Dinner with Sam", "Dinner", "Make dinner"), Names.suggest("din", history))
    }

    @Test fun newerFirstWithinTheSameKind() {
        assertEquals(listOf("Dinner with Sam", "Make dinner"), Names.suggest("dinner", history))
    }

    @Test fun insideAWordCountsAfterWordStarts() {
        assertEquals(listOf("Call landlord"), Names.suggest("lord", history))
    }

    @Test fun oneLetterOffStillFinds() {
        assertEquals(listOf("Dinner with Sam", "Make dinner", "Dinner"), Names.suggest("dinr", history))
        assertEquals(listOf("Dentist"), Names.suggest("dentsit", history))
        assertEquals(emptyList(), Names.suggest("dnr", history))
    }

    @Test fun twoLettersAtLeast() {
        assertEquals(emptyList(), Names.suggest("d", history))
        assertEquals(emptyList(), Names.suggest(" ", history))
    }

    @Test fun neverTheNameAlreadyTyped() {
        assertEquals(false, "Dinner" in Names.suggest("Dinner", history))
    }

    @Test fun closesOnceTypedPastAName() {
        assertEquals(emptyList(), Names.suggest("dinner tomorrow 6", history))
    }

    @Test fun atMostFour() {
        val many = (1..9).map { "Laundry $it" }
        assertEquals(4, Names.suggest("lau", many).size)
    }

    @Test fun caseAndSpacesDontMatter() {
        assertEquals(listOf("Call landlord"), Names.suggest("  CALL   la", history))
    }
}
