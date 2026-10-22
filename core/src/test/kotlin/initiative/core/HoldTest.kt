package initiative.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** The task just shown as Now holds its place a few minutes before On it. */
class HoldTest {
    private val day = LocalDate.of(2026, 10, 19)
    private fun at(h: Int, m: Int = 0) = day.atTime(h, m)
    private val engine = Placement()
    private val created = at(7)

    private val taxes = Item(id = 1, title = "taxes", kind = Kind.FLOAT, durationMinutes = 30, createdAt = created)
    private val urgent = Item(id = 2, title = "call school", kind = Kind.FLOAT, durationMinutes = 15, priority = Priority.HIGH, createdAt = created)
    private val hold = Hold(taxes.id, at(12))

    @Test fun aNewTaskWaitsWhileTheHoldLasts() {
        val b = engine.board(listOf(taxes, urgent), at(12, 4), hold = hold)
        assertEquals(taxes.id, b.now.item?.id)
        assertEquals(urgent.id, b.next?.id)
        assertEquals(taxes.id, engine.forecast(listOf(taxes, urgent), at(12, 4), hold = hold).slots.first().item.id)
    }

    @Test fun afterTheHoldTheRulesLeadAgain() {
        assertEquals(urgent.id, engine.board(listOf(taxes, urgent), at(12, 5), hold = hold).now.item?.id)
        assertEquals(urgent.id, engine.board(listOf(taxes, urgent), at(12, 4)).now.item?.id)
    }

    @Test fun aHoldNeverKeepsAnAppointmentOut() {
        val pickup = Item(id = 3, title = "pickup", kind = Kind.ANCHOR, start = at(12, 2), end = at(12, 30), wall = WallStrength.HARD, createdAt = created)
        assertEquals(pickup.id, engine.board(listOf(taxes, urgent, pickup), at(12, 3), hold = hold).now.item?.id)
    }

    @Test fun aTaskOnItOutranksTheHold() {
        // Done, then Undo: the task On it comes back as Now, past the one held meanwhile.
        // Equal rank, but older: the rules pick it, as they did before Done.
        val onIt = taxes.copy(id = 4, createdAt = at(6), status = Status.ACTIVE, activeSince = at(11, 50))
        assertEquals(onIt.id, engine.board(listOf(taxes, onIt), at(12, 1), hold = hold).now.item?.id)
    }

    @Test fun aWaitComingBackWaitsForTheHold() {
        // A tap on its waitmark ends the wait now; the board then drops the hold, since the person chose.
        val back = Item(id = 5, title = "laundry", kind = Kind.FLOAT, durationMinutes = 15, waitUntil = at(12, 1), createdAt = created)
        assertEquals(taxes.id, engine.board(listOf(taxes, back), at(12, 2), hold = hold).now.item?.id)
        assertEquals(back.id, engine.board(listOf(taxes, back), at(12, 2)).now.item?.id)
    }
}
