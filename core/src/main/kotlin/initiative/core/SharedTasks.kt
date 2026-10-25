package initiative.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * Shared tasks between two paired people. Whoever made a task owns it: its
 * title, times, steps, and repeats are theirs, and their copy always wins. Each
 * person's own doing (On it, ticked steps, Done, Later, Tomorrow) stays on their
 * own phone, and each box travels so both see both. A sync carries, from each
 * side, the owner's tasks and which times each has done; a task the owner no
 * longer sends, within the month either side of today, was removed.
 */
object SharedTasks {
    /** How far back and ahead a sync looks: a month, like what's shared. */
    const val WINDOW_DAYS = 31L

    fun newUid(): String = UUID.randomUUID().toString()

    /** What one side sends the other about the tasks they share. */
    data class Out(val tasks: List<Item>, val done: Map<String, LocalDateTime>)

    /** The tasks this phone owns and shares with [peer]: series and one-offs, never a series' made times. */
    fun owned(items: List<Item>, peer: Long, today: LocalDate): List<Item> =
        items.filter { it.isShared && it.owned && it.pairId == peer && it.templateId == null && it.status != Status.DROPPED && inWindow(it, today) }

    /** Which times of the tasks shared with [peer], either side's, this phone has done. */
    fun doneHere(items: List<Item>, peer: Long, today: LocalDate): Map<String, LocalDateTime> =
        items.filter { it.isShared && it.pairId == peer && it.status == Status.DONE && inWindow(it, today) }
            .mapNotNull { i -> i.shareKey?.let { it to (i.doneAt ?: i.createdAt) } }
            .toMap()

    fun out(items: List<Item>, peer: Long, today: LocalDate) = Out(owned(items, peer, today), doneHere(items, peer, today))

    private fun inWindow(item: Item, today: LocalDate): Boolean {
        if (item.isTemplate) return true
        val day = item.occurrenceDate ?: return true
        return !day.isBefore(today.minusDays(WINDOW_DAYS)) && !day.isAfter(today.plusDays(WINDOW_DAYS))
    }

    /** What to write after a sync: items to add or change, and items to remove. */
    data class Changes(val upserts: List<Item>, val removals: List<Item>)

    /**
     * Takes in what [peer] sent: their tasks become or update this phone's copies,
     * keeping this side's own doing; tasks of theirs no longer sent go; and their
     * boxes are set from what they've done.
     */
    fun apply(local: List<Item>, peer: Long, incoming: Out, today: LocalDate, now: LocalDateTime): Changes {
        val mine = local.filter { it.pairId == peer && it.isShared }
        val upserts = mutableMapOf<Long, Item>()
        val added = mutableListOf<Item>()
        val removals = mutableListOf<Item>()
        val theirs = mine.filter { it.fromThem && it.templateId == null }.associateBy { it.uid }
        val sentUids = incoming.tasks.mapNotNull { it.uid }.toSet()

        incoming.tasks.forEach { def ->
            val here = theirs[def.uid]
            if (here == null) {
                added += def.copy(id = 0, pairId = peer, owned = false, theirDone = false, status = Status.PENDING, createdAt = now)
            } else {
                val merged = fromOwner(here, def)
                if (merged != here) upserts[here.id] = merged
                // A series changed by its owner: its open times still to come follow it.
                if (here.isTemplate) {
                    mine.filter { it.templateId == here.id && it.isOpen && (it.occurrenceDate?.isBefore(today) == false) }.forEach { time ->
                        val day = time.occurrenceDate!!
                        val fresh = merged.copy(id = here.id).instancesBetween(day, day).firstOrNull()
                        if (fresh == null) {
                            removals += time
                        } else {
                            val updated = fromOwner(time, fresh.copy(templateId = here.id))
                            if (updated != time) upserts[time.id] = updated
                        }
                    }
                }
            }
        }

        // Removed by their owner: gone here too, a series with its open times; history stays.
        theirs.values.filter { it.uid !in sentUids && inWindow(it, today) }.forEach { gone ->
            if (gone.isTemplate) {
                removals += gone
                removals += mine.filter { it.templateId == gone.id && it.isOpen }
            } else if (gone.isOpen) {
                removals += gone
            }
        }

        // Their boxes.
        val removedIds = removals.map { it.id }.toSet()
        mine.filter { it.id !in removedIds && !it.isTemplate && inWindow(it, today) }.forEach { item ->
            val base = upserts[item.id] ?: item
            val done = item.shareKey in incoming.done
            if (base.theirDone != done) upserts[item.id] = base.copy(theirDone = done)
        }
        return Changes(upserts.values.toList() + added, removals.distinctBy { it.id })
    }

    /**
     * The owner's fields from [def], this side's own doing kept from [here]: status,
     * when done, On it, moves, waits, and which steps are ticked (matched by title).
     */
    fun fromOwner(here: Item, def: Item): Item {
        val ticked = here.steps.filter { it.done }.map { it.title }.toMutableList()
        val steps = def.steps.map { s ->
            val was = ticked.indexOf(s.title)
            if (was >= 0) {
                ticked.removeAt(was)
                s.copy(done = true)
            } else {
                s.copy(done = false)
            }
        }
        return here.copy(
            forThem = false,
            title = def.title,
            notes = def.notes,
            kind = def.kind,
            start = def.start,
            end = def.end,
            wall = def.wall,
            leadMinutes = def.leadMinutes,
            durationMinutes = def.durationMinutes,
            earliestStart = def.earliestStart,
            deadline = def.deadline,
            priority = def.priority,
            effort = def.effort,
            recurrence = def.recurrence,
            missPolicy = def.missPolicy,
            icon = def.icon,
            steps = steps,
        )
    }

