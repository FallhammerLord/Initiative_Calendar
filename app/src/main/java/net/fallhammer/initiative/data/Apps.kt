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

    /** Apps in the dock, for marking them in the drawer. */
    fun pinned(): List<String> = dock().filterNotNull()

    fun setSlot(slot: Int, key: String?) {
        val now = dock().toMutableList()
        now[slot] = key
        prefs.edit().putString(DOCK, now.joinToString("|") { it.orEmpty() }).apply()
    }

    /** Holding an app in the drawer: out of the dock if it's there, else into the first empty slot, or the last. */
    fun togglePin(entry: AppEntry) {
        val now = dock()
        val at = now.indexOf(entry.key)
        when {
            at >= 0 -> setSlot(at, null)
            null in now -> setSlot(now.indexOf(null), entry.key)
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
        private const val PINNED = "pinned"
        private const val DOCK = "dock"
    }
}
