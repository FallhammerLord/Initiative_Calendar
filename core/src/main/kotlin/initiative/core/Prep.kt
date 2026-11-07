package initiative.core

import java.time.LocalDate

/**
 * Prep tasks: the work that gets you ready for a marker, handed out like a quest
 * giver's. A prep task is a task like any other, with two dates set from its
 * marker: it can't start before the heads-up window's first day, and it's due
 * by the marker's day. Before the window opens it lives only on the marker's
 * page; after, it's on the board as any task, tagged with its marker.
 *
 * A repeating marker's prep is a series on the same rule, so each time the
 * marker comes round, fresh copies come with it. See docs/spec.md, Markers.
 */
object Prep {
    /** The heads-up the first prep task sets on a marker that has none: days to work in. */
    const val DEFAULT_HEADS_UP_DAYS = 3

    /** The first day of a marker's heads-up window. */
    fun windowStart(marker: Item): LocalDate? = marker.markDays?.start?.minusDays(marker.headsUpDays.toLong())

    /** The marker with room to prepare: a heads-up of [DEFAULT_HEADS_UP_DAYS] if it has none. */
    fun withHeadsUp(marker: Item): Item =
        if (marker.headsUpDays > 0) marker else marker.copy(leadMinutes = DEFAULT_HEADS_UP_DAYS * MINUTES_A_DAY)

    /**
     * A prep task for [marker] from [task] (a float, as quick-add read it). For one
     * time of a series of markers, [series] is the marker's series: the task
     * becomes a series too, and this time's copy is made by the caller.
     */
    fun task(task: Item, marker: Item, series: Item? = null): Item {
        val first = windowStart(marker) ?: return task
        val day = marker.markDays!!.start
        val base = task.copy(kind = Kind.FLOAT, start = null, end = null, earliestStart = first, deadline = day, pinnedSlot = null, notBefore = null)
        return if (series?.recurrence != null) {
            base.copy(markerId = series.id, recurrence = series.recurrence, templateId = null)
        } else {
            base.copy(markerId = marker.id, recurrence = null)
        }
    }

    /** Still waiting for its marker's heads-up: on the marker's page only. */
    fun waiting(item: Item, today: LocalDate): Boolean =
        item.markerId != null && item.earliestStart?.isAfter(today) == true

    /**
     * The prep tasks on [marker]'s page, done ones included: its own, or for one
     * time of a series, the copies due by that time's day.
     */
    fun of(items: List<Item>, marker: Item): List<Item> {
        val day = marker.markDays?.start ?: return emptyList()
        val owner = marker.templateId ?: marker.id
        return items.filter {
            !it.isTemplate && it.markerId == owner && it.status != Status.DROPPED &&
                (marker.templateId == null || it.deadline == day)
        }.sortedBy { it.createdAt }
    }

    /** The marker a prep task is for, to tag it on the board: the time due by its deadline, for a series. */
    fun markerOf(items: List<Item>, task: Item): Item? {
        val id = task.markerId ?: return null
        val direct = items.firstOrNull { it.id == id }
        if (direct?.isTemplate != true) return direct
        return items.firstOrNull { it.templateId == id && it.markDays?.start == task.deadline } ?: direct
    }
}
