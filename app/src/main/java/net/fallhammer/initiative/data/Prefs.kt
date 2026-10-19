package net.fallhammer.initiative.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Small per-device settings. */
class Prefs(private val context: Context) {
    // Opened lazily: the first open creates the file on disk, so InitiativeApp
    // warms it on a background thread at startup.
    private val prefs by lazy { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    fun warm() {
        prefs.all
    }

    /** Bring the board forward after every unlock. On by default. */
    var boardOnUnlock: Boolean
        get() = prefs.getBoolean(BOARD_ON_UNLOCK, true)
        set(value) = prefs.edit().putBoolean(BOARD_ON_UNLOCK, value).apply()

    /** The silent Now card on the lock screen. On by default. */
    var lockCard: Boolean
        get() = prefs.getBoolean(LOCK_CARD, true)
        set(value) = prefs.edit().putBoolean(LOCK_CARD, value).apply()

    /** The chosen theme's id; see Looks in ui/Theme.kt. */
    var lookId: String?
        get() = prefs.getString(LOOK, null)
        set(value) = prefs.edit().putString(LOOK, value).apply()

    /** Whether the one-tap notification prompt has been shown; after that, Settings is the only way. */
    var askedNotifications: Boolean
        get() = prefs.getBoolean(ASKED_NOTIFICATIONS, false)
        set(value) = prefs.edit().putBoolean(ASKED_NOTIFICATIONS, value).apply()

    /**
     * Android blocks background apps from opening a screen. "Display over other
     * apps" is the permission that lifts that block for the board-on-unlock jump.
     */
    fun canJumpToBoard(): Boolean = Settings.canDrawOverlays(context)

    fun openJumpPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private companion object {
        const val BOARD_ON_UNLOCK = "boardOnUnlock"
        const val LOCK_CARD = "lockCard"
        const val LOOK = "look"
        const val ASKED_NOTIFICATIONS = "askedNotifications"
    }
}
