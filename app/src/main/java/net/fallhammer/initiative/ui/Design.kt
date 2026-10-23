package net.fallhammer.initiative.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.draw.drawWithContent
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.setValue
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import initiative.core.Names
import initiative.core.Trio
import kotlinx.coroutines.delay
import net.fallhammer.initiative.R
import net.fallhammer.initiative.data.Apps
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The design system. Screens are assembled from these tokens and components and
 * never set sizes, colors, or spacing by hand. Colors and shapes live in the
 * current theme (Theme.kt), read through Ink and LocalLook.
 */

/** Spacing steps. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /*
     * Layout rhythm. Panels group by distance: a wide gap between zones (what's
     * Now, the day, getting to things), a narrow one inside a zone. Form fields
     * sit [l] apart. Touch targets keep at least [inside] between them.
     */
    val zone = xl
    val inside = s

    /**
     * Between the board's stacked panels: the Now card, the Next strip, task
     * entry, the dock, and the Apps handle all sit this far apart, so the bottom
     * group reads as one. Only the space above the Now card stretches.
     */
    val panel = m
}

/** Touch-target heights. Every button is [min]; rows and tiles that carry more are taller. */
object Tap {
    val min = 48.dp
    val row = 56.dp
    val primary = 64.dp
    val alarm = 88.dp
}


/** The type scale. Body text never goes below 18 sp; captions stay at 16 sp. */
object Type {
    private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = Atkinson, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

    val hero = style(40, 46, FontWeight.Bold)
    val display = style(30, 36, FontWeight.Bold)
    val title = style(24, 30, FontWeight.Bold)
    val heading = style(22, 28)
    val body = style(20, 26)
    val bodySmall = style(18, 24)
    val button = style(18, 22, FontWeight.Bold)
    val caption = style(16, 22)
    val label = style(16, 22, FontWeight.Bold)
}

/**
 * Every screen's frame: background, system bars, keyboard space, and margins.
 * [pulse], read while drawing, sends one ring of light across the hexagon grid.
 */
@Composable
fun Screen(modifier: Modifier = Modifier, pulse: () -> Pulse? = { null }, content: @Composable ColumnScope.() -> Unit) {
    val look = LocalLook.current
    // The pattern carries the pulse itself, unless the look has a flourish of its own.
    val ring: () -> Pulse? = if (look.flourish == Flourish.PATTERN) pulse else { { null } }
    val jewels = look.palette.jewels
    Column(
        modifier
            .fillMaxSize()
            .background(Ink.bg)
            .then(
                when (look.pattern) {
                    Pattern.HEX -> Modifier.hexGrid(Ink.pattern, Ink.accent, LocalHexLattice.current.value, ring)
                    Pattern.FANS -> Modifier.fans(Ink.pattern, Ink.bg, Ink.accent, ring)
                    Pattern.LEADED -> Modifier.leadedGlass(jewels, look.texture, Ink.bg, Ink.accent, ring)
                    Pattern.DOTS -> Modifier.scatter(jewels, look.texture, hearts = true)
                    Pattern.SPARKLES -> Modifier.scatter(jewels, look.texture, hearts = false)
                    Pattern.WEB -> Modifier.web(Ink.pattern.copy(alpha = 1f), Ink.text, Ink.bg, jewels.take(2), look.texture, ring)
                    Pattern.KNIT -> Modifier.knit(jewels, look.texture)
                    Pattern.NONE -> Modifier
                },
            )
            .then(
                when (look.flourish) {
                    Flourish.HEARTS, Flourish.SPARKLES, Flourish.BATS -> Modifier.burst(look.flourish, jewels, Ink.ornament, pulse)
                    Flourish.SNOW -> Modifier.burst(look.flourish, jewels, Ink.text, pulse)
                    else -> Modifier
                },
            )
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = Space.l, vertical = Space.m),
        content = content,
    )
}

/**
 * A one-line panel, framed like a [Card], with the same side padding so its text
 * lines up with the card's. The folded day panel is one.
 */
