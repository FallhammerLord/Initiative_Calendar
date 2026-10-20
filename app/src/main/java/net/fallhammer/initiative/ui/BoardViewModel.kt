package net.fallhammer.initiative.ui

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import initiative.core.Board
import initiative.core.DayWindow
import initiative.core.Item
import initiative.core.Kind
import initiative.core.LedgerMark
import initiative.core.Parsed
import initiative.core.Parser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.fallhammer.initiative.R
import net.fallhammer.initiative.alarms.Scheduler
import net.fallhammer.initiative.data.AppEntry
import net.fallhammer.initiative.data.Focus
import net.fallhammer.initiative.graph
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** What the board shows. [board] is null until the database has loaded. */
data class BoardUi(
    val now: LocalDateTime = LocalDateTime.now(),
    val board: Board? = null,
    val ledger: List<LedgerMark> = emptyList(),
    val queue: List<Item> = emptyList(),
    val window: DayWindow = DayWindow(),
    val focus: FocusView? = null,
)

/** A running focus session: the task, when it ends, and the wall that ends it (null: the day's end). */
data class FocusView(val item: Item, val until: LocalDateTime, val wall: Item?)

/** What's drawn over the board. */
enum class Overlay { NONE, DRAWER, SETTINGS, PICK }

/** A short note after an action. [undo] holds the item as it was, when the action can be taken back. */
data class Flash(val text: String, val undo: Item? = null)

/**
 * The home screen's state. The board, focus, quick-add, and which screen is open
 * live here; each other feature has its own part ([recap], [recopy], [edit],
 * [backup], [settings], [launcher]) sharing this scope through [BoardParts].
 */
class BoardViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {
    private val graph = app.graph
    private val repo = graph.repo
    private val placement = graph.placement

    private val clock = MutableStateFlow(LocalDateTime.now())
    private var items: List<Item> = emptyList()
    private var instancesFor: LocalDate? = null

    private val parts = BoardParts(app, viewModelScope, show = ::show, acted = ::acted)
    val recap = RecapModel(parts, repo.items, clock)
    val recopy = RecopyModel(parts, repo.items, clock, saved) { items }
    val edit = EditModel(parts, saved) { items }
    val backup = BackupModel(parts)
    val settings = SettingsModel(parts)
    val launcher = LauncherModel(parts)

    private val _focus = MutableStateFlow<Focus?>(null)

