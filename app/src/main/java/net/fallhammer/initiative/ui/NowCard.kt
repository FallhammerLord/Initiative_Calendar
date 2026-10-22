package net.fallhammer.initiative.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import initiative.core.Formats
import initiative.core.Item
import initiative.core.Status
import java.time.Duration
import net.fallhammer.initiative.R

/**
 * Now: the item, its steps, why it's Now, and the actions. The words scroll when
 * they overflow (large system fonts, many steps); the buttons stay below them,
 * always in view. [compact], while typing: just the title and any flash line.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NowCard(
    modifier: Modifier,
    ui: BoardUi,
    flash: Flash?,
    onDone: (Item) -> Unit,
    onSkip: (Item) -> Unit,
    onTomorrow: (Item) -> Unit,
    onUndo: () -> Unit,
    onFocus: (Item) -> Unit,
    onEdit: (Item) -> Unit,
    onStep: (Item, Int) -> Unit,
    compact: Boolean,
    onOnIt: (Item) -> Unit = {},
) {
    val board = ui.board
    val item = board?.now?.item
    // An anchor marked done whose time isn't over: it stays Now, folded to its
    // title and one "done" line, with nothing left to tap by mistake.
    val settled = item != null && item.isAnchor && !item.isOpen
    val haptics = rememberHaptics()
    val scroll = rememberScrollState()
    val hint = Ink.accent
    val surface = Ink.surface
    // On it locks brackets onto the card; Done wipes a scan line down it.
    val locking = rememberBlink(LOCK_MS)
    val wiping = rememberBlink(CHANGE_MS)
    BlinkOnRise(item?.status == Status.ACTIVE, locking)
    val effects = Modifier.drawWithContent {
        drawContent()
        if (locking.playing) lockOn(locking.t, hint)
        if (wiping.playing) scanDown(wiping.t, hint)
    }
    Card(modifier.then(effects), now = true, vertical = Space.l) {
        Column(
            Modifier
                .weight(1f, fill = false)
                // When the words run past the card: a slim bar in the right margin, and the
                // last line fading out, so it's clear there's more below.
                .drawWithContent {
                    drawContent()
                    val max = scroll.maxValue
                    if (max <= 0) return@drawWithContent
                    val shown = size.height
                    val thumb = shown * shown / (shown + max)
                    val top = (shown - thumb) * scroll.value / max
                    drawRect(hint.copy(alpha = HINT_ALPHA), Offset(size.width + SCROLL_INSET.toPx(), top), Size(SCROLL_WIDTH.toPx(), thumb))
                    if (scroll.value < max) {
                        val fade = FADE.toPx()
                        drawRect(
                            Brush.verticalGradient(listOf(surface.copy(alpha = 0f), surface), startY = shown - fade, endY = shown),
                            topLeft = Offset(0f, shown - fade),
                            size = Size(size.width, fade),
                        )
                    }
                }
                .verticalScroll(scroll, enabled = scroll.maxValue > 0),
        ) {
            // Tapping the title area opens the edit form; holding it starts focus,
            // with the Focus button, once on it, as the visible twin. TalkBack reads both actions.
            val editLabel = stringResource(R.string.edit_action)
            val focusLabel = stringResource(R.string.focus_action)
            Column(
                if (item == null) {
                    Modifier
                } else {
                    Modifier.combinedClickable(
                        onClickLabel = editLabel,
                        onClick = { onEdit(item) },
                        onLongClickLabel = if (item.isAnchor) null else focusLabel,
                        onLongClick = if (item.isAnchor) null else ({ onFocus(item) }),
                    )
                },
            ) {
                // Until the database loads, the card stays blank rather than showing a wrong "Free time".
                // A new Now fades in and rises a little, so the change is seen, not missed.
                if (board != null) {
                    AnimatedContent(
                        targetState = item,
                        contentKey = { it?.id },
                        transitionSpec = {
                            (fadeIn(tween(NOW_IN_MS)) + slideInVertically(tween(NOW_IN_MS)) { it / NOW_RISE }) togetherWith fadeOut(tween(NOW_OUT_MS))
                        },
                        label = "now",
                    ) { shown ->
                        Column {
                            // The card's place says Now; TalkBack hears it too.
                            val title = shown?.title ?: stringResource(R.string.free_time)
                            val spoken = stringResource(R.string.now_spoken, title)
                            Title(title, modifier = Modifier.semantics { contentDescription = spoken })
                            if (shown != null && shown.isAnchor && shown.isOpen) Body(Formats.anchorLine(shown), style = Type.bodySmall)
                        }
                    }
                }
            }
            if (!compact && board != null) {
                Spacer(Modifier.height(Space.xs))
                if (settled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.now_done_mark), style = Type.body, color = Ink.accent, modifier = Modifier.clearAndSetSemantics { })
                        Body(board.now.reason, Modifier.padding(start = Space.s))
                    }
                } else {
                    // On it, once tapped, leads the reason line, with when.
                    val since = item?.activeSince
                    if (item != null && item.status == Status.ACTIVE && since != null) {
                        val lead = stringResource(R.string.now_on_it, Formats.clock(since))
                        val join = stringResource(R.string.now_join)
                        val accent = Ink.accent
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = accent)) { append(lead) }
                                append(join)
                                append(board.now.reason)
                            },
                            style = Type.body,
                            color = Ink.dim,
                        )
                    } else {
                        Body(board.now.reason)
                    }
                }
                // A task back from a wait, waiting on this one: said in words as well as on its tab.
                board.waits.firstOrNull { it.ready(ui.now) && it.id != item?.id }?.let { back ->
                    Body(
                        stringResource(R.string.waitmark_next, back.title, Formats.clock(back.waitUntil!!)),
                        style = Type.bodySmall,
                    )
                }
            }
            // Steps tick off right here; Done asks about any left open.
            if (!compact && !settled && item != null && item.steps.isNotEmpty()) {
                Spacer(Modifier.height(Space.s))
                item.steps.forEachIndexed { i, step ->
                    // A step with a wait says so, so ticking it isn't a surprise.
                    val text = if (step.waitMinutes > 0 && !step.done) {
                        stringResource(R.string.step_then_wait, step.title, Formats.countdown(Duration.ofMinutes(step.waitMinutes.toLong())))
                    } else {
                        step.title
                    }
                    StepRow(text, step.done) { onStep(item, i) }
                }
            }
        }
        Column(Modifier.padding(top = if (compact && flash == null) Space.xs else Space.m)) {
            flash?.let {
                FlashLine(it.text, if (it.canUndo) stringResource(R.string.action_undo) else null, onUndo)
                Spacer(Modifier.height(Space.m))
            }
            // A done anchor still showing until its end has nothing left to act on.
            if (!compact && item != null && item.isOpen) {
                val done = stringResource(R.string.action_done)
                val later = stringResource(R.string.action_skip)
                val tomorrow = stringResource(R.string.action_tomorrow)
                val focusWord = stringResource(R.string.focus_action)
                // Done confirms; with steps open it only asks, so it just ticks. Later is a neutral tick.
                val doneTap = {
                    if (item.nextStep == null) {
                        haptics.confirm()
                        wiping.play()
                    } else {
                        haptics.tick()
                    }
                    onDone(item)
                }
                val laterTap = { haptics.tick(); onSkip(item) }
                // Up next: On it, or Later for not now. Once on it, the row turns
                // to what comes after: Done, Later, and for tasks Tomorrow and Focus.
                when {
                    item.status != Status.ACTIVE -> ActionRow(
                        Action(ON_IT_SHARE) { PrimaryButton(stringResource(R.string.on_it_action), { haptics.tick(); onOnIt(item) }, it) },
                        Action { SecondaryButton(later, laterTap, it) },
                    )
                    item.isAnchor -> ActionRow(
                        Action { PrimaryButton(done, doneTap, it) },
                        Action { SecondaryButton(later, laterTap, it) },
                    )
                    else -> ActionRow(
                        Action { PrimaryButton(done, doneTap, it) },
                        Action { SecondaryButton(later, laterTap, it) },
                        Action(TOMORROW_SHARE) { SecondaryButton(tomorrow, { haptics.tick(); onTomorrow(item) }, it) },
                        Action { SecondaryButton(focusWord, { onFocus(item) }, it) },
                    )
                }
            }
        }
    }
}

// The Now title's change: in, out, and how far it rises (a share of its height).
private const val NOW_IN_MS = 260
private const val NOW_OUT_MS = 120
private const val NOW_RISE = 6

/** On the Now card's row: On it takes most of it, beside Later; Tomorrow is the longest word. */
private const val ON_IT_SHARE = 2.4f
private const val TOMORROW_SHARE = 1.5f

// The Now card's scroll hint: the bar's width, its place in the margin, strength, and the fade.
private val SCROLL_WIDTH = 3.dp
private val SCROLL_INSET = 10.dp
private const val HINT_ALPHA = 0.7f
private val FADE = 24.dp
