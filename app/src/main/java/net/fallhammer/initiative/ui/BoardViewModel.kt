package net.fallhammer.initiative.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import initiative.core.Board
import initiative.core.DayWindow
import initiative.core.Item
import initiative.core.Kind
import initiative.core.LedgerMark
import initiative.core.Parsed
import initiative.core.Lapse
import initiative.core.Parser
import initiative.core.Recap
import initiative.core.Recopy
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
import net.fallhammer.initiative.data.Backup
import net.fallhammer.initiative.data.Focus
import net.fallhammer.initiative.data.QuietSetting
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
    val focus: FocusView? = null,
)

/** A running focus session: the task, when it ends, and the wall that ends it (null: the day's end). */
data class FocusView(val item: Item, val until: LocalDateTime, val wall: Item?)

/** What's drawn over the board. */
enum class Overlay { NONE, DRAWER, SETTINGS, RECOPY }

/** The recopy walk: the items to visit, oldest first, and where it's up to. */
data class RecopyWalk(val ids: List<Long>, val index: Int = 0)

/** The backup setting as shown: the chosen folder's short name and the last backup. */
data class BackupState(val folder: String? = null, val last: LocalDateTime? = null)

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

    private val _overlay = MutableStateFlow(Overlay.NONE)
    val overlay: StateFlow<Overlay> = _overlay.asStateFlow()

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

    private val _recapSince = MutableStateFlow<LocalDateTime?>(null)
    private val _autoSkipped = MutableStateFlow<Set<Long>>(emptySet())
    /** Ask items already answered in this recap. */
    private val _answered = MutableStateFlow<Set<Long>>(emptySet())

    /** "Previously on": shown after a lapse until Back to the board. */
    val recap: StateFlow<Recap?> = combine(repo.items, clock, _recapSince, _autoSkipped, _answered) { all, now, since, skipped, answered ->
        since?.let { Lapse.recap(all, skipped, it, now).let { r -> r.copy(ask = r.ask.filter { a -> a.id !in answered }) } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _lastRecopy = MutableStateFlow<LocalDateTime?>(null)
    private val _recopyLater = MutableStateFlow<LocalDateTime?>(null)

    /** The weekly recopy is due, and not put off with Later. */
    val recopyDue: StateFlow<Boolean> = combine(clock, _lastRecopy, _recopyLater) { now, last, later ->
        Recopy.due(last, now) && (later == null || !now.isBefore(later))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _walk = MutableStateFlow<RecopyWalk?>(null)
    /** The item the recopy walk is on, or null when there's no walk. */
    val recopyItem: StateFlow<Item?> = combine(repo.items, _walk) { all, walk ->
        walk?.ids?.getOrNull(walk.index)?.let { id -> all.firstOrNull { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _backup = MutableStateFlow(BackupState())
    val backup: StateFlow<BackupState> = _backup.asStateFlow()

    private val _quiet = MutableStateFlow(QuietSetting())
    val quiet: StateFlow<QuietSetting> = _quiet.asStateFlow()

    private val _look = MutableStateFlow<String?>(null)
    /** The chosen theme's id; null until settings load, which shows the default. */
    val look: StateFlow<String?> = _look.asStateFlow()

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
                _pinned.value = graph.apps.pinned()
                _unlock.value = UnlockSetting(prefs.boardOnUnlock, prefs.canJumpToBoard())
                _setup.value = graph.setup.missing(prefs)
                _lockCard.value = prefs.lockCard
                _look.value = prefs.lookId
                _quiet.value = prefs.quiet
                _focus.value = prefs.focus
                _backup.value = BackupState(prefs.backupFolder?.let(Backup::folderName), prefs.lastBackup)
                // First run: start the clocks for lapses and the weekly recopy.
                if (prefs.lastAction == null) prefs.lastAction = now
                if (prefs.lastRecopy == null) prefs.lastRecopy = now
                _lastRecopy.value = prefs.lastRecopy
                _recopyLater.value = prefs.recopyLaterUntil
                // A lapse, noticed once: the recap covers everything since the last action.
                if (prefs.recapSince == null && Lapse.started(prefs.lastAction, prefs.autoSkipStreak, now)) {
                    prefs.recapSince = prefs.lastAction ?: now
                }
                val since = prefs.recapSince
                _autoSkipped.value = since?.let { graph.dao.itemsWithNote(Scheduler.AUTO_SKIP, it.toString()).toSet() }.orEmpty()
                _recapSince.value = since
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

    /** Any action on the board: the lapse clock restarts and automatic skips stop counting. */
    private fun acted() {
        viewModelScope.launch(Dispatchers.IO) {
            graph.prefs.lastAction = LocalDateTime.now()
            graph.prefs.autoSkipStreak = 0
        }
    }

    // --- Lapse recap ---

    fun keepAsked(item: Item) {
        _answered.value = _answered.value + item.id
    }

    fun dropAsked(item: Item) {
        _answered.value = _answered.value + item.id
        viewModelScope.launch { repo.drop(item, note = "recap: dropped") }
    }

    fun dropAllAsked() {
        recap.value?.ask?.forEach(::dropAsked)
    }

    /** Back to the board: Drop-policy items go to the archive, and the lapse is over. */
    fun closeRecap() {
        val r = recap.value
        _recapSince.value = null
        _answered.value = emptySet()
        viewModelScope.launch {
            r?.dropped?.forEach { repo.drop(it, note = "recap: archived") }
            withContext(Dispatchers.IO) { graph.prefs.recapSince = null }
            acted()
        }
    }

    // --- Weekly recopy ---

    fun startRecopy() {
        val ids = Recopy.queue(items).map { it.id }
        if (ids.isEmpty()) {
            finishRecopy()
            return
        }
        _walk.value = RecopyWalk(ids)
        _overlay.value = Overlay.RECOPY
        acted()
    }

    /** Hides the recopy card until tomorrow morning. */
    fun recopyLater() {
        val until = LocalDate.now().plusDays(1).atTime(graph.window.start)
        _recopyLater.value = until
        viewModelScope.launch(Dispatchers.IO) { graph.prefs.recopyLaterUntil = until }
    }

    fun recopyKeep(item: Item) = recopyNext()

    fun recopyLaterThisWeek(item: Item) {
        viewModelScope.launch { repo.moveTo(item, Recopy.laterThisWeek(LocalDateTime.now(), graph.window), note = "recopy: later this week") }
        recopyNext()
    }

    fun recopyNextWeek(item: Item) {
        viewModelScope.launch { repo.moveTo(item, Recopy.nextWeek(LocalDateTime.now(), graph.window), note = "recopy: next week") }
        recopyNext()
    }

    fun recopyDrop(item: Item) {
        viewModelScope.launch { repo.drop(item, note = "recopy: dropped") }
        recopyNext()
    }

    /** Stop partway; the card stays until the walk is finished. */
    fun stopRecopy() {
        _walk.value = null
        _overlay.value = Overlay.NONE
    }

    private fun recopyNext() {
        val walk = _walk.value ?: return
        if (walk.index + 1 >= walk.ids.size) finishRecopy() else _walk.value = walk.copy(index = walk.index + 1)
    }

    private fun finishRecopy() {
        val now = LocalDateTime.now()
        _walk.value = null
        _overlay.value = Overlay.NONE
        _lastRecopy.value = now
        _recopyLater.value = null
        viewModelScope.launch(Dispatchers.IO) {
            graph.prefs.lastRecopy = now
            graph.prefs.recopyLaterUntil = null
        }
        show(Flash(text(R.string.recopy_finished)))
    }

    // --- Backup ---

    fun setBackupFolder(folder: Uri) {
        val app = getApplication<Application>()
        runCatching {
            app.contentResolver.takePersistableUriPermission(folder, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { graph.prefs.backupFolder = folder.toString() }
            backUpNow()
        }
    }

    fun backUpNow() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val now = LocalDateTime.now()
            val ok = withContext(Dispatchers.IO) {
                val folder = graph.prefs.backupFolder ?: return@withContext false
                Backup.write(app, graph.dao, Uri.parse(folder), now).also { if (it) graph.prefs.lastBackup = now }
            }
            _backup.value = withContext(Dispatchers.IO) { BackupState(graph.prefs.backupFolder?.let(Backup::folderName), graph.prefs.lastBackup) }
            show(Flash(text(if (ok) R.string.backup_written else R.string.backup_failed)))
        }
    }

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

    private fun saveFocus(focus: Focus?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { graph.prefs.focus = focus }
            Scheduler.sync(getApplication<Application>())
        }
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
        _overlay.value = if (open) Overlay.DRAWER else Overlay.NONE
    }

    /** Initiative's own settings screen (as opposed to the phone's, [openSettings]). */
    fun showSettings() {
        _overlay.value = Overlay.SETTINGS
    }

    /** Back from settings returns to the drawer; from the drawer, to the board. */
    fun back() {
        when (_overlay.value) {
            Overlay.SETTINGS -> _overlay.value = Overlay.DRAWER
            Overlay.RECOPY -> stopRecopy()
            else -> _overlay.value = Overlay.NONE
        }
    }

    fun setQuiet(quiet: QuietSetting) {
        _quiet.value = quiet
        viewModelScope.launch {
            withContext(Dispatchers.IO) { graph.prefs.quiet = quiet }
            Scheduler.sync(getApplication<Application>())
        }
    }

    fun launch(entry: AppEntry) {
        _overlay.value = Overlay.NONE
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

    fun setLook(id: String) {
        graph.prefs.lookId = id
        _look.value = id
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
    }
}
