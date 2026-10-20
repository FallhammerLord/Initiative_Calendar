package initiative.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class Kind { ANCHOR, FLOAT }
enum class WallStrength { SOFT, FIRM, HARD }
enum class Priority { AUTO, LOW, NORMAL, HIGH }
enum class Effort { QUICK, FOCUS }
enum class MissPolicy { REROLL, DROP, ASK }
enum class Status { PENDING, ACTIVE, DONE, MISSED, DROPPED }

/**
 * One entry on the board. Anchors use the start/end/wall fields; floats use the
 * duration/deadline/priority fields. An item with a [recurrence] is a template:
 * it never appears on the board itself, only through its generated instances.
 */
data class Item(
    val id: Long = 0,
    val title: String,
    val notes: String = "",
    val kind: Kind,
    // Anchor
    val start: LocalDateTime? = null,
    val end: LocalDateTime? = null,
    val wall: WallStrength = WallStrength.FIRM,
    val leadMinutes: Int = DEFAULT_LEAD,
    val partnerAlert: Boolean = false,
    // Float
    val durationMinutes: Int = DEFAULT_DURATION,
    val earliestStart: LocalDate? = null,
    val deadline: LocalDate? = null,
    val priority: Priority = Priority.AUTO,
    val effort: Effort = Effort.QUICK,
    val skipCount: Int = 0,
    val pinnedSlot: LocalDateTime? = null,
    /** Set by Skip and Tomorrow: the float stays off the board until this time. */
    val notBefore: LocalDateTime? = null,
    // Both
    val recurrence: Recurrence? = null,
    val templateId: Long? = null,
    val missPolicy: MissPolicy = MissPolicy.REROLL,
    val status: Status = Status.PENDING,
    val createdAt: LocalDateTime,
    val doneAt: LocalDateTime? = null,
    /** When a float first became Now; feeds duration learning. */
    val activeSince: LocalDateTime? = null,
) {
    val isTemplate: Boolean get() = recurrence != null
    val isOpen: Boolean get() = status == Status.PENDING || status == Status.ACTIVE
    val isAnchor: Boolean get() = kind == Kind.ANCHOR
    val anchorEnd: LocalDateTime? get() = end ?: start?.plusMinutes(DEFAULT_ANCHOR_MINUTES)
    val warnAt: LocalDateTime? get() = start?.minusMinutes(leadMinutes.toLong())
    val titleKey: String get() = keyFor(title)

    companion object {
        const val DEFAULT_DURATION = 30

        /** Quick tasks start smaller, so they fit the gaps between walls with time to spare. */
        const val QUICK_DURATION = 15

        /** A new task's estimate before any runs are learned. */
        fun defaultMinutes(effort: Effort): Int = if (effort == Effort.QUICK) QUICK_DURATION else DEFAULT_DURATION
        const val DEFAULT_LEAD = 15
        const val DEFAULT_ANCHOR_MINUTES = 30L

        /** Items with the same key count as the same repeated task. */
        fun keyFor(title: String): String = title.trim().lowercase().replace(Regex("\\s+"), " ")
    }
}

/** The part of the day the board plans in. Outside it, Now is Free time unless an anchor is due. */
data class DayWindow(
    val start: LocalTime = LocalTime.of(8, 0),
    val end: LocalTime = LocalTime.of(22, 0),
) {
    operator fun contains(time: LocalTime): Boolean = !time.isBefore(start) && time.isBefore(end)
}
