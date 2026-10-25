package net.fallhammer.initiative.alarms

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import net.fallhammer.initiative.pairs.SyncService
import android.util.Log
import initiative.core.Status
import initiative.core.AlarmState
import initiative.core.Escalation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.fallhammer.initiative.TAG
import net.fallhammer.initiative.graph
import net.fallhammer.initiative.data.EventRow
import net.fallhammer.initiative.data.toItem
import net.fallhammer.initiative.data.toRow
import net.fallhammer.initiative.data.toState
import java.time.LocalDateTime

/** Runs [block] off the main thread while keeping the broadcast (and its wake lock) alive. */
private fun BroadcastReceiver.async(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
        try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "background sync failed", e)
        } finally {
            pending.finish()
        }
    }
}

/** The scheduler's own alarm. */
class TickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = async { Scheduler.sync(context) }
}

/** Boot, app update, and clock or time-zone changes: re-arm everything. */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = async {
        Log.i(TAG, "re-arming after ${intent.action}")
        Scheduler.sync(context)
        // The always-on syncs start again after a restart or an update.
        SyncService.update(context)
    }
}

/** On it and Snooze, from a notification. */
class AlarmActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1)
        if (id < 0) return
        async {
            when (intent.action) {
                ON_IT -> AlarmActions.onIt(context, id)
                SNOOZE -> AlarmActions.snooze(context, id)
            }
        }
    }

    companion object {
        const val ON_IT = "net.fallhammer.initiative.ON_IT"
        const val SNOOZE = "net.fallhammer.initiative.SNOOZE"
        private const val EXTRA_ID = "itemId"

        fun pending(context: Context, itemId: Long, action: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            (itemId * 2 + if (action == SNOOZE) 1 else 0).toInt(),
            Intent(context, AlarmActionReceiver::class.java).setAction(action).putExtra(EXTRA_ID, itemId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/** What On it and Snooze do, shared by the notification and the alarm screen. */
object AlarmActions {

    suspend fun onIt(context: Context, id: Long) {
        val dao = context.graph.dao
        val item = dao.byId(id)?.toItem() ?: return
        val now = LocalDateTime.now()
        val state = dao.alarm(id)?.toState() ?: AlarmState()
        dao.putAlarm(state.copy(ackedAt = now, snoozedUntil = null).toRow(id))
        // On it shows on the Now card too, with when.
        if (item.isOpen) dao.update(item.copy(status = Status.ACTIVE, activeSince = item.activeSince ?: now).toRow())
        dao.insertEvent(EventRow(itemId = id, fromStatus = item.status.name, toStatus = Status.ACTIVE.name, at = now.toString(), note = "on it"))
        Notifier.cancelRung(context, id)
        Log.i(TAG, "on it: ${item.title}")
        Scheduler.sync(context)
    }

    /** Returns false when no snoozes are left. */
    suspend fun snooze(context: Context, id: Long): Boolean {
        val dao = context.graph.dao
        val item = dao.byId(id)?.toItem() ?: return false
        val now = LocalDateTime.now()
        val state = dao.alarm(id)?.toState() ?: AlarmState()
        val next = Escalation.snooze(item, state, now) ?: return false
        dao.putAlarm(next.toRow(id))
        dao.insertEvent(EventRow(itemId = id, fromStatus = item.status.name, toStatus = item.status.name, at = now.toString(), note = "snoozed"))
        Notifier.cancelRung(context, id)
        Log.i(TAG, "snoozed: ${item.title}")
        Scheduler.sync(context)
        return true
    }
}
