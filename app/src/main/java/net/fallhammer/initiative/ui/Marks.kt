package net.fallhammer.initiative.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import initiative.core.Clashes
import initiative.core.Formats
import initiative.core.Item
import initiative.core.MarkIcon
import initiative.core.Marks
import net.fallhammer.initiative.R
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.sin

/**
 * Markers on the board, like status effects under a health bar: bubbles that
 * hang off the bottom edge of the Plan ahead panel. Tap one and its label slides
 * out; tap the label for its note and Edit. A heads-up, a marker still days
 * away, is dim. At most [MAX_MARKS] show; past that, a "…" opens Plan ahead.
 */
@Composable
fun MarkHud(
    marks: List<Marks.Shown>,
    onEdit: (Item) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    clashes: List<Clashes.Clash> = emptyList(),
    today: LocalDate = LocalDate.now(),
    onClash: (Clashes.Clash) -> Unit = {},
) {
    if (marks.isEmpty() && clashes.isEmpty()) return
    // Which marker's label is out, and whether its note is open; by a key that
    // holds for a series' later times too, which have no id yet.
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var note by rememberSaveable { mutableStateOf(false) }
    fun keyOf(s: Marks.Shown) = "${s.item.id}:${s.item.templateId}:${s.item.start}"
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = Alignment.CenterVertically) {
            // Double-bookings first: made fresh, never saved. Tap for the label;
            // tap the label for the choice.
            val clashesShown = clashes.take(MAX_MARKS)
            clashesShown.forEach { clash ->
                val key = "clash:${clash.key}"
                val out = open == key
                val ahead = java.time.temporal.ChronoUnit.DAYS.between(today, clash.day).toInt().coerceAtLeast(0)
                val named = stringResource(R.string.clash_label, clash.mine.title, clash.theirs.title)
                val label = if (ahead == 0) named else stringResource(R.string.mark_ahead, named, pluralStringResource(R.plurals.days, ahead, ahead))
                Row(
                    Modifier
                        .heightIn(min = Tap.min)
                        .clickable {
                            if (out) {
                                open = null
                                onClash(clash)
                            } else {
                                open = key
                                note = false
                            }
                        }
                        .semantics { contentDescription = label },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MarkBubble(MarkIcon.FLAG, dim = ahead > 0, clash = true)
                    AnimatedVisibility(
                        visible = out,
                        enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                        exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
                    ) { HudLabel(label, dim = ahead > 0) }
                }
            }
            val room = MAX_MARKS - clashesShown.size
            marks.take(room).forEach { shown ->
                val key = keyOf(shown)
                val out = open == key
                val spoken = stringResource(R.string.mark_spoken, shown.item.title)
                val label = if (shown.ahead == 0) {
                    shown.item.title
                } else {
                    stringResource(R.string.mark_ahead, shown.item.title, pluralStringResource(R.plurals.days, shown.ahead, shown.ahead))
                }
                Row(
                    Modifier
                        .heightIn(min = Tap.min)
                        .clickable {
                            if (out && !note) {
                                note = true
                            } else {
                                open = if (out) null else key
                                note = false
                            }
                        }
                        .semantics { contentDescription = if (shown.ahead == 0) spoken else label },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MarkBubble(shown.item.icon, dim = shown.ahead > 0)
                    AnimatedVisibility(
                        visible = out,
                        enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                        exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
                    ) { HudLabel(label, dim = shown.ahead > 0) }
                }
            }
            if (marks.size > room) {
                val more = stringResource(R.string.mark_more)
                Box(
                    Modifier
                        .size(Tap.min)
                        .clickable(onClickLabel = more, onClick = onMore)
                        .semantics { contentDescription = more },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.mark_more_mark), style = Type.body, color = Ink.accent)
                }
            }
        }
        // The open marker's note, with Edit.
        val shown = marks.firstOrNull { keyOf(it) == open }
        if (note && shown != null) {
            val item = shown.item
            Card(inner = Space.l, arrangement = Arrangement.spacedBy(Space.inside)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    val accent = Ink.accent
                    Box(Modifier.size(GLYPH).drawBehind { markGlyph(item.icon, accent) })
                    Title(item.title, style = Type.heading)
                }
                val days = item.markDays
                val first = days?.start
                val last = days?.endInclusive
                Body(
                    when {
                        first == null -> stringResource(R.string.mark_all_day)
                        last == null || last == first -> Formats.day(first)
                        else -> stringResource(R.string.mark_span, Formats.day(first), Formats.day(last))
                    },
                    style = Type.bodySmall,
                )
                if (item.notes.isNotBlank()) Body(item.notes, color = Ink.text, style = Type.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
                    // A series' later time isn't made yet, so it can't be edited on its own.
                    if (item.id != 0L) SecondaryButton(stringResource(R.string.mark_edit), { note = false; open = null; onEdit(item) }, height = Tap.min)
                    QuietButton(stringResource(R.string.mark_close), { note = false; open = null })
                }
            }
        }
    }
}

