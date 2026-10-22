package net.fallhammer.initiative.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import initiative.core.FocusSession
import initiative.core.Pass
import initiative.core.PassInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.fallhammer.initiative.TAG
import net.fallhammer.initiative.data.Backup
import net.fallhammer.initiative.data.EventRow
import net.fallhammer.initiative.data.toItem
import net.fallhammer.initiative.data.toRow
import net.fallhammer.initiative.data.toState
import net.fallhammer.initiative.graph
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Carries out one pass over the day. The decisions live in core's [Pass]; this
 * reads the database and settings, writes the changes, shows and clears
 * notifications, writes the nightly backup, and sets one alarm for the next
 * moment anything changes. Runs on every data change, every alarm, boot, and
 * clock changes.
 */
object Scheduler {
    private val lock = Mutex()
    const val AUTO_SKIP = "auto-skip"
    val BACKUP_AT: LocalTime get() = Pass.BACKUP_AT

    // Runs on a background thread wherever it's called from: it reads the
    // database and settings, and talks to the alarm and notification services.
    suspend fun sync(context: Context) = withContext(Dispatchers.IO) { lock.withLock { syncLocked(context) } }

    private suspend fun syncLocked(context: Context) {
        val app = context.applicationContext
        val graph = app.graph
        val dao = graph.dao
        val repo = graph.repo
        val prefs = graph.prefs
        val now = LocalDateTime.now()
        Notifier.ensureChannels(app)

        val plan = Pass.plan(
            PassInput(
                items = dao.all().map { it.toItem() },
                states = dao.alarms().associate { it.itemId to it.toState() },
                now = now,
                quiet = prefs.quiet.hours,
                focus = prefs.focus?.let { FocusSession(it.itemId, it.until) },
                shown = prefs.shown,
                backupFolderSet = prefs.backupFolder != null,
                lastBackup = prefs.lastBackup,
                order = prefs.taskOrder,
                hold = prefs.hold,
            ),
            graph.placement,
        )

        for ((before, after) in plan.ended) repo.update(before, after, note = "ended")
        plan.quieted.forEach { Notifier.cancelRung(app, it) }

        for (f in plan.fired) {
            Notifier.showRung(app, f.anchor, f.rung, f.before, now)
            dao.putAlarm(f.after.toRow(f.anchor.id))
            dao.insertEvent(EventRow(itemId = f.anchor.id, fromStatus = f.anchor.status.name, toStatus = f.anchor.status.name, at = now.toString(), note = "rung ${f.rung.level}"))
            Log.i(TAG, "rung ${f.rung.level} for ${f.anchor.title}")
        }

        // Three automatic skips in a row count as a lapse.
        plan.autoSkip?.let { (before, after) ->
            repo.update(before, after, note = AUTO_SKIP)
            prefs.autoSkipStreak = prefs.autoSkipStreak + 1
            Log.i(TAG, "auto-skip: ${before.title}")
        }
        prefs.shown = plan.shown

        if (prefs.lockCard) Notifier.showCard(app, plan.board, now, plan.focusItem) else Notifier.hideCard(app)

        if (plan.backupDue) {
            val ok = prefs.backupFolder?.let { Backup.writeAll(app, dao, Uri.parse(it), now) } ?: false
            if (ok) prefs.lastBackup = now
            Log.i(TAG, "nightly backup ${if (ok) "written" else "failed"}")
        }

        schedule(app, plan.wake, plan.wakeIsAlarm)
        Log.i(TAG, "sync: next wake ${plan.wake}${if (plan.wakeIsAlarm) " (alarm)" else ""}")
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
