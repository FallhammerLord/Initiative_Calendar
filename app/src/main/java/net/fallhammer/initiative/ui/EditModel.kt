package net.fallhammer.initiative.ui

import androidx.lifecycle.SavedStateHandle
import initiative.core.Freq
import initiative.core.Item
import initiative.core.Kind
import initiative.core.Recurrence
import initiative.core.Step
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.fallhammer.initiative.R
import net.fallhammer.initiative.data.Backup
import net.fallhammer.initiative.data.toItem
import net.fallhammer.initiative.data.toRow
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The edit form: a draft of one item, saved in [saved] on every change so a
 * half-made edit survives a restart. Title, notes, and steps are typed in the
 * screen and handed over on Save.
 *
 * For a repeating item, the draft also holds how it repeats and whether the
 * edit covers this time only or this and later times. A change to how it
 * repeats always covers this and later.
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

    private val _rule = MutableStateFlow(saved.get<String>(RULE)?.let(::decodeRule))
    /** How the item repeats after the edit; null for a one-off. */
    val rule: StateFlow<Recurrence?> = _rule.asStateFlow()

    /** How it repeated when the form opened. */
    val originalRule: Recurrence? get() = saved.get<String>(RULE_BEFORE)?.let(::decodeRule)

    private val _later = MutableStateFlow(saved.get<Boolean>(LATER) ?: false)
    /** For one time of a series: whether the edit covers this and later times. */
    val later: StateFlow<Boolean> = _later.asStateFlow()

    val isOpen: Boolean get() = _draft.value != null

    fun open(item: Item) {
        val repeats = current().firstOrNull { it.id == item.templateId }?.recurrence
        saved[RULE_BEFORE] = encodeRule(repeats)
        setRule(repeats)
        setLater(false)
        set(item)
    }

    fun cancel() = set(null)

    fun setRule(rule: Recurrence?) {
        _rule.value = rule
        saved[RULE] = encodeRule(rule)
    }

    fun setLater(later: Boolean) {
        _later.value = later
        saved[LATER] = later
    }

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

    fun save(title: String, notes: String, steps: List<Step>) {
        val d = _draft.value ?: return
        if (title.isBlank()) {
            parts.show(Flash(parts.text(R.string.edit_need_title)))
            return
        }
        val old = current().firstOrNull { it.id == d.id }
        val rule = _rule.value
        val series = rule != originalRule || (old?.templateId != null && _later.value)
        set(null)
        if (old == null) return
        parts.acted()
        parts.scope.launch {
            // Empty steps are dropped.
            val kept = steps.map { it.copy(title = it.title.trim()) }.filter { it.title.isNotEmpty() }
            val edited = d.copy(title = title.trim(), notes = notes.trim(), steps = kept)
            if (series) graph.repo.editSeries(old, edited, rule, LocalDateTime.now()) else graph.repo.edit(old, edited)
            parts.show(Flash(parts.text(R.string.edit_saved)))
        }
    }

    /** A done or removed item back on the board. */
    fun putBack() {
        val d = _draft.value ?: return
        val old = current().firstOrNull { it.id == d.id }
        set(null)
        if (old == null) return
        parts.acted()
        parts.scope.launch {
            graph.repo.putBack(old)
            parts.show(Flash(parts.text(R.string.flash_restored)))
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
        const val RULE = "edit_rule"
        const val RULE_BEFORE = "edit_rule_before"
        const val LATER = "edit_later"

        // "WEEKLY:2:MONDAY,THURSDAY"; empty for a one-off.
        fun encodeRule(rule: Recurrence?): String =
            rule?.let { "${it.freq.name}:${it.interval}:${it.days.joinToString(",") { d -> d.name }}" }.orEmpty()

        fun decodeRule(text: String): Recurrence? = runCatching {
            if (text.isEmpty()) return@runCatching null
            val (freq, interval, days) = text.split(":")
            Recurrence(Freq.valueOf(freq), interval.toInt(), days.split(",").filter { it.isNotEmpty() }.map(DayOfWeek::valueOf).toSet())
        }.getOrNull()
    }
}
