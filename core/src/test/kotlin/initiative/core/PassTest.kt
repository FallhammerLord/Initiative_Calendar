package initiative.core

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The scheduler's decisions, without Android. */
class PassTest {
    // Monday.
    private val day = LocalDate.of(2026, 10, 19)
    private fun at(h: Int, m: Int = 0) = day.atTime(h, m)
    private val placement = Placement()
    private fun anchor(id: Long, h: Int, wall: WallStrength = WallStrength.FIRM) =
        Item(id = id, title = "a$id", kind = Kind.ANCHOR, start = at(h), wall = wall, createdAt = at(7))
    private fun float(id: Long) = Item(id = id, title = "f$id", kind = Kind.FLOAT, createdAt = at(7))

    @Test fun firesTheDueRungAndWakesForTheNext() {
        val plan = Pass.plan(PassInput(listOf(anchor(1, 15)), emptyMap(), at(14, 45)), placement)
        assertEquals(listOf(Rung.NOTICE), plan.fired.map { it.rung })
        assertEquals(1, plan.fired.single().after.lastRung)
        assertEquals(at(14, 55), plan.wake)
        assertFalse(plan.wakeIsAlarm)
    }

    @Test fun theFullAlarmGetsTheAlarmClock() {
        val a = anchor(1, 15)
        val plan = Pass.plan(PassInput(listOf(a), mapOf(1L to AlarmState(lastRung = 2)), at(14, 56)), placement)
        assertEquals(at(15), plan.wake)
        assertTrue(plan.wakeIsAlarm)
    }

    @Test fun anEndedAnchorSettlesAndItsNoticeClears() {
        val missed = Pass.plan(PassInput(listOf(anchor(1, 9)), mapOf(1L to AlarmState(lastRung = 3)), at(10)), placement)
        assertEquals(Status.MISSED, missed.ended.single().second.status)
        assertEquals(listOf(1L), missed.quieted)

        val acked = Pass.plan(PassInput(listOf(anchor(1, 9)), mapOf(1L to AlarmState(ackedAt = at(9), lastRung = 3)), at(10)), placement)
        assertEquals(Status.DONE, acked.ended.single().second.status)
    }

    @Test fun quietHoursSilenceFirmAndWakeWhenTheyEnd() {
        val quiet = QuietHours(LocalTime.of(14, 0), LocalTime.of(16, 0))
        val plan = Pass.plan(PassInput(listOf(anchor(1, 15)), emptyMap(), at(14, 45), quiet = quiet), placement)
        assertTrue(plan.fired.isEmpty())
        val hard = Pass.plan(PassInput(listOf(anchor(1, 15, WallStrength.HARD)), emptyMap(), at(14, 45), quiet = quiet), placement)
        assertEquals(listOf(Rung.NOTICE), hard.fired.map { it.rung })
    }

    @Test fun aFloatLeftUntouchedIsSkippedButNotDuringFocus() {
        val f = float(1)
        val shown = Shown(1, at(12), 0, null)
        val plan = Pass.plan(PassInput(listOf(f), emptyMap(), at(12), shown = shown), placement)
        assertEquals(1, plan.autoSkip?.second?.skipCount)

        val focusing = Pass.plan(PassInput(listOf(f), emptyMap(), at(12), shown = shown, focus = FocusSession(1, at(13))), placement)
        assertNull(focusing.autoSkip)
        assertEquals(f, focusing.focusItem)
    }

    @Test fun remembersTheFloatShownAsNow() {
        val plan = Pass.plan(PassInput(listOf(anchor(1, 15), float(2)), emptyMap(), at(12)), placement)
        assertEquals(2L, plan.shown?.itemId)
        assertEquals(at(14, 45), plan.shown?.until)
    }

    @Test fun nightlyBackupOncePerDayAfterThree() {
        assertFalse(Pass.plan(PassInput(emptyList(), emptyMap(), at(2), backupFolderSet = true), placement).backupDue)
        assertTrue(Pass.plan(PassInput(emptyList(), emptyMap(), at(3), backupFolderSet = true), placement).backupDue)
        assertFalse(Pass.plan(PassInput(emptyList(), emptyMap(), at(9), backupFolderSet = true, lastBackup = at(3)), placement).backupDue)
        assertFalse(Pass.plan(PassInput(emptyList(), emptyMap(), at(9)), placement).backupDue)
        // Before 3 AM with nothing else on, the next wake is the backup.
        assertEquals(at(3), Pass.plan(PassInput(emptyList(), emptyMap(), at(2), backupFolderSet = true), placement).wake)
    }
}
