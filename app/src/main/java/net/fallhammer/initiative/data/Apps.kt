package net.fallhammer.initiative.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

data class AppEntry(val label: String, val component: ComponentName) {
    val key: String get() = component.flattenToString()
}

/** Installed apps for the drawer, and the pinned row kept in preferences. */
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

    fun pinned(): List<String> = prefs.getString(PINNED, "").orEmpty().split("|").filter { it.isNotEmpty() }

    fun togglePin(entry: AppEntry) {
        val now = pinned().toMutableList()
        if (!now.remove(entry.key)) now += entry.key
        prefs.edit().putString(PINNED, now.joinToString("|")).apply()
    }

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

    private companion object {
        const val PINNED = "pinned"
    }
}
