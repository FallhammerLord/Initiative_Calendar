package net.fallhammer.initiative.data

import initiative.core.Item
import initiative.core.Learning
import initiative.core.Parsed
import initiative.core.Placement
import initiative.core.Recurrence
import initiative.core.Series
import initiative.core.Status
import initiative.core.instancesBetween
import initiative.core.occurrenceDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime

/** Every write goes through here, so each status change lands in the event log. */
class Repository(private val dao: ItemDao, private val placement: Placement) {

    val items: Flow<List<Item>> = dao.observeAll().map { rows -> rows.map { it.toItem() } }

    /** Saves a quick-add item. Repeated titles inherit what the text didn't set. */
    suspend fun add(parsed: Parsed, now: LocalDateTime) {
        var item = parsed.item
        dao.latestWithKey(item.titleKey)?.toItem()?.let { prev ->
            item = item.copy(
                priority = if (parsed.explicitPriority) item.priority else prev.priority,
                effort = if (parsed.explicitEffort) item.effort else prev.effort,
                durationMinutes = if (parsed.explicitDuration) item.durationMinutes else prev.durationMinutes,
                missPolicy = prev.missPolicy,
            )
        }
        val id = dao.insert(item.toRow())
        dao.insertEvent(EventRow(itemId = id, fromStatus = null, toStatus = item.status.name, at = now.toString(), note = "created"))
        if (item.isTemplate) ensureInstances(now)
    }

    /** Generates recurring instances for the next three weeks, skipping ones that exist. */
    suspend fun ensureInstances(now: LocalDateTime) {
        val all = dao.all().map { it.toItem() }
        val today = now.toLocalDate()
        val have = all.filter { it.templateId != null }.groupBy { it.templateId }
            .mapValues { (_, list) -> list.mapNotNull { it.occurrenceDate }.toSet() }
        val fresh = all.filter { it.isTemplate && it.status != Status.DROPPED }.flatMap { t ->
            val existing = have[t.id].orEmpty()
            t.instancesBetween(today, today.plusDays(LOOKAHEAD_DAYS))
                .filter { it.occurrenceDate !in existing }
        }
        if (fresh.isNotEmpty()) dao.insertAll(fresh.map { it.toRow() })
    }

    /** Done, with every step ticked: "All done" covers them all. */
    suspend fun done(item: Item, now: LocalDateTime) {
        update(item, item.copy(status = Status.DONE, doneAt = now, steps = item.steps.map { it.copy(done = true) }))
        if (item.isAnchor) return
        // Hands-on time only: what was banked before any wait, plus the last stretch.
        val run = (item.workedMinutes + (Learning.runMinutes(item.activeSince, now) ?: 0)).takeIf { it >= 1 } ?: return
        dao.insertRun(RunRow(titleKey = item.titleKey, minutes = run, at = now.toString()))
        val estimate = Learning.updatedEstimate(item.durationMinutes, dao.recentRuns(item.titleKey))
        dao.setDuration(item.titleKey, estimate)
    }

    /** Returns where the item landed, for the card to show; null for an anchor, which leaves today's board. */
    suspend fun skip(item: Item, all: List<Item>, now: LocalDateTime): String? {
        if (item.isAnchor) {
            update(item, item.copy(status = Status.DROPPED), note = "skipped")
            return null
        }
        val moved = placement.skip(item, all, now)
        update(item, moved.item, note = moved.message)
        return moved.message
    }

    suspend fun tomorrow(item: Item, now: LocalDateTime): String {
        val moved = placement.tomorrow(item, now)
        update(item, moved.item, note = moved.message)
        return moved.message
    }

    /**
     * Undo: puts an item back exactly as it was before Done, Skip, or Tomorrow.
     * A duration already learned from a Done stays learned.
     */
    suspend fun restore(before: Item) {
        val current = dao.byId(before.id)?.toItem() ?: return
        update(current, before, note = "undo")
    }

    /** Off the board for good, into the archive. */
    suspend fun drop(item: Item, note: String) {
        update(item, item.copy(status = Status.DROPPED, pinnedSlot = null), note = note)
    }

    /** Ticks one step on or off. */
    suspend fun setStep(item: Item, index: Int, done: Boolean) {
        if (index !in item.steps.indices) return
        update(item, item.copy(steps = item.steps.mapIndexed { i, s -> if (i == index) s.copy(done = done) else s }))
    }

    /** From the Log: a done or removed item goes back on the board, as it was. */
    suspend fun putBack(item: Item) {
        update(item, item.copy(status = Status.PENDING, doneAt = null, notBefore = null, pinnedSlot = null, activeSince = null), note = "put back")
    }