@Composable
fun Strip(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val look = LocalLook.current
    Row(
        // Clipped before the caller's modifier, so a tap's ripple keeps the panel's shape.
        Modifier
            .fillMaxWidth()
            .heightIn(min = Tap.min)
            .clip(look.cardShape)
            .then(modifier)
            .background(Ink.surface)
            .then(if (look.framed) Modifier.border(look.came, Ink.frame, look.cardShape) else Modifier)
            .padding(horizontal = Space.xl, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * The Apps button along the bottom edge: the look's emblem, pointing up, large
 * and itself the button, on a rail running the board's width through its
 * middle (see [HandleStyle]). TalkBack hears [label].
 */
@Composable
fun AppsHandle(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val style = look.handle
    val accent = Ink.accent
    val frame = Ink.frame
    val surface = Ink.surface
    val bg = Ink.bg
    val jewels = look.palette.jewels
    Box(
        modifier
            .fillMaxWidth()
            .drawBehind { rail(style, accent, frame, jewels) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(EMBLEM)
                .clickable(onClickLabel = label, onClick = onClick)
                .semantics { contentDescription = label }
                .drawBehind { emblem(style, accent, frame, surface, bg, jewels) },
        )
    }
}

/** The rail the Apps emblem sits on, through its middle; Classic has none. */
private fun DrawScope.rail(style: HandleStyle, accent: Color, frame: Color, jewels: List<Color>) {
    val y = size.height / 2
    val line = RAIL_LINE.toPx()
    when (style) {
        HandleStyle.PILL -> Unit
        HandleStyle.CLAMP -> {
            drawLine(accent.copy(alpha = RAIL_ALPHA), Offset(0f, y), Offset(size.width, y), line)
            val step = RAIL_STEP.toPx()
            var x = step / 2
            while (x < size.width) {
                drawLine(accent.copy(alpha = RAIL_ALPHA), Offset(x, y - RAIL_TICK.toPx()), Offset(x, y + RAIL_TICK.toPx()), line)
                x += step
            }
        }
        // Deco's rail is the horizon its sun rises over.
        HandleStyle.ZIGGURAT -> repeat(FLUTES) { k ->
            val fy = y + k * FLUTE_GAP.toPx()
            drawLine(frame, Offset(0f, fy), Offset(size.width, fy), Hairline.toPx())
        }
        HandleStyle.LANCET -> {
            drawLine(frame, Offset(0f, y), Offset(size.width, y), line)
            val step = RAIL_STEP.toPx()
            val d = RAIL_TICK.toPx()
            var x = step / 2
            while (x < size.width) {
                val diamond = Path().apply {
                    moveTo(x - d, y)
                    lineTo(x, y - d)
                    lineTo(x + d, y)
                    lineTo(x, y + d)
                    close()
                }
                drawPath(diamond, frame, style = Stroke(line))
                x += step
            }
        }
        // Pastel: a dotted line, like stitching.
        HandleStyle.CLOUD -> {
            val step = RAIL_STEP.toPx() / 2
            var x = step / 2
            while (x < size.width) {
                drawCircle(frame, radius = line, center = Offset(x, y))
                x += step
            }
        }
        // Halloween: a string of pennants, swagging between pins.
        HandleStyle.HAT -> pennants(jewels, frame, y)
        // Christmas: a string of lights.
        HandleStyle.GIFT -> lights(jewels, frame, y)
        // Starlight: a ribbon, gently waving.
        HandleStyle.COMPACT -> {
            val wave = Path().apply {
                moveTo(0f, y)
                val step = RAIL_STEP.toPx() * 2
                var x = 0f
                var up = true
                while (x < size.width) {
                    quadraticTo(x + step / 2, y + (if (up) -1 else 1) * RAIL_TICK.toPx(), x + step, y)
                    x += step
                    up = !up
                }
            }
            drawPath(wave, frame, style = Stroke(line))
        }
    }
}

/**
 * The Apps emblem, each pointing up: twin chevrons in a hexagon like the dock's
 * (Sci-fi), a chevron in a circle (Classic), a sun half risen over the horizon
 * (Deco), a rose window (Stained glass).
 */
private fun DrawScope.emblem(style: HandleStyle, accent: Color, frame: Color, fill: Color, lead: Color, jewels: List<Color>) {
    val w = size.width
    val h = size.height
    val c = center
    val r = minOf(w, h) / 2 - RAIL_LINE.toPx()
    val line = CHEVRON_LINE.toPx()
    fun chevron(top: Float, bottom: Float, half: Float) {
        val p = Path().apply {
            moveTo(c.x - half, bottom)
            lineTo(c.x, top)
            lineTo(c.x + half, bottom)
        }
        drawPath(p, accent, style = Stroke(line))
    }
    when (style) {
        HandleStyle.PILL -> {
            drawCircle(fill, radius = r, center = c)
            drawCircle(frame, radius = r, center = c, style = Stroke(RAIL_LINE.toPx()))
            chevron(c.y - r * 0.3f, c.y + r * 0.25f, r * 0.5f)
        }
        HandleStyle.CLAMP -> {
            val hex = Path().apply {
                for (k in 0..5) {
                    val a = Math.toRadians(60.0 * k).toFloat()
                    val x = c.x + r * cos(a)
                    val y = c.y + r * sin(a)
                    if (k == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(hex, fill)
            drawPath(hex, accent, style = Stroke(RAIL_LINE.toPx()))
            chevron(c.y - r * 0.45f, c.y - r * 0.05f, r * 0.45f)
            chevron(c.y - r * 0.05f, c.y + r * 0.35f, r * 0.45f)
        }
        HandleStyle.ZIGGURAT -> {
            // The horizon is the rail, at the middle; the sun stands half above it.
            val sun = r * SUN_SHARE
            drawArc(accent, 180f, 180f, useCenter = true, topLeft = Offset(c.x - sun, c.y - sun), size = Size(2 * sun, 2 * sun))
            for (k in 0..6) {
                val a = Math.toRadians(180.0 + 15.0 + 25.0 * k).toFloat()
                val from = Offset(c.x + cos(a) * sun * SUN_GAP, c.y + sin(a) * sun * SUN_GAP)
                val to = Offset(c.x + cos(a) * r, c.y + sin(a) * r)
                drawLine(accent, from, to, line / 2)
            }
        }
        HandleStyle.LANCET -> {
            drawCircle(lead, radius = r, center = c)
            if (jewels.isNotEmpty()) {
                val box = Size(2 * r, 2 * r)
                val corner = Offset(c.x - r, c.y - r)
                for (k in 0 until ROSE_PANES) {
                    val start = 360f * k / ROSE_PANES - 90f
                    val sweep = 360f / ROSE_PANES
                    drawArc(jewels[k % jewels.size], start, sweep, useCenter = true, topLeft = corner, size = box)
                    drawArc(lead, start, sweep, useCenter = true, topLeft = corner, size = box, style = Stroke(LEAD.toPx() / 2))
                }
            }
            drawCircle(lead, radius = r * ROSE_HEART, center = c)
            drawCircle(accent, radius = r * ROSE_HEART, center = c, style = Stroke(Hairline.toPx()))
            drawCircle(accent, radius = r * ROSE_EYE * 2, center = c)
            drawCircle(accent, radius = r, center = c, style = Stroke(RAIL_LINE.toPx()))
        }
        HandleStyle.CLOUD -> {
            // Five puffs and a flat base make a cloud; the chevron sits on it.
            val cloud = Path().apply {
                listOf(
                    Offset(-0.55f, 0.15f) to 0.38f,
                    Offset(-0.2f, -0.2f) to 0.45f,
                    Offset(0.25f, -0.15f) to 0.48f,
                    Offset(0.6f, 0.15f) to 0.35f,
                    Offset(0f, 0.25f) to 0.42f,
                ).forEach { (o, k) ->
                    addOval(Rect(Offset(c.x + o.x * r, c.y + o.y * r), k * r))
                }
            }
            // Stroked twice as wide, then filled over, so only the outer edge shows.
            drawPath(cloud, frame, style = Stroke(RAIL_LINE.toPx() * 2))
            drawPath(cloud, fill)
            chevron(c.y - r * 0.3f, c.y + r * 0.15f, r * 0.4f)
        }
        HandleStyle.HAT -> {
            // A witch's hat: a crooked cone on a wide brim, with a band and the chevron on it.
            val cone = Path().apply {
                moveTo(c.x, c.y - r)
                cubicTo(c.x - r * 0.1f, c.y - r * 0.5f, c.x - r * 0.3f, c.y, c.x - r * 0.6f, c.y + r * 0.55f)
                lineTo(c.x + r * 0.6f, c.y + r * 0.55f)
                cubicTo(c.x + r * 0.3f, c.y, c.x + r * 0.15f, c.y - r * 0.5f, c.x, c.y - r)
                close()
            }
            val brim = Size(2 * r * 1.05f, r * 0.42f)
            val brimAt = Offset(c.x - r * 1.05f, c.y + r * 0.5f)
            drawPath(cone, fill)
            drawPath(cone, frame, style = Stroke(RAIL_LINE.toPx()))
            drawOval(fill, brimAt, brim)
            drawOval(frame, brimAt, brim, style = Stroke(RAIL_LINE.toPx()))
            drawRect(accent, Offset(c.x - r * 0.55f, c.y + r * 0.38f), Size(r * 1.1f, r * 0.16f))
            chevron(c.y - r * 0.25f, c.y + r * 0.1f, r * 0.28f)
        }
        HandleStyle.GIFT -> {
            // A gift: a box and lid, a ribbon down the middle, a bow on top, and the chevron on the box.
            val ribbon = jewels.firstOrNull() ?: accent
            drawRoundRect(fill, Offset(c.x - r * 0.75f, c.y - r * 0.35f), Size(r * 1.5f, r * 1.15f), CornerRadius(r * 0.1f))
            drawRoundRect(frame, Offset(c.x - r * 0.75f, c.y - r * 0.35f), Size(r * 1.5f, r * 1.15f), CornerRadius(r * 0.1f), style = Stroke(RAIL_LINE.toPx()))
            drawRoundRect(fill, Offset(c.x - r * 0.88f, c.y - r * 0.6f), Size(r * 1.76f, r * 0.32f), CornerRadius(r * 0.08f))
            drawRoundRect(frame, Offset(c.x - r * 0.88f, c.y - r * 0.6f), Size(r * 1.76f, r * 0.32f), CornerRadius(r * 0.08f), style = Stroke(RAIL_LINE.toPx()))
            drawRect(ribbon, Offset(c.x - r * 0.12f, c.y - r * 0.6f), Size(r * 0.24f, r * 1.4f))
            bow(Offset(c.x, c.y - r * 0.72f), r * 0.24f, ribbon, ribbon)
            chevron(c.y + r * 0.05f, c.y + r * 0.45f, r * 0.3f)
        }
        HandleStyle.COMPACT -> {
            // A compact mirror: a round case with a star in the middle and a sparkle beside it.
            drawCircle(fill, radius = r * 0.8f, center = c)
            drawCircle(frame, radius = r * 0.8f, center = c, style = Stroke(RAIL_LINE.toPx()))
            drawCircle(accent, radius = r * 0.68f, center = c, style = Stroke(Hairline.toPx()))
            drawPath(starPath(c, r * 0.42f), accent)
            if (jewels.isNotEmpty()) drawPath(sparklePath(Offset(c.x + r * 0.62f, c.y - r * 0.62f), r * 0.22f), jewels[0])
        }
    }
}

/** Halloween's rail: pennants in the jewels, hung from a swagging string. */
private fun DrawScope.pennants(jewels: List<Color>, string: Color, y: Float) {
    val swag = SWAG.toPx()
    val step = PENNANT_STEP.toPx()
    val dip = SWAG_DIP.toPx()
    fun sagAt(x: Float): Float { val t = (x % swag) / swag; return y + 4 * dip * t * (1 - t) }
    val line = Path().apply {
        moveTo(0f, y)
        var x = 0f
        while (x < size.width) { quadraticTo(x + swag / 2, y + 2 * dip, x + swag, y); x += swag }
    }
    drawPath(line, string, style = Stroke(Hairline.toPx() * 1.5f))
    if (jewels.isEmpty()) return
    val half = PENNANT.toPx() / 2
    var x = step / 2
    var i = 0
    while (x < size.width) {
        // Leave the emblem's middle clear.
        if (kotlin.math.abs(x - size.width / 2) > EMBLEM.toPx() * 0.7f) {
            val top = sagAt(x)
            val flag = Path().apply {
                moveTo(x - half, top)
                lineTo(x + half, top)
                lineTo(x, top + PENNANT.toPx() * 0.9f)
                close()
            }
            drawPath(flag, jewels[i % minOf(3, jewels.size)])
            i++
        }
        x += step
    }
}

/** Christmas's rail: a string of colored lights. */
private fun DrawScope.lights(jewels: List<Color>, wire: Color, y: Float) {
    val swag = SWAG.toPx()
    val dip = SWAG_DIP.toPx()
    val line = Path().apply {
        moveTo(0f, y)
        var x = 0f
        while (x < size.width) { quadraticTo(x + swag / 2, y + 2 * dip, x + swag, y); x += swag }
    }
    val leaf = jewels.lastOrNull() ?: wire
    drawPath(line, leaf, style = Stroke(Hairline.toPx() * 1.2f))
    if (jewels.isEmpty()) return
    val step = BULB_STEP.toPx()
    val bulb = Size(BULB.toPx() * 0.7f, BULB.toPx())
    var x = step / 2
    var i = 0
    while (x < size.width) {
        if (kotlin.math.abs(x - size.width / 2) > EMBLEM.toPx() * 0.7f) {
            val t = (x % swag) / swag
            val top = y + 4 * dip * t * (1 - t)
            drawRect(wire, Offset(x - bulb.width * 0.3f, top), Size(bulb.width * 0.6f, bulb.height * 0.3f))
            drawOval(jewels[i % jewels.size], Offset(x - bulb.width / 2, top + bulb.height * 0.25f), bulb)
            i++
        }
        x += step
    }
}

private val SWAG = 70.dp
private val SWAG_DIP = 4.dp
private val PENNANT = 12.dp
private val PENNANT_STEP = 22.dp
private val BULB = 9.dp
private val BULB_STEP = 18.dp

// The Apps handle: its emblem, rail, and lines.
private val EMBLEM = 64.dp
private val CHEVRON_LINE = 3.dp
private const val SUN_SHARE = 0.45f
private const val SUN_GAP = 1.25f
private val RAIL_LINE = 2.dp
private val RAIL_STEP = 18.dp
private val RAIL_TICK = 4.dp
private const val RAIL_ALPHA = 0.8f
private const val FLUTES = 3
private val FLUTE_GAP = 4.dp

/**
 * The raised surface the Now card and alarm sit on, and the panel every other
 * screen's words sit in, so plain text never lies on the background pattern.
 * [now] gives it the look's Now shape (a window's arch, in glass) and crown.
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    arrangement: Arrangement.Vertical = Arrangement.Top,
    now: Boolean = false,
    inner: Dp = Space.xl,
    vertical: Dp = inner,
    content: @Composable ColumnScope.() -> Unit,
) {
    val look = LocalLook.current
    val shape = if (now) look.nowShape ?: look.cardShape else look.cardShape
    val gold = Ink.accent.copy(alpha = BRACKET_ALPHA)
    val accent = Ink.accent
    val lead = Ink.bg
    val jewels = look.palette.jewels
    val ornament = Ink.ornament
    val wing = Ink.text.copy(alpha = WING_ALPHA)
    val frame = Ink.frame.copy(alpha = BRACKET_ALPHA)
    val snow = Ink.text
    // A pumpkin's ribs and stem: the ornament darkened, and the third jewel. Holly's leaves: the last jewel.
    val rib = lerp(Ink.ornament, Ink.bg, RIB_SHADE)
    val stem = look.palette.jewels.getOrNull(2) ?: Ink.frame
    val leafy = look.palette.jewels.lastOrNull() ?: Ink.frame
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ink.surface)
            .then(
                when (look.corners) {
                    Corners.BRACKETS -> Modifier.drawBehind { brackets(gold) }
                    Corners.SPARKLES -> Modifier.drawBehind { cornerSparkles(ornament) }
                    Corners.COBWEB -> Modifier.drawBehind { cobwebs(frame) }
                    Corners.SNOW -> Modifier.drawBehind { snowcap(snow) }
                    Corners.NONE -> Modifier
                },
            )
            .then(
                if (!now) {
                    Modifier
                } else {
                    when (look.crown) {
                        Crown.ROSE -> Modifier.drawBehind { keystone(accent, lead, jewels) }
                        Crown.BOW -> Modifier.drawBehind { bow(Offset(size.width / 2, CROWN_DROP.toPx()), CROWN.toPx(), ornament, accent) }
                        Crown.BROOCH -> Modifier.drawBehind {
                            brooch(Offset(size.width / 2, CROWN_DROP.toPx()), CROWN.toPx(), ornament, accent, wing)
                        }
                        Crown.PUMPKIN -> Modifier.drawBehind {
                            jack(Offset(size.width / 2, CROWN_DROP.toPx() + CROWN.toPx() * 0.3f), CROWN.toPx() * 1.5f, ornament, rib, stem, lead)
                        }
                        Crown.HOLLY -> Modifier.drawBehind { holly(Offset(size.width / 2, CROWN_DROP.toPx()), CROWN.toPx(), leafy, ornament) }
                        Crown.NONE -> Modifier
                    }
                },
            )
            .then(if (look.framed) Modifier.border(look.came, Ink.frame, shape) else Modifier)
            .padding(start = inner, end = inner, bottom = vertical, top = vertical + if (now) look.nowRise else 0.dp),
        verticalArrangement = arrangement,
        content = content,
    )
}

/** Glass's crown for the Now card: a small rose window at the top of its arch. */
private fun DrawScope.keystone(gold: Color, lead: Color, jewels: List<Color>) {
    if (jewels.size < 2) return
    val r = KEYSTONE.toPx()
    val c = Offset(size.width / 2, KEYSTONE_DROP.toPx())
    drawCircle(lead, radius = r, center = c)
    val petal = r * 0.42f
    for (k in 0..3) {
        val a = Math.toRadians(90.0 * k - 90.0).toFloat()
        drawCircle(jewels[k % 2], radius = petal, center = Offset(c.x + cos(a) * petal, c.y + sin(a) * petal))
    }
    drawCircle(gold, radius = r, center = c, style = Stroke(RAIL_LINE.toPx()))
    drawCircle(gold, radius = r * 0.18f, center = c)
}

private val KEYSTONE = 13.dp
private val KEYSTONE_DROP = 18.dp

/**
 * Starlight's corners: a sparkle tucked into each top corner, a small one below
 * it, both in the margin outside the card's text.
 */
private fun DrawScope.cornerSparkles(color: Color) {
    val i = CORNER_INSET.toPx()
    val big = CORNER_SPARKLE.toPx()
    listOf(i + big, size.width - i - big).forEach { x ->
        drawPath(sparklePath(Offset(x, i + big), big), color)
        drawPath(sparklePath(Offset(x, i + big * 3.2f), big * 0.45f), color.copy(alpha = BRACKET_ALPHA))
    }
}

/** Halloween's corners: a small cobweb spun into each top corner. */
private fun DrawScope.cobwebs(color: Color) {
    val reach = COBWEB.toPx()
    val inset = CORNER_INSET.toPx()
    val line = Hairline.toPx()
    listOf(false, true).forEach { right ->
        val o = Offset(if (right) size.width - inset else inset, inset)
        val sx = if (right) -1f else 1f
        val angles = (0..4).map { Math.toRadians(it * 22.5) }
        angles.forEach { a -> drawLine(color, o, Offset(o.x + sx * reach * cos(a).toFloat(), o.y + reach * sin(a).toFloat()), line) }
        listOf(0.35f, 0.65f, 0.95f).forEach { share ->
            val r = reach * share
            val p = Path()
            angles.forEachIndexed { i, a ->
                val x = o.x + sx * r * cos(a).toFloat()
                val y = o.y + r * sin(a).toFloat()
                if (i == 0) p.moveTo(x, y) else {
                    val m = a - Math.toRadians(11.25)
                    p.quadraticTo(o.x + sx * r * WEB_SAG_CARD * cos(m).toFloat(), o.y + r * WEB_SAG_CARD * sin(m).toFloat(), x, y)
                }
            }
            drawPath(p, color, style = Stroke(line))
        }
    }
}

/** Christmas's corners: a drift of snow along the card's top edge. */
private fun DrawScope.snowcap(color: Color) {
    val h = SNOWCAP.toPx()
    val bump = SNOW_BUMP.toPx()
    val p = Path().apply {
        moveTo(0f, 0f)
        lineTo(size.width, 0f)
        lineTo(size.width, h * 0.6f)
        var x = size.width
        var k = 0
        while (x > 0f) {
            val next = maxOf(0f, x - bump)
            quadraticTo((x + next) / 2, h * (if (k % 2 == 0) 1.15f else 0.95f), next, h * 0.6f)
            x = next
            k++
        }
        close()
    }
    drawPath(p, color)
}

private val COBWEB = 30.dp
private const val WEB_SAG_CARD = 0.88f
private val SNOWCAP = 9.dp
private val SNOW_BUMP = 18.dp
private const val RIB_SHADE = 0.35f

// The cute crowns: their size and how far below the card's top they sit.
private val CROWN = 9.dp
private val CROWN_DROP = 14.dp
private const val WING_ALPHA = 0.85f
private val CORNER_SPARKLE = 5.dp
private val CORNER_INSET = 6.dp

private val Hairline = 1.dp

/**
 * Two nested gold corner brackets inside each top corner, after the quest log's
 * Dark theme: a long pair, and a short pair tucked inside it.
 */
private fun DrawScope.brackets(color: Color) {
    val line = Hairline.toPx()
    listOf(BRACKET_OUTER to BRACKET_LONG, BRACKET_INNER to BRACKET_SHORT).forEach { (inset, length) ->
        val i = inset.toPx()
        val l = length.toPx()
        // Top left, then top right, mirrored.
        drawRect(color, topLeft = Offset(i, i), size = Size(l, line))
        drawRect(color, topLeft = Offset(i, i), size = Size(line, l))
        drawRect(color, topLeft = Offset(size.width - i - l, i), size = Size(l, line))
        drawRect(color, topLeft = Offset(size.width - i - line, i), size = Size(line, l))
    }
}

private const val BRACKET_ALPHA = 0.6f
private val BRACKET_OUTER = 16.dp
private val BRACKET_INNER = 20.dp
private val BRACKET_LONG = 16.dp
private val BRACKET_SHORT = 8.dp

/**
 * Where the background honeycomb sits: one hexagon's width, and the center of one
 * cell, in window coordinates. The dock reports its own, so its tiles sit exactly
 * on the background's cells; until then, the grid uses the dock's size, centered.
 */
data class HexLattice(val hex: Float, val origin: Offset)

/** Shared by every screen, so the grid doesn't shift between them. */
val LocalHexLattice = compositionLocalOf { mutableStateOf<HexLattice?>(null) }

/** One ring of light running out from [center]; [progress] goes from 0 to 1. */
data class Pulse(val center: Offset, val progress: Float)

/**
 * A faint flat-topped hexagon grid behind a screen, as in the sci-fi quest log.
 * A [pulse] lights the grid's lines in a ring that runs outward and fades.
 */
private fun Modifier.hexGrid(color: Color, glow: Color, lattice: HexLattice?, pulse: () -> Pulse?): Modifier = drawWithCache {
    val margin = Space.l.toPx()
    val w = lattice?.hex ?: Trio.hexFor(size.width - 2 * margin, DOCK_SLOTS)
    val origin = lattice?.origin ?: Offset(margin + w / 2, 0f)
    val r = w / 2
    val path = Path()
    Trio.cells(w, origin.x, origin.y, size.width, size.height).forEach { (x, y) ->
        for (i in 0..6) {
            val a = Math.toRadians(60.0 * i).toFloat()
            val px = x + r * cos(a)
            val py = y + r * sin(a)
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
    }
    val far = hypot(size.width, size.height)
    onDrawBehind {
        drawPath(path, color, style = Stroke(Hairline.toPx()))
        val p = pulse() ?: return@onDrawBehind
        if (p.progress <= 0f || p.progress >= 1f) return@onDrawBehind
        val ring = p.progress
        val band = PULSE_BAND
        val light = glow.copy(alpha = PULSE_ALPHA * (1f - p.progress))
        val stops = arrayOf(
            0f to Color.Transparent,
            (ring - band).coerceIn(0f, 1f) to Color.Transparent,
            ring to light,
            (ring + band).coerceIn(0f, 1f) to Color.Transparent,
            1f to Color.Transparent,
        )
        drawPath(path, Brush.radialGradient(*stops, center = p.center, radius = far), style = Stroke(PULSE_LINE.toPx()))
    }
}

/**
 * Seigaiha fans behind a screen, as in the quest log's Dark theme: rows of
 * half-hidden circles of fine rings, each row overlapping the one above. A
 * [pulse] sends a sunburst of gold rays out from its center, once.
 */
private fun Modifier.fans(color: Color, ground: Color, glow: Color, pulse: () -> Pulse?): Modifier = drawWithCache {
    val r = FAN.toPx()
    // The screen's size, taken before buildList, where "size" means the list's.
    val width = size.width
    val height = size.height
    // Row by row, top to bottom, so each lower fan covers the one above it.
    val centers = buildList {
        var row = 0
        var y = 0f
        while (y < height + 2 * r) {
            var x = if (row % 2 == 0) 0f else r
            while (x < width + 2 * r) {
                add(Offset(x, y))
                x += 2 * r
            }
            y += r
            row++
        }
    }
    val far = hypot(size.width, size.height)
    val line = Hairline.toPx()
    onDrawBehind {
        centers.forEach { c ->
            drawCircle(ground, radius = r, center = c)
            FAN_RINGS.forEach { share -> drawCircle(color, radius = r * share, center = c, style = Stroke(line)) }
        }
        val p = pulse() ?: return@onDrawBehind
        if (p.progress <= 0f || p.progress >= 1f) return@onDrawBehind
        val light = glow.copy(alpha = PULSE_ALPHA * (1f - p.progress))
        val reach = far * p.progress
        repeat(SUN_RAYS) { k ->
            val a = Math.toRadians(360.0 * k / SUN_RAYS).toFloat()
            val from = Offset(p.center.x + cos(a) * reach * RAY_START, p.center.y + sin(a) * reach * RAY_START)
            val to = Offset(p.center.x + cos(a) * reach, p.center.y + sin(a) * reach)
            drawLine(light, from, to, strokeWidth = PULSE_LINE.toPx())
        }
    }
}

/**
 * Leaded glass behind a screen: diamond quarries in the look's jewel colors and
 * clear panes, set in dark lead, at the chosen [strength]. A [pulse] opens a
 * rose window from its center that blooms and fades: the All done flourish.
 */
private fun Modifier.leadedGlass(jewels: List<Color>, strength: Float, lead: Color, gold: Color, pulse: () -> Pulse?): Modifier = drawWithCache {
    val qw = QUARRY_W.toPx()
    val qh = QUARRY_H.toPx()
    // The screen's size, taken before buildList, where "size" means the list's.
    val width = size.width
    val height = size.height
    // Each quarry's color, fixed by its place, so the glass doesn't shift; about a third are clear.
    val panes = buildList {
        var row = 0
        var y = 0f
        while (y < height + qh) {
            var col = 0
            var x = if (row % 2 == 0) 0f else qw / 2
            while (x < width + qw) {
                val pick = (col * 7 + row * 13 + (col * row) % 5) % (jewels.size + CLEAR_SHARE)
                val color = jewels.getOrNull(pick)
                val diamond = Path().apply {
                    moveTo(x, y - qh / 2)
                    lineTo(x + qw / 2, y)
                    lineTo(x, y + qh / 2)
                    lineTo(x - qw / 2, y)
                    close()
                }
                add(diamond to color)
                x += qw
                col++
            }
            y += qh / 2
            row++
        }
    }
    val came = LEAD.toPx()
    val far = hypot(size.width, size.height)
    onDrawBehind {
        panes.forEach { (diamond, color) ->
            if (color != null) drawPath(diamond, color.copy(alpha = strength))
            drawPath(diamond, lead, style = Stroke(came))
        }
        val p = pulse() ?: return@onDrawBehind
        if (p.progress <= 0f || p.progress >= 1f || jewels.isEmpty()) return@onDrawBehind
        // The rose window: twelve panes and a ring of roundels, opening out, then fading.
        val fade = 1f - p.progress
        val r = minOf(far * ROSE_REACH, ROSE_MAX.toPx()) * sqrt(p.progress)
        val box = Size(2 * r, 2 * r)
        val corner = Offset(p.center.x - r, p.center.y - r)
        for (k in 0 until ROSE_PANES) {
            val start = 360f * k / ROSE_PANES - 90f
            val sweep = 360f / ROSE_PANES
            drawArc(jewels[k % jewels.size].copy(alpha = fade), start, sweep, useCenter = true, topLeft = corner, size = box)
            drawArc(lead.copy(alpha = fade), start, sweep, useCenter = true, topLeft = corner, size = box, style = Stroke(came))
        }
        drawCircle(lead.copy(alpha = fade), radius = r * ROSE_HEART, center = p.center)
        drawCircle(gold.copy(alpha = fade), radius = r * ROSE_HEART, center = p.center, style = Stroke(came))
        drawCircle(gold.copy(alpha = fade), radius = r * ROSE_EYE, center = p.center)
        drawCircle(gold.copy(alpha = fade), radius = r, center = p.center, style = Stroke(came))
    }
}

// The leaded glass: each quarry's size, the lead between them, and how many clear panes to each colored one's kinds.
private val QUARRY_W = 40.dp
private val QUARRY_H = 60.dp
private val LEAD = 3.dp
private const val CLEAR_SHARE = 3

// The rose window flourish: how far it opens, its panes, and its center.
private const val ROSE_REACH = 0.35f
private val ROSE_MAX = 180.dp
private const val ROSE_PANES = 12
private const val ROSE_HEART = 0.3f
private const val ROSE_EYE = 0.08f

// The fans: radius, and the rings inside each (shares of the radius), as in the quest log.
private val FAN = 28.dp
private val FAN_RINGS = listOf(0.3f, 0.55f, 0.8f, 0.97f)

// The sunburst: how many rays, and where each starts, as a share of its reach.
private const val SUN_RAYS = 24
private const val RAY_START = 0.6f

// The pulse: its ring's width as a share of the screen's diagonal, its brightness, its line.
private const val PULSE_BAND = 0.12f
private const val PULSE_ALPHA = 0.9f
private val PULSE_LINE = 2.dp

/** The dock's slot count, which also sizes the background grid's hexagons. */
const val DOCK_SLOTS = Apps.DOCK_SIZE

/** The small "Now" over a card's title. Sentence case: all caps is hard to read. */
@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Type.label, color = Ink.label, modifier = modifier)
}

/** A screen or card title, announced as a heading by TalkBack. */
@Composable
fun Title(text: String, style: TextStyle = Type.display, modifier: Modifier = Modifier) {
    Text(text, style = style, color = Ink.text, modifier = modifier.semantics { heading() })
}

@Composable
fun Body(text: String, modifier: Modifier = Modifier, color: Color = Ink.dim, style: TextStyle = Type.body) {
    Text(text, style = style, color = color, modifier = modifier)
}

/** The one filled action on a screen. */
@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = Tap.min) {
    val blink = rememberBlink()
    val shape = LocalLook.current.buttonShape
    Button(
        onClick = { blink.play(); onClick() },
        modifier = modifier.heightIn(min = height).tapFx(blink, shape),
        shape = shape,
        contentPadding = ButtonPadding,
        colors = ButtonDefaults.buttonColors(containerColor = Ink.accent, contentColor = Ink.onAccent),
    ) { ButtonText(label) }
}

/**
 * Every other action. Like every button and text field, it sits on the panel's
 * dark surface, so its words stand clear of the background pattern.
 */
@Composable
fun SecondaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = Tap.min) {
    val blink = rememberBlink()
    val shape = LocalLook.current.buttonShape
    OutlinedButton(
        onClick = { blink.play(); onClick() },
        modifier = modifier.heightIn(min = height).tapFx(blink, shape),
        shape = shape,
        border = BorderStroke(Hairline, Ink.frame),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Ink.surface, contentColor = Ink.text),
        contentPadding = ButtonPadding,
    ) {
        ButtonText(label, color = Ink.text, weight = FontWeight.Normal)
    }
}

/** A small action, for Close, Cancel, and Undo: outlined, like every button, so anything tappable looks it. */
@Composable
fun QuietButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Ink.dim) {
    val blink = rememberBlink()
    val shape = LocalLook.current.buttonShape
    TextButton(
        onClick = { blink.play(); onClick() },
        modifier = modifier.heightIn(min = Tap.min).tapFx(blink, shape),
        shape = shape,
        border = BorderStroke(Hairline, Ink.frame),
        colors = ButtonDefaults.textButtonColors(containerColor = Ink.surface),
    ) {
        Text(label, style = Type.caption, color = color)
    }
}

/** A small arrow button, such as Earlier and Later: the [mark] shown, the [label] spoken. */
@Composable
fun ArrowButton(mark: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val blink = rememberBlink()
    val shape = LocalLook.current.buttonShape
    TextButton(
        onClick = { blink.play(); onClick() },
        modifier = modifier.heightIn(min = Tap.min).semantics { contentDescription = label }.tapFx(blink, shape),
        shape = shape,
        border = BorderStroke(Hairline, Ink.frame),
        colors = ButtonDefaults.textButtonColors(containerColor = Ink.surface),
    ) {
        Text(mark, style = Type.title, color = Ink.accent, modifier = Modifier.clearAndSetSemantics { })
    }
}

/**
 * A small square button with a drawn icon instead of a word, such as Ping:
 * outlined on the surface like every button. TalkBack hears [label].
 */
@Composable
fun GlyphButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    glyph: DrawScope.(Color) -> Unit,
) {
    val color = Ink.accent
    val blink = rememberBlink()
    val shape = LocalLook.current.buttonShape
    TextButton(
        onClick = { blink.play(); onClick() },
        modifier = modifier
            .then(if (text == null) Modifier.size(Tap.min) else Modifier.heightIn(min = Tap.min).widthIn(min = Tap.min))
            .semantics { contentDescription = label }
            .tapFx(blink, shape),
        shape = shape,
        border = BorderStroke(Hairline, Ink.frame),
        colors = ButtonDefaults.textButtonColors(containerColor = Ink.surface),
        contentPadding = PaddingValues(Space.s),
    ) {
        if (text == null) {
            Box(Modifier.fillMaxSize().clearAndSetSemantics { }.drawBehind { glyph(color) })
        } else {
            // A glyph with a short word beside it, such as a wait's length.
            Row(Modifier.clearAndSetSemantics { }, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(Space.xl).drawBehind { glyph(color) })
                Text(text, style = Type.button, color = color, modifier = Modifier.padding(start = Space.xs))
            }
        }
    }
}

