package net.fallhammer.initiative.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/*
 * The look kit's small drawings, shared by patterns, crowns, corners, the dial,
 * and the flourishes: a heart, a four-point sparkle, a five-point star, a bow,
 * and a winged heart brooch. Each is drawn around a center at a given size.
 */

/** A heart, point down, [s] about half its width. */
fun heartPath(c: Offset, s: Float): Path = Path().apply {
    moveTo(c.x, c.y + s * 0.9f)
    cubicTo(c.x - s * 1.4f, c.y, c.x - s * 0.9f, c.y - s * 1.1f, c.x, c.y - s * 0.35f)
    cubicTo(c.x + s * 0.9f, c.y - s * 1.1f, c.x + s * 1.4f, c.y, c.x, c.y + s * 0.9f)
    close()
}

/** A four-point sparkle reaching [r] from its center. */
fun sparklePath(c: Offset, r: Float): Path = Path().apply {
    val k = r * SPARKLE_WAIST
    moveTo(c.x, c.y - r)
    quadraticBezierTo(c.x + k, c.y - k, c.x + r, c.y)
    quadraticBezierTo(c.x + k, c.y + k, c.x, c.y + r)
    quadraticBezierTo(c.x - k, c.y + k, c.x - r, c.y)
    quadraticBezierTo(c.x - k, c.y - k, c.x, c.y - r)
    close()
}

