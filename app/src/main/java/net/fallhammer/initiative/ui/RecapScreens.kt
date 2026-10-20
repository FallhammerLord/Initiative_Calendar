package net.fallhammer.initiative.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import initiative.core.Formats
import initiative.core.Item
import initiative.core.Recap
import initiative.core.Recopy
import initiative.core.Status
import net.fallhammer.initiative.R

/**
 * "Previously on": after a lapse, what passed, what the app handled, and
 * what's ahead. Plain facts, no blame; one way back to the board.
 */
@Composable
fun RecapScreen(
    recap: Recap,
    onKeep: (Item) -> Unit,
    onDrop: (Item) -> Unit,
    onDropAll: () -> Unit,
    onBack: () -> Unit,
) {
    Screen {
        Title(stringResource(R.string.recap_title), style = Type.title)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Part(stringResource(R.string.recap_passed)) {
                if (recap.passed.isEmpty()) Body(stringResource(R.string.recap_nothing))
                recap.passed.forEach { a ->
                    val start = a.start ?: return@forEach
                    val line = if (a.status == Status.DONE) R.string.recap_item_done else R.string.recap_item_passed
                    Body(stringResource(line, a.title, Formats.weekday(start.toLocalDate()), Formats.clock(start)), color = Ink.text)
                }
            }
            val handled = recap.rerolled.isNotEmpty() || recap.dropped.isNotEmpty() || recap.ask.isNotEmpty()
            Part(stringResource(R.string.recap_handled)) {
                if (!handled) Body(stringResource(R.string.recap_nothing))
                recap.rerolled.forEach { Body(stringResource(R.string.recap_rerolled, it.title), color = Ink.text) }
                recap.dropped.forEach { Body(stringResource(R.string.recap_dropped, it.title), color = Ink.text) }
                if (recap.ask.isNotEmpty()) {
                    Spacer(Modifier.height(Space.s))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Body(stringResource(R.string.recap_ask), Modifier.weight(1f), color = Ink.text)
                        QuietButton(stringResource(R.string.recap_drop_all), onDropAll, color = Ink.accent)
                    }
                    recap.ask.forEach { item ->
                        Body(item.title, color = Ink.text)
                        ActionRow(
                            Action { SecondaryButton(stringResource(R.string.recap_keep), { onKeep(item) }, it, height = Tap.min) },
                            Action { SecondaryButton(stringResource(R.string.recap_drop), { onDrop(item) }, it, height = Tap.min) },
                        )
                        Spacer(Modifier.height(Space.s))
                    }
                }
            }
            Part(stringResource(R.string.recap_ahead)) {
                if (recap.ahead.isEmpty()) Body(stringResource(R.string.recap_nothing))
                recap.ahead.forEach { a ->
                    val start = a.start ?: return@forEach
                    Body(stringResource(R.string.recap_ahead_item, a.title, Formats.weekday(start.toLocalDate()), Formats.clock(start)), color = Ink.text)
                }
            }
        }
        Spacer(Modifier.height(Space.m))
        PrimaryButton(stringResource(R.string.recap_back), onBack, Modifier.fillMaxWidth())
    }
}

/** The weekly recopy card on the board: Start or Later. */
@Composable
fun RecopyCard(onStart: () -> Unit, onLater: () -> Unit) {
    Card {
        Title(stringResource(R.string.recopy_title), style = Type.title)
        Spacer(Modifier.height(Space.xs))
        Body(stringResource(R.string.recopy_hint), style = Type.bodySmall)
        Spacer(Modifier.height(Space.m))
        ActionRow(
            Action { SecondaryButton(stringResource(R.string.recopy_start), onStart, it, height = Tap.min) },
            Action { QuietButton(stringResource(R.string.recopy_later), onLater, it) },
        )
    }
}

/** One item of the recopy walk: keep it, move it, or drop it. */
@Composable
fun RecopyScreen(
    item: Item,
    onKeep: (Item) -> Unit,
    onThisWeek: (Item) -> Unit,
    onNextWeek: (Item) -> Unit,
    onDrop: (Item) -> Unit,
    onStop: () -> Unit,
) {
    // Catch taps on empty space so they never reach the board underneath.
    Screen(Modifier.pointerInput(Unit) { detectTapGestures { } }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Label(stringResource(R.string.recopy_label), Modifier.weight(1f))
            QuietButton(stringResource(R.string.recopy_stop), onStop)
        }
        Card(Modifier.weight(1f).verticalScroll(rememberScrollState()), arrangement = Arrangement.spacedBy(Space.m)) {
            Title(item.title)
            if (Recopy.nudge(item)) Body(stringResource(R.string.recopy_nudge))
        }
        Spacer(Modifier.height(Space.m))
        PrimaryButton(stringResource(R.string.recopy_keep), { onKeep(item) }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(Space.s))
        ActionRow(
            Action { SecondaryButton(stringResource(R.string.recopy_this_week), { onThisWeek(item) }, it) },
            Action { SecondaryButton(stringResource(R.string.recopy_next_week), { onNextWeek(item) }, it) },
        )
        Spacer(Modifier.height(Space.s))
        SecondaryButton(stringResource(R.string.recopy_drop), { onDrop(item) }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Part(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(Space.l))
    Label(title)
    Spacer(Modifier.height(Space.xs))
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) { content() }
}
