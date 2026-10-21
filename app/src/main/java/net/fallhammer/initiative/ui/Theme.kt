package net.fallhammer.initiative.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import initiative.core.Trio
import net.fallhammer.initiative.R

/*
 * Themes. Every color and shape the app draws comes from the current Look.
 * Each Look must keep text at 7:1 contrast or better and outlines at 3:1;
 * ThemeContrastTest checks every one in CI.
 */

/** Atkinson Hyperlegible, bundled, in every theme. OFL license in docs/. */
val Atkinson = FontFamily(
    Font(R.font.atkinson_regular, FontWeight.Normal),
    Font(R.font.atkinson_bold, FontWeight.Bold),
)

data class Palette(
    val bg: Color,
    val surface: Color,
    val text: Color,
    val dim: Color,
    val accent: Color,
    val onAccent: Color,
    /** Panel and button outlines. */
    val frame: Color,
    /** Faint background pattern; drawn only when the Look asks for it. */
    val pattern: Color,
    /** Colors for a many-colored pattern and its ornaments (glass, dots, sparkles); never under text. */
    val jewels: List<Color> = emptyList(),
    /** Ornament color, such as a gold sparkle; the accent unless a look sets its own. Never text. */
    val ornament: Color = accent,
    /** A light look: dark text on light panels, so the system bars draw dark icons. */
    val light: Boolean = false,
)

/*
 * The look kit. A look is its palette and shapes plus one choice from each of
 * these slots, so a new look is mostly a list of choices; each slot has a plain
 * choice for when a look has nothing special. The contrast test checks every
 * palette, so no look can ship hard to read.
 */

/** The pattern behind every screen. */
enum class Pattern {
    NONE,

    /** A flat-topped hexagon grid lined up with the dock (Sci-fi). */
    HEX,

    /** Seigaiha fans (Deco). */
    FANS,

    /** Leaded glass in the palette's jewels (Stained glass). */
    LEADED,

    /** Scattered dots and small hearts in the palette's jewels (Pastel). */
    DOTS,

    /** Scattered four-point sparkles and specks in the palette's jewels (Starlight). */
    SPARKLES,
}

/** What crowns the Now card at its top. */
enum class Crown { NONE, ROSE, BOW, BROOCH }

/** Small ornaments in each card's top corners. */
enum class Corners { NONE, BRACKETS, SPARKLES }

/** The dial's mark for now, under the arc. */
enum class Pointer { TRIANGLE, SEED, HEART, STAR }

/** The dial's mark for each thing done. */
enum class DoneMark { TICK, COIN, HEART, SPARKLE }

/** The All done flourish: the pattern's own (a ring, a sunburst, a rose window), or its own burst. */
enum class Flourish { NONE, PATTERN, HEARTS, SPARKLES }

/** A marker bubble's shape. */
enum class Bubble { HEX, CIRCLE, MEDALLION, ROUNDEL, CANDY, GEM }

/** The Apps handle's fixture and emblem, and the waitmark bookmark's end. */
enum class HandleStyle {
    /** Twin chevrons in a hexagon on a ticked rail; a chevron cut. */
    CLAMP,

    /** A chevron in a circle, no rail; a round end. */
    PILL,

    /** A sun rising over a fluted horizon; steps. */
    ZIGGURAT,

    /** A rose window on a leaded rail; half a pointed arch. */
    LANCET,

    /** A cloud on a dotted rail; a round end. */
    CLOUD,

    /** A star compact on a ribbon rail; a ribbon's notched end. */
    COMPACT,
}

