package net.fallhammer.initiative.ui

import initiative.core.Item
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.fallhammer.initiative.data.Prefs

/**
 * Names you've used before, newest first, for the suggestions under a task's
 * name while you type. Done ones count; one deleted for good, or forgotten here,
 * doesn't.
 */
class NamesModel(private val parts: BoardParts, items: Flow<List<Item>>) {
    private val forgotten = MutableStateFlow<Set<String>>(emptySet())

    val names: StateFlow<List<String>> = combine(items, forgotten) { all, gone ->
        all.sortedByDescending { it.createdAt }
            .distinctBy { it.titleKey }
            .filter { it.titleKey !in gone }
            .map { it.title }
    }.stateIn(parts.scope, SharingStarted.Eagerly, emptyList())

    /** Reads the forgotten names. Call off the main thread. */
    fun load(prefs: Prefs) {
        forgotten.value = prefs.forgottenNames
    }

    /** Stops offering [name]; past tasks keep it. */
    fun forget(name: String) = save(forgotten.value + Item.keyFor(name))

    fun unforget(name: String) = save(forgotten.value - Item.keyFor(name))

    private fun save(keys: Set<String>) {
        forgotten.value = keys
        parts.scope.launch(Dispatchers.IO) { parts.graph.prefs.forgottenNames = keys }
    }
}