/** A HUD bubble's label, slid out beside it. */
@Composable
private fun HudLabel(label: String, dim: Boolean) {
    Text(
        label,
        style = Type.caption,
        color = if (dim) Ink.dim else Ink.text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(LocalLook.current.buttonShape)
            .background(Ink.surface)
            .border(1.dp, Ink.frame, LocalLook.current.buttonShape)
            .padding(horizontal = Space.m, vertical = Space.s),
    )
}

/**
 * A marker's bubble in the look's own shape: a hexagon (Sci-fi), a circle
 * (Classic), a medallion with a stepped rim (Deco), a jewel roundel (Stained
 * glass), a sweet (Pastel), a cut gem (Starlight). [dim] for a heads-up: faded, with a dashed rim.
 * [clash] draws two overlapping blocks in place of the icon, for a double-booking.
 */
@Composable
fun MarkBubble(icon: MarkIcon, dim: Boolean, modifier: Modifier = Modifier, clash: Boolean = false) {
    val look = LocalLook.current
    val style = look.bubble
    val accent = Ink.accent
    val frame = Ink.frame
    val fill = Ink.surface
    val lead = Ink.bg
    val jewel = look.palette.jewels.getOrNull(icon.ordinal % look.palette.jewels.size.coerceAtLeast(1))
    val ornament = Ink.ornament
    val stem = look.palette.jewels.getOrNull(2) ?: frame
    // On a jewel's fill the icon is drawn dark: glass's jewels are deep, the cute looks' are light.
    val glyph = when (style) {
        Bubble.ROUNDEL, Bubble.CANDY -> Ink.text
        Bubble.GEM, Bubble.PUMPKIN -> Ink.onAccent
        Bubble.BAUBLE -> Ink.text
        else -> accent
    }
    Box(
        modifier
            .size(BUBBLE)
            .alpha(if (dim) DIM else 1f)
            .drawBehind {
                val r = size.minDimension / 2 - RIM.toPx()
                val c = center
                val rim = Stroke(RIM.toPx(), pathEffect = if (dim) PathEffect.dashPathEffect(floatArrayOf(DASH.toPx(), DASH.toPx())) else null)
                when (style) {
                    Bubble.HEX -> {
                        val hex = Path().apply {
                            for (k in 0..5) {
                                val a = Math.toRadians(60.0 * k).toFloat()
                                if (k == 0) moveTo(c.x + r * cos(a), c.y + r * sin(a)) else lineTo(c.x + r * cos(a), c.y + r * sin(a))
                            }
                            close()
                        }
                        drawPath(hex, fill)
                        drawPath(hex, accent, style = rim)
                    }
                    Bubble.CIRCLE -> {
                        drawCircle(fill, r, c)
                        drawCircle(frame, r, c, style = rim)
                    }
                    Bubble.MEDALLION -> {
                        drawCircle(fill, r, c)
                        drawCircle(accent, r, c, style = rim)
                        drawCircle(frame, r - RIM_STEP.toPx(), c, style = Stroke(1.dp.toPx()))
                    }
                    Bubble.ROUNDEL -> {
                        drawCircle(jewel ?: fill, r, c)
                        drawCircle(lead, r, c, style = Stroke(RIM.toPx() * 1.5f))
                        drawCircle(accent, r, c, style = rim)
                    }
                    Bubble.CANDY -> {
                        drawCircle(jewel ?: fill, r, c)
                        drawCircle(frame, r, c, style = rim)
                    }
                    Bubble.PUMPKIN -> pumpkin(Offset(c.x, c.y + RIM.toPx()), r * PUMPKIN_SHARE, ornament, lerp(ornament, lead, RIB_SHADE), stem)
                    Bubble.BAUBLE -> {
                        // A bauble: a gold cap above a red ball with a gold rim.
                        drawRect(accent, Offset(c.x - r * 0.2f, c.y - r - RIM.toPx()), Size(r * 0.4f, r * 0.3f))
                        drawCircle(ornament, r * BAUBLE_SHARE, Offset(c.x, c.y + r * (1 - BAUBLE_SHARE)))
                        drawCircle(accent, r * BAUBLE_SHARE, Offset(c.x, c.y + r * (1 - BAUBLE_SHARE)), style = rim)
                    }
                    Bubble.GEM -> {
                        val gem = Path().apply {
                            for (k in 0..7) {
                                val a = Math.toRadians(45.0 * k + 22.5).toFloat()
                                if (k == 0) moveTo(c.x + r * cos(a), c.y + r * sin(a)) else lineTo(c.x + r * cos(a), c.y + r * sin(a))
                            }
                            close()
                        }
                        drawPath(gem, jewel ?: fill)
                        drawPath(gem, ornament, style = rim)
                    }
                }
                val g = size.minDimension * GLYPH_SHARE
                translate(c.x - g / 2, c.y - g / 2) { if (clash) drawClashIn(glyph, g) else drawGlyphIn(icon, glyph, g) }
            },
    )
}

