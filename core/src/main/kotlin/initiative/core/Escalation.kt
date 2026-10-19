package initiative.core

import java.time.LocalDateTime
import java.time.LocalTime

/** Interrupting rungs. Rung 0 is the board itself and never fires. Rung 4 (partner) comes in v0.2. */
enum class Rung(val level: Int) { NOTICE(1), NUDGE(2), ALARM(3) }

data class RungTime(val rung: Rung, val at: LocalDateTime)

/** Per-anchor alarm progress. */
data class AlarmState(
    val ackedAt: LocalDateTime? = null,
    val snoozes: Int = 0,
    val snoozedUntil: LocalDateTime? = null,
    val lastRung: Int = 0,
)

/** Hours when only Hard walls may interrupt. Spans midnight when [start] is after [end]. */
data class QuietHours(val start: LocalTime, val end: LocalTime) {
    operator fun contains(t: LocalTime): Boolean =
        if (start.isBefore(end)) !t.isBefore(start) && t.isBefore(end) else !t.isBefore(start) || t.isBefore(end)
}

/**
 * The escalation ladder. See docs/spec.md, Escalation ladder.
 * Each anchor climbs one rung at a time until On it; wall strength sets the top.
 */
object Escalation {
    const val SNOOZE_MINUTES = 5L
    private const val NUDGE_BEFORE = 5L

    fun ceiling(wall: WallStrength): Rung = when (wall) {
        WallStrength.SOFT -> Rung.NUDGE
        WallStrength.FIRM, WallStrength.HARD -> Rung.ALARM
    }

    fun maxSnoozes(wall: WallStrength): Int = if (wall == WallStrength.HARD) 1 else 2

    /** When each rung fires. A lead under 5 minutes merges notice and nudge into one nudge. */
    fun ladder(anchor: Item): List<RungTime> {
        val start = anchor.start ?: return emptyList()
        val warn = anchor.warnAt!!
        val steps = if (anchor.leadMinutes < NUDGE_BEFORE) {
            listOf(RungTime(Rung.NUDGE, warn), RungTime(Rung.ALARM, start))
        } else {
            listOf(RungTime(Rung.NOTICE, warn), RungTime(Rung.NUDGE, start.minusMinutes(NUDGE_BEFORE)), RungTime(Rung.ALARM, start))
        }
        val top = ceiling(anchor.wall).level
        return steps.filter { it.rung.level <= top }
    }

    private fun live(anchor: Item, state: AlarmState, now: LocalDateTime): Boolean =
        anchor.isAnchor && !anchor.isTemplate && anchor.isOpen && anchor.start != null &&
            state.ackedAt == null && now.isBefore(anchor.anchorEnd!!)

    /**
     * The rung to show now, or null. When several rungs have passed (the phone was
     * off, say) only the highest fires. A finished snooze re-fires the same rung.
     * While [focusing], Soft anchors stay silent; Firm and Hard still climb.
     */
    fun due(anchor: Item, state: AlarmState, now: LocalDateTime, quiet: QuietHours? = null, focusing: Boolean = false): Rung? {
        if (!live(anchor, state, now)) return null
        if (focusing && anchor.wall == WallStrength.SOFT) return null
        val until = state.snoozedUntil
        if (until != null && now.isBefore(until)) return null
        if (quiet != null && anchor.wall != WallStrength.HARD && now.toLocalTime() in quiet) return null
        val reached = ladder(anchor).lastOrNull { !it.at.isAfter(now) } ?: return null
        return if (reached.rung.level > state.lastRung || until != null) reached.rung else null
    }

    /** State after a rung fires. */
    fun fired(state: AlarmState, rung: Rung): AlarmState = state.copy(lastRung = rung.level, snoozedUntil = null)

    /** State after Snooze, or null when this anchor has none left. */
    fun snooze(anchor: Item, state: AlarmState, now: LocalDateTime): AlarmState? =
        if (state.snoozes >= maxSnoozes(anchor.wall)) null
        else state.copy(snoozes = state.snoozes + 1, snoozedUntil = now.plusMinutes(SNOOZE_MINUTES))

    fun snoozesLeft(anchor: Item, state: AlarmState): Int = (maxSnoozes(anchor.wall) - state.snoozes).coerceAtLeast(0)

    /** Next time this anchor needs attention: a rung, a snooze ending, or its end. */
    fun nextEvent(anchor: Item, state: AlarmState, now: LocalDateTime): LocalDateTime? {
        if (!anchor.isAnchor || anchor.isTemplate || !anchor.isOpen || anchor.start == null) return null
        val times = mutableListOf<LocalDateTime>()
        anchor.anchorEnd!!.takeIf { it.isAfter(now) }?.let { times += it }
        if (state.ackedAt == null) {
            state.snoozedUntil?.takeIf { it.isAfter(now) }?.let { times += it }
            ladder(anchor).firstOrNull { it.at.isAfter(now) && it.rung.level > state.lastRung }?.let { times += it.at }
        }
        return times.minOrNull()
    }

    /** Whether the event at [at] is this anchor's full alarm, which gets the system alarm clock. */
    fun isAlarmAt(anchor: Item, at: LocalDateTime): Boolean =
        ladder(anchor).any { it.rung == Rung.ALARM && it.at == at }

    /** At an anchor's end: On it means Done, silence means Missed. Null while it's still running. */
    fun settle(anchor: Item, state: AlarmState, now: LocalDateTime): Status? {
        if (!anchor.isAnchor || anchor.isTemplate || !anchor.isOpen || anchor.start == null) return null
        if (now.isBefore(anchor.anchorEnd!!)) return null
        return if (state.ackedAt != null) Status.DONE else Status.MISSED
    }
}
