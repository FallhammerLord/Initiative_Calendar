package initiative.core

import java.time.LocalDate

/**
 * Editing a repeating item from one of its times onward. The series restarts
 * on that time's date with the edited fields; times before it stay as they were.
 */
object Series {
    /** The template after a "this and later" edit of [edited], one of its instances. */
    fun from(template: Item, edited: Item, rule: Recurrence): Item =
        fresh(edited, rule, edited.occurrenceDate ?: template.seriesStart).copy(id = template.id, createdAt = template.createdAt)

    /** A new template for a one-off [item] that starts repeating, from [joinDate]. */
    fun start(item: Item, rule: Recurrence, today: LocalDate): Item =
        fresh(item, rule, joinDate(item, today)).copy(id = 0)

    /**
     * The date a one-off stands for once it joins a new series, so that date isn't
     * made twice. An anchor keeps its own date. A float carried over from an
     * earlier day joins from today.
     */
    fun joinDate(item: Item, today: LocalDate): LocalDate =
        if (item.isAnchor) item.occurrenceDate ?: today else maxOf(item.occurrenceDate ?: today, today)

    private fun fresh(item: Item, rule: Recurrence, first: LocalDate): Item = item.copy(
        recurrence = rule,
        templateId = null,
        earliestStart = if (item.isAnchor) null else first,
        status = Status.PENDING,
        skipCount = 0,
        notBefore = null,
        pinnedSlot = null,
        doneAt = null,
        activeSince = null,
        steps = Steps.fresh(item.steps),
    )
}
