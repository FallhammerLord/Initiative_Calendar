package net.fallhammer.initiative.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
    quadraticTo(c.x + k, c.y - k, c.x + r, c.y)
    quadraticTo(c.x + k, c.y + k, c.x, c.y + r)
    quadraticTo(c.x - k, c.y + k, c.x - r, c.y)
    quadraticTo(c.x - k, c.y - k, c.x, c.y - r)
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
            Flourish.BATS -> repeat(BURST_COUNT) { k ->
                val drift = (k - BURST_COUNT / 2f) * BURST_SPREAD.toPx() * 2
                val rise = far * BURST_RISE * p.progress * (0.6f + 0.4f * ((k * 7) % 5) / 4f)
                val c = Offset(p.center.x + drift * p.progress, p.center.y - rise)
                drawPath(batPath(c, (14f + (k % 3) * 5f) * unit), colors[k % colors.size].copy(alpha = fade))
            }
            // Snow drifts down over the Now card and settles.
            Flourish.SNOW -> repeat(BURST_COUNT * 2) { k ->
                val x = p.center.x + (k - BURST_COUNT) * BURST_SPREAD.toPx() * 2.5f
                val fall = far * BURST_RISE * (0.3f + 0.7f * p.progress) * (0.5f + 0.5f * ((k * 7) % 5) / 4f)
                val c = Offset(x + 6 * unit * sin(k + p.progress * 6), p.center.y - far * BURST_RISE + fall)
                snowflake(c, (4f + (k % 3) * 2f) * unit, center.copy(alpha = fade), 1.4f * unit)
            }
            else -> Unit
        }
    }
}

/** A small bat, wings out, [w] wide. */
fun batPath(c: Offset, w: Float): Path {
    val k = w / 24f
    fun x(v: Float) = c.x + v * k
    fun y(v: Float) = c.y + v * k
    return Path().apply {
        moveTo(x(-12f), y(-2f))
        cubicTo(x(-8f), y(-8f), x(-4f), y(-6f), x(-2f), y(-3f))
        lineTo(x(-1f), y(-6f))
        lineTo(x(0f), y(-4f))
        lineTo(x(1f), y(-6f))
        lineTo(x(2f), y(-3f))
        cubicTo(x(4f), y(-6f), x(8f), y(-8f), x(12f), y(-2f))
        cubicTo(x(9f), y(-2f), x(8f), y(1f), x(6f), y(2f))
        cubicTo(x(5f), y(0f), x(3f), y(0f), x(2f), y(2f))
        cubicTo(x(1f), y(4f), x(-1f), y(4f), x(-2f), y(2f))
        cubicTo(x(-3f), y(0f), x(-5f), y(0f), x(-6f), y(2f))
        cubicTo(x(-8f), y(1f), x(-9f), y(-2f), x(-12f), y(-2f))
        close()
    }
}

/** A little ghost [h] tall, with two eyes. */
fun DrawScope.ghost(c: Offset, h: Float, body: Color, eye: Color) {
    val k = h / 16f
    val p = Path().apply {
        moveTo(c.x - 6 * k, c.y + 8 * k)
        lineTo(c.x - 6 * k, c.y - k)
        cubicTo(c.x - 6 * k, c.y - 5 * k, c.x - 3 * k, c.y - 8 * k, c.x, c.y - 8 * k)
        cubicTo(c.x + 3 * k, c.y - 8 * k, c.x + 6 * k, c.y - 5 * k, c.x + 6 * k, c.y - k)
        lineTo(c.x + 6 * k, c.y + 8 * k)
        for (i in 0 until 5) lineTo(c.x + (4 - 2 * i) * k, c.y + (if (i % 2 == 0) 6 else 8) * k)
        close()
    }
    drawPath(p, body)
    drawCircle(eye, 1.1f * k, Offset(c.x - 2 * k, c.y - 2 * k))
    drawCircle(eye, 1.1f * k, Offset(c.x + 2 * k, c.y - 2 * k))
}

