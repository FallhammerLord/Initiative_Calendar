package net.fallhammer.initiative.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import net.fallhammer.initiative.R

/** Atkinson Hyperlegible, bundled. OFL license in docs/. */
val Atkinson = FontFamily(
    Font(R.font.atkinson_regular, FontWeight.Normal),
    Font(R.font.atkinson_bold, FontWeight.Bold),
)

/** High-contrast palette. Dim text stays above 10:1 on black. */
object Ink {
    val bg = Color(0xFF000000)
    val surface = Color(0xFF151515)
    val text = Color(0xFFFFFFFF)
    val dim = Color(0xFFB8B8B8)
    val line = Color(0xFF4A4A4A)
    val accent = Color(0xFFFFC857)
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
fun InitiativeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Ink.accent,
            onPrimary = Ink.bg,
            secondary = Ink.text,
            onSecondary = Ink.bg,
            background = Ink.bg,
            onBackground = Ink.text,
            surface = Ink.surface,
            onSurface = Ink.text,
            onSurfaceVariant = Ink.dim,
            outline = Ink.line,
        ),
        typography = Typography().withFont(Atkinson),
        content = content,
    )
}