/** A five-point star, point up, reaching [r]. */
fun starPath(c: Offset, r: Float): Path = Path().apply {
    for (k in 0 until 10) {
        val a = Math.toRadians(-90.0 + 36.0 * k).toFloat()
        val rr = if (k % 2 == 0) r else r * STAR_WAIST
        val x = c.x + rr * cos(a)
        val y = c.y + rr * sin(a)
        if (k == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** A ribbon bow: two loops, two tails, and a knot. */
fun DrawScope.bow(c: Offset, s: Float, ribbon: Color, knot: Color) {
    val left = Path().apply {
        moveTo(c.x, c.y)
        cubicTo(c.x - s * 0.6f, c.y - s * 0.9f, c.x - s * 1.6f, c.y - s * 0.6f, c.x - s * 1.5f, c.y)
        cubicTo(c.x - s * 1.6f, c.y + s * 0.6f, c.x - s * 0.6f, c.y + s * 0.9f, c.x, c.y)
        close()
    }
    val right = Path().apply {
        moveTo(c.x, c.y)
        cubicTo(c.x + s * 0.6f, c.y - s * 0.9f, c.x + s * 1.6f, c.y - s * 0.6f, c.x + s * 1.5f, c.y)
        cubicTo(c.x + s * 1.6f, c.y + s * 0.6f, c.x + s * 0.6f, c.y + s * 0.9f, c.x, c.y)
        close()
    }
    drawPath(left, ribbon)
    drawPath(right, ribbon)
    drawLine(ribbon, Offset(c.x - s * 0.2f, c.y + s * 0.2f), Offset(c.x - s * 0.7f, c.y + s * 1.3f), s * 0.35f)
    drawLine(ribbon, Offset(c.x + s * 0.2f, c.y + s * 0.2f), Offset(c.x + s * 0.7f, c.y + s * 1.3f), s * 0.35f)
    drawCircle(knot, s * 0.3f, c)
}

/** A winged heart brooch: two small wings behind a gold heart with a pink gem. */
fun DrawScope.brooch(c: Offset, s: Float, gold: Color, gem: Color, wing: Color) {
    listOf(-1f, 1f).forEach { side ->
        val w = Path().apply {
            moveTo(c.x + side * s * 0.4f, c.y - s * 0.1f)
            cubicTo(c.x + side * s * 1.2f, c.y - s * 1.1f, c.x + side * s * 2.2f, c.y - s * 0.8f, c.x + side * s * 2.4f, c.y - s * 0.1f)
            cubicTo(c.x + side * s * 1.9f, c.y - s * 0.1f, c.x + side * s * 1.4f, c.y + s * 0.3f, c.x + side * s * 0.5f, c.y + s * 0.4f)
            close()
        }
        drawPath(w, wing)
    }
    drawPath(heartPath(c, s), gold)
    drawPath(heartPath(Offset(c.x, c.y - s * 0.05f), s * 0.5f), gem)
}

/**
 * A scatter of small shapes behind a screen, in the palette's jewels at the
 * chosen [strength]: dots and small hearts (Pastel), or sparkles and specks
 * (Starlight). Placed by a fixed recipe, so they never shift between screens.
 */
fun Modifier.scatter(colors: List<Color>, strength: Float, hearts: Boolean): Modifier = drawWithCache {
    val tile = SCATTER_TILE.toPx()
    val unit = 1.dp.toPx()
    // A fixed recipe of places within a tile: (x, y) as shares of the tile, and a size step.
    val recipe = List(SCATTER_COUNT) { i ->
        val x = ((i * 37 + 11) % 100) / 100f
        val y = ((i * 61 + 29) % 100) / 100f
        Triple(x, y, i % 3)
    }
    onDrawBehind {
        if (colors.isEmpty() || strength <= 0f) return@onDrawBehind
        var ty = 0f
        while (ty < size.height) {
            var tx = 0f
            while (tx < size.width) {
                recipe.forEachIndexed { i, (x, y, step) ->
                    val c = Offset(tx + x * tile, ty + y * tile)
                    val color = colors[i % colors.size].copy(alpha = strength)
                    when {
                        hearts && i % 3 == 0 -> drawPath(heartPath(c, (4 + step * 1.5f) * unit), color)
                        hearts -> drawCircle(color, (3.5f + step * 1.5f) * unit, c)
                        i % 2 == 0 -> drawPath(sparklePath(c, (3f + step * 1.5f) * unit), color)
                        else -> drawCircle(color, 1.3f * unit, c)
                    }
                }
                tx += tile
            }
            ty += tile
        }
    }
}

/**
 * The All done flourish for looks with their own: hearts that rise from the Now
 * card and fade (Pastel), or a burst of sparkles around a star (Starlight).
 */
fun Modifier.burst(kind: Flourish, colors: List<Color>, center: Color, pulse: () -> Pulse?): Modifier = drawWithCache {
    val far = hypot(size.width, size.height)
    val unit = 1.dp.toPx()
    onDrawBehind {
        val p = pulse() ?: return@onDrawBehind
        if (p.progress <= 0f || p.progress >= 1f || colors.isEmpty()) return@onDrawBehind
        val fade = 1f - p.progress
        when (kind) {
            Flourish.HEARTS -> repeat(BURST_COUNT) { k ->
                val drift = (k - BURST_COUNT / 2f) * BURST_SPREAD.toPx()
                val rise = far * BURST_RISE * p.progress * (0.6f + 0.4f * ((k * 7) % 5) / 4f)
                val c = Offset(p.center.x + drift * p.progress, p.center.y - rise)
                drawPath(heartPath(c, (6f + (k % 3) * 3f) * unit), colors[k % colors.size].copy(alpha = fade))
            }
            Flourish.SPARKLES -> {
                repeat(BURST_COUNT) { k ->
                    val a = Math.toRadians(360.0 * k / BURST_COUNT).toFloat()
                    val reach = far * BURST_REACH * p.progress
                    val c = Offset(p.center.x + cos(a) * reach, p.center.y + sin(a) * reach)
                    drawPath(sparklePath(c, (5f + (k % 2) * 4f) * unit), colors[k % colors.size].copy(alpha = fade))
                }
                drawPath(starPath(p.center, STAR_HEART.toPx() * (0.5f + p.progress)), center.copy(alpha = fade))
            }
            else -> Unit
        }
    }
}

/** A small outlined heart or sparkle on the dial, or filled. */
fun DrawScope.drawMark(path: Path, fill: Color, edge: Color? = null) {
    drawPath(path, fill)
    if (edge != null) drawPath(path, edge, style = Stroke(1.dp.toPx()))
}

// The shapes' proportions.
private const val SPARKLE_WAIST = 0.18f
private const val STAR_WAIST = 0.45f

// The scatter: its tile, and how many shapes each tile holds.
private val SCATTER_TILE = 120.dp
private const val SCATTER_COUNT = 14

// The bursts: how many shapes, how far they rise or reach (shares of the screen's diagonal), and their spread.
private const val BURST_COUNT = 10
private const val BURST_RISE = 0.3f
private const val BURST_REACH = 0.25f
private val BURST_SPREAD = 8.dp
private val STAR_HEART = 18.dp