data class Look(
    val id: String,
    @StringRes val label: Int,
    val palette: Palette,
    val cardShape: Shape,
    val buttonShape: Shape,
    /** Thin outline around cards. */
    val framed: Boolean,
    /** Small labels like "Now" drawn in the accent color instead of dim. */
    val accentLabels: Boolean,
    /** Dock tiles as nested hexagon trios; otherwise a plain row of [buttonShape] squares. */
    val dockTrio: Boolean = false,
    val pattern: Pattern = Pattern.NONE,
    val corners: Corners = Corners.NONE,
    val crown: Crown = Crown.NONE,
    val pointer: Pointer = Pointer.TRIANGLE,
    val doneMark: DoneMark = DoneMark.TICK,
    val flourish: Flourish = Flourish.PATTERN,
    val bubble: Bubble = Bubble.CIRCLE,
    /** How strongly the background pattern shows, 0 to 1; set in Settings. */
    val texture: Float = DEFAULT_TEXTURE,
    /** The Apps handle's fixture. */
    val handle: HandleStyle = HandleStyle.PILL,
    /** The Now card's own shape, such as a window's arch; null: [cardShape]. */
    val nowShape: Shape? = null,
    /** Room at the Now card's top for an arch or a crown, so its words clear them. */
    val nowRise: Dp = 0.dp,
    /** Outline width for cards and panels: a hairline, or heavier came for glass. */
    val came: Dp = 1.dp,
) {
    /** Whether a pattern shows behind the screens, so its strength can be set. */
    val patterned: Boolean get() = pattern != Pattern.NONE

    /** The same look with its ornaments off: no pattern, corner details, crown, or flourish. */
    fun plain(): Look = copy(pattern = Pattern.NONE, corners = Corners.NONE, crown = Crown.NONE, flourish = Flourish.NONE)
}

/**
 * A window's arch: square feet, straight sides, and a top that rises [rise]
 * from each side to a soft point at the middle.
 */
class ArchShape(private val rise: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { rise.toPx() }.coerceAtMost(size.height / 2)
        val w = size.width
        val path = Path().apply {
            moveTo(0f, size.height)
            lineTo(0f, r)
            cubicTo(0f, r * ARCH_PULL, w * ARCH_SHOULDER, 0f, w / 2, 0f)
            cubicTo(w * (1 - ARCH_SHOULDER), 0f, w, r * ARCH_PULL, w, r)
            lineTo(w, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * A waitmark's bookmark: square on the right, where it meets the screen's edge,
 * and pointing in on the left with its look's end: a chevron cut (Sci-fi), a
 * round end (Classic, Pastel), steps (Deco), half a pointed arch (Stained
 * glass), or a ribbon's notched end (Starlight).
 */
class BookmarkShape(private val style: HandleStyle) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w, 0f)
            when (style) {
                HandleStyle.CLAMP -> {
                    lineTo(h * BOOKMARK_CUT, 0f)
                    lineTo(0f, h / 2)
                    lineTo(h * BOOKMARK_CUT, h)
                }
                HandleStyle.PILL, HandleStyle.CLOUD -> {
                    lineTo(h / 2, 0f)
                    arcTo(androidx.compose.ui.geometry.Rect(0f, 0f, h, h), 270f, -180f, false)
                }
                HandleStyle.ZIGGURAT -> {
                    val s = h * BOOKMARK_STEP
                    lineTo(3 * s, 0f)
                    lineTo(3 * s, s)
                    lineTo(2 * s, s)
                    lineTo(2 * s, 2 * s)
                    lineTo(s, 2 * s)
                    lineTo(s, h - 2 * s)
                    lineTo(2 * s, h - 2 * s)
                    lineTo(2 * s, h - s)
                    lineTo(3 * s, h - s)
                    lineTo(3 * s, h)
                }
                // A ribbon's end, notched in.
                HandleStyle.COMPACT -> {
                    lineTo(0f, 0f)
                    lineTo(h * BOOKMARK_NOTCH, h / 2)
                    lineTo(0f, h)
                }
                HandleStyle.LANCET -> {
                    val r = h * BOOKMARK_ARCH
                    lineTo(r, 0f)
                    cubicTo(r * ARCH_PULL_SIDE, 0f, 0f, h * 0.3f, 0f, h / 2)
                    cubicTo(0f, h * 0.7f, r * ARCH_PULL_SIDE, h, r, h)
                }
            }
            lineTo(w, h)
            close()
        }
        return Outline.Generic(path)
    }
}

// The bookmark's ends: the chevron's cut, the steps' size, and the arch's reach, as shares of its height.
private const val BOOKMARK_CUT = 0.4f
private const val BOOKMARK_STEP = 0.18f
private const val BOOKMARK_ARCH = 0.9f
private const val BOOKMARK_NOTCH = 0.35f
private const val ARCH_PULL_SIDE = 0.45f

