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
    /** One level of steps inside the item, ticked off as it goes. */
    val steps: List<Step> = emptyList(),
    /**
     * Set when a step with a wait is ticked: the item steps aside until then
     * (the washer runs), and comes back first. Cleared once it's Now again.
     */
    val waitUntil: LocalDateTime? = null,
    /** Hands-on minutes before a wait, so duration learning counts work, not waiting. */
    val workedMinutes: Int = 0,
) {
    val isTemplate: Boolean get() = recurrence != null
    val isOpen: Boolean get() = status == Status.PENDING || status == Status.ACTIVE
    val isAnchor: Boolean get() = kind == Kind.ANCHOR
    val anchorEnd: LocalDateTime? get() = end ?: start?.plusMinutes(DEFAULT_ANCHOR_MINUTES)
    val warnAt: LocalDateTime? get() = start?.minusMinutes(leadMinutes.toLong())
    val titleKey: String get() = keyFor(title)

    /** The first step not yet ticked; null when there are none left. */
    val nextStep: Step? get() = steps.firstOrNull { !it.done }

    /** A wait is set and hasn't ended yet. */
    fun waiting(now: LocalDateTime): Boolean = waitUntil?.isAfter(now) == true

    /** A wait has ended and the item is ready to pick up again. */
    fun ready(now: LocalDateTime): Boolean = waitUntil?.let { !it.isAfter(now) } == true

    /**
     * An anchor past its end with a wait still part of it: it runs over, and is
     * worked like a task until it's done.
     */
    fun overrunning(now: LocalDateTime): Boolean =
        isAnchor && isOpen && waitUntil != null && start != null && !now.isBefore(anchorEnd!!)

    /** When the current wait began: the wait's end less the last ticked step's wait. */
    val waitStart: LocalDateTime?
        get() = waitUntil?.let { end -> steps.lastOrNull { it.done && it.waitMinutes > 0 }?.let { end.minusMinutes(it.waitMinutes.toLong()) } }

    /**
     * The item stepping aside for a wait of [minutes] from [now]: the hands-on
     * time so far is banked, and it's held until the wait ends.
     */
    fun startWait(minutes: Int, now: LocalDateTime): Item {
        val until = now.plusMinutes(minutes.toLong())
        return copy(
            waitUntil = until,
            notBefore = until,
            workedMinutes = workedMinutes + (Learning.runMinutes(activeSince, now) ?: 0),
            activeSince = null,
            pinnedSlot = null,
            status = if (isAnchor) status else Status.PENDING,
        )
    }

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

/**
 * One step inside an item, like an objective inside a quest. [waitMinutes] is a
 * wait after it ("run washer, then 60 min"), when the item steps aside.
 */
data class Step(val title: String, val done: Boolean = false, val waitMinutes: Int = 0)

/**
 * Steps stored as text: one per line, "x " before a ticked one and "- " before an
 * open one; a wait after a step follows a tab, in minutes.
 */
object Steps {
    private const val DONE = "x "
    private const val OPEN = "- "

    fun encode(steps: List<Step>): String = steps.joinToString("\n", transform = ::line)

    fun decode(text: String): List<Step> = text.lines().filter { it.length > OPEN.length }.map(::parse)

    /** One step as one line; the edit form keeps its steps this way while they're typed. */
    fun line(step: Step): String =
        (if (step.done) DONE else OPEN) + step.title.replace('\n', ' ').replace(WAIT, ' ') +
            (if (step.waitMinutes > 0) "$WAIT${step.waitMinutes}" else "")

    fun parse(line: String): Step {
        val body = line.drop(OPEN.length)
        val tab = body.lastIndexOf(WAIT)
        val wait = if (tab >= 0) body.substring(tab + 1).toIntOrNull() else null
        val title = if (wait != null) body.substring(0, tab) else body
        return Step(title, line.startsWith(DONE), wait ?: 0)
    }

    private const val WAIT = '\t'

    /** Every step open again, for a new time of a series. */
    fun fresh(steps: List<Step>): List<Step> = steps.map { it.copy(done = false) }
}

/** The part of the day the board plans in. Outside it, Now is Free time unless an anchor is due. */
data class DayWindow(
    val start: LocalTime = LocalTime.of(8, 0),
    val end: LocalTime = LocalTime.of(22, 0),
) {
    operator fun contains(time: LocalTime): Boolean = !time.isBefore(start) && time.isBefore(end)
}
