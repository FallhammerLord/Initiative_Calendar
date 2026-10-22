package initiative.core

/**
 * Names you've used before, offered while you type a new one, so "Dinner"
 * doesn't become "Make dinner" by accident and lose what it had learned.
 */
object Names {
    const val MIN_TYPED = 2
    const val SHOWN = 4

    /**
     * Up to [limit] of [history] (newest first) that match [typed]: names that
     * start with it, then a word inside that starts with it, then names holding
     * it anywhere, then near-misses (one letter off, or two swapped). Newer names first within
     * each. Nothing for under two letters, and never the name already typed.
     */
    fun suggest(typed: String, history: List<String>, limit: Int = SHOWN): List<String> {
        val q = Item.keyFor(typed)
        if (q.length < MIN_TYPED) return emptyList()
        return history
            .mapIndexedNotNull { i, name -> rank(q, Item.keyFor(name))?.let { Triple(it, i, name) } }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .map { it.third }
            .take(limit)
    }

    /** Where [typed] falls in [name]: 0 starts it, 1 starts a word, 2 inside, 3 one letter off; null: no match. */
    private fun rank(typed: String, name: String): Int? = when {
        name == typed -> null
        name.startsWith(typed) -> 0
        name.split(' ').any { it.startsWith(typed) } -> 1
        typed in name -> 2
        typed.length >= NEAR_MIN && name.split(' ').any { oneOff(typed, it.take(typed.length + 1)) } -> 3
        else -> null
    }

    /**
     * Whether [typed] is one letter off the start of [word]: one letter changed,
     * dropped, or added, or two side by side swapped. [word] is cut to one letter longer than [typed].
     */
    private fun oneOff(typed: String, word: String): Boolean =
        (-1..1).any { d -> word.length >= typed.length + d && distance(typed, word.take(typed.length + d)) <= 1 }

    /** Letters to change, drop, or add, or neighbors to swap, to turn [a] into [b]. */
    private fun distance(a: String, b: String): Int {
        var before = IntArray(b.length + 1)
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val row = IntArray(b.length + 1)
            row[0] = i
            for (j in 1..b.length) {
                row[j] = minOf(prev[j] + 1, row[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                // Two letters swapped, as in "dentsit".
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) row[j] = minOf(row[j], before[j - 2] + 1)
            }
            before = prev
            prev = row
        }
        return prev[b.length]
    }

    /** Below this many letters, a near-miss would match too much. */
    private const val NEAR_MIN = 4
}
