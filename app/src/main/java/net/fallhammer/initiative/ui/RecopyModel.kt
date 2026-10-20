package net.fallhammer.initiative.ui

import androidx.lifecycle.SavedStateHandle
import initiative.core.Item
import initiative.core.Recopy
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
import net.fallhammer.initiative.R
import net.fallhammer.initiative.data.Prefs
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The weekly recopy: the card when it's due, and the walk through the open list.
 * The walk is kept in [saved], so it picks up where it was after a restart.
 */
class RecopyModel(
    private val parts: BoardParts,
    items: Flow<List<Item>>,
    clock: Flow<LocalDateTime>,
    private val saved: SavedStateHandle,
    private val current: () -> List<Item>,
) {
    private val graph get() = parts.graph
    private val last = MutableStateFlow<LocalDateTime?>(null)
    private val laterUntil = MutableStateFlow<LocalDateTime?>(null)
    private val everyDays = MutableStateFlow(Recopy.EVERY_DAYS)

    // The board re-reads these on a timer; this keeps a read from undoing a write in flight.
    private val lock = Mutex()
    private val ids = saved.getStateFlow(IDS, LongArray(0))
    private val index = saved.getStateFlow(INDEX, 0)

    /** The weekly recopy is due, and not put off with Later. */
    val due: StateFlow<Boolean> = combine(clock, last, laterUntil, everyDays) { now, lastRecopy, later, days ->
        Recopy.due(lastRecopy, now, days) && (later == null || !now.isBefore(later))
    }.stateIn(parts.scope, SharingStarted.Eagerly, false)

    /** The item the walk is on, or null when there's no walk. */
    val item: StateFlow<Item?> = combine(items, ids, index) { all, walk, at ->
        walk.getOrNull(at)?.let { id -> all.firstOrNull { it.id == id } }
    }.stateIn(parts.scope, SharingStarted.Eagerly, null)

    val walking: Boolean get() = ids.value.isNotEmpty()

    /** Starts the weekly clock on first run. Call off the main thread. */
    suspend fun load(prefs: Prefs, now: LocalDateTime) = lock.withLock {
        if (prefs.lastRecopy == null) prefs.lastRecopy = now
        last.value = prefs.lastRecopy
        laterUntil.value = prefs.recopyLaterUntil
        everyDays.value = prefs.recopyDays.toLong()
    }

    fun start() {
        val queue = Recopy.queue(current()).map { it.id }
        parts.acted()
        if (queue.isEmpty()) finish() else walk(queue.toLongArray(), 0)
    }

    /** Hides the card until tomorrow morning. */
    fun later() {
        val until = LocalDate.now().plusDays(1).atTime(graph.window.start)
        laterUntil.value = until
        parts.scope.launch(Dispatchers.IO) {
            lock.withLock {
                graph.prefs.recopyLaterUntil = until
                laterUntil.value = until
            }
        }
    }

    fun keep() = next()

    fun laterThisWeek(item: Item) = move(item, Recopy.laterThisWeek(LocalDateTime.now(), graph.window), "recopy: later this week")

    fun nextWeek(item: Item) = move(item, Recopy.nextWeek(LocalDateTime.now(), graph.window), "recopy: next week")

    fun drop(item: Item) {
        parts.scope.launch { graph.repo.drop(item, note = "recopy: dropped") }
        next()
    }

    /** Stop partway; the card stays until a walk is finished. */
    fun stop() = walk(LongArray(0), 0)

    private fun move(item: Item, to: LocalDateTime, note: String) {
        parts.scope.launch { graph.repo.moveTo(item, to, note) }
        next()
    }

    private fun next() {
        if (!walking) return
        if (index.value + 1 >= ids.value.size) {
            finish()
        } else {
            saved[INDEX] = index.value + 1
        }
    }

    private fun finish() {
        val now = LocalDateTime.now()
        stop()
        last.value = now
        laterUntil.value = null
        parts.scope.launch(Dispatchers.IO) {
            lock.withLock {
                graph.prefs.lastRecopy = now
                graph.prefs.recopyLaterUntil = null
                last.value = now
                laterUntil.value = null
            }
        }
        parts.show(Flash(parts.text(R.string.recopy_finished)))
    }

    private fun walk(queue: LongArray, at: Int) {
        saved[IDS] = queue
        saved[INDEX] = at
    }

    private companion object {
        const val IDS = "recopy_ids"
        const val INDEX = "recopy_index"
    }
}