private val ButtonPadding = PaddingValues(horizontal = Space.s, vertical = Space.s)

@Composable
private fun ButtonText(label: String, color: Color = Color.Unspecified, weight: FontWeight = FontWeight.Bold) {
    // Wraps at word breaks rather than clipping when the system font is large.
    Text(label, style = Type.button.copy(fontWeight = weight), color = color, textAlign = TextAlign.Center, maxLines = 2)
}

/**
 * A row of actions. At large system font sizes the first (the primary) takes a
 * full row and the rest go two to a row under it, so no label is cut mid-word.
 */
@Composable
fun ActionRow(vararg actions: Action) {
    if (LocalDensity.current.fontScale > LARGE_FONT) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.fillMaxWidth()) {
            actions.firstOrNull()?.content?.invoke(Modifier.fillMaxWidth())
            actions.drop(1).chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.fillMaxWidth()) {
                    pair.forEach { it.content(Modifier.weight(1f)) }
                }
            }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.fillMaxWidth()) {
            actions.forEach { it.content(Modifier.weight(it.weight)) }
        }
    }
}

/** One button in an [ActionRow]; [weight] sets its share of the row. */
class Action(val weight: Float = 1f, val content: @Composable (Modifier) -> Unit)

/** Above this system font scale, rows of buttons stack and panels drop extras. */
internal const val LARGE_FONT = 1.15f

