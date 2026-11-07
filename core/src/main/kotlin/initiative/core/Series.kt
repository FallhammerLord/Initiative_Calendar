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

    /**
     * Open times of a series that pass: a time lasts until the series' next time
     * begins, so a missed one isn't carried beside the new one. A daily task
     * passes overnight; a weekly one waits up to six days. Only floats this
     * person made and hasn't started (an anchor settles through its alarms; a
     * shared time from someone else settles on their phone).
     */
    fun passing(items: List<Item>, today: LocalDate): List<Item> {
        val templates = items.filter { it.isTemplate }.associateBy { it.id }
        val dates = items.filter { it.templateId != null }.groupBy { it.templateId }
            .mapValues { (_, list) -> list.mapNotNull { it.occurrenceDate } }
        return items.filter { it.templateId != null && it.kind == Kind.FLOAT && it.owned && it.status == Status.PENDING }
            .filter { item ->
                val date = item.occurrenceDate
                if (date == null || !date.isBefore(today)) return@filter false
                val after = date.plusDays(1)
                val sibling = dates[item.templateId].orEmpty().any { !it.isBefore(after) && !it.isAfter(today) }
                val rule = templates[item.templateId]?.takeIf { it.status != Status.DROPPED }?.let { t ->
                    t.recurrence?.occurrences(t.seriesStart, after, today)?.isNotEmpty() == true
                } == true
                sibling || rule
            }
    }

    /**
     * Series whose last [STALE_TIMES] times all passed untouched: the weekly recopy
     * asks once about the series, not about each time.
     */
    fun stale(items: List<Item>, today: LocalDate): List<Item> {
        val times = items.filter { it.templateId != null }.groupBy { it.templateId }
        return items.filter { it.isTemplate && it.owned && !it.isMark && it.status != Status.DROPPED }.filter { t ->
            val past = times[t.id].orEmpty().filter { it.occurrenceDate?.isBefore(today) == true }
                .sortedByDescending { it.occurrenceDate }.take(STALE_TIMES)
            past.size == STALE_TIMES && past.all { it.status == Status.MISSED }
        }.sortedBy { it.createdAt }
    }

    const val STALE_TIMES = 3

    private fun fresh(
item: Item, rule: Recurrence, first: LocalDate): Item = item.copy(
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
        waitUntil = null,
        workedMinutes = 0,
    )
}
