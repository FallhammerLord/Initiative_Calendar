package net.fallhammer.initiative.pairs

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.fallhammer.initiative.R
import net.fallhammer.initiative.alarms.Notifier
import net.fallhammer.initiative.alarms.Scheduler
import net.fallhammer.initiative.data.PeerRow
import net.fallhammer.initiative.graph
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The always-on syncs, while anyone has one on: Sync over Bluetooth, always,
 * and Sync at home over Wi-Fi. Android only lets this keep going with a quiet
 * notification, which says so. In the first minute of every ten by the clock,
 * the phone is findable and looking, over Bluetooth, the Wi-Fi, or both; two
 * phones with it on for each other meet in that same minute and sync, each
 * only with people it's on for. The rest of the time, neither.
 */
class SyncService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val started = runCatching {
            ServiceCompat.startForeground(this, NOTE_ID, notice(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        }
        if (started.isFailure) {
            Log.i(TAG, "sync: couldn't start (${started.exceptionOrNull()?.message})")
            stopSelf()
            return START_NOT_STICKY
        }
        if (loop?.isActive != true) loop = scope.launch { run() }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun run() {
        val graph = applicationContext.graph
        while (scope.isActive) {
            val peers = graph.dao.peers()
            val bluetooth = peers.any { it.syncBluetooth }
            val wifi = peers.any { it.syncWifi }
            if (!bluetooth && !wifi) {
                Log.i(TAG, "sync: none on, stopping")
                stopSelf()
                return
            }
            delay(untilNextWindow(System.currentTimeMillis()))
            val changed = AtomicBoolean(false)
            coroutineScope {
                if (bluetooth && graph.nearby.problem() == null) {
                    launch { if (meet("bluetooth", { graph.nearby.meet(Nearby.SYNC, WINDOW_MS).first?.link() }) { it.syncBluetooth }) changed.set(true) }
                }
                if (wifi) {
                    launch { if (meet("wifi", { graph.localNet.meet(WINDOW_MS) }) { it.syncWifi }) changed.set(true) }
                }
            }
            // Shared anchors may have come or moved: their alarms follow.
            if (changed.get()) Scheduler.sync(applicationContext)
        }
    }

    /** One meeting, and a sync over it with the people [on] is true for. True when it synced. */
    private suspend fun meet(way: String, open: suspend () -> Link?, on: (PeerRow) -> Boolean): Boolean {
        val link = runCatching { open() }.getOrNull() ?: return false
        val synced = runCatching { link.use { applicationContext.graph.pairSync.over(it, on) } }.getOrNull()
        Log.i(TAG, "sync: $way ${if (synced != null) "swapped" else "stranger"}")
        return synced != null
    }

    private fun notice(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_sync), NotificationManager.IMPORTANCE_MIN).apply {
                description = getString(R.string.channel_sync_about)
                setShowBadge(false)
            },
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(getString(R.string.sync_note_title))
            .setContentText(getString(R.string.sync_note_text))
            .setContentIntent(Notifier.openBoard(this))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val TAG = "Initiative"
        private const val CHANNEL = "sync"
        private const val NOTE_ID = 7_000

        /** Every ten minutes by the clock, both phones look for one minute. */
        const val EVERY_MS = 10 * 60_000L
        const val WINDOW_MS = 60_000L

        /** How long until the next window opens: the next ten-minute mark. */
        fun untilNextWindow(nowMs: Long): Long = EVERY_MS - nowMs % EVERY_MS

        /** Starts the service when anyone has an always-on sync, and stops it when no one does. Call off the main thread. */
        suspend fun update(context: Context) {
            val app = context.applicationContext
            val on = app.graph.dao.peers().any { it.syncBluetooth || it.syncWifi }
            val intent = Intent(app, SyncService::class.java)
            if (on) {
                // Android refuses while the app is in the background; the next start or boot tries again.
                runCatching { ContextCompat.startForegroundService(app, intent) }
                    .onFailure { Log.i(TAG, "sync: not started now (${it.message})") }
            } else {
                app.stopService(intent)
            }
        }
    }
}
