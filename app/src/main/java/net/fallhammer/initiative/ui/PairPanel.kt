package net.fallhammer.initiative.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import initiative.core.Item
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import initiative.core.Formats
import initiative.core.Outcome
import initiative.core.SharedLine
import net.fallhammer.initiative.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * One paired person's panel, from a tap on their pairmark, over the dimmed
 * board: who, how fresh, Ping; their day report, when they share it; and their
 * month, with a mark on each day that holds something, a tap listing that day.
 * All of it from their last sync, read-only. Below, what's just for them:
 * tasks added here for them, off this board, and Add for them. [extra] is room
 * for what more there is to do with them.
 */
@Composable
fun PairPanel(
    view: PairView,
    now: LocalDateTime,
    onPing: () -> Unit,
    onClose: () -> Unit,
    forThem: List<Item> = emptyList(),
    onAddFor: ((String) -> Boolean)? = null,
    onEditFor: (Item) -> Unit = {},
    extra: @Composable () -> Unit = {},
) {
    val title = stringResource(R.string.pair_panel_title, view.peer.nickname)
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.scrim)
            // A tap outside the panel closes it; taps on the panel stay there.
            .pointerInput(Unit) { detectTapGestures { onClose() } },
    ) {
        Card(
            Modifier
                .padding(horizontal = Space.l, vertical = Space.xl)
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures { } }
                .semantics { paneTitle = title },
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Header(view, now, onPing, onClose)
                Spacer(Modifier.height(Space.zone))
                Report(view, now)
                Spacer(Modifier.height(Space.zone))
                Month(view, now.toLocalDate())
                if (onAddFor != null) {
                    Spacer(Modifier.height(Space.zone))
                    ForThem(view.peer.nickname, forThem, now, onAddFor, onEditFor)
                }
                extra()
            }
        }
    }
}

@Composable
private fun Header(view: PairView, now: LocalDateTime, onPing: () -> Unit, onClose: () -> Unit) {
    val lit = view.peek?.lit(now) == true
    Row(verticalAlignment = Alignment.CenterVertically) {
        PairBadge(view.peer)
        Column(Modifier.weight(1f).padding(start = Space.s)) {
            Text(view.peer.nickname, style = Type.heading, color = Ink.text, modifier = Modifier.semantics { heading() })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                PairLight(view.peer, lit)
                Text(seenText(view.peek, now), style = Type.caption, color = Ink.dim)
            }
        }
        PingButton(onPing)
        Spacer(Modifier.width(Space.inside))
        QuietButton(stringResource(R.string.report_close), onClose)
    }
}

/** Their day report, as their last sync brought it; or why there's none. */
@Composable
private fun Report(view: PairView, now: LocalDateTime) {
    val peek = view.peek
    val name = view.peer.nickname
    val day = peek?.at?.toLocalDate()
    val heading = if (day == null || day == now.toLocalDate()) {
        stringResource(R.string.pair_panel_report, name)
    } else {
        stringResource(R.string.pair_panel_report_on, name, Formats.day(day))
    }
    Text(heading, style = Type.bodySmall, color = Ink.dim, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(Space.inside))
    val report = peek?.report
    when {
        report == null -> Body(stringResource(R.string.pair_panel_no_report, name), style = Type.bodySmall)
        report.isEmpty() -> Body(stringResource(R.string.report_empty), style = Type.bodySmall)
        else -> report.forEach { ReportRow(it) }
    }
}

@Composable
private fun ReportRow(line: SharedLine) {
    val time = line.time?.let { Formats.clock(it) } ?: stringResource(R.string.report_any_time)
    val outcome = when (line.outcome) {
        Outcome.DONE -> stringResource(R.string.report_done)
        Outcome.PASSED -> stringResource(R.string.report_passed)
        Outcome.MOVED -> stringResource(R.string.report_moved)
        Outcome.OPEN -> null
    }
    val done = line.outcome == Outcome.DONE
    Row(
        Modifier.fillMaxWidth().heightIn(min = Tap.min).semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time, style = Type.caption, color = Ink.dim, modifier = Modifier.width(TIME_COLUMN))
        Column(Modifier.weight(1f)) {
            Text(line.title, style = Type.body, color = if (done) Ink.dim else Ink.text)
            outcome?.let { Text(it, style = Type.caption, color = if (done) Ink.accent else Ink.dim) }
        }
    }
}

/**
 * Their month: a grid with a dot on each day that holds something they share,
 * today outlined. A tap lists that day below, today to start.
 */
