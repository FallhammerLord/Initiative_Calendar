package net.fallhammer.initiative.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import initiative.core.QuietHours
import java.time.LocalDateTime
import java.time.LocalTime

/** A focus session: on [itemId] until [until], the next Firm or Hard wall. */
data class Focus(val itemId: Long, val until: LocalDateTime)

/** Quiet hours as set, whether on or off, so the times survive switching off. */
data class QuietSetting(val on: Boolean = false, val start: LocalTime = LocalTime.of(22, 0), val end: LocalTime = LocalTime.of(7, 0)) {
    val hours: QuietHours? get() = if (on) QuietHours(start, end) else null
}

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

    var quiet: QuietSetting
        get() = QuietSetting(
            on = prefs.getBoolean(QUIET_ON, false),
            start = LocalTime.parse(prefs.getString(QUIET_START, "22:00")),
            end = LocalTime.parse(prefs.getString(QUIET_END, "07:00")),
        )
        set(value) = prefs.edit()
            .putBoolean(QUIET_ON, value.on)
            .putString(QUIET_START, value.start.toString())
            .putString(QUIET_END, value.end.toString())
            .apply()

    /** The running focus session, if any. */
    var focus: Focus?
        get() {
            val id = prefs.getLong(FOCUS_ITEM, -1)
            val until = prefs.getString(FOCUS_UNTIL, null)
            return if (id < 0 || until == null) null else Focus(id, LocalDateTime.parse(until))
        }
        set(value) {
            val edit = prefs.edit()
            if (value == null) {
                edit.remove(FOCUS_ITEM).remove(FOCUS_UNTIL)
            } else {
                edit.putLong(FOCUS_ITEM, value.itemId).putString(FOCUS_UNTIL, value.until.toString())
            }
            edit.apply()
        }

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
        const val QUIET_ON = "quietOn"
        const val QUIET_START = "quietStart"
        const val QUIET_END = "quietEnd"
        const val FOCUS_ITEM = "focusItem"
        const val FOCUS_UNTIL = "focusUntil"
    }
}
