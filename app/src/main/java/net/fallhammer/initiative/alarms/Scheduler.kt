package net.fallhammer.initiative.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import initiative.core.AlarmState
import initiative.core.Escalation
import initiative.core.Placement
import initiative.core.Status
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.fallhammer.initiative.TAG
import net.fallhammer.initiative.data.EventRow
import net.fallhammer.initiative.data.InitiativeDb
import net.fallhammer.initiative.data.Prefs
import net.fallhammer.initiative.data.Repository
import net.fallhammer.initiative.data.toItem
import net.fallhammer.initiative.data.toRow
import net.fallhammer.initiative.data.toState
import java.time.LocalDateTime

/**
 * One pass over the day: settle anchors that ended, fire due rungs, refresh the
 * lock-screen card, and set a single alarm for the next moment anything changes.
 * Runs on every data change, every alarm, boot, and clock changes.
 */
object Scheduler {
    private val lock = Mutex()
    private val placement = Placement()

    suspend fun sync(context: Context) = lock.withLock {
        val app = context.applicationContext
        val dao = InitiativeDb.get(app).items()
        val repo = Repository(dao, placement)
        val now = LocalDateTime.now()
        Notifier.ensureChannels(app)

        var items = dao.all().map { it.toItem() }
        var states = dao.alarms().associate { it.itemId to it.toState() }
        fun stateOf(id: Long) = states[id] ?: AlarmState()

        // Anchors that ended: On it means Done, silence means Missed.
        val ended = items.mapNotNull { a -> Escalation.settle(a, stateOf(a.id), now)?.let { a to it } }
        for ((a, status) in ended) {
            repo.update(a, a.copy(status = status, doneAt = if (status == Status.DONE) now else a.doneAt), note = "ended")
            Notifier.cancelRung(app, a.id)
        }
        if (ended.isNotEmpty()) items = dao.all().map { it.toItem() }

        // Clear notifications for anchors that are done or acknowledged.
        val byId = items.associateBy { it.id }
        for ((id, s) in states) {
            val a = byId[id]
            if (a == null || !a.isOpen || s.ackedAt != null) Notifier.cancelRung(app, id)
        }

        // Fire due rungs.
        for (a in items) {
            val s = stateOf(a.id)
            val rung = Escalation.due(a, s, now) ?: continue
            Notifier.showRung(app, a, rung, s, now)
            dao.putAlarm(Escalation.fired(s, rung).toRow(a.id))
            dao.insertEvent(EventRow(itemId = a.id, fromStatus = a.status.name, toStatus = a.status.name, at = now.toString(), note = "rung ${rung.level}"))
            Log.i(TAG, "rung ${rung.level} for ${a.title}")
        }
        states = dao.alarms().associate { it.itemId to it.toState() }

        if (Prefs(app).lockCard) Notifier.showCard(app, placement.board(items, now)) else Notifier.hideCard(app)

        // Next wake: the sooner of the next alarm event and the next board change.
        val nextAnchor = items
            .mapNotNull { a -> Escalation.nextEvent(a, stateOf(a.id), now)?.let { a to it } }
            .minByOrNull { it.second }
        val wake = listOfNotNull(nextAnchor?.second, placement.nextChange(items, now)).min()
        val isAlarm = nextAnchor != null && nextAnchor.second == wake && Escalation.isAlarmAt(nextAnchor.first, wake)
        schedule(app, wake, isAlarm)
        Log.i(TAG, "sync: next wake $wake${if (isAlarm) " (alarm)" else ""}")
    }

    private fun schedule(context: Context, at: LocalDateTime, alarmClock: Boolean) {
        val am = context.getSystemService(AlarmManager::class.java)
        val ms = Notifier.millis(at)
        val tick = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, TickReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        when {
            // The system alarm clock is exempt from battery saving and shows in the status bar.
            alarmClock && exact -> am.setAlarmClock(AlarmManager.AlarmClockInfo(ms, Notifier.openBoard(context)), tick)
            exact -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, tick)
            else -> am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, tick)
        }
    }
}
