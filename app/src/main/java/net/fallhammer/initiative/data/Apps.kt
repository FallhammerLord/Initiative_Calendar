package net.fallhammer.initiative.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppEntry(val label: String, val component: ComponentName) {
    val key: String get() = component.flattenToString()
}

/** Installed apps for the drawer, and the dock's slots kept in preferences. */
class Apps(private val context: Context) {
    private val prefs by lazy { context.getSharedPreferences("apps", Context.MODE_PRIVATE) }

    /** Opens the pinned-apps file off the main thread; see [Prefs.warm]. */
    fun warm() {
        prefs.all
    }

    fun installed(): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map { AppEntry(it.loadLabel(pm).toString(), ComponentName(it.activityInfo.packageName, it.activityInfo.name)) }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * The dock: [DOCK_SIZE] fixed slots, each an app's key or null when empty.
     * The first time, it's filled from the older pinned row.
     */
    fun dock(): List<String?> {
        val saved = prefs.getString(DOCK, null)?.split("|")
            ?: prefs.getString(PINNED, "").orEmpty().split("|").filter { it.isNotEmpty() }
        return List(DOCK_SIZE) { saved.getOrNull(it)?.ifEmpty { null } }
    }

    /**
     * Each slot's apps, the slot's own first, then up to [TRAY_SIZE] - 1 more in
     * its tray, nearest the slot first. Empty for an empty slot.
     */
    fun slots(): List<List<String>> {
        val trays = prefs.getString(TRAYS, "").orEmpty().split("|")
        return dock().mapIndexed { i, own ->
            if (own == null) emptyList() else listOf(own) + trays.getOrNull(i).orEmpty().split(",").filter { it.isNotEmpty() }
        }
    }

    private fun save(slots: List<List<String>>) {
        prefs.edit()
            .putString(DOCK, slots.joinToString("|") { it.firstOrNull().orEmpty() })
            .putString(TRAYS, slots.joinToString("|") { it.drop(1).joinToString(",") })
            .apply()
    }

    /** Apps in the dock and its trays, for marking them in the drawer. */
    fun pinned(): List<String> = slots().flatten()

    /** Puts [key] in [slot]'s own place, or empties the slot and its tray. */
    fun setSlot(slot: Int, key: String?) {
        val now = slots().map { it - setOfNotNull(key) }.toMutableList()
        now[slot] = if (key == null) emptyList() else listOf(key) + now[slot].drop(1)
        save(now)
    }

    /**
     * Adds [key] to [slot]: its own place if empty, else the tray's next spot.
     * An app lives in one place only, so it leaves anywhere else. False when full.
     */
    fun add(slot: Int, key: String): Boolean {
        val now = slots().map { it - key }.toMutableList()
        if (now[slot].size >= TRAY_SIZE) return false
        now[slot] = now[slot] + key
        save(now)
        return true
    }

    /** Takes [key] out of [slot]; if it was the slot's own app, the tray's nearest takes its place. */
    fun remove(slot: Int, key: String) {
        save(slots().toMutableList().also { it[slot] = it[slot] - key })
    }

    /** Moves the app at [index] one place nearer the slot; at 1, it becomes the slot's own app. */
    fun moveDown(slot: Int, index: Int) {
        val now = slots().toMutableList()
        val apps = now[slot].toMutableList()
        if (index !in 1 until apps.size) return
        apps[index] = apps[index - 1].also { apps[index - 1] = apps[index] }
        now[slot] = apps
        save(now)
    }

    /**
     * Holding an app in the drawer: out of the dock if it's there, else into the
     * first empty slot, then the first tray with room, or the last slot.
     */
    fun togglePin(entry: AppEntry) {
        val now = slots()
        val at = now.indexOfFirst { entry.key in it }
        when {
            at >= 0 -> remove(at, entry.key)
            now.any { it.isEmpty() } -> setSlot(now.indexOfFirst { it.isEmpty() }, entry.key)
            now.any { it.size < TRAY_SIZE } -> add(now.indexOfFirst { it.size < TRAY_SIZE }, entry.key)
            else -> setSlot(DOCK_SIZE - 1, entry.key)
        }
    }

    /** An app's icon at [sizePx], for the dock. Reads the package, so call off the main thread. */
    fun icon(entry: AppEntry, sizePx: Int): ImageBitmap? = runCatching {
        context.packageManager.getActivityIcon(entry.component).toBitmap(sizePx, sizePx).asImageBitmap()
    }.getOrNull()

    fun launch(entry: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(entry.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        runCatching { context.startActivity(intent) }
    }

    /** The phone's own Settings app. */
    fun openSettings() = start(Intent(Settings.ACTION_SETTINGS))

    /** The system screen for picking the default home app: the way back to the stock launcher. */
    fun openHomeChooser() = start(Intent(Settings.ACTION_HOME_SETTINGS))

    private fun start(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    companion object {
        const val DOCK_SIZE = 6

        /** Apps one slot holds: its own, and four more in its tray. */
        const val TRAY_SIZE = 5
        private const val PINNED = "pinned"
        private const val DOCK = "dock"
        private const val TRAYS = "trays"
    }
}