// The arch's curve: how far down each side its pull starts, and where its shoulders sit.
private const val ARCH_PULL = 0.35f
private const val ARCH_SHOULDER = 0.28f

/** The dock's trio tile; see core's Trio. Sized to the tile's box, which is 1.75 hexagons wide. */
object TrioShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val points = Trio.outline(size.width / 1.75f)
        val path = Path().apply {
            moveTo(points[0].first, points[0].second)
            points.drop(1).forEach { (x, y) -> lineTo(x, y) }
            close()
        }
        return Outline.Generic(path)
    }
}

object Looks {
    /** Navy panels, teal frames, cut corners, a faint hex grid. After the quest log. */
    val SciFi = Look(
        id = "scifi",
        label = R.string.look_scifi,
        palette = Palette(
            bg = Color(0xFF0A1219),
            surface = Color(0xFF0F1C26),
            text = Color(0xFFE8F1F5),
            dim = Color(0xFFA9BEC9),
            accent = Color(0xFF3FD0E8),
            onAccent = Color(0xFF05121A),
            frame = Color(0xFF2E6F85),
            pattern = Color(0xFF3FD0E8),
        ),
        cardShape = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp),
        buttonShape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
        framed = true,
        accentLabels = true,
        dockTrio = true,
        pattern = Pattern.HEX,
        bubble = Bubble.HEX,
        handle = HandleStyle.CLAMP,
    )

    /** Black and amber, rounded. The first look. */
    val Classic = Look(
        id = "classic",
        label = R.string.look_classic,
        palette = Palette(
            bg = Color(0xFF000000),
            surface = Color(0xFF151515),
            text = Color(0xFFFFFFFF),
            dim = Color(0xFFB8B8B8),
            accent = Color(0xFFFFC857),
            onAccent = Color(0xFF000000),
            frame = Color(0xFF707070),
            pattern = Color(0x00000000),
        ),
        cardShape = RoundedCornerShape(20.dp),
        buttonShape = RoundedCornerShape(50),
        framed = false,
        accentLabels = false,
    )

    /**
     * Coffee, cream, and gold, with stepped corners and faint seigaiha fans. After
     * the quest log's Dark (art deco) theme, with its tan and gold nudged lighter
     * to reach 7:1.
     */
    val Deco = Look(
        id = "deco",
        label = R.string.look_deco,
        palette = Palette(
            bg = Color(0xFF1E1812),
            surface = Color(0xFF2A221A),
            text = Color(0xFFEADFC8),
            dim = Color(0xFFBFB195),
            accent = Color(0xFFE8AB62),
            onAccent = Color(0xFF1E1812),
            frame = Color(0xFF9A7448),
            pattern = Color(0xFFE0A054),
        ),
        cardShape = StepCornerShape(14.dp),
        buttonShape = StepCornerShape(6.dp),
        framed = true,
        accentLabels = true,
        pattern = Pattern.FANS,
        corners = Corners.BRACKETS,
        bubble = Bubble.MEDALLION,
        handle = HandleStyle.ZIGGURAT,
    )

    /**
     * Stained glass, after a cathedral window: dark panes in bronze came over a
     * ground of leaded glass, ivory text, gold light. Its glass is pomegranate,
     * garnet, violet, night blue, asphodel, and amber, for Hades and Persephone;
     * the dial keeps time with a pomegranate seed, and things done are coins.
     */
    val Glass = Look(
        id = "glass",
        label = R.string.look_glass,
        palette = Palette(
            bg = Color(0xFF0E0B12),
            surface = Color(0xFF17121C),
            text = Color(0xFFF1E8D8),
            dim = Color(0xFFC9BBA6),
            accent = Color(0xFFE3B65A),
            onAccent = Color(0xFF1A1208),
            frame = Color(0xFF8E6C38),
            pattern = Color(0xFFE3B65A),
            jewels = listOf(
                Color(0xFFA8283C),
                Color(0xFF4A2A66),
                Color(0xFF22305A),
                Color(0xFF3E5A3A),
                Color(0xFF8A5A1E),
                Color(0xFF7E1F2E),
            ),
        ),
        cardShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 4.dp, bottomStart = 4.dp),
        buttonShape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 3.dp, bottomStart = 3.dp),
        framed = true,
        accentLabels = true,
        pattern = Pattern.LEADED,
        crown = Crown.ROSE,
        pointer = Pointer.SEED,
        doneMark = DoneMark.COIN,
        bubble = Bubble.ROUNDEL,
        handle = HandleStyle.LANCET,
        nowShape = ArchShape(36.dp),
        nowRise = 28.dp,
        came = 2.dp,
    )

    /**
     * Soft and sweet: a light look. Blush cream, white panels with round corners
     * and pill buttons, deep plum text and a raspberry accent, and a scatter of
     * pastel dots and small hearts. A bow crowns the Now card; the dial keeps
     * time with a heart, things done are little hearts, and All done floats
     * hearts up. The Apps button is a cloud.
     */
    val Pastel = Look(
        id = "pastel",
        label = R.string.look_pastel,
        palette = Palette(
            bg = Color(0xFFFFF4F8),
            surface = Color(0xFFFFFFFF),
            text = Color(0xFF3B2340),
            dim = Color(0xFF634868),
            accent = Color(0xFF96205A),
            onAccent = Color(0xFFFFFFFF),
            frame = Color(0xFFC9628C),
            pattern = Color(0xFFF7C6D9),
            jewels = listOf(Color(0xFFF7C6D9), Color(0xFFBDE7DA), Color(0xFFD9CBF2), Color(0xFFFBE7A6)),
            ornament = Color(0xFFF49AC1),
            light = true,
        ),
        cardShape = RoundedCornerShape(26.dp),
        buttonShape = RoundedCornerShape(50),
        framed = true,
        accentLabels = true,
        pattern = Pattern.DOTS,
        crown = Crown.BOW,
        pointer = Pointer.HEART,
        doneMark = DoneMark.HEART,
        flourish = Flourish.HEARTS,
        bubble = Bubble.CANDY,
        handle = HandleStyle.CLOUD,
        nowRise = 16.dp,
        came = 2.dp,
    )

    /**
     * Magical girl at dusk: midnight violet with gold, pink, and lavender
     * sparkles, a bubblegum accent, and sparkles in each card's top corners. A
     * winged heart brooch crowns the Now card; the dial keeps time with a gold
     * star, things done are sparkles, and All done is a sparkle burst. The Apps
     * button is a star compact on a ribbon.
     */
    val Starlight = Look(
        id = "starlight",
        label = R.string.look_starlight,
        palette = Palette(
            bg = Color(0xFF1B1030),
            surface = Color(0xFF271945),
            text = Color(0xFFFFF4FB),
            dim = Color(0xFFE2C8EE),
            accent = Color(0xFFFF9FD0),
            onAccent = Color(0xFF2A0F2E),
            frame = Color(0xFFA27AD6),
            pattern = Color(0xFFFFD86B),
            jewels = listOf(Color(0xFFFFD86B), Color(0xFFFF9FD0), Color(0xFFC9A8F2), Color(0xFFFFFFFF)),
            ornament = Color(0xFFFFD86B),
        ),
        cardShape = RoundedCornerShape(20.dp),
        buttonShape = RoundedCornerShape(16.dp),
        framed = true,
        accentLabels = true,
        pattern = Pattern.SPARKLES,
        corners = Corners.SPARKLES,
        crown = Crown.BROOCH,
        pointer = Pointer.STAR,
        doneMark = DoneMark.SPARKLE,
        flourish = Flourish.SPARKLES,
        bubble = Bubble.GEM,
        handle = HandleStyle.COMPACT,
        nowRise = 16.dp,
        came = 2.dp,
    )

    val all = listOf(SciFi, Classic, Deco, Glass, Pastel, Starlight)

    fun byId(id: String?): Look = all.firstOrNull { it.id == id } ?: SciFi
}