    /**
     * Unpaired from [peer]: sharing stops. Tasks this phone made are simply its own
     * again. Theirs stay, no longer changing, and no longer repeat: a series ends,
     * its times still to come go, and each can be claimed as this person's own.
     */
    fun unpair(local: List<Item>, peer: Long, today: LocalDate): Changes {
        val shared = local.filter { it.pairId == peer }
        val upserts = mutableListOf<Item>()
        val removals = mutableListOf<Item>()
        shared.forEach { item ->
            when {
                // A task just for them, unpaired, is simply this person's own again.
                item.owned -> upserts += item.copy(uid = null, pairId = null, theirDone = false, forThem = false)
                item.isTemplate -> upserts += item.copy(pairId = null, theirDone = false, status = Status.DROPPED)
                item.templateId != null && item.isOpen && item.occurrenceDate?.isAfter(today) == true -> removals += item
                else -> upserts += item.copy(pairId = null, theirDone = false, templateId = null)
            }
        }
        return Changes(upserts, removals)
    }

    /** Claimed after unpairing: this person's own, with every change open to them. */
    fun claim(item: Item): Item = item.copy(uid = null, pairId = null, owned = true, theirDone = false)

    /** No longer shared and not this phone's: waiting to be claimed or removed. */
    fun claimable(item: Item): Boolean = item.fromThem && item.pairId == null

    // The wire: one task to a line, as key=value fields, so a later build can add fields.

    private const val TAB = '\t'
    private const val STEP_SEP = '\u001E'

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")
    private fun unesc(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                out.append(when (s[i + 1]) { 't' -> '\t'; 'n' -> '\n'; else -> s[i + 1] })
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    /** One task's fields, for a TASK line. */
    fun encode(item: Item): String {
        val f = linkedMapOf(
            "uid" to item.uid.orEmpty(),
            "kind" to item.kind.name,
            "title" to item.title,
            "notes" to item.notes,
            "start" to item.start?.toString().orEmpty(),
            "end" to item.end?.toString().orEmpty(),
            "wall" to item.wall.name,
            "lead" to item.leadMinutes.toString(),
            "dur" to item.durationMinutes.toString(),
            "after" to item.earliestStart?.toString().orEmpty(),
            "due" to item.deadline?.toString().orEmpty(),
            "prio" to item.priority.name,
            "effort" to item.effort.name,
            "miss" to item.missPolicy.name,
            "icon" to item.icon.name,
            "rec" to item.recurrence?.let { r -> "${r.freq.name},${r.interval},${r.days.joinToString("+") { it.name }}" }.orEmpty(),
            "steps" to item.steps.joinToString(STEP_SEP.toString()) { Steps.line(it.copy(done = false)) },
        )
        return f.entries.joinToString(TAB.toString()) { (k, v) -> "$k=${esc(v)}" }
    }

    /** A task back from its fields; null when it can't be read. Unknown fields are skipped. */
    fun decode(line: String, now: LocalDateTime): Item? = runCatching {
        val f = line.split(TAB).mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq < 0) null else part.substring(0, eq) to unesc(part.substring(eq + 1))
        }.toMap()
        fun opt(k: String) = f[k]?.takeIf { it.isNotEmpty() }
        val uid = opt("uid") ?: return null
        val rec = opt("rec")?.split(',')?.let { (freq, interval, days) ->
            Recurrence(Freq.valueOf(freq), interval.toInt(), days.split('+').filter { it.isNotEmpty() }.map(DayOfWeek::valueOf).toSet())
        }
        Item(
            uid = uid,
            kind = Kind.valueOf(f.getValue("kind")),
            title = f.getValue("title"),
            notes = f["notes"].orEmpty(),
            start = opt("start")?.let(LocalDateTime::parse),
            end = opt("end")?.let(LocalDateTime::parse),
            wall = opt("wall")?.let(WallStrength::valueOf) ?: WallStrength.FIRM,
            leadMinutes = opt("lead")?.toInt() ?: Item.DEFAULT_LEAD,
            durationMinutes = opt("dur")?.toInt() ?: Item.DEFAULT_DURATION,
            earliestStart = opt("after")?.let(LocalDate::parse),
            deadline = opt("due")?.let(LocalDate::parse),
            priority = opt("prio")?.let(Priority::valueOf) ?: Priority.AUTO,
            effort = opt("effort")?.let(Effort::valueOf) ?: Effort.QUICK,
            missPolicy = opt("miss")?.let(MissPolicy::valueOf) ?: MissPolicy.REROLL,
            icon = opt("icon")?.let(MarkIcon::valueOf) ?: MarkIcon.FLAG,
            recurrence = rec,
            steps = opt("steps")?.split(STEP_SEP)?.map(Steps::parse).orEmpty(),
            createdAt = now,
            owned = false,
        )
    }.getOrNull()
}
