package initiative.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrioTest {
    private val hex = 40f
    private val h = Trio.hexHeight(hex)

    @Test fun sizesFitTheRow() {
        val w = Trio.hexFor(370f, 6)
        assertEquals(370f, Trio.advance(w) * 5 + Trio.width(w), 0.01f)
        assertEquals(Trio.width(hex), Trio.outline(hex).maxOf { it.first }, 0.01f)
        assertEquals(Trio.height(hex), Trio.outline(hex).maxOf { it.second }, 0.01f)
    }

    @Test fun insideAndOutside() {
        val (cx, cy) = Trio.iconCenter(hex)
        assertTrue(Trio.contains(cx, cy, hex))
        // The right point, and the left notch, which belongs to the tile before.
        assertTrue(Trio.contains(Trio.width(hex) - 1, h, hex))
        assertFalse(Trio.contains(hex / 8, h, hex))
        // Top right corner of the box is empty.
        assertFalse(Trio.contains(Trio.width(hex) - 1, 1f, hex))
    }

    @Test fun tapsNearASharedEdgeGoToTheTileTheyHit() {
        // Tile 0's point lies inside tile 1's box, in tile 1's notch.
        val x = hex * 1.65f
        assertTrue(x > Trio.advance(hex))
        assertEquals(0, Trio.tileAt(x, h, hex, 6))
        // Just right of the notch is tile 1.
        assertEquals(1, Trio.tileAt(Trio.advance(hex) + hex / 2, h / 2, hex, 6))
        assertEquals(5, Trio.tileAt(Trio.advance(hex) * 5 + hex / 2, h, hex, 6))
        assertNull(Trio.tileAt(Trio.advance(hex) * 5 + Trio.width(hex) + 5, h, hex, 6))
    }

    @Test fun everyDockHexagonSitsOnABackgroundCell() {
        // A dock 6 tiles across a 1080-wide screen with 42 margins, lower on the screen.
        val w = Trio.hexFor(1080f - 84f, 6)
        val rowX = 42f
        val rowY = 1980f
        val cells = Trio.cells(w, rowX + w / 2, rowY + Trio.hexHeight(w) / 2, 1080f, 2400f)
        for (tile in 0 until 6) {
            for ((cx, cy) in Trio.centers(w)) {
                val x = rowX + tile * Trio.advance(w) + cx
                val y = rowY + cy
                assertTrue(cells.any { (gx, gy) -> kotlin.math.abs(gx - x) < 0.01f && kotlin.math.abs(gy - y) < 0.01f }, "tile $tile hexagon at ($x, $y)")
            }
        }
        // And the grid reaches every edge: some cell overlaps each one.
        val r = w / 2
        val h = Trio.hexHeight(w) / 2
        assertTrue(cells.any { it.first - r <= 0f } && cells.any { it.first + r >= 1080f })
        assertTrue(cells.any { it.second - h <= 0f } && cells.any { it.second + h >= 2400f })
    }
}
