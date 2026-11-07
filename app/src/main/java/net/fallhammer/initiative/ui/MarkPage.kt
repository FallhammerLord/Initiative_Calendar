package net.fallhammer.initiative.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import initiative.core.Formats
import initiative.core.Item
import initiative.core.Prep
import initiative.core.Status
import net.fallhammer.initiative.R
import java.time.LocalDate

/**
 * A marker's page, opened from its panel: its days, its note, its heads-up, and
 * the prep tasks that get you ready for it, with plain ticks and a line to add
 * one. A prep task waits here until the heads-up opens, then joins the board.
 * [startAdding] puts the cursor in the line, for Add a prep task.
 */
@Composable
fun MarkPage(
    marker: Item,
    tasks: List<Item>,
    today: LocalDate,
    onAdd: (String) -> Boolean,
    onTick: (Item, Boolean) -> Unit,
    onEditTask: (Item) -> Unit,
    onEditMarker: (Item) -> Unit,
    onClose: () -> Unit,
    startAdding: Boolean = false,
) {
    var text by rememberSaveable(marker.id) { mutableStateOf("") }
    val cursor = remember { FocusRequester() }
    LaunchedEffect(marker.id) { if (startAdding || tasks.isEmpty()) runCatching { cursor.requestFocus() } }
    fun submit() {
        if (text.isNotBlank() && onAdd(text)) text = ""
    }
    Screen {
        Strip {
            Title(marker.title, style = Type.title, modifier = Modifier.weight(1f))
            QuietButton(stringResource(R.string.mark_page_back), onClose)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(Space.panel))
            Card(inner = Space.l, arrangement = Arrangement.spacedBy(Space.inside)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    val accent = Ink.accent
                    Box(Modifier.size(Space.xl).drawBehind { markGlyph(marker.icon, accent) })
                    Body(markDaysText(marker), color = Ink.text)
                }
                val ahead = marker.headsUpDays
                Body(
                    if (ahead == 0) stringResource(R.string.mark_page_no_heads_up) else stringResource(R.string.mark_page_heads_up, pluralStringResource(R.plurals.days, ahead, ahead)),
                    style = Type.bodySmall,
                )
                if (marker.notes.isNotBlank()) Body(marker.notes, color = Ink.text, style = Type.bodySmall)
                SecondaryButton(stringResource(R.string.mark_page_edit), { onEditMarker(marker) }, Modifier.fillMaxWidth(), height = Tap.min)
            }
            Spacer(Modifier.height(Space.panel))
            Card(inner = Space.l, arrangement = Arrangement.spacedBy(Space.inside)) {
                Label(stringResource(R.string.mark_page_tasks))
                if (tasks.isEmpty()) Body(stringResource(R.string.mark_page_none), style = Type.bodySmall)
                tasks.forEach { task ->
                    val done = task.status == Status.DONE
                    val detail = when {
                        done -> stringResource(R.string.mark_page_done)
                        Prep.waiting(task, today) -> stringResource(R.string.mark_page_waits, Formats.day(task.earliestStart!!))
                        else -> stringResource(R.string.mark_page_on_board)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { CheckRow(task.title, detail, done) { onTick(task, it) } }
                        QuietButton(stringResource(R.string.mark_edit), { onEditTask(task) })
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.mark_page_add_field), style = Type.bodySmall) },
                    textStyle = Type.body.copy(color = Ink.text),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth().focusRequester(cursor).keepAboveKeyboard(),
                )
                Body(stringResource(R.string.mark_page_add_hint), style = Type.bodySmall)
            }
            Spacer(Modifier.height(Space.panel))
        }
        PrimaryButton(stringResource(R.string.mark_page_add), ::submit, Modifier.fillMaxWidth())
    }
}

/** A marker's day, or its first and last days. */
@Composable
fun markDaysText(marker: Item): String {
    val days = marker.markDays ?: return stringResource(R.string.mark_all_day)
    return if (days.start == days.endInclusive) Formats.day(days.start) else stringResource(R.string.mark_span, Formats.day(days.start), Formats.day(days.endInclusive))
}
