package net.fallhammer.initiative.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.fallhammer.initiative.data.AppEntry

/** The app drawer's list, the pinned row, and the exits to the normal phone. */
class LauncherModel(private val parts: BoardParts) {
    private val apps get() = parts.graph.apps

    private val _installed = MutableStateFlow<List<AppEntry>>(emptyList())
    val installed: StateFlow<List<AppEntry>> = _installed.asStateFlow()

    private val _pinned = MutableStateFlow<List<String>>(emptyList())
    val pinned: StateFlow<List<String>> = _pinned.asStateFlow()

    /** Reads the installed apps and pins. Call off the main thread. */
    fun load() {
        _pinned.value = apps.pinned()
        _installed.value = apps.installed()
    }

    fun launch(entry: AppEntry) = apps.launch(entry)

    fun togglePin(entry: AppEntry) {
        parts.scope.launch(Dispatchers.IO) {
            apps.togglePin(entry)
            _pinned.value = apps.pinned()
        }
    }

    fun openSettings() = apps.openSettings()
    fun openHomeChooser() = apps.openHomeChooser()
}
