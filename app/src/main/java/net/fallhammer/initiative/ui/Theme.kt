package net.fallhammer.initiative.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
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
)

data class Look(
    val id: String,
    @StringRes val label: Int,
    val palette: Palette,
    val cardShape: Shape,
    val buttonShape: Shape,
    /** Thin outline around cards. */
    val framed: Boolean,
    /** The hexagon grid behind every screen. */
    val hexGrid: Boolean,
    /** Small labels like "Now" drawn in the accent color instead of dim. */
    val accentLabels: Boolean,
    /** Dock tiles as nested hexagon trios; otherwise a plain row of [buttonShape] squares. */
    val dockTrio: Boolean,
    /** Seigaiha fans behind every screen, in the pattern color; the Art Deco look's ground. */
    val fans: Boolean = false,
    /** Stepped gold brackets inside each card's top corners, as in the quest log's Dark theme. */
    val brackets: Boolean = false,
) {
    /** The same look with its ornaments off: no pattern, no brackets, no flourish. */
    fun plain(): Look = copy(hexGrid = false, fans = false, brackets = false)
}

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
            pattern = Color(0x803FD0E8),
        ),
        cardShape = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp),
        buttonShape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
        framed = true,
        hexGrid = true,
        accentLabels = true,
        dockTrio = true,
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
        hexGrid = false,
        accentLabels = false,
        dockTrio = false,
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
            pattern = Color(0x80E0A054),
        ),
        cardShape = StepCornerShape(14.dp),
        buttonShape = StepCornerShape(6.dp),
        framed = true,
        hexGrid = false,
        accentLabels = true,
        dockTrio = false,
        fans = true,
        brackets = true,
    )

    val all = listOf(SciFi, Classic, Deco)

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
    val pattern: Color @Composable @ReadOnlyComposable get() = p.pattern
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
            colorScheme = darkColorScheme(
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
            ),
            typography = Typography().withFont(Atkinson),
            content = content,
        )
    }
}
