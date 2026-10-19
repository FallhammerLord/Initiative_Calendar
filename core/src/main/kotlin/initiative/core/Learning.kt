package initiative.core

import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** Duration estimates that improve as repeated items get done. */
object Learning {
    private const val WINDOW = 5
    private const val OUTLIER = 3

    /** Minutes from first becoming Now to Done, or null when there is nothing to learn from. */
    fun runMinutes(activeSince: LocalDateTime?, doneAt: LocalDateTime): Int? {
        val since = activeSince ?: return null
        val mins = Duration.between(since, doneAt).toMinutes()
        return mins.takeIf { it >= 1 }?.toInt()
    }

    /**
     * Rolling average of the last five runs, ignoring any over 3× the current estimate.
     * [runs] is newest first. Rounded to 5 minutes, never below 5.
     */
    fun updatedEstimate(current: Int, runs: List<Int>): Int {
        val kept = runs.filter { it in 1..current * OUTLIER }.take(WINDOW)
        if (kept.isEmpty()) return current
        val avg = kept.average()
        return maxOf(5, (avg / 5).roundToInt() * 5)
    }
}
