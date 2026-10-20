package net.fallhammer.initiative.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import initiative.core.AlarmState
import initiative.core.AutoSkip
import initiative.core.Escalation
import initiative.core.Shown
import initiative.core.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.fallhammer.initiative.TAG
import net.fallhammer.initiative.graph
import android.net.Uri
import net.fallhammer.initiative.data.Backup
import net.fallhammer.initiative.data.EventRow
import net.fallhammer.initiative.data.toItem
import net.fallhammer.initiative.data.toRow
import net.fallhammer.initiative.data.toState
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * One pass over the day: settle anchors that ended, fire due rungs, refresh the
 * lock-screen card, and set a single alarm for the next moment anything changes.
 * Runs on every data change, every alarm, boot, and clock changes.
 */
object Scheduler {
    private val lock = Mutex()
    const val AUTO_SKIP = "auto-skip"
    val BACKUP_AT: LocalTime = LocalTime.of(3, 0)

    // Runs on a background thread wherever it's called from: it reads the
    // database and settings, and talks to the alarm and notification services.
    suspend fun sync(context: Context) = withContext(Dispatchers.IO) { lock.withLock { syncLocked(context) } }

    private suspend fun syncLocked(context: Context) {
        val app = context.applicationContext
        val graph = app.graph
        val dao = graph.dao
        val repo = graph.repo
        val placement = graph.placement
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

        // Fire due rungs. Quiet hours silence all but Hard; focus silences Soft.
        val quiet = graph.prefs.quiet.hours
        val focus = graph.prefs.focus?.takeIf { now.isBefore(it.until) }
        for (a in items) {
            val s = stateOf(a.id)
            val rung = Escalation.due(a, s, now, quiet, focusing = focus != null) ?: continue
            Notifier.showRung(app, a, rung, s, now)
            dao.putAlarm(Escalation.fired(s, rung).toRow(a.id))
            dao.insertEvent(EventRow(itemId = a.id, fromStatus = a.status.name, toStatus = a.status.name, at = now.toString(), note = "rung ${rung.level}"))
            Log.i(TAG, "rung ${rung.level} for ${a.title}")
        }
        states = dao.alarms().associate { it.itemId to it.toState() }

        // A float that sat untouched as Now until its window closed is skipped for
        // you. Not during focus. Three in a row count as a lapse.
        val prefs = graph.prefs
        if (focus == null) {
            AutoSkip.due(prefs.shown, items, now)?.let { f ->
                repo.update(f, placement.skip(f, items, now).item, note = AUTO_SKIP)
                prefs.autoSkipStreak = prefs.autoSkipStreak + 1
                prefs.shown = null
                items = dao.all().map { it.toItem() }
                Log.i(TAG, "auto-skip: ${f.title}")
            }
        }

        val board = placement.board(items, now)
        val nowFloat = board.now.item?.takeIf { !it.isAnchor }
        val until = board.until
        prefs.shown = if (nowFloat != null && until != null) Shown(nowFloat.id, until, nowFloat.skipCount, nowFloat.notBefore) else null

        val focusItem = focus?.let { f -> items.firstOrNull { it.id == f.itemId && it.isOpen } }
        if (prefs.lockCard) Notifier.showCard(app, board, focusItem) else Notifier.hideCard(app)

        // The nightly backup, once a day after 3 AM, when a folder is chosen.
        val folder = prefs.backupFolder
        if (folder != null && now.toLocalTime() >= BACKUP_AT && prefs.lastBackup?.toLocalDate() != now.toLocalDate()) {
            val ok = Backup.write(app, dao, Uri.parse(folder), now)
            if (ok) prefs.lastBackup = now
            Log.i(TAG, "nightly backup ${if (ok) "written" else "failed"}")
        }
        val nextBackup = folder?.let {
            now.toLocalDate().atTime(BACKUP_AT).let { t -> if (t.isAfter(now)) t else t.plusDays(1) }
        }

        // Next wake: the sooner of the next alarm event and the next board change.
        val nextAnchor = items
            .mapNotNull { a -> Escalation.nextEvent(a, stateOf(a.id), now)?.let { a to it } }
            .minByOrNull { it.second }
        // Silenced rungs resume when quiet hours or focus end, so wake then too.
        val quietEnds = quiet?.takeIf { now.toLocalTime() in it }?.let { q ->
            now.toLocalDate().atTime(q.end).let { if (it.isAfter(now)) it else it.plusDays(1) }
        }
        val wake = listOfNotNull(nextAnchor?.second, placement.nextChange(items, now), quietEnds, focus?.until, prefs.shown?.until, nextBackup).min()
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
