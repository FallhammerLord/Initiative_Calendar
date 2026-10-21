package net.fallhammer.initiative.ui

import androidx.lifecycle.SavedStateHandle
import initiative.core.Freq
import initiative.core.Item
import initiative.core.Kind
import initiative.core.Recurrence
import initiative.core.Status
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

    fun cancel() {
        cancelDupe()
        set(null)
    }

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

    /**
     * Anchor, float, or marker. A new anchor starts at the next whole hour, on the
     * marker's day if it was one; a new marker is all day, on the day it had.
     */
    fun setKind(kind: Kind) = change { d ->
        val day = d.start?.toLocalDate() ?: d.earliestStart ?: LocalDate.now()
        when {
            d.kind == kind -> d
            kind == Kind.ANCHOR -> d.copy(kind = kind, start = if (d.isMark) day.atTime(nextHour().toLocalTime()) else d.start ?: nextHour(), end = null, leadMinutes = Item.DEFAULT_LEAD)
            kind == Kind.MARK -> d.copy(kind = kind, start = day.atStartOfDay(), end = null, earliestStart = null, leadMinutes = 0)
            else -> d.copy(kind = kind, start = null, end = null, earliestStart = if (d.isMark) day else d.earliestStart, leadMinutes = Item.DEFAULT_LEAD)
        }
    }

    /** A marker's first day; a span keeps its length. */
    fun setMarkDay(date: LocalDate) = change { d ->
        val first = d.start?.toLocalDate() ?: date
        val days = d.end?.let { java.time.temporal.ChronoUnit.DAYS.between(first, it.toLocalDate()) } ?: 0
        d.copy(start = date.atStartOfDay(), end = if (days > 0) date.plusDays(days).atStartOfDay() else null)
    }

    /** A marker's last day; null, or a day not after the first, makes it one day. */
    fun setMarkUntil(date: LocalDate?) = change { d ->
        val first = d.start?.toLocalDate() ?: return@change d
        d.copy(end = date?.takeIf { it.isAfter(first) }?.atStartOfDay())
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

    private val _dupe = MutableStateFlow<Item?>(null)

    /**
     * Saving would start a series named like one already running: that one,
     * while the form asks Replace it or Keep both.
     */
    val dupe: StateFlow<Item?> = _dupe.asStateFlow()
    private var pending: Triple<String, String, List<Step>>? = null

    fun save(title: String, notes: String, steps: List<Step>) {
        val d = _draft.value ?: return
        if (title.isBlank()) {
            parts.show(Flash(parts.text(R.string.edit_need_title)))
            return
        }
        val old = current().firstOrNull { it.id == d.id }
        // A one-off becoming a series: does one by that name already run?
        if (old != null && old.templateId == null && _rule.value != null) {
            val key = d.copy(title = title.trim()).titleKey
            current().firstOrNull { it.isTemplate && it.status != Status.DROPPED && it.titleKey == key }?.let {
                pending = Triple(title, notes, steps)
                _dupe.value = it
                return
            }
        }
        commit(title, notes, steps, replace = null)
    }

    /** The answer to "already repeats": [replace] stops that series from today first. */
    fun answerDupe(replace: Boolean) {
        val match = _dupe.value ?: return
        val (title, notes, steps) = pending ?: return
        _dupe.value = null
        pending = null
        commit(title, notes, steps, replace = match.takeIf { replace })
    }

    fun cancelDupe() {
        _dupe.value = null
        pending = null
    }

    private fun commit(title: String, notes: String, steps: List<Step>, replace: Item?) {
        val d = _draft.value ?: return
        val old = current().firstOrNull { it.id == d.id }
        val rule = _rule.value
        val series = rule != originalRule || (old?.templateId != null && _later.value)
        set(null)
        if (old == null) return
        parts.acted()
        parts.scope.launch {
            replace?.let { graph.repo.stopSeries(it, LocalDate.now()) }
            val edited = edited(d, title, notes, steps)
            if (series) graph.repo.editSeries(old, edited, rule, LocalDateTime.now()) else graph.repo.edit(old, edited)
            parts.show(Flash(parts.text(R.string.edit_saved)))
        }
    }

    /** The draft with what was typed; empty steps are dropped. */
    private fun edited(d: Item, title: String, notes: String, steps: List<Step>): Item {
        val kept = steps.map { it.copy(title = it.title.trim()) }.filter { it.title.isNotEmpty() }
        return d.copy(title = title.trim().ifEmpty { d.title }, notes = notes.trim(), steps = kept)
    }

    /** Done from the form, with what was typed kept, and Undo. Any open item, Now or not. */
    fun done(title: String, notes: String, steps: List<Step>) {
        val d = _draft.value ?: return
        val old = current().firstOrNull { it.id == d.id }
        set(null)
        if (old == null) return
        parts.acted()
        parts.scope.launch {
            val edited = edited(d, title, notes, steps)
            if (edited != old) graph.repo.edit(old, edited)
            graph.repo.done(edited, LocalDateTime.now())
            parts.show(Flash(parts.text(R.string.flash_done), undo = old))
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

    /**
     * Off the board, with Undo. For a time of a series, [later] also stops the
     * series and takes off its later times; Undo puts it all back.
     */
    fun remove(later: Boolean = false) {
        val d = _draft.value ?: return
        val old = current().firstOrNull { it.id == d.id }
        set(null)
        if (old == null) return
        parts.acted()
        parts.scope.launch {
            if (later && old.templateId != null) {
                val (before, off) = graph.repo.removeLater(old, LocalDateTime.now())
                parts.show(Flash(parts.text(R.string.series_removed_later), undo = before.first(), undoAlso = before.drop(1), readd = off))
            } else {
                graph.repo.drop(old, note = "edited: removed")
                parts.show(Flash(parts.text(R.string.edit_removed), undo = old))
            }
        }
    }

    /** A removed item off the ledger for good, as if it never was; Undo puts it back. */
    fun deleteForGood() {
        val d = _draft.value ?: return
        val old = current().firstOrNull { it.id == d.id }
        set(null)
        if (old == null) return
        parts.acted()
        parts.scope.launch {
            graph.repo.deleteForGood(old)
            parts.show(Flash(parts.text(R.string.edit_deleted), readd = listOf(old)))
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