    /** The board recalculates whenever the data changes, the clock ticks, focus changes, or the screen opens. */
    val ui: StateFlow<BoardUi> = combine(repo.items, clock, _focus) { all, now, focus ->
        items = all
        BoardUi(
            now = now,
            board = placement.board(all, now),
            ledger = placement.ledger(all, now.toLocalDate()),
            queue = placement.dayQueue(all, now),
            window = graph.window,
            focus = focus?.takeIf { now.isBefore(it.until) }?.let { f ->
                all.firstOrNull { it.id == f.itemId && it.isOpen }?.let { FocusView(it, f.until, placement.nextWall(all, now)) }
            },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BoardUi())

    private val _flash = MutableStateFlow<Flash?>(null)
    val flash: StateFlow<Flash?> = _flash.asStateFlow()
    private var flashJob: Job? = null

    private val _ask = MutableStateFlow<Parsed?>(null)
    /** A quick-add the parser couldn't place: "Anchor or float?" */
    val ask: StateFlow<Parsed?> = _ask.asStateFlow()

    /** Kept across a restart, so the drawer or settings reopen where they were. */
    val overlay: StateFlow<Overlay> = saved.getStateFlow(OVERLAY, Overlay.NONE)

    /** The dock slot an app is being picked for, while the picker is open. */
    val pickSlot: StateFlow<Int> = saved.getStateFlow(PICK_SLOT, -1)

    private val _opened = MutableStateFlow(0)
    /** Bumps each time the board comes to the front, so the screen can reset focus. */
    val opened: StateFlow<Int> = _opened.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                delay(TICK_MS)
                val now = LocalDateTime.now()
                clock.value = now
                // A lapse or a due recopy shows even while the board stays open.
                withContext(Dispatchers.IO) {
                    recap.load(graph.prefs, now)
                    recopy.load(graph.prefs, now)
                }
            }
        }
        // Start the duration clock the first time a float shows as Now.
        viewModelScope.launch {
            ui.collect { u ->
                val f = u.board?.now?.item ?: return@collect
                if (f.kind == Kind.FLOAT && f.activeSince == null) repo.markActive(f, u.now)
            }
        }
        // Focus ends by itself at the wall, or when its task is done some other way.
        viewModelScope.launch {
            ui.collect { u ->
                if (u.board != null && _focus.value != null && u.focus == null) endFocus()
            }
        }
        // Any change to the data re-arms alarms and refreshes the lock-screen card.
        viewModelScope.launch {
            repo.items.conflate().collect { Scheduler.sync(getApplication<Application>()) }
        }
        refresh()
    }

    fun refresh() {
        _opened.value += 1
        val now = LocalDateTime.now()
        clock.value = now
        // Settings files, permission checks, and the installed-app list all touch
        // disk or system services, so they're read off the main thread.
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val prefs = graph.prefs
                if (!prefs.quickDefaultsShortened) {
                    graph.dao.shortenQuickDefaults(Item.DEFAULT_DURATION, Item.QUICK_DURATION)
                    prefs.quickDefaultsShortened = true
                }
                settings.load(prefs)
                backup.load(prefs)
                recopy.load(prefs, now)
                recap.load(prefs, now)
                _focus.value = prefs.focus
                launcher.load()
            }
        }
        if (instancesFor != now.toLocalDate()) {
            instancesFor = now.toLocalDate()
            viewModelScope.launch { repo.ensureInstances(now) }
        }
    }

    fun done(item: Item) = act(item) { repo.done(item, it); text(R.string.flash_done) }
    fun skip(item: Item) = act(item) { repo.skip(item, items, it) ?: text(R.string.flash_anchor_skipped) }
    fun tomorrow(item: Item) = act(item) { repo.tomorrow(item, it) }

    /** Focus on [item] until the next Firm or Hard wall. Soft alarms stay silent meanwhile. */
    fun startFocus(item: Item) {
        val focus = Focus(item.id, placement.focusEnd(items, LocalDateTime.now()))
        _focus.value = focus
        saveFocus(focus)
        acted()
    }

    fun endFocus() {
        if (_focus.value == null) return
        _focus.value = null
        saveFocus(null)
    }

    /** Done with the focused task: marks it done, with Undo, and returns to the board. */
    fun doneWithIt(item: Item) {
        endFocus()
        done(item)
    }

    /** Takes back the last Done, Skip, or Tomorrow. */
    fun undo() {
        val before = _flash.value?.undo ?: return
        viewModelScope.launch {
            repo.restore(before)
            show(Flash(text(R.string.flash_restored)))
        }
    }

    /** Returns true when the line was understood, so the field can clear. */
    fun submit(text: String): Boolean {
        val parsed = Parser.parse(text, LocalDateTime.now()) ?: return false
        if (parsed.unsureKind) {
            _ask.value = parsed
        } else {
            save(parsed)
        }
        return true
    }

    fun answerFloat() {
        _ask.value?.let(::save)
        _ask.value = null
    }

    fun answerAnchor(time: LocalTime) {
        val p = _ask.value ?: return
        val day = p.item.earliestStart ?: LocalDate.now()
        save(p.copy(item = p.item.copy(kind = Kind.ANCHOR, start = day.atTime(time), earliestStart = null)))
        _ask.value = null
    }

    fun cancelAsk() {
        _ask.value = null
    }

    fun setDrawer(open: Boolean) {
        if (open) refresh()
        saved[OVERLAY] = if (open) Overlay.DRAWER else Overlay.NONE
    }

    /** Home: closes the drawer, settings, the edit form, or a recopy walk, back to the board. */
    fun home() {
        recopy.stop()
        edit.cancel()
        saved[OVERLAY] = Overlay.NONE
    }

    /** Initiative's own settings screen (as opposed to the phone's, [LauncherModel.openSettings]). */
    fun showSettings() {
        saved[OVERLAY] = Overlay.SETTINGS
    }

    /** Back steps out one level: edit form or recopy walk, then settings to the drawer, then the drawer to the board. */
    fun back() {
        if (edit.isOpen) {
            edit.cancel()
        } else if (recopy.walking) {
            recopy.stop()
        } else {
            saved[OVERLAY] = if (overlay.value == Overlay.SETTINGS) Overlay.DRAWER else Overlay.NONE
        }
    }

    /** A dock slot: an app opens; an empty slot opens the picker. */
    fun tapSlot(slot: Int) {
        val app = launcher.dock.value.getOrNull(slot)
        if (app != null) launch(app) else pick(slot)
    }

    /** Holding a dock slot opens the picker to change or empty it. */
    fun holdSlot(slot: Int) = pick(slot)

    fun pickApp(entry: AppEntry) {
        launcher.setSlot(pickSlot.value, entry)
        saved[OVERLAY] = Overlay.NONE
    }

    fun clearSlot() {
        launcher.setSlot(pickSlot.value, null)
        saved[OVERLAY] = Overlay.NONE
    }

    private fun pick(slot: Int) {
        saved[PICK_SLOT] = slot
        saved[OVERLAY] = Overlay.PICK
    }

    fun launch(entry: AppEntry) {
        saved[OVERLAY] = Overlay.NONE
        launcher.launch(entry)
    }

    /** Any action on the board: the lapse clock restarts and automatic skips stop counting. */
    private fun acted() {
        viewModelScope.launch(Dispatchers.IO) {
            graph.prefs.lastAction = LocalDateTime.now()
            graph.prefs.autoSkipStreak = 0
        }
    }

    private fun saveFocus(focus: Focus?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { graph.prefs.focus = focus }
            Scheduler.sync(getApplication<Application>())
        }
    }

    private fun save(parsed: Parsed) {
        acted()
        viewModelScope.launch {
            repo.add(parsed, LocalDateTime.now())
            show(Flash(getApplication<Application>().getString(R.string.flash_added, parsed.item.title)))
        }
    }

    private fun act(before: Item, block: suspend (LocalDateTime) -> String) {
        acted()
        viewModelScope.launch {
            val now = LocalDateTime.now()
            show(Flash(block(now), undo = before))
            clock.value = now
        }
    }

    private fun show(flash: Flash) {
        _flash.value = flash
        flashJob?.cancel()
        flashJob = viewModelScope.launch {
            delay(if (flash.undo != null) UNDO_MS else FLASH_MS)
            _flash.value = null
        }
    }

    private fun text(@StringRes id: Int): String = getApplication<Application>().getString(id)

    private companion object {
        const val TICK_MS = 30_000L
        const val FLASH_MS = 4_000L
        const val UNDO_MS = 8_000L
        const val OVERLAY = "overlay"
        const val PICK_SLOT = "pick_slot"
    }
}
