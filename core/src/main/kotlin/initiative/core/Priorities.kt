package initiative.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class Level { LOW, NORMAL, HIGH }

/** A priority level plus the signal that set it, for the reason line. */
data class ResolvedPriority(val level: Level, val reason: String?)

/**
 * Hand-set priority wins. Auto starts at Normal and rises with a near deadline,
 * every two skips, and two weeks on the list.
 */
fun Item.resolvePriority(today: LocalDate): ResolvedPriority {
    when (priority) {
        Priority.HIGH -> return ResolvedPriority(Level.HIGH, "High priority")
        Priority.NORMAL -> return ResolvedPriority(Level.NORMAL, null)
        Priority.LOW -> return ResolvedPriority(Level.LOW, null)
        Priority.AUTO -> Unit
    }
    val due = deadline
    if (due != null && !due.isAfter(today.plusDays(1))) {
        return ResolvedPriority(Level.HIGH, dueText(due, today))
    }
    if (skipCount >= 2) {
        return ResolvedPriority(Level.HIGH, "Skipped $skipCount times, moved up")
    }
    if (ChronoUnit.DAYS.between(createdAt.toLocalDate(), today) >= 14) {
        return ResolvedPriority(Level.HIGH, "On the list two weeks, moved up")
    }
    return ResolvedPriority(Level.NORMAL, null)
}

/** Neutral wording for a deadline. No "overdue". */
fun dueText(due: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, due)
    return when {
        days < 0 -> "Due date passed"
        days == 0L -> "Due today"
        days == 1L -> "Due tomorrow"
        days < 7 -> "Due " + Formats.weekday(due)
        else -> "Due " + Formats.shortDate(due)
    }
}