/** One accent line, such as the next setup step. */
@Composable
fun PromptLine(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = Tap.min),
        shape = LocalLook.current.buttonShape,
        border = BorderStroke(Hairline, Ink.frame),
        colors = ButtonDefaults.textButtonColors(containerColor = Ink.surface),
    ) {
        Text(text, style = Type.caption, color = Ink.accent, modifier = Modifier.fillMaxWidth())
    }
}

/** A labelled on/off setting. The whole row is one switch for TalkBack and touch. */
@Composable
fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Tap.min)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Type.bodySmall, color = Ink.text, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = Ink.accent, checkedThumbColor = Ink.onAccent),
        )
    }
}

/**
 * A row in a list that opens something, such as an entry in the Log: outlined,
 * like every button, so anything tappable looks it. [lit] outlines it in the
 * accent, for Now. With no [onClick] it's a plain row, and has no outline.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowButton(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    lit: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = LocalLook.current.buttonShape
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = Tap.min)
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier
                        .clip(shape)
                        .background(Ink.surface)
                        .border(Hairline, if (lit) Ink.accent else Ink.frame, shape)
                        .combinedClickable(onClickLabel = onClickLabel, onClick = onClick, onLongClick = onLongClick)
                },
            )
            .padding(horizontal = Space.m, vertical = Space.xs),
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

/**
 * A labelled setting picked along a range, such as the texture's strength, with
 * its value in words beside the label. Moves in [range]'s steps.
 */
