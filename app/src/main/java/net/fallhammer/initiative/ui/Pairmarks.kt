package net.fallhammer.initiative.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import initiative.core.Peek
import net.fallhammer.initiative.R
import net.fallhammer.initiative.data.PeerRow
import java.time.LocalDateTime

/** A paired person as the board shows them: who, and their last peek. */
data class PairView(val peer: PeerRow, val peek: Peek?)

/**
 * The pairmark row, just under the Plan ahead panel on the left. The front
 * person's pairmark points right: badge, nickname, light, how fresh, and what
 * they're probably doing. The others wait as tabs beside it; a tap brings one
 * to the front. A half-tab on the screen's left edge pulls the row out or folds
 * it away into the edge; folded, the tab is all that stays.
 */
@Composable
fun PairmarkRow(
    pairs: List<PairView>,
    front: Long,
    now: LocalDateTime,
    shown: Boolean,
    onFront: (Long) -> Unit,
    onShown: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val motion = rememberMotionOn()
    BoxWithConstraints(modifier.fillMaxWidth().heightIn(min = Tap.min)) {
        val widest = maxWidth * PAIRMARK_SHARE
        AnimatedVisibility(
            visible = shown && pairs.isNotEmpty(),
            modifier = Modifier.align(Alignment.CenterStart),
            enter = if (motion) slideInHorizontally(tween(SLIDE_MS)) { -it } + fadeIn(tween(SLIDE_MS)) else EnterTransition.None,
            exit = if (motion) slideOutHorizontally(tween(SLIDE_MS)) { -it } + fadeOut(tween(SLIDE_MS)) else ExitTransition.None,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val first = pairs.firstOrNull { it.peer.id == front } ?: pairs.first()
                Front(first, now, Modifier.widthIn(max = widest))
                pairs.filter { it.peer.id != first.peer.id }.forEach { p ->
                    Spacer(Modifier.padding(start = Space.xs))
                    Tab(p, now) { haptics.tick(); onFront(p.peer.id) }
                }
            }
        }
        // Over the row's start, in the board's side margin at the screen's edge.
        EdgeTab(shown, Modifier.align(Alignment.CenterStart).offset(x = -Space.l)) { haptics.toggle(!shown); onShown(!shown) }
    }
}

/**
 * The pull tab: drawn [TAB_WIDTH] wide against the screen's edge, its mark
 * pointing out while folded and back in while out. Its touch area is a full
 * [Tap.min] square, reaching a little over the pairmark's padded start.
 */
@Composable
private fun EdgeTab(shown: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val look = LocalLook.current
    val shape = remember(look.handle) { EdgeTabShape(look.handle) }
    val mark = Ink.accent
    val label = stringResource(if (shown) R.string.pairs_hide else R.string.pairs_show)
    Box(
        modifier
            .size(Tap.min)
            .clickable(role = Role.Button, onClick = onTap)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(TAB_WIDTH, Tap.min)
                .clip(shape)
                .background(Ink.surface)
                .border(look.came, mark, shape)
                .drawBehind {
                    // A chevron: out (right) to pull, back in (left) to fold.
                    val cx = size.width * TAB_MARK_X
                    val half = TAB_MARK.toPx()
                    val dx = if (shown) -half / 2 else half / 2
                    val path = Path().apply {
                        moveTo(cx - dx, size.height / 2 - half)
                        lineTo(cx + dx, size.height / 2)
                        lineTo(cx - dx, size.height / 2 + half)
                    }
                    drawPath(path, mark, style = Stroke(GLYPH_LINE.toPx(), cap = StrokeCap.Round))
                }
                .clearAndSetSemantics { },
        )
    }
}

/**
 * Ping, for the Plan ahead panel while anyone is paired: it sends radar rings
 * out from its button as it looks for them nearby.
 */
@Composable
fun PingButton(onPing: () -> Unit) {
    val radio = rememberBlink(RADAR_MS)
    val ring = Ink.accent
    Box(Modifier.drawWithContent { drawContent(); if (radio.playing) radar(radio.t, ring) }) {
        GlyphButton(stringResource(R.string.pair_ping), { radio.play(); onPing() }) { bluetooth(it) }
    }
}

