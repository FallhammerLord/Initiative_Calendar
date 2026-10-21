package net.fallhammer.initiative.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Every theme keeps text readable: 7:1 contrast or better for all text colors on
 * both backgrounds, and 3:1 for outlines (the WCAG levels for enhanced text and
 * for controls). A new or changed theme that misses fails CI.
 */
class ThemeContrastTest {

    @Test
    fun everyThemeMeetsContrastTargets() {
        val misses = Looks.all.flatMap { look ->
            val p = look.palette
            val text = listOf("text" to p.text, "dim" to p.dim, "accent" to p.accent)
            val grounds = listOf("bg" to p.bg, "surface" to p.surface)
            text.flatMap { (fn, f) -> grounds.map { (bn, b) -> Triple("${look.id}: $fn on $bn", ratio(f, b), TEXT) } } +
                Triple("${look.id}: onAccent on accent", ratio(p.onAccent, p.accent), TEXT) +
                grounds.map { (bn, b) -> Triple("${look.id}: frame on $bn", ratio(p.frame, b), OUTLINE) }
        } + Looks.all.flatMap { look ->
            // Pair colors: clear as lines on both grounds, and their badge's initial readable.
            val p = look.palette
            (0 until PairColors.count).flatMap { i ->
                val c = PairColors.of(i, p)
                listOf(
                    Triple("${look.id}: pair $i on bg", ratio(c, p.bg), OUTLINE),
                    Triple("${look.id}: pair $i on surface", ratio(c, p.surface), OUTLINE),
                    Triple("${look.id}: initial on pair $i", ratio(PairColors.ink(p), c), TEXT),
                )
            }
        }.filter { (_, r, target) -> r < target }

        assertTrue(
            "Below target: " + misses.joinToString { (what, r, target) -> "$what %.1f < %.1f".format(r, target) },
            misses.isEmpty(),
        )
    }

    private fun ratio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun luminance(c: Color): Double {
        val argb = c.toArgb()
        fun ch(shift: Int): Double {
            val v = ((argb shr shift) and 0xFF) / 255.0
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(16) + 0.7152 * ch(8) + 0.0722 * ch(0)
    }

    private companion object {
        const val TEXT = 7.0
        const val OUTLINE = 3.0
    }
}