/** A pumpkin [r] tall from its middle: three lobes and a stem. */
fun DrawScope.pumpkin(c: Offset, r: Float, fill: Color, rib: Color, stem: Color) {
    drawRoundRect(stem, Offset(c.x - r * 0.12f, c.y - r * 1.15f), Size(r * 0.24f, r * 0.4f), CornerRadius(r * 0.08f))
    val line = Stroke(maxOf(1f, r * 0.06f))
    listOf(-0.42f to 0.62f, 0.42f to 0.62f, 0f to 0.55f).forEach { (dx, rx) ->
        val ry = if (dx == 0f) 0.9f else 0.86f
        val tl = Offset(c.x + dx * r - rx * r, c.y - ry * r)
        val sz = Size(2 * rx * r, 2 * ry * r)
        drawOval(fill, tl, sz)
        drawOval(rib, tl, sz, style = line)
    }
}

/** A jack-o'-lantern: a pumpkin with a friendly face cut in it. */
fun DrawScope.jack(c: Offset, r: Float, fill: Color, rib: Color, stem: Color, face: Color) {
    pumpkin(c, r, fill, rib, stem)
    listOf(-1f, 1f).forEach { side ->
        val eye = Path().apply {
            moveTo(c.x + side * r * 0.48f, c.y - r * 0.05f)
            lineTo(c.x + side * r * 0.3f, c.y - r * 0.42f)
            lineTo(c.x + side * r * 0.12f, c.y - r * 0.05f)
            close()
        }
        drawPath(eye, face)
    }
    val smile = Path().apply {
        moveTo(c.x - r * 0.55f, c.y + r * 0.2f)
        quadraticTo(c.x, c.y + r * 0.75f, c.x + r * 0.55f, c.y + r * 0.2f)
        quadraticTo(c.x, c.y + r * 0.48f, c.x - r * 0.55f, c.y + r * 0.2f)
        close()
    }
    drawPath(smile, face)
}

/** A six-armed snowflake reaching [r]. */
fun DrawScope.snowflake(c: Offset, r: Float, color: Color, width: Float) {
    for (k in 0 until 6) {
        val a = Math.toRadians(60.0 * k - 90.0)
        val end = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
        drawLine(color, c, end, width, StrokeCap.Round)
        val m = Offset(c.x + r * 0.6f * cos(a).toFloat(), c.y + r * 0.6f * sin(a).toFloat())
        listOf(-1, 1).forEach { d ->
            val b = a + d * Math.toRadians(40.0)
            drawLine(color, m, Offset(m.x + r * 0.32f * cos(b).toFloat(), m.y + r * 0.32f * sin(b).toFloat()), width, StrokeCap.Round)
        }
    }
}

/** A little tree, point up at [tip], [h] tall with its trunk. */
fun treePath(tip: Offset, h: Float): Path = Path().apply {
    val w = h * 0.62f
    moveTo(tip.x, tip.y)
    lineTo(tip.x - w * 0.32f, tip.y + h * 0.38f)
    lineTo(tip.x - w * 0.16f, tip.y + h * 0.38f)
    lineTo(tip.x - w * 0.5f, tip.y + h * 0.8f)
    lineTo(tip.x + w * 0.5f, tip.y + h * 0.8f)
    lineTo(tip.x + w * 0.16f, tip.y + h * 0.38f)
    lineTo(tip.x + w * 0.32f, tip.y + h * 0.38f)
    close()
    addRect(androidx.compose.ui.geometry.Rect(tip.x - h * 0.07f, tip.y + h * 0.8f, tip.x + h * 0.07f, tip.y + h))
}

