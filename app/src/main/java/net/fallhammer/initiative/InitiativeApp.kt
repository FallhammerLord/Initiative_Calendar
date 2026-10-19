package net.fallhammer.initiative

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.core.content.ContextCompat
import net.fallhammer.initiative.data.Prefs

class InitiativeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Unlock can't be caught from the manifest on Android 8+, so the receiver
        // lives as long as the app's process. Android keeps the home app's process
        // around, so this holds in practice.
        ContextCompat.registerReceiver(
            this,
            UnlockReceiver(),
            IntentFilter(Intent.ACTION_USER_PRESENT),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }
}

/** After each unlock, bring the board forward, unless a call is on. */
class UnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return
        val prefs = Prefs(context)
        if (!prefs.boardOnUnlock || !prefs.canJumpToBoard()) return
        if (inCall(context)) return
        val board = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        runCatching { context.startActivity(board) }
    }

    private fun inCall(context: Context): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        return audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION
    }
}