@Composable
fun SettingSlider(label: String, valueText: String, value: Int, range: IntProgression, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Type.bodySmall, color = Ink.text, modifier = Modifier.weight(1f))
            Text(valueText, style = Type.bodySmall, color = Ink.dim)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { v ->
                val snapped = range.first + ((v - range.first) / range.step).roundToInt() * range.step
                onChange(snapped.coerceIn(range.first, range.last))
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / range.step - 1,
            colors = SliderDefaults.colors(thumbColor = Ink.accent, activeTrackColor = Ink.accent, inactiveTrackColor = Ink.frame),
            modifier = Modifier.heightIn(min = Tap.min).semantics { contentDescription = label },
        )
    }
}

/**
 * A labelled pick-one setting, such as the theme, or unlabelled tabs. Each option is a radio button
 * for TalkBack. Options wrap onto more lines when they don't fit.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceRow(label: String?, options: List<String>, chosen: Int, onSelect: (Int) -> Unit) {
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxWidth().selectableGroup()) {
        if (label != null) Text(label, style = Type.bodySmall, color = Ink.text)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.s),
            modifier = Modifier.padding(top = Space.xs),
        ) {
            options.forEachIndexed { i, option ->
                val mod = Modifier.semantics { role = Role.RadioButton; selected = i == chosen }
                if (i == chosen) {
                    PrimaryButton(option, { onSelect(i) }, mod, height = Tap.min)
                } else {
                    SecondaryButton(option, { haptics.tick(); onSelect(i) }, mod, height = Tap.min)
                }
            }
        }
    }
}

/**
 * One step of an item, ticked on or off. The whole row is one checkbox for
 * TalkBack and touch. A ticked step dims; it isn't struck through, which is
 * harder to read.
 */
