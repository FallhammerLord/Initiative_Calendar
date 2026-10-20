package net.fallhammer.initiative.ui

import androidx.lifecycle.SavedStateHandle
import initiative.core.Item
import initiative.core.Kind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.fallhammer.initiative.R
import net.fallhammer.initiative.data.Backup
import net.fallhammer.initiative.data.toItem
import net.fallhammer.initiative.data.toRow
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The edit form: a draft of one item, saved in [saved] on every change so a
 * half-made edit survives a restart. Title and notes are typed in the screen
 * and handed over on Save.
 */
class EditModel(
    private val parts: BoardParts,
    private val saved: SavedStateHandle,
    private val current: () -> List<Item>,
) {
    private val graph get() = parts.graph
    private val _draft = MutableStateFlow(saved.get<String>(DRAFT)?.let(::decode))

    /** The item being edited, with changes so far; null when the form is closed. */
    val draft: StateFlow<Item?> = _draft.asStateFlow()

    val isOpen: Boolean get() = _draft.value != null

    fun open(item: Item) = set(item)

    fun cancel() = set(null)

    /** Changes one or more fields of the draft. */
    fun change(update: (Item) -> Item) = set(_draft.value?.let(update))

    /** Anchor or float. A new anchor starts at the next whole hour today. */
    fun setKind(kind: Kind) = change { d ->
        when {
            d.kind == kind -> d
            kind == Kind.ANCHOR -> d.copy(kind = kind, start = d.start ?: nextHour())
            else -> d.copy(kind = kind, start = null, end = null)
        }
    }

    /** Moves an anchor to [date], keeping its times. */
    fun setDate(date: LocalDate) = change { d ->
        val start = d.start ?: return@change d
        val shift = java.time.Period.between(start.toLocalDate(), date)
        d.copy(start = start.plus(shift), end = d.end?.plus(shift))
    }

    fun setStart(time: LocalTime) = change { d ->
        val day = d.start?.toLocalDate() ?: LocalDate.now()
        val start = day.atTime(time)
        // Keep the same length when the start moves.
        val length = d.start?.let { s -> d.end?.let { java.time.Duration.between(s, it) } }
        d.copy(start = start, end = length?.let { start.plus(it) })
    }

    /** An end that isn't after the start clears it, so the default length applies. */
    fun setEnd(time: LocalTime) = change { d ->
        val start = d.start ?: return@change d
        val end = start.toLocalDate().atTime(time)
        d.copy(end = end.takeIf { it.isAfter(start) })
    }

    fun save(title: String, notes: String) {
        val d = _draft.value ?: return
        if (title.isBlank()) {
            parts.show(Flash(parts.text(R.string.edit_need_title)))
            return
        }
        val old = current().firstOrNull { it.id == d.id }
        set(null)
        if (old == null) return
        parts.acted()
        parts.scope.launch {
            graph.repo.edit(old, d.copy(title = title.trim(), notes = notes.trim()))
            parts.show(Flash(parts.text(R.string.edit_saved)))
        }
    }

    /** Off the board, with Undo. */
    fun remove() {
        val d = _draft.value ?: return
        val old = current().firstOrNull { it.id == d.id }
        set(null)
        if (old == null) return
        parts.acted()
        parts.scope.launch {
            graph.repo.drop(old, note = "edited: removed")
            parts.show(Flash(parts.text(R.string.edit_removed), undo = old))
        }
    }

    private fun set(item: Item?) {
        _draft.value = item
        saved[DRAFT] = item?.let(::encode)
    }

    private fun nextHour(): LocalDateTime = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0).plusHours(1)

    private fun encode(item: Item): String = Backup.itemJson(item.toRow()).toString()

    private fun decode(text: String): Item? = runCatching { Backup.itemRow(JSONObject(text)).toItem() }.getOrNull()

    private companion object {
        const val DRAFT = "edit_draft"
    }
}