/** Holly: two leaves and three berries. */
fun DrawScope.holly(c: Offset, s: Float, leaf: Color, berry: Color) {
    listOf(200.0, -20.0).forEach { deg ->
        val a = Math.toRadians(deg)
        val end = Offset(c.x + cos(a).toFloat() * s * 1.8f, c.y + sin(a).toFloat() * s * 1.8f)
        val n = Offset(-sin(a).toFloat() * s * 0.55f, cos(a).toFloat() * s * 0.55f)
        val mid = Offset((c.x + end.x) / 2, (c.y + end.y) / 2)
        val p = Path().apply {
            moveTo(c.x, c.y)
            quadraticTo(mid.x + n.x, mid.y + n.y, end.x, end.y)
            quadraticTo(mid.x - n.x, mid.y - n.y, c.x, c.y)
            close()
        }
        drawPath(p, leaf)
    }
    drawCircle(berry, s * 0.38f, Offset(c.x - s * 0.3f, c.y - s * 0.35f))
    drawCircle(berry, s * 0.38f, Offset(c.x + s * 0.35f, c.y - s * 0.3f))
    drawCircle(berry, s * 0.38f, Offset(c.x, c.y + s * 0.2f))
}

/**
 * Halloween's ground: one big web spun from the top left corner over the whole
 * screen, a faint moon at the top right, and a few bats. Fixed, so it never
 * shifts between screens.
 */
fun Modifier.web(color: Color, moon: Color, ground: Color, bats: List<Color>, strength: Float, pulse: () -> Pulse?): Modifier = drawWithCache {
    val reach = hypot(size.width, size.height)
    val rays = (0..8).map { Math.toRadians(it * 11.25) }
    val path = Path()
    rays.forEach { a -> path.moveTo(0f, 0f); path.lineTo(reach * cos(a).toFloat(), reach * sin(a).toFloat()) }
    var ring = WEB_FIRST.toPx()
    while (ring < reach) {
        rays.forEachIndexed { i, a ->
            val x = ring * cos(a).toFloat()
            val y = ring * sin(a).toFloat()
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                // Each thread sags a little toward the corner.
                val m = a - Math.toRadians(5.6)
                path.quadraticTo(ring * WEB_SAG * cos(m).toFloat(), ring * WEB_SAG * sin(m).toFloat(), x, y)
            }
        }
        ring *= WEB_GROWTH
    }
    val unit = 1.dp.toPx()
    onDrawBehind {
        if (strength <= 0f) return@onDrawBehind
        val moonAt = Offset(size.width - WEB_MOON.toPx() * 1.6f, WEB_MOON.toPx() * 3f)
        drawCircle(moon.copy(alpha = strength * MOON_ALPHA), WEB_MOON.toPx(), moonAt)
        drawCircle(ground.copy(alpha = strength * MOON_ALPHA), WEB_MOON.toPx(), Offset(moonAt.x + WEB_MOON.toPx() * 0.35f, moonAt.y - WEB_MOON.toPx() * 0.25f))
        drawPath(path, color.copy(alpha = strength), style = Stroke(1.2f * unit))
        if (bats.isNotEmpty()) {
            WEB_BATS.forEachIndexed { i, (fx, fy) ->
                drawPath(batPath(Offset(fx * size.width, fy * size.height), (16 + i % 3 * 4) * unit), bats[i % bats.size].copy(alpha = strength))
            }
        }
        // All done: one ring of light runs out along the web.
        val p = pulse() ?: return@onDrawBehind
        if (p.progress <= 0f || p.progress >= 1f) return@onDrawBehind
        drawCircle(moon.copy(alpha = (1 - p.progress) * MOON_ALPHA), reach * p.progress / 2, p.center, style = Stroke(2 * unit))
    }
}

/**
 * Christmas's ground: a knit sweater's bands, row after row: zigzags, a row
 * of diamonds, a row of snowflakes, and a row of dots, in the palette's jewels.
 */
