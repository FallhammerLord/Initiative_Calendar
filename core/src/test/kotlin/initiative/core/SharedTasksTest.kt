package initiative.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Shared tasks: the owner's copy wins, each side's doing stays its own, and both boxes travel. */
class SharedTasksTest {
    private val day = LocalDate.of(2026, 10, 25)
    private fun at(h: Int, m: Int = 0) = day.atTime(h, m)
    private val air = 7L

    // Air's trash task, as Air's phone holds it, and as it arrives here.
    private val trash = Item(
        id = 1, title = "Take out trash", kind = Kind.FLOAT, uid = "u-trash", pairId = air, owned = true,
        steps = listOf(Step("bins"), Step("recycling")), createdAt = at(8),
    )
    private fun arrived(local: List<Item>, sent: List<Item>, done: Map<String, java.time.LocalDateTime> = emptyMap()) =
        SharedTasks.apply(local, air, SharedTasks.Out(sent, done), day, at(12))

    @Test fun aTaskTravelsWhole() {
        val odd = trash.copy(title = "odd\ttitle\nhere", notes = "back\\slash", recurrence = Recurrence(Freq.WEEKLY, 2, setOf(java.time.DayOfWeek.MONDAY)))
        val back = assertNotNull(SharedTasks.decode(SharedTasks.encode(odd), at(12)))
        assertEquals(odd.title, back.title)
        assertEquals(odd.notes, back.notes)
        assertEquals(odd.recurrence, back.recurrence)
        assertEquals(listOf("bins", "recycling"), back.steps.map { it.title })
        assertFalse(back.owned)
        val peek = Peek(at(12), emptyList(), null, listOf(trash), mapOf("u-trash" to at(11)))
        val read = assertNotNull(PeekFormat.decode(PeekFormat.encode(peek)))
        assertEquals(listOf("Take out trash"), read.tasks.map { it.title })
        assertEquals(mapOf("u-trash" to at(11)), read.done)
    }

    @Test fun aNewTaskArrivesAsTheirs() {
        val changes = arrived(emptyList(), listOf(trash.copy(id = 0, owned = false)))
        val here = changes.upserts.single()
        assertEquals(0L, here.id)
        assertEquals(air, here.pairId)
        assertTrue(here.fromThem)
        assertEquals(Status.PENDING, here.status)
    }

    @Test fun theOwnerWinsButMyDoingStays() {
        // Here: on it, the first step ticked, moved to later.
        val mine = trash.copy(id = 20, owned = false, status = Status.ACTIVE, activeSince = at(9),
            steps = listOf(Step("bins", done = true), Step("recycling")), notBefore = at(13))
        val changed = trash.copy(title = "Trash night", steps = listOf(Step("bins"), Step("recycling"), Step("compost")))
        val after = arrived(listOf(mine), listOf(changed)).upserts.single()
        assertEquals("Trash night", after.title)
        assertEquals(listOf(true, false, false), after.steps.map { it.done })
        assertEquals(Status.ACTIVE, after.status)
        assertEquals(at(13), after.notBefore)
        assertEquals(20L, after.id)
    }

    @Test fun theirBoxComesFromWhatTheyveDone() {
        val mine = trash.copy(id = 20, owned = false)
        val after = arrived(listOf(mine), listOf(trash), mapOf("u-trash" to at(11))).upserts.single()
        assertTrue(after.theirDone)
        // And it unticks if they put it back.
        assertFalse(arrived(listOf(after), listOf(trash)).upserts.single().theirDone)
        // Mine travels to them the same way.
        val done = trash.copy(id = 21, owned = true, status = Status.DONE, doneAt = at(10))
        assertEquals(mapOf("u-trash" to at(10)), SharedTasks.doneHere(listOf(done), air, day))
    }

    @Test fun removedByTheOwnerGoesHereButHistoryStays() {
        val open = trash.copy(id = 20, owned = false)
        assertEquals(listOf(20L), arrived(listOf(open), emptyList()).removals.map { it.id })
        val finished = open.copy(status = Status.DONE, doneAt = at(9))
        assertTrue(arrived(listOf(finished), emptyList()).removals.isEmpty())
    }

    @Test fun aSeriesChangeReachesItsTimesToCome() {
        // Started three days ago, so it has a yesterday.
        val series = Item(id = 30, title = "School pickup", kind = Kind.ANCHOR, start = at(15).minusDays(3), end = at(15, 30).minusDays(3),
            recurrence = Recurrence(Freq.DAILY), uid = "u-pickup", pairId = air, owned = false, createdAt = at(7))
        val tomorrow = series.instancesBetween(day.plusDays(1), day.plusDays(1)).single().copy(id = 31)
        val yesterday = series.instancesBetween(day.minusDays(1), day.minusDays(1)).single().copy(id = 29, status = Status.DONE)
        val moved = series.copy(id = 0, owned = true, start = at(15, 15).minusDays(3), end = at(15, 45).minusDays(3))
        val changes = arrived(listOf(series, tomorrow, yesterday), listOf(moved))
        val time = changes.upserts.single { it.id == 31L }
        assertEquals(day.plusDays(1).atTime(15, 15), time.start)
        assertFalse(changes.upserts.any { it.id == 29L && it.start != yesterday.start })
        // Each time of a series has its own key, so each day's boxes are their own.
        assertEquals("u-pickup@${day.plusDays(1)}", time.shareKey)
    }

    @Test fun onlyTheOwnersSeriesAndOneOffsAreSent() {
        val series = Item(id = 30, title = "School pickup", kind = Kind.ANCHOR, start = at(15), recurrence = Recurrence(Freq.DAILY),
            uid = "u-pickup", pairId = air, createdAt = at(7))
        val time = series.instancesBetween(day, day).single().copy(id = 31)
        val theirs = trash.copy(id = 40, owned = false)
        val farOff = trash.copy(id = 41, uid = "u-far", earliestStart = day.plusDays(60))
        assertEquals(listOf(30L), SharedTasks.owned(listOf(series, time, theirs, farOff), air, day).map { it.id })
    }

    @Test fun unpairingStopsSharingAndLeavesTheirsToClaim() {
        val mineShared = trash.copy(id = 1)
        val series = Item(id = 30, title = "School pickup", kind = Kind.ANCHOR, start = at(15), recurrence = Recurrence(Freq.DAILY),
            uid = "u-pickup", pairId = air, owned = false, createdAt = at(7))
        val today = series.instancesBetween(day, day).single().copy(id = 31)
        val later = series.instancesBetween(day.plusDays(2), day.plusDays(2)).single().copy(id = 32)
        val changes = SharedTasks.unpair(listOf(mineShared, series, today, later), air, day)
        val byId = changes.upserts.associateBy { it.id }
        assertEquals(null, byId.getValue(1L).uid)
        assertTrue(byId.getValue(1L).owned)
        assertEquals(Status.DROPPED, byId.getValue(30L).status)
        assertEquals(listOf(32L), changes.removals.map { it.id })
        val left = byId.getValue(31L)
        assertTrue(SharedTasks.claimable(left))
        val claimed = SharedTasks.claim(left)
        assertTrue(claimed.owned)
        assertFalse(claimed.isShared)
    }
}