@Composable
fun StepRow(text: String, done: Boolean, onChange: (Boolean) -> Unit) {
    val haptics = rememberHaptics()
    val color by animateColorAsState(if (done) Ink.dim else Ink.text, label = "step")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Tap.min)
            .toggleable(value = done, role = Role.Checkbox) { haptics.toggle(it); onChange(it) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = done,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = Ink.accent, uncheckedColor = Ink.accent, checkmarkColor = Ink.onAccent),
        )
        Text(text, style = Type.bodySmall, color = color, modifier = Modifier.weight(1f).padding(start = Space.s))
    }
}

/**
 * A choice from a list that would be too long as buttons, such as the theme: a
 * row showing the chosen one, with its [swatch], that opens the list. TalkBack
 * hears "[label], [chosen]".
 */
@Composable
fun Dropdown(label: String, options: List<String>, chosen: Int, swatch: (Int) -> Color, onSelect: (Int) -> Unit) {
    val haptics = rememberHaptics()
    val shape = LocalLook.current.buttonShape
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = Type.bodySmall, color = Ink.text)
        Box(Modifier.padding(top = Space.xs)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Tap.min)
                    .clip(shape)
                    .background(Ink.surface)
                    .border(1.dp, Ink.frame, shape)
                    .clickable(onClickLabel = label) { open = true }
                    .semantics { contentDescription = "$label, ${options.getOrElse(chosen) { "" }}" }
                    .padding(horizontal = Space.l),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Swatch(swatch(chosen))
                Text(
                    options.getOrElse(chosen) { "" },
                    style = Type.body,
                    color = Ink.text,
                    modifier = Modifier.weight(1f).padding(start = Space.m).clearAndSetSemantics { },
                )
                Text(DROPDOWN_MARK, style = Type.body, color = Ink.accent, modifier = Modifier.clearAndSetSemantics { })
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(Ink.surface)) {
                options.forEachIndexed { i, option ->
                    DropdownMenuItem(
                        text = { Text(option, style = Type.body, color = Ink.text) },
                        leadingIcon = { Swatch(swatch(i)) },
                        onClick = {
                            haptics.tick()
                            open = false
                            onSelect(i)
                        },
                        modifier = Modifier.heightIn(min = Tap.min),
                    )
                }
            }
        }
    }
}

