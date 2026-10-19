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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
)

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
            pattern = Color(0x143FD0E8),
        ),
        cardShape = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp),
        buttonShape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
        framed = true,
        hexGrid = true,
        accentLabels = true,
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
    )

    val all = listOf(SciFi, Classic)

    fun byId(id: String?): Look = all.firstOrNull { it.id == id } ?: SciFi
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
