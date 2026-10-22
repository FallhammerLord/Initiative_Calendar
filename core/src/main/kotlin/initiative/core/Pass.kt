package initiative.core

import java.time.LocalDateTime
import java.time.LocalTime

/** A running focus session, as the scheduler sees it. */
data class FocusSession(val itemId: Long, val until: LocalDateTime)

/** Everything one scheduler pass reads: the items, alarm progress, settings, and session state. */
data class PassInput(
    val items: List<Item>,
    val states: Map<Long, AlarmState>,
    val now: LocalDateTime,
    val quiet: QuietHours? = null,
    val focus: FocusSession? = null,
    val shown: Shown? = null,
    val backupFolderSet: Boolean = false,
    val lastBackup: LocalDateTime? = null,
    /** The person's own task order, by id; see Placement.board. */
    val order: List<Long> = emptyList(),
    /** The task just shown as Now, held a few minutes; see Placement.board. */
    val hold: Hold? = null,
)

/** A rung that fires now, with the anchor's alarm state before and after. */
data class Fired(val anchor: Item, val rung: Rung, val before: AlarmState) {
    val after: AlarmState get() = Escalation.fired(before, rung)
}

/**
 * What one pass decided. The app's Scheduler carries it out: it writes the
 * changes, shows and clears notifications, and sets one alarm for [wake].
 */
data class Plan(
    /** Anchors that ended, as (before, after): On it means Done, silence means Missed. */
    val ended: List<Pair<Item, Item>>,
    /** Anchors whose notifications should go: ended, done, dropped, or acknowledged. */
    val quieted: List<Long>,
    val fired: List<Fired>,
    /** A float skipped for you, as (before, after), when its window closed untouched. */
    val autoSkip: Pair<Item, Item>?,
    val board: Board,
    /** The float now shown as Now, remembered for the next automatic skip. */
    val shown: Shown?,
    /** The focused item, while a session runs. */
    val focusItem: Item?,
    val backupDue: Boolean,
    val wake: LocalDateTime,
    /** Whether [wake] is a full alarm, which gets the system alarm clock. */
    val wakeIsAlarm: Boolean,
)

/**
 * One pass over the day, with no Android code: settle anchors that ended, fire
 * due rungs, skip a missed float, pick the board, decide on the nightly backup,
 * and find the next moment anything changes.
 */
object Pass {
    val BACKUP_AT: LocalTime = LocalTime.of(3, 0)

    fun plan(input: PassInput, placement: Placement): Plan {
        val now = input.now
        // Markers never ring or settle.
        var items = input.items.work()
        val states = input.states.toMutableMap()
        fun stateOf(id: Long) = states[id] ?: AlarmState()

        val ended = items.mapNotNull { a ->
            Escalation.settle(a, stateOf(a.id), now)?.let { status ->
                a to a.copy(status = status, doneAt = if (status == Status.DONE) now else a.doneAt)
            }
        }
        items = items.replacing(ended.map { it.second })

        val byId = items.associateBy { it.id }
        val quieted = (ended.map { it.first.id } + input.states.filter { (id, s) ->
            val a = byId[id]
            a == null || !a.isOpen || s.ackedAt != null
        }.keys).distinct()

        // Quiet hours silence all but Hard; focus silences Soft.
        val focus = input.focus?.takeIf { now.isBefore(it.until) }
        val fired = items.mapNotNull { a ->
            val s = stateOf(a.id)
            Escalation.due(a, s, now, input.quiet, focusing = focus != null)?.let { Fired(a, it, s) }
        }
        fired.forEach { states[it.anchor.id] = it.after }

        // Not during focus: the person chose to stay on one thing.
        val autoSkip = if (focus == null) {
            AutoSkip.due(input.shown, items, now)?.let { f -> f to placement.skip(f, items, now).item }
        } else {
            null
        }
        autoSkip?.let { items = items.replacing(listOf(it.second)) }

        val board = placement.board(items, now, input.order, input.hold)
        val nowFloat = board.now.item?.takeIf { !it.isAnchor }
        val until = board.until
        val shown = if (nowFloat != null && until != null) Shown(nowFloat.id, until, nowFloat.skipCount, nowFloat.notBefore) else null
        val focusItem = focus?.let { f -> items.firstOrNull { it.id == f.itemId && it.isOpen } }

        // The nightly backup: once a day after 3 AM, when a folder is chosen.
        val backupDue = input.backupFolderSet && now.toLocalTime() >= BACKUP_AT && input.lastBackup?.toLocalDate() != now.toLocalDate()
        val nextBackup = if (input.backupFolderSet) nextAt(now, BACKUP_AT) else null

        val nextAnchor = items
            .mapNotNull { a -> Escalation.nextEvent(a, stateOf(a.id), now)?.let { a to it } }
            .minByOrNull { it.second }
        // Silenced rungs resume when quiet hours or focus end, so wake then too.
        val quietEnds = input.quiet?.takeIf { now.toLocalTime() in it }?.let { nextAt(now, it.end) }
        val wake = listOfNotNull(nextAnchor?.second, placement.nextChange(items, now), quietEnds, focus?.until, shown?.until, nextBackup).min()
        val wakeIsAlarm = nextAnchor != null && nextAnchor.second == wake && Escalation.isAlarmAt(nextAnchor.first, wake)

        return Plan(ended, quieted, fired, autoSkip, board, shown, focusItem, backupDue, wake, wakeIsAlarm)
    }

    /** The next time the clock reads [time], after [now]. */
    private fun nextAt(now: LocalDateTime, time: LocalTime): LocalDateTime =
        now.toLocalDate().atTime(time).let { if (it.isAfter(now)) it else it.plusDays(1) }

    private fun List<Item>.replacing(changed: List<Item>): List<Item> {
        if (changed.isEmpty()) return this
        val byId = changed.associateBy { it.id }
        return map { byId[it.id] ?: it }
    }
}