/** A small square of a color, outlined, beside a choice's name. */
@Composable
private fun Swatch(color: Color) {
    Box(Modifier.size(SWATCH).background(color).border(1.dp, Ink.frame))
}

/**
 * Round color choices, wrapping onto more rows as needed, such as a paired
 * person's color. [names] are what TalkBack hears for each; [labeled] shows
 * them under the circles too.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorChoice(colors: List<Color>, names: List<String>, chosen: Int, labeled: Boolean = false, onSelect: (Int) -> Unit) {
    val haptics = rememberHaptics()
    FlowRow(
        Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Space.m),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        colors.forEachIndexed { i, c ->
            Column(
                Modifier
                    .selectable(selected = i == chosen, role = Role.RadioButton) { haptics.tick(); onSelect(i) }
                    .semantics(mergeDescendants = true) { contentDescription = names.getOrElse(i) { "" } },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(Tap.min)
                        .clip(CircleShape)
                        .background(c)
                        .border(if (i == chosen) CHOSEN_RING else 1.dp, if (i == chosen) Ink.text else Ink.frame, CircleShape),
                )
                if (labeled) Text(names.getOrElse(i) { "" }, style = Type.caption, color = Ink.text, modifier = Modifier.clearAndSetSemantics { })
            }
        }
    }
}

private const val DROPDOWN_MARK = "⌄"
private val SWATCH = 22.dp
private val CHOSEN_RING = 3.dp

/** A setting ticked on or off, with a second line under its name. The whole row is one checkbox. */
@Composable
fun CheckRow(label: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val haptics = rememberHaptics()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Tap.min)
            .toggleable(value = checked, role = Role.Checkbox) { haptics.toggle(it); onChange(it) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = Ink.accent, uncheckedColor = Ink.accent, checkmarkColor = Ink.onAccent),
        )
        Column(Modifier.weight(1f).padding(start = Space.s)) {
            Text(label, style = Type.bodySmall, color = Ink.text)
            Text(detail, style = Type.caption, color = Ink.dim)
        }
    }
}

