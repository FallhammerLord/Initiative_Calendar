package net.fallhammer.initiative.ui

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.fallhammer.initiative.data.AppEntry
import net.fallhammer.initiative.data.Apps

/** The app drawer's list, the dock, and the exits to the normal phone. */
class LauncherModel(private val parts: BoardParts) {
    private val apps get() = parts.graph.apps

    private val _installed = MutableStateFlow<List<AppEntry>>(emptyList())
    val installed: StateFlow<List<AppEntry>> = _installed.asStateFlow()

    private val _pinned = MutableStateFlow<List<String>>(emptyList())
    /** Keys of the apps in the dock, for marking them in the drawer. */
    val pinned: StateFlow<List<String>> = _pinned.asStateFlow()

    private val _dock = MutableStateFlow<List<AppEntry?>>(List(Apps.DOCK_SIZE) { null })
    /** The dock's slots, in order; null for an empty slot or an app that's gone. */
    val dock: StateFlow<List<AppEntry?>> = _dock.asStateFlow()

    private val _slots = MutableStateFlow<List<List<AppEntry>>>(List(Apps.DOCK_SIZE) { emptyList() })
    /** Each slot's apps: its own first, then its tray's, nearest the slot first. Apps that are gone are left out. */
    val slots: StateFlow<List<List<AppEntry>>> = _slots.asStateFlow()

    private val _icons = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    val icons: StateFlow<Map<String, ImageBitmap>> = _icons.asStateFlow()

    /** Reads the installed apps, the dock, and its icons. Call off the main thread. */
    fun load() {
        _installed.value = apps.installed()
        readDock()
    }

    fun launch(entry: AppEntry) = apps.launch(entry)

    fun togglePin(entry: AppEntry) = write { apps.togglePin(entry) }

    fun setSlot(slot: Int, entry: AppEntry?) = write { apps.setSlot(slot, entry?.key) }

    /** Adds to a slot, or its tray; [onFull] runs, on the main thread, when there's no room. */
    fun add(slot: Int, entry: AppEntry, onFull: () -> Unit) {
        if ((_slots.value.getOrNull(slot)?.size ?: 0) >= Apps.TRAY_SIZE && _slots.value[slot].none { it.key == entry.key }) {
            onFull()
            return
        }
        write { apps.add(slot, entry.key) }
    }

    fun remove(slot: Int, entry: AppEntry) = write { apps.remove(slot, entry.key) }

    fun moveDown(slot: Int, index: Int) = write { apps.moveDown(slot, index) }

    fun openSettings() = apps.openSettings()
    fun openHomeChooser() = apps.openHomeChooser()

    private fun write(change: () -> Unit) {
        parts.scope.launch(Dispatchers.IO) {
            change()
            readDock()
        }
    }

    private fun readDock() {
        val byKey = _installed.value.associateBy { it.key }
        val slots = apps.slots().map { keys -> keys.mapNotNull(byKey::get) }
        _slots.value = slots
        _pinned.value = slots.flatten().map { it.key }
        _dock.value = slots.map { it.firstOrNull() }
        val size = (ICON_DP * parts.app.resources.displayMetrics.density).toInt()
        _icons.value = slots.flatten().mapNotNull { e -> (_icons.value[e.key] ?: apps.icon(e, size))?.let { e.key to it } }.toMap()
    }

    private companion object {
        const val ICON_DP = 44
    }
}
