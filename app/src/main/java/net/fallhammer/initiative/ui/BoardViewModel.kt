package net.fallhammer.initiative.ui

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
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
import net.fallhammer.initiative.data.SetupStep
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
)

/** Board on unlock: whether it's switched on, and whether Android allows it yet. */
data class UnlockSetting(val enabled: Boolean = true, val permitted: Boolean = true)

/** A short note after an action. [undo] holds the item as it was, when the action can be taken back. */
data class Flash(val text: String, val undo: Item? = null)

class BoardViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = app.graph
    private val repo = graph.repo
    private val placement = graph.placement

    private val clock = MutableStateFlow(LocalDateTime.now())
    private var items: List<Item> = emptyList()
    private var instancesFor: LocalDate? = null

    /** The board recalculates whenever the data changes, the clock ticks, or the screen opens. */
    val ui: StateFlow<BoardUi> = combine(repo.items, clock) { all, now ->
        items = all
        BoardUi(
            now = now,
            board = placement.board(all, now),
            ledger = placement.ledger(all, now.toLocalDate()),
            queue = placement.dayQueue(all, now),
            window = graph.window,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BoardUi())

    private val _flash = MutableStateFlow<Flash?>(null)
    val flash: StateFlow<Flash?> = _flash.asStateFlow()
    private var flashJob: Job? = null

    private val _ask = MutableStateFlow<Parsed?>(null)
    /** A quick-add the parser couldn't place: "Anchor or float?" */
    val ask: StateFlow<Parsed?> = _ask.asStateFlow()

    private val _drawerOpen = MutableStateFlow(false)
    val drawerOpen: StateFlow<Boolean> = _drawerOpen.asStateFlow()

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val appList: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val _pinned = MutableStateFlow<List<String>>(emptyList())
    val pinned: StateFlow<List<String>> = _pinned.asStateFlow()

    private val _opened = MutableStateFlow(0)
    /** Bumps each time the board comes to the front, so the screen can reset focus. */
    val opened: StateFlow<Int> = _opened.asStateFlow()

    private val _unlock = MutableStateFlow(UnlockSetting())
    val unlock: StateFlow<UnlockSetting> = _unlock.asStateFlow()

    private val _setup = MutableStateFlow<List<SetupStep>>(emptyList())
    /** Permissions still to grant, in order. */
    val setup: StateFlow<List<SetupStep>> = _setup.asStateFlow()

    private val _lockCard = MutableStateFlow(true)
    val lockCard: StateFlow<Boolean> = _lockCard.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                delay(TICK_MS)
                clock.value = LocalDateTime.now()
            }
        }
        // Start the duration clock the first time a float shows as Now.
        viewModelScope.launch {
            ui.collect { u ->
                val f = u.board?.now?.item ?: return@collect
                if (f.kind == Kind.FLOAT && f.activeSince == null) repo.markActive(f, u.now)
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
                _pinned.value = graph.apps.pinned()
                _unlock.value = UnlockSetting(prefs.boardOnUnlock, prefs.canJumpToBoard())
                _setup.value = graph.setup.missing(prefs)
                _lockCard.value = prefs.lockCard
                _apps.value = graph.apps.installed()
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
        _drawerOpen.value = open
    }

    fun launch(entry: AppEntry) {
        _drawerOpen.value = false
        graph.apps.launch(entry)
    }

    fun togglePin(entry: AppEntry) {
        graph.apps.togglePin(entry)
        _pinned.value = graph.apps.pinned()
    }

    fun setBoardOnUnlock(on: Boolean) {
        graph.prefs.boardOnUnlock = on
        _unlock.value = _unlock.value.copy(enabled = on)
        _setup.value = graph.setup.missing(graph.prefs)
    }

    fun setLockCard(on: Boolean) {
        graph.prefs.lockCard = on
        _lockCard.value = on
        viewModelScope.launch { Scheduler.sync(getApplication<Application>()) }
    }

    /**
     * Whether the notification step should use Android's one-tap prompt. The prompt
     * only works once or twice; after the first ask, the step opens Settings instead.
     */
    fun useNotificationPrompt(): Boolean {
        if (graph.prefs.askedNotifications) return false
        graph.prefs.askedNotifications = true
        return true
    }

    fun openSetup(step: SetupStep) = graph.setup.open(step)

    fun openSettings() = graph.apps.openSettings()
    fun openHomeChooser() = graph.apps.openHomeChooser()

    private fun save(parsed: Parsed) {
        viewModelScope.launch {
            repo.add(parsed, LocalDateTime.now())
            show(Flash(getApplication<Application>().getString(R.string.flash_added, parsed.item.title)))
        }
    }

    private fun act(before: Item, block: suspend (LocalDateTime) -> String) {
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
    }
}
