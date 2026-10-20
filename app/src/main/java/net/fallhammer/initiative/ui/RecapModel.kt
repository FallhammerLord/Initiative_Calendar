package net.fallhammer.initiative.ui

import initiative.core.Item
import initiative.core.Lapse
import initiative.core.Recap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.fallhammer.initiative.alarms.Scheduler
import net.fallhammer.initiative.data.Prefs
import java.time.LocalDateTime

/** "Previously on": noticed once per lapse, shown until Back to the board. */
class RecapModel(private val parts: BoardParts, items: Flow<List<Item>>, clock: Flow<LocalDateTime>) {
    private val graph get() = parts.graph
    private val since = MutableStateFlow<LocalDateTime?>(null)

    // The board re-checks for a lapse on a timer; this keeps a check from
    // bringing back a recap that's closing.
    private val lock = Mutex()
    private val autoSkipped = MutableStateFlow<Set<Long>>(emptySet())

    /** Ask items already answered in this recap. */
    private val answered = MutableStateFlow<Set<Long>>(emptySet())

    val state: StateFlow<Recap?> = combine(items, clock, since, autoSkipped, answered) { all, now, from, skipped, done ->
        from?.let { Lapse.recap(all, skipped, it, now).let { r -> r.copy(ask = r.ask.filter { a -> a.id !in done }) } }
    }.stateIn(parts.scope, SharingStarted.Eagerly, null)

    /**
     * Starts the lapse clock on first run, and notices a lapse once: the recap then
     * covers everything since the last action. Call off the main thread.
     */
    suspend fun load(prefs: Prefs, now: LocalDateTime) = lock.withLock {
        if (prefs.lastAction == null) prefs.lastAction = now
        if (prefs.recapSince == null && Lapse.started(prefs.lastAction, prefs.autoSkipStreak, now, prefs.lapseHours.toLong())) {
            prefs.recapSince = prefs.lastAction ?: now
        }
        val from = prefs.recapSince
        autoSkipped.value = from?.let { graph.dao.itemsWithNote(Scheduler.AUTO_SKIP, it.toString()).toSet() }.orEmpty()
        since.value = from
    }

    fun keep(item: Item) {
        answered.value = answered.value + item.id
    }

    fun drop(item: Item) {
        answered.value = answered.value + item.id
        parts.scope.launch { graph.repo.drop(item, note = "recap: dropped") }
    }

    fun dropAll() {
        state.value?.ask?.forEach(::drop)
    }

    /** Back to the board: Drop-policy items go to the archive, and the lapse is over. */
    fun close() {
        val r = state.value
        since.value = null
        answered.value = emptySet()
        parts.scope.launch {
            r?.dropped?.forEach { graph.repo.drop(it, note = "recap: archived") }
            withContext(Dispatchers.IO) {
                lock.withLock {
                    // The lapse clock restarts before the recap clears, so no check sees a lapse in between.
                    graph.prefs.lastAction = LocalDateTime.now()
                    graph.prefs.autoSkipStreak = 0
                    graph.prefs.recapSince = null
                    since.value = null
                }
            }
        }
    }
}