/** The front pairmark, from the screen's left edge. */
@Composable
private fun Front(p: PairView, now: LocalDateTime, modifier: Modifier) {
    val look = LocalLook.current
    val shape = remember(look.handle) { BookmarkShape(look.handle, fromLeft = true) }
    val color = PairColors.of(p.peer.color, look.palette)
    val lit = p.peek?.lit(now) == true
    val seen = seenText(p.peek, now)
    val doing = doingText(p.peek, now)
    Row(
        modifier
            .heightIn(min = Tap.row)
            .clip(shape)
            .background(Ink.surface)
            .border(PAIRMARK_EDGE, color, shape)
            .semantics { contentDescription = "${p.peer.nickname}, $seen: $doing" }
            .padding(start = Space.xl, end = Space.xxl, top = Space.xs, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PairBadge(p.peer)
        Column(Modifier.padding(start = Space.s).clearAndSetSemantics { }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(p.peer.nickname, style = Type.caption.copy(fontWeight = FontWeight.Bold), color = Ink.text)
                // Coming on: one ring flares out from the light.
                val flaring = rememberBlink(CHANGE_MS)
                BlinkOnRise(lit, flaring)
                Box(Modifier.drawWithContent { drawContent(); if (flaring.playing) flare(flaring.t, color) }) { PairLight(p.peer, lit) }
                Text(seen, style = Type.caption, color = Ink.dim, maxLines = 1)
            }
            Text(doing, style = Type.bodySmall, color = Ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A person waiting behind the front one: their badge and light; a tap brings them forward. */
@Composable
private fun Tab(p: PairView, now: LocalDateTime, onTap: () -> Unit) {
    val look = LocalLook.current
    val shape = remember(look.handle) { BookmarkShape(look.handle, fromLeft = true) }
    val color = PairColors.of(p.peer.color, look.palette)
    val front = stringResource(R.string.pair_to_front)
    val seen = seenText(p.peek, now)
    Row(
        Modifier
            .heightIn(min = Tap.min)
            .clip(shape)
            .background(Ink.surface)
            .border(PAIRMARK_EDGE, color, shape)
            .clickable(onClickLabel = front, onClick = onTap)
            .semantics { contentDescription = "${p.peer.nickname}, $seen" }
            .padding(start = Space.s, end = Space.xl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        PairBadge(p.peer)
        PairLight(p.peer, p.peek?.lit(now) == true)
    }
}

/** What they're doing: live while the light is lit, "probably" after; or nothing shared. */
@Composable
fun doingText(peek: Peek?, now: LocalDateTime): String {
    val s = peek?.probably(now)
    return when {
        peek == null -> stringResource(R.string.pair_no_peek)
        s == null -> stringResource(R.string.pair_nothing_now)
        peek.lit(now) -> s.title
        else -> stringResource(R.string.pair_probably, s.title)
    }
}

/** Bluetooth's rune, for Ping. */
private fun DrawScope.bluetooth(color: Color) {
    val w = size.width
    val h = size.height
    val p = Path().apply {
        moveTo(w * 0.28f, h * 0.3f)
        lineTo(w * 0.7f, h * 0.68f)
        lineTo(w * 0.5f, h * 0.88f)
        lineTo(w * 0.5f, h * 0.12f)
        lineTo(w * 0.7f, h * 0.32f)
        lineTo(w * 0.28f, h * 0.7f)
    }
    drawPath(p, color, style = Stroke(GLYPH_LINE.toPx(), cap = StrokeCap.Round))
}

// The front pairmark's widest (a share of the board), its edge, and the glyphs' line.
private const val PAIRMARK_SHARE = 0.72f

/** The pull tab: as wide as the board's side margin; where its mark sits, and how big. */
private val TAB_WIDTH = Space.l
private const val TAB_MARK_X = 0.42f
private val TAB_MARK = 5.dp

/** How long the pairmarks take to slide out or fold away. */
private const val SLIDE_MS = 260
private val PAIRMARK_EDGE = 1.5.dp
private val GLYPH_LINE = 2.dp
