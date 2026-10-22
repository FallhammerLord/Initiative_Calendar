package net.fallhammer.initiative.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * The HUD's quick effects. Each is a short one-shot (a Blink) that a tap or a
 * change starts; nothing loops unless Idle effects is on. All of them are
 * drawn, not laid out, so they never move anything, and TalkBack never hears
 * them. Android's "Remove animations" turns every one off.
 */

/** Whether Android lets apps animate; off when "Remove animations" is on. */
@Composable
fun rememberMotionOn(): Boolean = remember { ValueAnimator.areAnimatorsEnabled() }

/**
 * One short effect. [play] starts it from the top; [t] runs from 0 to 1 while
 * it plays, read while drawing so only the drawing redoes.
 */
@Stable
class Blink internal constructor(private val scope: CoroutineScope, private val on: Boolean, private val ms: Int) {
    private val anim = Animatable(1f)
    val t: Float get() = anim.value
    val playing: Boolean get() = anim.value < 1f

    fun play() {
        if (!on) return
        scope.launch {
            anim.snapTo(0f)
            anim.animateTo(1f, tween(ms, easing = LinearEasing))
        }
    }
}

@Composable
fun rememberBlink(ms: Int = TAP_MS): Blink {
    val scope = rememberCoroutineScope()
    val on = rememberMotionOn()
    return remember(scope, on, ms) { Blink(scope, on, ms) }
}

/** Plays [blink] once when [now] turns true, not when it already was. */
@Composable
fun BlinkOnRise(now: Boolean, blink: Blink) {
    val was = remember { booleanArrayOf(now) }
    LaunchedEffect(now) {
        if (now && !was[0]) blink.play()
        was[0] = now
    }
}

/** The look's tap flash over a button, clipped to its [shape]. */
@Composable
fun Modifier.tapFx(blink: Blink, shape: Shape): Modifier {
    val look = LocalLook.current
    val shine = Ink.text
    val ornament = Ink.ornament
    val bg = Ink.bg
    val jewels = look.palette.jewels
    return clip(shape).drawWithContent {
        drawContent()
        if (blink.playing) lookFx(look.fx, blink.t, shine, ornament, bg, jewels)
    }
}

/** One frame of a look's flash at [t], 0 to 1, over the whole area. */
fun DrawScope.lookFx(fx: Fx, t: Float, shine: Color, ornament: Color, bg: Color, jewels: List<Color>) {
    val w = size.width
    val h = size.height
    val fade = 1f - t
    when (fx) {
        Fx.SCAN, Fx.GLINT, Fx.SHEEN -> {
            // A bright band crosses: level for a scan, tilted for a glint, corner to corner for a sheen.
            val color = if (fx == Fx.GLINT) ornament else shine
            val x = -w * BAND + (w * (1 + 2 * BAND)) * t
            val tilt = when (fx) {
                Fx.SCAN -> 0f
                Fx.GLINT -> h * 0.6f
                Fx.SHEEN -> h * 1.4f
                else -> 0f
            }
            drawRect(
                Brush.linearGradient(
                    0f to Color.Transparent,
                    0.5f to color.copy(alpha = BAND_ALPHA * fade + BAND_FLOOR),
                    1f to Color.Transparent,
                    start = Offset(x - w * BAND, -tilt / 2),
                    end = Offset(x + w * BAND, tilt / 2),
                ),
            )
        }
        Fx.TWINKLE -> {
            // Three sparkles grow and shrink at the corners.
            val r = h * SPARKLE * sin(PI.toFloat() * t)
            val spots = listOf(Offset(w * 0.12f, h * 0.25f), Offset(w * 0.88f, h * 0.3f), Offset(w * 0.5f, h * 0.85f))
            spots.forEachIndexed { i, c ->
                drawPath(sparklePath(c, r), jewels.getOrElse(i) { ornament })
            }
        }
        Fx.WISP -> {
            // A small ghost rises from the middle and fades away.
            ghost(Offset(w / 2 + w * 0.2f * t, h * (0.7f - 0.6f * t)), h * GHOST * (0.6f + 0.4f * t), shine.copy(alpha = fade), bg.copy(alpha = fade))
        }
        Fx.LIGHTS -> {
            // Fairy lights along the top edge, blinking in turn.
            val n = BULBS
            val r = h * BULB
            for (i in 0 until n) {
                val on = ((t * BULB_STEPS).toInt() + i) % 2 == 0
                val color = jewels.getOrElse(i % maxOf(jewels.size, 1)) { ornament }
                drawCircle(color.copy(alpha = if (on) fade else fade * 0.35f), r, Offset(w * (i + 0.5f) / n, r * 2))
            }
        }
    }
}

