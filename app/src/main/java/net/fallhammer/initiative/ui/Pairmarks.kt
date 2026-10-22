package net.fallhammer.initiative.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * The pairmark row, up high on the left under the marker bubbles. The front
 * person's pairmark comes in from the left edge, pointing right: badge,
 * nickname, light, how fresh, and what they're probably doing. The others wait
 * as tabs beside it; a tap brings one to the front. On the right, Ping and the
 * pairs on/off toggle; with pairs off, only those two stay.
 */
@Composable
fun PairmarkRow(
    pairs: List<PairView>,
    front: Long,
    now: LocalDateTime,
    shown: Boolean,
    onFront: (Long) -> Unit,
    onPing: () -> Unit,
    onShown: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widest = maxWidth * PAIRMARK_SHARE
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (shown && pairs.isNotEmpty()) {
                val first = pairs.firstOrNull { it.peer.id == front } ?: pairs.first()
                Front(first, now, Modifier.widthIn(max = widest))
                pairs.filter { it.peer.id != first.peer.id }.forEach { p ->
                    Spacer(Modifier.padding(start = Space.xs))
                    Tab(p, now) { haptics.tick(); onFront(p.peer.id) }
                }
            }
            Spacer(Modifier.weight(1f))
            GlyphButton(stringResource(R.string.pair_ping), onPing) { bluetooth(it) }
            Spacer(Modifier.padding(start = Space.s))
            GlyphButton(stringResource(if (shown) R.string.pairs_hide else R.string.pairs_show), { haptics.toggle(!shown); onShown(!shown) }) {
                eye(it, open = shown)
            }
        }
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
            // Past the board's side margin, to the screen's edge.
            .offset(x = -Space.l)
            .heightIn(min = Tap.primary)
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
                PairLight(p.peer, lit)
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

/** An eye, open while pairs show; struck through while they're hidden. */
private fun DrawScope.eye(color: Color, open: Boolean) {
    val w = size.width
    val h = size.height
    val line = Stroke(GLYPH_LINE.toPx(), cap = StrokeCap.Round)
    val lid = Path().apply {
        moveTo(w * 0.1f, h * 0.5f)
        quadraticBezierTo(w * 0.5f, h * 0.1f, w * 0.9f, h * 0.5f)
        quadraticBezierTo(w * 0.5f, h * 0.9f, w * 0.1f, h * 0.5f)
        close()
    }
    drawPath(lid, color, style = line)
    drawCircle(color, w * 0.12f, Offset(w * 0.5f, h * 0.5f))
    if (!open) drawLine(color, Offset(w * 0.15f, h * 0.85f), Offset(w * 0.85f, h * 0.15f), GLYPH_LINE.toPx(), StrokeCap.Round)
}

// The front pairmark's widest (a share of the board), its edge, and the glyphs' line.
private const val PAIRMARK_SHARE = 0.72f
private val PAIRMARK_EDGE = 1.5.dp
private val GLYPH_LINE = 2.dp