/** A marker's icon, drawn in a [GLYPH] box. */
fun DrawScope.markGlyph(icon: MarkIcon, color: Color) = drawGlyphIn(icon, color, size.minDimension)

/**
 * The six marker icons as line drawings on a 24-unit grid, scaled to [side]:
 * star, cake, door, plane, coin, flag.
 */
private fun DrawScope.drawGlyphIn(icon: MarkIcon, color: Color, side: Float) {
    val unit = side / 24f
    val line = Stroke(width = 2f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun path(vararg points: Pair<Float, Float>, closed: Boolean = false) = Path().apply {
        points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * unit, y * unit) else lineTo(x * unit, y * unit) }
        if (closed) close()
    }
    when (icon) {
        MarkIcon.STAR -> drawPath(
            path(12f to 3f, 14.6f to 9f, 21f to 9.6f, 16.2f to 13.8f, 17.6f to 20.2f, 12f to 16.9f, 6.4f to 20.2f, 7.8f to 13.8f, 3f to 9.6f, 9.4f to 9f, closed = true),
            color,
            style = line,
        )
        MarkIcon.CAKE -> {
            drawPath(path(4f to 20f, 20f to 20f, 20f to 12f, 4f to 12f, closed = true), color, style = line)
            drawPath(path(12f to 12f, 12f to 8f), color, style = line)
            drawCircle(color, 1.6f * unit, Offset(12f * unit, 6f * unit))
        }
        MarkIcon.DOOR -> {
            drawPath(path(6f to 21f, 6f to 3f, 18f to 3f, 18f to 21f), color, style = line)
            drawPath(path(3f to 21f, 21f to 21f), color, style = line)
            drawCircle(color, 1.2f * unit, Offset(14.5f * unit, 12.5f * unit))
        }
        MarkIcon.PLANE -> {
            drawPath(path(2.5f to 13f, 21f to 6.5f, 15f to 21f, 11.5f to 14.5f, closed = true), color, style = line)
            drawPath(path(11.5f to 14.5f, 21f to 6.5f), color, style = line)
        }
        MarkIcon.COIN -> {
            drawCircle(color, 8.5f * unit, Offset(12f * unit, 12f * unit), style = line)
            drawCircle(color, 4.5f * unit, Offset(12f * unit, 12f * unit), style = line)
        }
        MarkIcon.FLAG -> {
            drawPath(path(5f to 21f, 5f to 3f), color, style = line)
            drawPath(path(5f to 4f, 18f to 4f, 15f to 8.5f, 18f to 13f, 5f to 13f), color, style = line)
        }
    }
}

/** A double-booking's icon: two blocks, overlapping, on the same 24-unit grid. */
fun DrawScope.drawClashIn(color: Color, side: Float) {
    val unit = side / 24f
    val line = Stroke(width = 2f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawRect(color, Offset(2f * unit, 4f * unit), Size(13f * unit, 10f * unit), style = line)
    drawRect(color, Offset(9f * unit, 10f * unit), Size(13f * unit, 10f * unit), style = line)
}

/** Markers shown at once along the panel's edge; the rest wait behind "…". */
private const val MAX_MARKS = 4

/** A bubble's size, its rim, and how much of it its icon takes. */
val BUBBLE = 44.dp
private val RIM = 2.dp
private val RIM_STEP = 5.dp
private val DASH = 3.dp
private val GLYPH = 24.dp
private const val GLYPH_SHARE = 0.5f

/** A heads-up's strength: still readable, clearly not yet. */
private const val DIM = 0.6f

// The holiday bubbles: a pumpkin's and a bauble's size, as shares of the bubble, and how dark a pumpkin's ribs are.
private const val PUMPKIN_SHARE = 0.85f
private const val BAUBLE_SHARE = 0.9f
private const val RIB_SHADE = 0.35f