/** The outline [shape] gives this area, as a path: an arch, cut corners, steps, whatever the look's is. */
fun DrawScope.outlineOf(shape: Shape): Path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@outlineOf)) }

/** On it: the card's own [outline] closes in on it from a little outside, then fades. */
fun DrawScope.lockOn(t: Float, color: Color, outline: Path) {
    val grow = 1f + LOCK_GROW * (1f - (t * 3f).coerceAtMost(1f))
    val alpha = if (t < 0.5f) 1f else (1f - t) * 2f
    scale(grow, grow, center) {
        drawPath(outline, color.copy(alpha = alpha), style = Stroke(LOCK_WIDTH.toPx()))
    }
}

/** Done: a scan line wipes down the card, inside its [outline]. */
fun DrawScope.scanDown(t: Float, color: Color, outline: Path) = clipPath(outline) {
    val y = size.height * t
    val glow = SCAN_GLOW.toPx()
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, color.copy(alpha = 0.35f * (1 - t)), Color.Transparent), startY = y - glow, endY = y + glow),
        topLeft = Offset(0f, y - glow),
        size = Size(size.width, glow * 2),
    )
    drawLine(color.copy(alpha = 1 - t), Offset(0f, y), Offset(size.width, y), SCAN_LINE.toPx())
}

/** Ping: two radar rings go out from the button. */
fun DrawScope.radar(t: Float, color: Color) {
    val base = size.minDimension / 2
    listOf(t, t - RING_LAG).filter { it > 0f }.forEach { u ->
        drawCircle(color.copy(alpha = (1 - u).coerceIn(0f, 1f)), base * (1 + RING_REACH * u), center, style = Stroke(RING_WIDTH.toPx()))
    }
}

/** A pairmark's light coming on: one ring flares out from it. */
fun DrawScope.flare(t: Float, color: Color) {
    drawCircle(color.copy(alpha = 1 - t), size.minDimension / 2 * (1 + FLARE_REACH * t), center, style = Stroke(RING_WIDTH.toPx()))
}

/**
 * Idle effects, when chosen: a slow scan crosses the area every so often. It
 * runs only while on screen, and draws nothing between passes.
 */
@Composable
fun Modifier.idleScan(on: Boolean): Modifier {
    val blink = rememberBlink(IDLE_SWEEP_MS)
    val shine = Ink.accent
    if (on) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(IDLE_GAP_MS)
                blink.play()
            }
        }
    }
    return drawWithContent {
        drawContent()
        if (blink.playing) {
            val x = -size.width * BAND + size.width * (1 + 2 * BAND) * blink.t
            drawRect(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, shine.copy(alpha = IDLE_ALPHA), Color.Transparent),
                    startX = x - size.width * BAND / 2,
                    endX = x + size.width * BAND / 2,
                ),
            )
        }
    }
}

/** How big the breathing pointer is at [ms] into its cycle: 1, rising gently and back. */
fun breath(ms: Long): Float = 1f + BREATH * (0.5f - 0.5f * cos(2 * PI * (ms % BREATH_MS) / BREATH_MS).toFloat())

const val TAP_MS = 240
const val CHANGE_MS = 700
const val LOCK_MS = 520
const val RADAR_MS = 800
const val BREATH_MS = 3200L
private const val IDLE_SWEEP_MS = 2400
private const val IDLE_GAP_MS = 45_000L
private const val IDLE_ALPHA = 0.22f
private const val BAND = 0.35f
private const val BAND_ALPHA = 0.55f
private const val BAND_FLOOR = 0.05f
private const val SPARKLE = 0.32f
private const val GHOST = 0.7f
private const val BULBS = 6
private const val BULB = 0.07f
private const val BULB_STEPS = 6
private const val RING_LAG = 0.3f
private const val RING_REACH = 1.6f
private const val FLARE_REACH = 2.2f
private const val BREATH = 0.18f
/** How far out the lock-on outline starts: a share of the card's size. */
private const val LOCK_GROW = 0.08f
private val LOCK_WIDTH = 3.dp
private val SCAN_GLOW = 18.dp
private val SCAN_LINE = 2.dp
private val RING_WIDTH = 2.dp
