package net.fallhammer.initiative.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import initiative.core.Board
import initiative.core.DayWindow
import initiative.core.Item
import initiative.core.Kind
import initiative.core.LedgerMark
import initiative.core.Parsed
import initiative.core.Parser
import initiative.core.Placement
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.fallhammer.initiative.data.AppEntry
import net.fallhammer.initiative.data.Apps
import net.fallhammer.initiative.data.InitiativeDb
import net.fallhammer.initiative.data.Prefs
import net.fallhammer.initiative.data.Repository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class BoardUi(
    val now: LocalDateTime = LocalDateTime.now(),
    val board: Board? = null,
    val ledger: List<LedgerMark> = emptyList(),
    val queue: List<Item> = emptyList(),
    val window: DayWindow = DayWindow(),
)

/** Board on unlock: whether it's switched on, and whether Android allows it yet. */
data class UnlockSetting(val enabled: Boolean = true, val permitted: Boolean = true) {
    val needsPermission: Boolean get() = enabled && !permitted
}

class BoardViewModel(app: Application) : AndroidViewModel(app) {
    private val window = DayWindow()
    private val placement = Placement(window)
    private val repo = Repository(InitiativeDb.get(app).items(), placement)
    private val apps = Apps(app)
    private val prefs = Prefs(app)

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
            window = window,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BoardUi())

    private val _flash = MutableStateFlow<String?>(null)
    /** A short note on the card after a button press, e.g. "Back at 3:30, after pickup". */
    val flash: StateFlow<String?> = _flash.asStateFlow()
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
        refresh()
    }

    fun refresh() {
        _opened.value += 1
        val now = LocalDateTime.now()
        clock.value = now
        _apps.value = apps.installed()
        _pinned.value = apps.pinned()
        _unlock.value = UnlockSetting(prefs.boardOnUnlock, prefs.canJumpToBoard())
        if (instancesFor != now.toLocalDate()) {
            instancesFor = now.toLocalDate()
            viewModelScope.launch { repo.ensureInstances(now) }
        }
    }

    fun done(item: Item) = act { repo.done(item, it); "Done" }
    fun skip(item: Item) = act { repo.skip(item, items, it) }
    fun tomorrow(item: Item) = act { repo.tomorrow(item, it) }

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
        apps.launch(entry)
    }

    fun togglePin(entry: AppEntry) {
        apps.togglePin(entry)
        _pinned.value = apps.pinned()
    }

    fun setBoardOnUnlock(on: Boolean) {
        prefs.boardOnUnlock = on
        _unlock.value = _unlock.value.copy(enabled = on)
    }

    fun grantUnlockPermission() = prefs.openJumpPermission()

    fun openSettings() = apps.openSettings()
    fun openHomeChooser() = apps.openHomeChooser()

    private fun save(parsed: Parsed) {
        viewModelScope.launch {
            repo.add(parsed, LocalDateTime.now())
            show("Added: ${parsed.item.title}")
        }
    }

    private fun act(block: suspend (LocalDateTime) -> String) {
        viewModelScope.launch {
            val now = LocalDateTime.now()
            show(block(now))
            clock.value = now
        }
    }

    private fun show(message: String) {
        _flash.value = message
        flashJob?.cancel()
        flashJob = viewModelScope.launch {
            delay(FLASH_MS)
            _flash.value = null
        }
    }

    private companion object {
        const val TICK_MS = 30_000L
        const val FLASH_MS = 4_000L
    }
}