    /** Back on the board from [notBefore]. */
    suspend fun moveTo(item: Item, notBefore: LocalDateTime, note: String) {
        update(item, item.copy(notBefore = notBefore, status = Status.PENDING, pinnedSlot = null, activeSince = null), note = note)
    }

    /**
     * Saves changes from the edit form. A new time for an anchor starts its
     * alarm ladder over.
     */
    suspend fun edit(old: Item, new: Item) {
        update(old, new, note = "edited")
        if (old.start != new.start || old.leadMinutes != new.leadMinutes || old.kind != new.kind) dao.clearAlarm(new.id)
    }

    /**
     * Saves an edit to one time of a series, or to a one-off item, that also
     * changes the series from here on. [rule] is how it repeats after the edit:
     * - a time of a series, with a rule: the series restarts here, edited, and
     *   its open times from here on are made again;
     * - a time of a series, with no rule: the series stops, and this time stays
     *   as a one-off with the edit;
     * - a one-off, with a rule: a new series starts here, and this item becomes
     *   its first time.
     */
    suspend fun editSeries(old: Item, new: Item, rule: Recurrence?, now: LocalDateTime) {
        val template = old.templateId?.let { dao.byId(it)?.toItem() }
        val today = now.toLocalDate()
        // Open times of the series from this one's date on are made again from the edit.
        val from = (old.occurrenceDate ?: today).toString()
        when {
            template != null && rule != null -> {
                update(template, Series.from(template, new, rule), note = "series edited")
                dao.deleteOpenInstances(template.id, from, keep = NONE)
                if (!old.isOpen) edit(old, new)
                ensureInstances(now)
            }
            template != null -> {
                update(template, template.copy(status = Status.DROPPED), note = "series stopped")
                dao.deleteOpenInstances(template.id, from, keep = old.id)
                edit(old, new.copy(templateId = null))
            }
            rule != null -> {
                val first = Series.start(new, rule, today)
                val id = dao.insert(first.toRow())
                dao.insertEvent(EventRow(itemId = id, fromStatus = null, toStatus = first.status.name, at = now.toString(), note = "series started"))
                val joined = if (new.isAnchor) new else new.copy(earliestStart = Series.joinDate(new, today))
                edit(old, joined.copy(templateId = id))
                ensureInstances(now)
            }
            else -> edit(old, new)
        }
    }

    /** Dragged in the day panel: the task is held to start at [at]. */
    suspend fun pin(item: Item, at: LocalDateTime) {
        update(item, item.copy(pinnedSlot = at, notBefore = null, status = Status.PENDING), note = "moved by hand")
    }

    /** First time a float becomes Now, start its clock. */
    suspend fun markActive(item: Item, now: LocalDateTime) {
        if (item.activeSince != null || item.isAnchor) return
        // Back from a wait, it keeps its wait time, so it stays first until it's done or set aside.
        update(item, item.copy(status = Status.ACTIVE, activeSince = now, notBefore = null))
    }

    /** A step with a wait was ticked: the item steps aside until the wait ends. */
    suspend fun startWait(item: Item, minutes: Int, now: LocalDateTime) {
        update(item, item.startWait(minutes, now), note = "waiting $minutes min")
    }

    /**
     * Switching to a waitmark: the task under way goes back into the day's order,
     * its hands-on time banked; the waiting item's wait ends now.
     */
    suspend fun switchTo(target: Item, current: Item?, now: LocalDateTime) {
        if (current != null && current.id != target.id) setAside(current, now)
        if (target.waiting(now)) update(target, target.copy(waitUntil = now, notBefore = null), note = "wait ended early")
    }

    /**
     * A task that was under way and is no longer Now: its hands-on time is banked
     * and its clock stops, so only the task in hand counts as under way.
     */
    suspend fun setAside(item: Item, now: LocalDateTime) {
        if (item.isAnchor || !item.isOpen || item.activeSince == null) return
        val banked = item.workedMinutes + (Learning.runMinutes(item.activeSince, now) ?: 0)
        update(item, item.copy(status = Status.PENDING, activeSince = null, pinnedSlot = null, workedMinutes = banked), note = "set aside")
    }

    suspend fun update(old: Item, new: Item, note: String = "") {
        dao.update(new.toRow())
        if (old.status != new.status || note.isNotEmpty()) {
            dao.insertEvent(
                EventRow(
                    itemId = new.id,
                    fromStatus = old.status.name,
                    toStatus = new.status.name,
                    at = LocalDateTime.now().toString(),
                    note = note,
                ),
            )
        }
    }

    private companion object {
        const val LOOKAHEAD_DAYS = 21L
        const val NONE = -1L
    }
}
