package net.fallhammer.initiative.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import initiative.core.Forecast
import initiative.core.Formats
import initiative.core.Item
import initiative.core.Log
import initiative.core.LogEntry
import initiative.core.Status
import net.fallhammer.initiative.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The Log: planning beyond today. Day, Week, Month, and Year, each paged with
 * Earlier and Later; one search box across everything, done and removed items
 * included. Tap an item to edit it. Now stays in view along the bottom, and
 * leads back to the board.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogScreen(
    model: LogModel,
    nowItem: Item?,
    forecast: Forecast,
    flash: Flash?,
    onAdd: (String, LocalDate) -> Boolean,
    onUndo: () -> Unit,
    onEdit: (Item) -> Unit,
    onClose: () -> Unit,
) {
    val view by model.view.collectAsStateWithLifecycle()
    val zoom by model.zoom.collectAsStateWithLifecycle()
    val query by model.query.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    // Days in the Week view folded by hand, as epoch days. Days start open.
    var flipped by rememberSaveable { mutableStateOf(longArrayOf()) }
    val zooms = listOf(R.string.log_day, R.string.log_week, R.string.log_month, R.string.log_year).map { stringResource(it) }
    val nowId = nowItem?.id
    // Today's open tasks take their times and order from the forecast, as on the board.
    val planned = remember(forecast) { forecast.slots.associate { it.item.id to it.at } }
    // + Add swaps the search box for a quick-add line aimed at the day in view
    // (never a past one); Search swaps back. Not in Year, which has no one day.
    var adding by rememberSaveable { mutableStateOf(false) }
    val showAdd = adding && zoom != Zoom.YEAR
    val addDay = maxOf(view.date, view.today)

    Screen {
        Strip {
            Title(stringResource(R.string.plan_ahead), style = Type.title, modifier = Modifier.weight(1f))
            QuietButton(stringResource(R.string.log_back), onClose, color = Ink.accent)
        }
        Spacer(Modifier.height(Space.inside))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f)) { ChoiceRow(null, zooms, zoom.ordinal) { model.setZoom(Zoom.entries[it]) } }
            if (zoom != Zoom.YEAR) {
                QuietButton(
                    stringResource(if (showAdd) R.string.log_search_mode else R.string.log_add),
                    { adding = !showAdd },
                    color = Ink.accent,
                )
            }
        }
        Spacer(Modifier.height(Space.inside))
        if (showAdd) {
            AddField(addDay, view.today, onAdd)
        } else {
            OutlinedTextField(
                value = query,
                onValueChange = model::setQuery,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.log_search), style = Type.bodySmall, color = Ink.dim) },
                textStyle = Type.body.copy(color = Ink.text),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(Space.inside))
        val results = view.results
        // The period and its entries sit in one panel, so no words lie on the pattern.
        Card(Modifier.weight(1f), inner = Space.m) {
            if (results == null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ArrowButton(stringResource(R.string.log_earlier_mark), stringResource(R.string.log_earlier), { model.page(-1) })
                    Text(
                        periodTitle(view),
                        style = Type.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = Ink.text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    ArrowButton(stringResource(R.string.log_later_mark), stringResource(R.string.log_later), { model.page(1) })
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                when {
                    results != null -> entries(results, nowId, emptyMap(), showDate = true, empty = R.string.log_no_results, onEdit = onEdit)
                    view.zoom == Zoom.DAY -> entries(view.days.firstOrNull()?.second.orEmpty(), nowId, planned, onEdit = onEdit)
                    // A day with nothing on it has nothing to fold: just its name.
                    view.zoom == Zoom.WEEK -> view.days.forEach { (date, list) ->
                        if (list.isEmpty()) {
                            item { DayHeader(date, view.today, open = true, onToggle = null) }
                        } else {
                            val open = date.toEpochDay() !in flipped
                            item {
                                DayHeader(date, view.today, open) {
                                    val day = date.toEpochDay()
                                    flipped = if (day in flipped) flipped.filter { it != day }.toLongArray() else flipped + day
                                }
                            }
                            if (open) entries(list, nowId, planned, onEdit = onEdit)
                        }
                    }
                    view.zoom == Zoom.MONTH -> {
                        item { MonthGrid(view, model::pick) }
                        item { DayHeader(view.date, view.today, open = true, onToggle = null) }
                        entries(view.days.firstOrNull { it.first == view.date }?.second.orEmpty(), nowId, planned, onEdit = onEdit)
                    }
                    else -> items(view.months) { MonthRow(it, view.today, model::openMonth) }
                }
            }
        }
        Spacer(Modifier.height(Space.inside))
        // What was just done (Added, Saved), here where it was done, with Undo when there is one.
        flash?.let {
            FlashLine(it.text, if (it.undo != null) stringResource(R.string.action_undo) else null, onUndo)
            Spacer(Modifier.height(Space.inside))
        }
        NowBar(nowItem, onClose)
    }
}