fun Modifier.knit(colors: List<Color>, strength: Float): Modifier = drawWithCache {
    val tile = KNIT_W.toPx()
    val tall = KNIT_H.toPx()
    val u = tall / 120f
    onDrawBehind {
        if (colors.size < 4 || strength <= 0f) return@onDrawBehind
        val red = colors[0].copy(alpha = strength)
        val white = colors[2].copy(alpha = strength)
        val mint = colors[3].copy(alpha = strength)
        var ty = 0f
        while (ty < size.height) {
            var tx = 0f
            while (tx < size.width) {
                for (dx in 0 until 4) {
                    val x = tx + dx * 15 * u
                    listOf(20f, 28f).forEach { y ->
                        val z = Path().apply {
                            moveTo(x, ty + y * u)
                            lineTo(x + 7.5f * u, ty + (y - 8) * u)
                            lineTo(x + 15 * u, ty + y * u)
                        }
                        drawPath(z, red, style = Stroke(3 * u))
                    }
                    drawCircle(mint, 2.4f * u, Offset(x + 7.5f * u, ty + 104 * u))
                }
                listOf(0f, 30f).forEach { dx ->
                    val d = Path().apply {
                        moveTo(tx + (dx + 15) * u, ty + 44 * u)
                        lineTo(tx + (dx + 21) * u, ty + 52 * u)
                        lineTo(tx + (dx + 15) * u, ty + 60 * u)
                        lineTo(tx + (dx + 9) * u, ty + 52 * u)
                        close()
                    }
                    drawPath(d, white)
                    snowflake(Offset(tx + (dx + 15) * u, ty + 82 * u), 6 * u, white, 1.6f * u)
                }
                tx += tile
            }
            ty += tall
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

// Halloween's web: the first ring's radius, how much each ring grows, how far
// threads sag, the moon's size and how faint, and where the bats sit (shares
// of the screen).
private val WEB_FIRST = 70.dp
private const val WEB_GROWTH = 1.45f
private const val WEB_SAG = 0.9f
private val WEB_MOON = 40.dp
private const val MOON_ALPHA = 0.3f
private val WEB_BATS = listOf(0.78f to 0.25f, 0.18f to 0.62f, 0.85f to 0.72f)

// Christmas's knit: one repeat of the bands.
private val KNIT_W = 60.dp
private val KNIT_H = 120.dp

/**
 * The mark on a dock slot that holds a tray, pointing up, [w] wide around [c]:
 * a chevron in the look's style, a glass arch's tip, a candy corn, or a star.
 * [tones] are the candy corn's base, middle, and tip.
 */
fun DrawScope.moreMark(kind: More, c: Offset, w: Float, color: Color, tones: List<Color>) {
    val h = w * MORE_RISE
    val line = w * MORE_LINE
    fun chevron(dy: Float, cap: StrokeCap) {
        val path = Path().apply {
            moveTo(c.x - w / 2, c.y + h / 2 + dy)
            lineTo(c.x, c.y - h / 2 + dy)
            lineTo(c.x + w / 2, c.y + h / 2 + dy)
        }
        drawPath(path, color, style = Stroke(line, cap = cap, join = if (cap == StrokeCap.Round) StrokeJoin.Round else StrokeJoin.Miter))
    }
    when (kind) {
        More.CHEVRON -> chevron(0f, StrokeCap.Butt)
        More.ROUND -> chevron(0f, StrokeCap.Round)
        More.DOUBLE -> {
            chevron(-h * 0.35f, StrokeCap.Butt)
            chevron(h * 0.45f, StrokeCap.Butt)
        }
        More.ARCH -> drawPath(
            Path().apply {
                moveTo(c.x, c.y - h * 0.7f)
                cubicTo(c.x + w * 0.15f, c.y - h * 0.2f, c.x + w * 0.45f, c.y, c.x + w / 2, c.y + h * 0.7f)
                lineTo(c.x - w / 2, c.y + h * 0.7f)
                cubicTo(c.x - w * 0.45f, c.y, c.x - w * 0.15f, c.y - h * 0.2f, c.x, c.y - h * 0.7f)
                close()
            },
            color,
        )
        More.CANDY_CORN -> listOf(1f, 0.66f, 0.33f).forEachIndexed { i, share ->
            val top = c.y - h * 0.8f
            val bottom = top + h * 1.6f * share
            val half = w / 2 * share
            drawPath(
                Path().apply {
                    moveTo(c.x, top)
                    lineTo(c.x + half, bottom)
                    lineTo(c.x - half, bottom)
                    close()
                },
                tones.getOrElse(i) { color },
            )
        }
        More.STAR -> drawPath(starPath(c, w * 0.55f), color)
    }
}

/** A "more" mark's height and line, as shares of its width. */
private const val MORE_RISE = 0.6f
private const val MORE_LINE = 0.16f
