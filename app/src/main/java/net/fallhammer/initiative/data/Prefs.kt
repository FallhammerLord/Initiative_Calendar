package net.fallhammer.initiative.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import initiative.core.Lapse
import initiative.core.QuietHours
import initiative.core.Recopy
import initiative.core.Shown
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

    /**
     * Bring the board forward after every unlock. Off by default: as the Home app
     * the board is one press away, and this needs "Display over other apps",
     * which Android blocks for sideloaded apps.
     */
    var boardOnUnlock: Boolean
        get() = prefs.getBoolean(BOARD_ON_UNLOCK, false)
        set(value) = prefs.edit().putBoolean(BOARD_ON_UNLOCK, value).apply()

    /** The silent Now card on the lock screen. On by default. */
    var lockCard: Boolean
        get() = prefs.getBoolean(LOCK_CARD, true)
        set(value) = prefs.edit().putBoolean(LOCK_CARD, value).apply()

    /** The chosen theme's id; see Looks in ui/Theme.kt. */
    var lookId: String?
        get() = prefs.getString(LOOK, null)
        set(value) = prefs.edit().putString(LOOK, value).apply()

    /** The look's ornaments: pattern, corner brackets, and the All done flourish. On by default. */
    var ornaments: Boolean
        get() = prefs.getBoolean(ORNAMENTS, true)
        set(value) = prefs.edit().putBoolean(ORNAMENTS, value).apply()

    /** A look's background pattern strength, in percent; each look keeps its own. Half by default. */
    fun texture(look: String): Int = prefs.getInt("$TEXTURE.$look", DEFAULT_TEXTURE)

    fun setTexture(look: String, percent: Int) = prefs.edit().putInt("$TEXTURE.$look", percent).apply()

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

    /** The last time the person did something on the board; a day without one is a lapse. */
    var lastAction: LocalDateTime?
        get() = time(LAST_ACTION)
        set(value) = putTime(LAST_ACTION, value)

    /** Automatic skips in a row; three is a lapse. Any action resets it. */
    var autoSkipStreak: Int
        get() = prefs.getInt(AUTO_SKIPS, 0)
        set(value) = prefs.edit().putInt(AUTO_SKIPS, value).apply()

    /** The float last shown as Now, for automatic skips. */
    var shown: Shown?
        get() {
            val id = prefs.getLong(SHOWN_ITEM, -1)
            val until = time(SHOWN_UNTIL) ?: return null
            if (id < 0) return null
            return Shown(id, until, prefs.getInt(SHOWN_SKIPS, 0), time(SHOWN_NOT_BEFORE))
        }
        set(value) {
            val edit = prefs.edit()
            if (value == null) {
                edit.remove(SHOWN_ITEM).remove(SHOWN_UNTIL).remove(SHOWN_SKIPS).remove(SHOWN_NOT_BEFORE)
            } else {
                edit.putLong(SHOWN_ITEM, value.itemId).putString(SHOWN_UNTIL, value.until.toString())
                    .putInt(SHOWN_SKIPS, value.skipCount).putString(SHOWN_NOT_BEFORE, value.notBefore?.toString())
            }
            edit.apply()
        }

    /** Set when a lapse is noticed: the recap covers everything since then. Cleared when it closes. */
    var recapSince: LocalDateTime?
        get() = time(RECAP_SINCE)
        set(value) = putTime(RECAP_SINCE, value)

    var lastRecopy: LocalDateTime?
        get() = time(LAST_RECOPY)
        set(value) = putTime(LAST_RECOPY, value)

    /** Whether open Quick tasks have been moved to the 15-minute default; see ItemDao.shortenQuickDefaults. */
    var quickDefaultsShortened: Boolean
        get() = prefs.getBoolean(QUICK_SHORTENED, false)
        set(value) = prefs.edit().putBoolean(QUICK_SHORTENED, value).apply()

    /** The person's own task order, by id, from dragging in the day panel: a tie breaker in ranking. */
    var taskOrder: List<Long>
        get() = prefs.getString(TASK_ORDER, "").orEmpty().split(",").mapNotNull { it.toLongOrNull() }
        set(value) = prefs.edit().putString(TASK_ORDER, value.joinToString(",")).apply()

    /** Hours with no action before the recap shows. */
    var lapseHours: Int
        get() = prefs.getInt(LAPSE_HOURS, Lapse.UNTOUCHED_HOURS.toInt())
        set(value) = prefs.edit().putInt(LAPSE_HOURS, value).apply()

    /** Days between weekly recopies. */
    var recopyDays: Int
        get() = prefs.getInt(RECOPY_DAYS, Recopy.EVERY_DAYS.toInt())
        set(value) = prefs.edit().putInt(RECOPY_DAYS, value).apply()

    /** "Later" on the recopy card hides it until this time. */
    var recopyLaterUntil: LocalDateTime?
        get() = time(RECOPY_LATER)
        set(value) = putTime(RECOPY_LATER, value)

    /** The folder chosen for the nightly backup, as a document-tree address. */
    var backupFolder: String?
        get() = prefs.getString(BACKUP_FOLDER, null)
        set(value) = prefs.edit().putString(BACKUP_FOLDER, value).apply()

    var lastBackup: LocalDateTime?
        get() = time(LAST_BACKUP)
        set(value) = putTime(LAST_BACKUP, value)

    private fun time(key: String): LocalDateTime? = prefs.getString(key, null)?.let(LocalDateTime::parse)

    private fun putTime(key: String, value: LocalDateTime?) {
        val edit = prefs.edit()
        if (value == null) edit.remove(key) else edit.putString(key, value.toString())
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
        const val ORNAMENTS = "ornaments"
        const val TEXTURE = "texture"
        const val DEFAULT_TEXTURE = 50
        const val ASKED_NOTIFICATIONS = "askedNotifications"
        const val QUIET_ON = "quietOn"
        const val QUIET_START = "quietStart"
        const val QUIET_END = "quietEnd"
        const val FOCUS_ITEM = "focusItem"
        const val FOCUS_UNTIL = "focusUntil"
        const val LAST_ACTION = "lastAction"
        const val AUTO_SKIPS = "autoSkipStreak"
        const val SHOWN_ITEM = "shownItem"
        const val SHOWN_UNTIL = "shownUntil"
        const val SHOWN_SKIPS = "shownSkips"
        const val SHOWN_NOT_BEFORE = "shownNotBefore"
        const val RECAP_SINCE = "recapSince"
        const val LAST_RECOPY = "lastRecopy"
        const val LAPSE_HOURS = "lapseHours"
        const val TASK_ORDER = "taskOrder"
        const val QUICK_SHORTENED = "quickDefaultsShortened"
        const val RECOPY_DAYS = "recopyDays"
        const val RECOPY_LATER = "recopyLater"
        const val BACKUP_FOLDER = "backupFolder"
        const val LAST_BACKUP = "lastBackup"
    }
}