/**
 * Touch feedback through Android's own named effects, so the phone's haptics
 * setting applies. It answers touches only: a clear "got it", never a buzz for
 * its own sake. Done confirms; "doesn't fit" is the one "no".
 */
class Haptics(private val view: View) {
    fun confirm() = feel(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
    fun reject() = feel(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
    fun tick() = feel(HapticFeedbackConstants.CLOCK_TICK)

    fun toggle(on: Boolean) = feel(
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> HapticFeedbackConstants.CLOCK_TICK
            on -> HapticFeedbackConstants.TOGGLE_ON
            else -> HapticFeedbackConstants.TOGGLE_OFF
        },
    )

    /** A drag: picked up, passing another entry, let go. */
    fun pickUp() = feel(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.GESTURE_START else HapticFeedbackConstants.LONG_PRESS)
    fun pass() = feel(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
    fun drop() = feel(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.GESTURE_END else HapticFeedbackConstants.CLOCK_TICK)

    private fun feel(effect: Int) {
        view.performHapticFeedback(effect)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}

/**
 * Every text field's colors: the panel's dark surface underneath, so typed text
 * stands clear of the background pattern; the frame color for the outline, and
 * the accent while typing.
 */
@Composable
fun fieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Ink.surface,
    unfocusedContainerColor = Ink.surface,
    focusedBorderColor = Ink.accent,
    unfocusedBorderColor = Ink.frame,
)

/** A short note after an action, with Undo when the action can be taken back. */
@Composable
fun FlashLine(text: String, undoLabel: String?, onUndo: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = Type.bodySmall, color = Ink.accent, modifier = Modifier.weight(1f))
        if (undoLabel != null) QuietButton(undoLabel, onUndo, color = Ink.text)
    }
}

/**
 * Names used before, under or over a task's name field while it's typed: up to
 * four, newest first, the typed part in bold. A tap fills the name; holding one
 * forgets it, with Undo. It shows nothing until two letters are typed, and goes
 * once the text runs past every name.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Suggestions(typed: String, names: List<String>, onPick: (String) -> Unit, onForget: (String) -> Unit, onUnforget: (String) -> Unit) {
    // Once a name is picked, the list stays shut while the text still starts with it.
    var picked by remember { mutableStateOf<String?>(null) }
    val past = picked?.let { typed.startsWith(it, ignoreCase = true) } == true
    val shown = remember(typed, names, past) { if (past) emptyList() else Names.suggest(typed, names) }
    var forgot by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(forgot) {
        if (forgot != null) {
            delay(FORGOT_MS)
            forgot = null
        }
    }
    if (shown.isEmpty() && forgot == null) return
    val haptics = rememberHaptics()
    val shape = LocalLook.current.cardShape
    val pickLabel = stringResource(R.string.names_pick)
    val forgetLabel = stringResource(R.string.names_forget)
    val count = pluralStringResource(R.plurals.names_count, shown.size, shown.size)
    val bold = SpanStyle(fontWeight = FontWeight.Bold, color = Ink.text)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ink.surface)
            .border(Hairline, Ink.frame, shape)
            .semantics { paneTitle = count },
    ) {
        shown.forEachIndexed { i, name ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(Hairline).background(Ink.frame))
            val at = name.lowercase().indexOf(typed.trim().lowercase())
            Text(
                buildAnnotatedString {
                    append(name)
                    if (at >= 0 && typed.isNotBlank()) addStyle(bold, at, at + typed.trim().length)
                },
                style = Type.body,
                color = Ink.dim,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Tap.min)
                    .combinedClickable(
                        onClickLabel = pickLabel,
                        onClick = {
                            haptics.tick()
                            picked = name
                            onPick(name)
                        },
                        onLongClickLabel = forgetLabel,
                        onLongClick = {
                            haptics.tick()
                            forgot = name
                            onForget(name)
                        },
                    )
                    .padding(horizontal = Space.l, vertical = Space.m),
            )
        }
        forgot?.let { name ->
            if (shown.isNotEmpty()) Box(Modifier.fillMaxWidth().height(Hairline).background(Ink.frame))
            Row(Modifier.fillMaxWidth().padding(start = Space.l, end = Space.s), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.names_forgot, name), style = Type.bodySmall, color = Ink.text, modifier = Modifier.weight(1f))
                QuietButton(stringResource(R.string.action_undo), {
                    onUnforget(name)
                    forgot = null
                }, color = Ink.accent)
            }
        }
    }
}

private const val FORGOT_MS = 4_000L

/*
 * When the words run past their space: a slim bar in the right margin, and a
 * soft fade at each edge that has more past it, so it's clear there's more.
 */
fun Modifier.scrollHint(scroll: ScrollState, hint: Color, surface: Color): Modifier = drawWithContent {
    drawContent()
    val max = scroll.maxValue
    if (max <= 0) return@drawWithContent
    val shown = size.height
    val thumb = shown * shown / (shown + max)
    val top = (shown - thumb) * scroll.value / max
    drawRect(hint.copy(alpha = HINT_ALPHA), Offset(size.width + SCROLL_INSET.toPx(), top), Size(SCROLL_WIDTH.toPx(), thumb))
    val fade = FADE.toPx()
    if (scroll.value > 0) {
        drawRect(Brush.verticalGradient(listOf(surface, surface.copy(alpha = 0f)), startY = 0f, endY = fade), size = Size(size.width, fade))
    }
    if (scroll.value < max) {
        drawRect(
            Brush.verticalGradient(listOf(surface.copy(alpha = 0f), surface), startY = shown - fade, endY = shown),
            topLeft = Offset(0f, shown - fade),
            size = Size(size.width, fade),
        )
    }
}

/** The scroll hint's bar: its width, its gap from the words, and how strong; and the fade at each edge. */
private val SCROLL_WIDTH = 3.dp
private val SCROLL_INSET = 10.dp
private const val HINT_ALPHA = 0.7f
private val FADE = 24.dp