/** A list of entries, or a plain line when there are none. */
private fun LazyListScope.entries(
    list: List<LogEntry>,
    nowId: Long?,
    planned: Map<Long, LocalDateTime>,
    showDate: Boolean = false,
    empty: Int = R.string.log_empty_day,
    onEdit: (Item) -> Unit,
) {
    if (list.isEmpty()) {
        item { Body(stringResource(empty), Modifier.padding(vertical = Space.s), style = Type.bodySmall) }
        return
    }
    // An open task in today's forecast shows its planned time, in the forecast's order.
    fun at(e: LogEntry): LocalDateTime? = (if (!e.projected && e.item.isOpen) planned[e.item.id] else null) ?: e.time
    val ordered = if (showDate) list else list.sortedWith(compareBy({ it.date }, { at(it) == null }, { at(it) }))
    items(ordered) { EntryRow(it, at(it), nowId, showDate, onEdit) }
}

@Composable
private fun periodTitle(view: LogView): String = when (view.zoom) {
    Zoom.DAY -> if (view.date == view.today) stringResource(R.string.log_today, Formats.longDay(view.date)) else Formats.longDay(view.date)
    Zoom.WEEK -> Log.weekStart(view.date).let { Formats.span(it, it.plusDays(6)) }
    Zoom.MONTH -> Formats.month(view.date)
    Zoom.YEAR -> view.date.year.toString()
}

/** A date heading. In the Week view it folds and opens its day; [onToggle] null: a plain heading. */
@Composable
private fun DayHeader(date: LocalDate, today: LocalDate, open: Boolean, onToggle: (() -> Unit)?) {
    val name = Formats.longDay(date)
    // Folding a day is a button; a day with nothing to fold is a plain heading.
    RowButton(
        onToggle,
        Modifier.semantics { heading() },
        onClickLabel = stringResource(if (open) R.string.log_fold_day else R.string.log_show_day),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (date == today) stringResource(R.string.log_today, name) else name,
                style = Type.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = if (date == today) Ink.accent else Ink.text,
                modifier = Modifier.weight(1f),
            )
            if (onToggle != null) {
                Text(
                    stringResource(if (open) R.string.day_close_mark else R.string.log_folded_mark),
                    style = Type.body,
                    color = Ink.accent,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
    }
}

/**
 * One item: its time (or date, in search), title, and steps. Done, removed, and
 * passed items dim. A later time of a series that isn't made yet can't be
 * edited on its own, so it isn't tappable.
 */
@Composable
private fun EntryRow(entry: LogEntry, at: LocalDateTime?, nowId: Long?, showDate: Boolean, onEdit: (Item) -> Unit) {
    val item = entry.item
    val isNow = !entry.projected && item.id == nowId
    val time = when {
        showDate -> Formats.shortDate(entry.date)
        isNow -> stringResource(R.string.log_now)
        else -> at?.let { Formats.clock(it) } ?: stringResource(R.string.log_any_time)
    }
    val title = when {
        entry.projected -> stringResource(R.string.log_repeats, item.title)
        item.status == Status.DONE -> stringResource(R.string.log_done, item.title)
        item.status == Status.DROPPED -> stringResource(R.string.log_removed, item.title)
        item.status == Status.MISSED -> stringResource(R.string.log_passed, item.title)
        else -> item.title
    }
    val timeText = @Composable { m: Modifier -> Text(time, style = Type.bodySmall, color = if (isNow) Ink.accent else Ink.dim, modifier = m) }
    // Each entry opens its edit form, so it's a button; Now's is lit.
    RowButton(
        if (entry.projected) null else ({ onEdit(item) }),
        onClickLabel = stringResource(R.string.edit_action),
        lit = isNow,
    ) {
        // At large font sizes the time sits above the title, rather than squeezing it.
        if (LocalDensity.current.fontScale > LARGE_FONT) {
            timeText(Modifier)
            EntryText(entry, title)
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                timeText(Modifier.width(TIME_COLUMN))
                EntryText(entry, title, Modifier.weight(1f))
            }
        }
    }
}

/** An entry's title and steps. */
@Composable
private fun EntryText(entry: LogEntry, title: String, modifier: Modifier = Modifier) {
    val item = entry.item
    Column(modifier) {
        Text(
            title,
            style = Type.bodySmall.copy(fontWeight = if (item.isAnchor) FontWeight.Bold else FontWeight.Normal),
            color = if (item.isOpen) Ink.text else Ink.dim,
        )
        if (!entry.projected) {
            item.steps.forEach { s ->
                Text(
                    stringResource(if (s.done) R.string.log_step_done else R.string.log_step_open, s.title),
                    style = Type.caption,
                    color = if (s.done) Ink.dim else Ink.text,
                )
            }
        }
    }
}

/**
 * Quick-add for the day in view, in place of the search box: read as quick-add
 * reads a line, on this day unless the text names another ("dentist fri").
 * Enter adds and leaves the line ready for the next one.
 */