@Composable
private fun Month(view: PairView, today: LocalDate) {
    val items = view.peek?.items.orEmpty()
    val color = PairColors.of(view.peer.color, LocalLook.current.palette)
    var picked by rememberSaveable(view.peer.id) { mutableLongStateOf(today.toEpochDay()) }
    // Months from this one: sharing reaches a month ahead, so into the next one too.
    var shift by rememberSaveable(view.peer.id) { mutableIntStateOf(0) }
    val pickedDay = LocalDate.ofEpochDay(picked)
    // Each day an entry covers: a marker over several days marks each of them.
    fun on(day: LocalDate) = items.filter { !it.start.toLocalDate().isAfter(day) && it.end.isAfter(day.atStartOfDay()) }
    val first = today.withDayOfMonth(1).plusMonths(shift.toLong())
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ArrowButton(stringResource(R.string.log_earlier_mark), stringResource(R.string.log_earlier), { if (shift > 0) shift-- })
        Text(
            Formats.month(first),
            style = Type.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = Ink.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        ArrowButton(stringResource(R.string.log_later_mark), stringResource(R.string.log_later), { if (shift < MONTHS_AHEAD) shift++ })
    }
    Spacer(Modifier.height(Space.inside))
    val lead = first.dayOfWeek.value - 1
    val days = (0 until first.lengthOfMonth()).map { first.plusDays(it.toLong()) }
    val cells: List<LocalDate?> = List(lead) { null } + days
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            DayOfWeek.entries.forEach { d ->
                Text(Formats.weekdayLetter(d), style = Type.caption, color = Ink.dim, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        cells.chunked(DAYS_A_WEEK).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                week.forEach { day ->
                    if (day == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        DayCell(day, on(day).isNotEmpty(), day == pickedDay, day == today, color, Modifier.weight(1f)) { picked = day.toEpochDay() }
                    }
                }
                repeat(DAYS_A_WEEK - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    Spacer(Modifier.height(Space.s))
    Text(Formats.longDay(pickedDay), style = Type.bodySmall.copy(fontWeight = FontWeight.Bold), color = Ink.text)
    val list = on(pickedDay)
    if (list.isEmpty()) {
        Body(stringResource(R.string.pair_panel_day_empty), style = Type.bodySmall)
    } else {
        list.forEach { s ->
            val time = if (s.start.toLocalDate() == pickedDay) Formats.clock(s.start) else stringResource(R.string.report_any_time)
            Row(Modifier.fillMaxWidth().heightIn(min = Tap.min).semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.CenterVertically) {
                Text(time, style = Type.caption, color = Ink.dim, modifier = Modifier.width(TIME_COLUMN))
                Text(s.title, style = Type.body, color = Ink.text, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, held: Boolean, picked: Boolean, today: Boolean, dot: androidx.compose.ui.graphics.Color, modifier: Modifier, onPick: () -> Unit) {
    val shape = LocalLook.current.buttonShape
    val name = Formats.longDay(date)
    val spoken = if (held) stringResource(R.string.pair_panel_day_held, name) else name
    Column(
        modifier
            .heightIn(min = Tap.min)
            .clip(shape)
            .background(if (picked) Ink.accent else Ink.surface)
            .then(if (today && !picked) Modifier.border(1.dp, Ink.accent, shape) else Modifier)
            .clickable(onClick = onPick)
            .semantics {
                contentDescription = spoken
                selected = picked
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(date.dayOfMonth.toString(), style = Type.bodySmall, color = if (picked) Ink.onAccent else Ink.text, modifier = Modifier.clearAndSetSemantics { })
        // A dot in their color for a day that holds something; no counts.
        Box(Modifier.size(DOT).clip(MarkDot).then(if (held) Modifier.background(if (picked) Ink.onAccent else dot) else Modifier))
    }
}

/**
 * Tasks just for them, added here: they go to their phone at the next sync and
 * stay off this board. A tap opens one to change; Add opens a line to type,
 * read as quick-add reads it.
 */
@Composable
private fun ForThem(name: String, items: List<Item>, now: LocalDateTime, onAdd: (String) -> Boolean, onEdit: (Item) -> Unit) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    if (items.isNotEmpty()) {
        Text(stringResource(R.string.pair_panel_for, name), style = Type.bodySmall, color = Ink.dim, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Space.inside))
        items.forEach { item ->
            val time = item.start?.takeIf { item.isAnchor }?.let { if (it.toLocalDate() == now.toLocalDate()) Formats.clock(it) else Formats.day(it.toLocalDate()) }
                ?: stringResource(R.string.report_any_time)
            Row(
                Modifier.fillMaxWidth().heightIn(min = Tap.min).clickable { onEdit(item) }.semantics(mergeDescendants = true) { },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(time, style = Type.caption, color = Ink.dim, modifier = Modifier.width(TIME_COLUMN))
                Text(item.title, style = Type.body, color = Ink.text, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(Space.inside))
    }
    if (!adding) {
        PrimaryButton(stringResource(R.string.pair_panel_add, name), { adding = true }, Modifier.fillMaxWidth())
        return
    }
    val cursor = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { cursor.requestFocus() } }
    fun submit() {
        if (onAdd(text)) {
            text = ""
            adding = false
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        label = { Text(stringResource(R.string.pair_panel_add_field, name), style = Type.bodySmall) },
        textStyle = Type.body.copy(color = Ink.text),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth().focusRequester(cursor),
    )
    Spacer(Modifier.height(Space.inside))
    ActionRow(
        Action(2f) { PrimaryButton(stringResource(R.string.pair_panel_add_go), ::submit, it) },
        Action { QuietButton(stringResource(R.string.edit_cancel), { text = ""; adding = false }, it) },
    )
}

private const val DAYS_A_WEEK = 7

/** How many months past this one the panel pages to: as far as sharing reaches. */
private const val MONTHS_AHEAD = 1
private val TIME_COLUMN = 84.dp
private val DOT = 6.dp