/**
 * Art deco's stepped corner: each corner cut in two small square steps, like the
 * setbacks of a skyscraper. [step] is how far in the corner reaches.
 */
class StepCornerShape(private val step: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val s = with(density) { step.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2)
        val h = s / 2
        val w = size.width
        val t = size.height
        val path = Path().apply {
            moveTo(s, 0f)
            lineTo(w - s, 0f)
            lineTo(w - s, h)
            lineTo(w - h, h)
            lineTo(w - h, s)
            lineTo(w, s)
            lineTo(w, t - s)
            lineTo(w - h, t - s)
            lineTo(w - h, t - h)
            lineTo(w - s, t - h)
            lineTo(w - s, t)
            lineTo(s, t)
            lineTo(s, t - h)
            lineTo(h, t - h)
            lineTo(h, t - s)
            lineTo(0f, t - s)
            lineTo(0f, s)
            lineTo(h, s)
            lineTo(h, h)
            lineTo(s, h)
            close()
        }
        return Outline.Generic(path)
    }
}

val LocalLook = staticCompositionLocalOf { Looks.SciFi }

/** The current theme's colors. Read inside composables; capture before drawing on a Canvas. */
object Ink {
    private val p: Palette @Composable @ReadOnlyComposable get() = LocalLook.current.palette
    val bg: Color @Composable @ReadOnlyComposable get() = p.bg
    val surface: Color @Composable @ReadOnlyComposable get() = p.surface
    val text: Color @Composable @ReadOnlyComposable get() = p.text
    val dim: Color @Composable @ReadOnlyComposable get() = p.dim
    val accent: Color @Composable @ReadOnlyComposable get() = p.accent
    val onAccent: Color @Composable @ReadOnlyComposable get() = p.onAccent
    val frame: Color @Composable @ReadOnlyComposable get() = p.frame
    /** The pattern's color at the strength chosen in Settings. */
    val pattern: Color @Composable @ReadOnlyComposable get() = p.pattern.copy(alpha = p.pattern.alpha * LocalLook.current.texture)
    /** Ornaments such as a gold sparkle or a bow; never text. */
    val ornament: Color @Composable @ReadOnlyComposable get() = p.ornament
    /** Thin lines that aren't controls, such as the ledger's baseline. */
    val line: Color @Composable @ReadOnlyComposable get() = p.frame
    val label: Color @Composable @ReadOnlyComposable get() = if (LocalLook.current.accentLabels) p.accent else p.dim
}

