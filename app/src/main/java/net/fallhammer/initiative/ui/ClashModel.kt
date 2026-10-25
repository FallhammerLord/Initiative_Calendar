package net.fallhammer.initiative.ui

import androidx.lifecycle.SavedStateHandle
import initiative.core.Clashes
import initiative.core.Item
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.fallhammer.initiative.R
import net.fallhammer.initiative.data.Prefs
import java.time.LocalDateTime

/**
 * Double-bookings: one of your anchors and a shared one from someone else at
 * the same time, worked out fresh from the items, never saved. Each shows as a
 * marker ahead of its day and a bookmark on it; a tap opens the choice. Keep
 * both, Skip theirs (for you only), Move mine (the edit form), or Not now.
 */
class ClashModel(
    private val parts: BoardParts,
    private val saved: SavedStateHandle,
    items: Flow<List<Item>>,
    clock: Flow<LocalDateTime>,
    private val edit: EditModel,
) {
    private val kept = MutableStateFlow<Set<String>>(emptySet())

    val clashes: StateFlow<List<Clashes.Clash>> = combine(items, clock, kept) { all, now, keep -> Clashes.find(all, now, keep) }
        .stateIn(parts.scope, SharingStarted.Eagerly, emptyList())

    /** The double-booking whose choice is open, by its key; kept across a restart. */
    val openKey: StateFlow<String> = saved.getStateFlow(OPEN, "")

    /** Reads the kept ones. Call off the main thread. */
    fun load(prefs: Prefs) {
        kept.value = prefs.keptClashes
    }

    fun open(clash: Clashes.Clash) {
        saved[OPEN] = clash.key
    }

    fun close() {
        saved[OPEN] = ""
    }

    private fun current(): Clashes.Clash? = clashes.value.firstOrNull { it.key == openKey.value }

    fun keepBoth() {
        val clash = current() ?: return close()
        close()
        parts.acted()
        // Only keys still in view are kept, so the set doesn't grow forever.
        val live = clashes.value.map { it.key }.toSet()
        val keys = kept.value.filter { it in live }.toSet() + clash.key
        kept.value = keys
        parts.scope.launch(Dispatchers.IO) { parts.graph.prefs.keptClashes = keys }
    }

    /** Theirs off your board only; their own copy stays. Undo puts it back. */
    fun skipTheirs() {
        val clash = current() ?: return close()
        close()
        parts.acted()
        parts.scope.launch {
            parts.graph.repo.drop(clash.theirs, note = "double-booked: skipped")
            parts.show(Flash(parts.text(R.string.clash_skipped), undo = clash.theirs))
        }
    }

    fun moveMine() {
        val clash = current() ?: return close()
        close()
        edit.open(clash.mine)
    }

    private companion object {
        const val OPEN = "clash"
    }
}
