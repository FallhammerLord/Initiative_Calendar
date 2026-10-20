package initiative.core

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The dock's tile: three flat-topped hexagons that all touch, two stacked on
 * the left and one nested between them on the right. Tiles point the same
 * way and nest in a row, each one's point sitting in the next one's notch.
 *
 * Every size is in the same unit as [hex], the width of one hexagon from
 * point to point.
 */
object Trio {
    private val ROOT3 = sqrt(3f)

    /** One hexagon's height, flat side to flat side. */
    fun hexHeight(hex: Float): Float = hex * ROOT3 / 2

    fun width(hex: Float): Float = hex * 1.75f
    fun height(hex: Float): Float = hexHeight(hex) * 2

    /** From one tile's left edge to the next one's. */
    fun advance(hex: Float): Float = hex * 1.5f

    /** The hexagon width that fits [count] nested tiles across [rowWidth]. */
    fun hexFor(rowWidth: Float, count: Int): Float = rowWidth / (1.5f * (count - 1) + 1.75f)

    /** The tile's outline, clockwise from the top left, as (x, y) points. */
    fun outline(hex: Float): List<Pair<Float, Float>> {
        val w = hex
        val h = hexHeight(hex)
        return listOf(
            w / 4 to 0f, 3 * w / 4 to 0f, w to h / 2, 3 * w / 2 to h / 2, 7 * w / 4 to h,
            3 * w / 2 to 3 * h / 2, w to 3 * h / 2, 3 * w / 4 to 2 * h, w / 4 to 2 * h,
            0f to 3 * h / 2, w / 4 to h, 0f to h / 2,
        )
    }

    /** The middle of the tile's wide left part, where the icon goes. */
    fun iconCenter(hex: Float): Pair<Float, Float> = hex * 0.75f to hexHeight(hex)

    /** Whether (x, y), measured from the tile's top left, falls inside the tile. */
    fun contains(x: Float, y: Float, hex: Float): Boolean {
        val h = hexHeight(hex)
        return inHex(x, y, hex / 2, h / 2, hex) || inHex(x, y, hex / 2, 3 * h / 2, hex) || inHex(x, y, 5 * hex / 4, h, hex)
    }

    /** Which of [count] nested tiles (x, y) falls in, measured from the row's top left; null for none. */
    fun tileAt(x: Float, y: Float, hex: Float, count: Int): Int? =
        (0 until count).firstOrNull { contains(x - it * advance(hex), y, hex) }

    // A flat-topped hexagon: half as wide at its top and bottom edges as across its middle.
    private fun inHex(x: Float, y: Float, cx: Float, cy: Float, w: Float): Boolean {
        val dy = abs(y - cy)
        val h = hexHeight(w)
        return dy <= h / 2 && abs(x - cx) <= w / 2 - dy * w / (2 * h)
    }
}
