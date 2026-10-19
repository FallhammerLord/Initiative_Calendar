package net.fallhammer.initiative

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.StrictMode
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class InitiativeApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        watchMainThread()
        graph = AppGraph(this)
        // Settings files are created on first open; keep that disk work off the main thread.
        CoroutineScope(Dispatchers.IO).launch {
            graph.prefs.warm()
            graph.apps.warm()
        }
        // Screen-off and unlock can't be caught from the manifest on Android 8+, so
        // the receiver lives as long as the app's process. Android keeps the home
        // app's process around, so this holds in practice. Both actions are
        // protected: only the system can send them.
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, UnlockReceiver(), filter, ContextCompat.RECEIVER_EXPORTED)
        Log.i(TAG, "board-on-unlock receiver registered")
    }
}

/*
 * StrictMode reports disk, network, and leaked-handle problems to the log. It
 * only logs, never crashes, so it costs nothing until something goes wrong;
 * the emulator test reads these lines and fails on the serious ones.
 */
private fun watchMainThread() {
    StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
    StrictMode.setVmPolicy(
        StrictMode.VmPolicy.Builder()
            .detectLeakedClosableObjects()
            .detectLeakedSqlLiteObjects()
            .detectLeakedRegistrationObjects()
            .penaltyLog()
            .build(),
    )
}

const val TAG = "Initiative"

/**
 * Puts the board in front when the screen turns off, so the next unlock lands on
 * it without a visible jump. Unlock is a second chance, in case the first was
 * missed. Skipped during calls.
 */
class UnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val prefs = context.graph.prefs
        when {
            !prefs.boardOnUnlock -> Log.i(TAG, "$action: board after unlock is off")
            !prefs.canJumpToBoard() -> Log.i(TAG, "$action: no permission to come forward")
            inCall(context) -> Log.i(TAG, "$action: call in progress, staying put")
            else -> {
                val board = Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                runCatching { context.startActivity(board) }
                    .onSuccess { Log.i(TAG, "$action: board brought forward") }
                    .onFailure { Log.w(TAG, "$action: couldn't bring board forward", it) }
            }
        }
    }

    private fun inCall(context: Context): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        return audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION
    }
}
