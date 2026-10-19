package initiative.core

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EscalationTest {
    private val day = LocalDate.of(2026, 10, 19)
    private fun at(h: Int, m: Int = 0) = day.atTime(h, m)
    private fun anchor(wall: WallStrength, lead: Int = 15) =
        Item(id = 1, title = "pickup", kind = Kind.ANCHOR, start = at(15), wall = wall, leadMinutes = lead, createdAt = at(7))

    @Test fun ladderTimesAndCeilings() {
        assertEquals(
            listOf(RungTime(Rung.NOTICE, at(14, 45)), RungTime(Rung.NUDGE, at(14, 55)), RungTime(Rung.ALARM, at(15))),
            Escalation.ladder(anchor(WallStrength.HARD)),
        )
        assertEquals(listOf(Rung.NOTICE, Rung.NUDGE), Escalation.ladder(anchor(WallStrength.SOFT)).map { it.rung })
    }

    @Test fun shortLeadMergesNoticeIntoNudge() {
        assertEquals(
            listOf(RungTime(Rung.NUDGE, at(14, 57)), RungTime(Rung.ALARM, at(15))),
            Escalation.ladder(anchor(WallStrength.FIRM, lead = 3)),
        )
    }

    @Test fun climbsOneRungAtATime() {
        val a = anchor(WallStrength.FIRM)
        var s = AlarmState()
        assertNull(Escalation.due(a, s, at(14, 40)))
        assertEquals(Rung.NOTICE, Escalation.due(a, s, at(14, 45)))
        s = Escalation.fired(s, Rung.NOTICE)
        assertNull(Escalation.due(a, s, at(14, 50)))
        assertEquals(Rung.NUDGE, Escalation.due(a, s, at(14, 55)))
        s = Escalation.fired(s, Rung.NUDGE)
        assertEquals(Rung.ALARM, Escalation.due(a, s, at(15)))
    }

    @Test fun missedRungsJumpToTheHighest() {
        assertEquals(Rung.ALARM, Escalation.due(anchor(WallStrength.HARD), AlarmState(), at(15, 2)))
    }

    @Test fun onItStopsTheClimb() {
        val s = AlarmState(ackedAt = at(14, 46), lastRung = 1)
        assertNull(Escalation.due(anchor(WallStrength.HARD), s, at(15)))
        assertEquals(at(15, 30), Escalation.nextEvent(anchor(WallStrength.HARD), s, at(14, 50)))
    }

    @Test fun snoozeRefiresSameRungAndRunsOut() {
        val a = anchor(WallStrength.HARD)
        val fired = Escalation.fired(AlarmState(), Rung.ALARM)
        val snoozed = Escalation.snooze(a, fired, at(15))!!
        assertNull(Escalation.due(a, snoozed, at(15, 3)))
        assertEquals(at(15, 5), Escalation.nextEvent(a, snoozed, at(15, 1)))
        assertEquals(Rung.ALARM, Escalation.due(a, snoozed, at(15, 5)))
        // Hard allows one snooze.
        assertNull(Escalation.snooze(a, Escalation.fired(snoozed, Rung.ALARM), at(15, 5)))
        assertEquals(2, Escalation.snoozesLeft(anchor(WallStrength.FIRM), AlarmState()))
    }

    @Test fun quietHoursSilenceAllButHard() {
        val quiet = QuietHours(LocalTime.of(14, 0), LocalTime.of(16, 0))
        assertNull(Escalation.due(anchor(WallStrength.FIRM), AlarmState(), at(15), quiet))
        assertEquals(Rung.ALARM, Escalation.due(anchor(WallStrength.HARD), AlarmState(), at(15), quiet))
        assertTrue(LocalTime.of(23, 0) in QuietHours(LocalTime.of(22, 0), LocalTime.of(7, 0)))
    }

    @Test fun settlesAtTheEnd() {
        val a = anchor(WallStrength.FIRM)
        assertNull(Escalation.settle(a, AlarmState(), at(15, 10)))
        assertEquals(Status.MISSED, Escalation.settle(a, AlarmState(), at(15, 30)))
        assertEquals(Status.DONE, Escalation.settle(a, AlarmState(ackedAt = at(15)), at(15, 30)))
    }

    @Test fun nextEventIsTheNextRung() {
        val a = anchor(WallStrength.HARD)
        assertEquals(at(14, 45), Escalation.nextEvent(a, AlarmState(), at(10)))
        assertTrue(Escalation.isAlarmAt(a, at(15)))
    }
}