@Composable
private fun AddField(day: LocalDate, today: LocalDate, onAdd: (String, LocalDate) -> Boolean) {
    // Saved across a restart, so a half-typed line survives.
    var text by rememberSaveable { mutableStateOf("") }
    val cursor = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { cursor.requestFocus() } }
    fun submit() {
        if (onAdd(text, day)) text = ""
    }
    val hint = if (day == today) stringResource(R.string.log_add_hint_today) else stringResource(R.string.log_add_hint, Formats.weekday(day))
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        placeholder = { Text(hint, style = Type.bodySmall, color = Ink.dim) },
        textStyle = Type.body.copy(color = Ink.text),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        trailingIcon = if (text.isBlank()) null else ({ QuietButton(stringResource(R.string.quick_add_button), ::submit, color = Ink.accent) }),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth().focusRequester(cursor),
    )
}

/** The month as a grid, Monday first. Days with fixed times carry a mark; tap a day to list it below. */
@Composable
private fun MonthGrid(view: LogView, onPick: (LocalDate) -> Unit) {
    val lead = view.date.withDayOfMonth(1).dayOfWeek.value - 1
    val cells: List<Pair<LocalDate, List<LogEntry>>?> = List(lead) { null } + view.days
    Column(Modifier.fillMaxWidth().padding(bottom = Space.s), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            DayOfWeek.values().forEach { d ->
                Text(Formats.weekdayLetter(d), style = Type.caption, color = Ink.dim, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        cells.chunked(DAYS_A_WEEK).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                week.forEach { cell ->
                    if (cell == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val (date, list) = cell
                        DayCell(date, list.any { it.item.isAnchor }, date == view.date, date == view.today, onPick, Modifier.weight(1f))
                    }
                }
                repeat(DAYS_A_WEEK - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, fixed: Boolean, picked: Boolean, today: Boolean, onPick: (LocalDate) -> Unit, modifier: Modifier) {
    val shape = LocalLook.current.buttonShape
    val name = Formats.longDay(date)
    val spoken = if (fixed) stringResource(R.string.log_has_fixed, name) else name
    val mark = if (picked) Ink.onAccent else Ink.accent
    val haptics = rememberHaptics()
    Column(
        modifier
            .heightIn(min = Tap.min)
            .clip(shape)
            .background(if (picked) Ink.accent else Ink.surface)
            .then(if (today && !picked) Modifier.border(1.dp, Ink.accent, shape) else Modifier)
            .clickable { haptics.tick(); onPick(date) }
            .semantics {
                contentDescription = spoken
                selected = picked
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = Type.bodySmall,
            color = if (picked) Ink.onAccent else Ink.text,
            modifier = Modifier.clearAndSetSemantics { },
        )
        // A small diamond for a day with fixed times; no counts.
        Box(Modifier.size(MARK).rotate(45f).then(if (fixed) Modifier.background(mark) else Modifier))
    }
}

/** One month in the Year view: its name and its fixed times. Tap to open the month. */
@Composable
private fun MonthRow(month: LogMonth, today: LocalDate, onOpen: (LocalDate) -> Unit) {
    val context = LocalContext.current
    val thisMonth = today.withDayOfMonth(1)
    val past = month.first < thisMonth
    val summary = when {
        month.anchors.isNotEmpty() -> month.anchors.joinToString(", ") {
            context.getString(R.string.log_month_anchor, it.item.title, it.date.dayOfMonth)
        }
        // A month with nothing fixed stays blank, rather than saying so twelve times.
        else -> ""
    }
    RowButton({ onOpen(month.first) }, onClickLabel = stringResource(R.string.log_open_month)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                Formats.monthName(month.first),
                style = Type.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = when {
                    month.first == thisMonth -> Ink.accent
                    past -> Ink.dim
                    else -> Ink.text
                },
                modifier = Modifier.width(MONTH_COLUMN),
            )
            Text(summary, style = Type.bodySmall, color = Ink.dim, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
    }
}

/** Now, along the bottom of the Log, with its next step. Tap to go back to the board. */
@Composable
private fun NowBar(item: Item?, onClick: () -> Unit) {
    val step = item?.nextStep
    val text = when {
        item == null -> stringResource(R.string.log_now_bar, stringResource(R.string.free_time))
        step != null -> stringResource(R.string.log_now_step, item.title, step.title)
        else -> stringResource(R.string.log_now_bar, item.title)
    }
    Strip(Modifier.clickable(onClickLabel = stringResource(R.string.log_back), onClick = onClick)) {
        Body(text, Modifier.weight(1f), color = Ink.text, style = Type.bodySmall)
        Text(stringResource(R.string.day_close_mark), style = Type.body, color = Ink.accent, modifier = Modifier.clearAndSetSemantics { })
    }
}

private const val DAYS_A_WEEK = 7
private val TIME_COLUMN = 104.dp
private val MONTH_COLUMN = 132.dp
private val MARK = 6.dp