private fun Typography.withFont(f: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = f),
    displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f),
    headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f),
    headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f),
    titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f),
    bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f),
    bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f),
    labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f),
)

@Composable
fun InitiativeTheme(look: Look = Looks.SciFi, content: @Composable () -> Unit) {
    val p = look.palette
    CompositionLocalProvider(LocalLook provides look) {
        MaterialTheme(
            // A light look takes Material's light scheme, so ripples and fields suit it.
            colorScheme = if (p.light) {
                lightColorScheme(
                    primary = p.accent,
                    onPrimary = p.onAccent,
                    secondary = p.text,
                    onSecondary = p.bg,
                    background = p.bg,
                    onBackground = p.text,
                    surface = p.surface,
                    onSurface = p.text,
                    onSurfaceVariant = p.dim,
                    outline = p.frame,
                )
            } else {
                darkColorScheme(
                    primary = p.accent,
                    onPrimary = p.onAccent,
                    secondary = p.text,
                    onSecondary = p.bg,
                    background = p.bg,
                    onBackground = p.text,
                    surface = p.surface,
                    onSurface = p.text,
                    onSurfaceVariant = p.dim,
                    outline = p.frame,
                )
            },
            typography = Typography().withFont(Atkinson),
            content = content,
        )
    }
}

/** The pattern's strength out of the box: half. */
const val DEFAULT_TEXTURE = 0.5f

/** Paper for a printed or saved report: black ink on white, whatever the look. */
object Paper {
    val ink = Color(0xFF000000)
    val dim = Color(0xFF4A4A4A)
}

/** A marker's dot in the Log's month grid. */
val MarkDot: Shape = RoundedCornerShape(50)
