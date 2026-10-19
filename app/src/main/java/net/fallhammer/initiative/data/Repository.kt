package net.fallhammer.initiative.data

import initiative.core.Item
import initiative.core.Learning
import initiative.core.Parsed
import initiative.core.Placement
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
        val fresh = all.filter { it.isTemplate }.flatMap { t ->
            val existing = have[t.id].orEmpty()
            t.instancesBetween(today, today.plusDays(LOOKAHEAD_DAYS))
                .filter { it.occurrenceDate !in existing }
        }
        if (fresh.isNotEmpty()) dao.insertAll(fresh.map { it.toRow() })
    }

    suspend fun done(item: Item, now: LocalDateTime) {
        update(item, item.copy(status = Status.DONE, doneAt = now))
        if (item.isAnchor) return
        val run = Learning.runMinutes(item.activeSince, now) ?: return
        dao.insertRun(RunRow(titleKey = item.titleKey, minutes = run, at = now.toString()))
        val estimate = Learning.updatedEstimate(item.durationMinutes, dao.recentRuns(item.titleKey))
        dao.setDuration(item.titleKey, estimate)
    }

    /** Returns where the item landed, for the card to show. */
    suspend fun skip(item: Item, all: List<Item>, now: LocalDateTime): String {
        if (item.isAnchor) {
            update(item, item.copy(status = Status.DROPPED), note = "skipped")
            return "Off today's board"
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

    /** First time a float becomes Now, start its clock. */
    suspend fun markActive(item: Item, now: LocalDateTime) {
        if (item.activeSince != null || item.isAnchor) return
        update(item, item.copy(status = Status.ACTIVE, activeSince = now))
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
    }
}
