package net.fallhammer.initiative.ui

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import initiative.core.Board
import initiative.core.DayWindow
import initiative.core.Forecast
import initiative.core.Formats
import initiative.core.Item
import initiative.core.Kind
import initiative.core.LedgerMark
import initiative.core.Parsed
import initiative.core.Marks
import initiative.core.Parser
import initiative.core.Report
import initiative.core.Shareable
import initiative.core.Sharing
import initiative.core.ReportLine
import initiative.core.Status
import initiative.core.occurrenceDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.fallhammer.initiative.R
import net.fallhammer.initiative.alarms.AlarmActions
import net.fallhammer.initiative.alarms.Scheduler
import net.fallhammer.initiative.data.AppEntry
import net.fallhammer.initiative.data.Focus
import net.fallhammer.initiative.graph
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** What the board shows. [board] is null until the database has loaded. */
data class BoardUi(
    val now: LocalDateTime = LocalDateTime.now(),
    val board: Board? = null,
    val ledger: List<LedgerMark> = emptyList(),
    /** The rest of today, in order, for the open day panel. */
    val forecast: Forecast = Forecast(emptyList(), emptyList()),
    val window: DayWindow = DayWindow(),
    val focus: FocusView? = null,
    /** Today's markers, and those coming up within their heads-up, for the HUD. */
    val marks: List<Marks.Shown> = emptyList(),
)

/** A running focus session: the task, when it ends, and the wall that ends it (null: the day's end). */
data class FocusView(val item: Item, val until: LocalDateTime, val wall: Item?)

/** What's drawn over the board. */
enum class Overlay { NONE, DRAWER, SETTINGS, MANUAL, PICK }

/**
 * A short note after an action. Undo puts [undo] and [undoAlso] back as they
 * were, and adds [readd] back after it was taken off (deleted for good, or a
 * series' later times).
 */
data class Flash(
    val text: String,
    val undo: Item? = null,
    val undoAlso: List<Item> = emptyList(),
    val readd: List<Item> = emptyList(),
) {
    val canUndo: Boolean get() = undo != null || readd.isNotEmpty()
}

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
    val names = NamesModel(parts, repo.items)
    // What could be shared is read when a ping happens, from the board as it stands then.
    val pairs = PairsModel(parts) { shareable.value }
    val launcher = LauncherModel(parts)
    val log = LogModel(parts, saved, repo.items, clock)

    private val _focus = MutableStateFlow<Focus?>(null)

    /** The person's own task order; see Placement.board. */
    private val order = MutableStateFlow<List<Long>>(emptyList())

    /** The board recalculates whenever the data changes, the clock ticks, focus changes, or the screen opens. */
    val ui: StateFlow<BoardUi> = combine(repo.items, clock, _focus, order) { all, now, focus, mine ->
        items = all
        BoardUi(
            now = now,
            board = placement.board(all, now, mine),
            ledger = placement.ledger(all, now.toLocalDate()),
            forecast = placement.forecast(all, now, mine),
            window = graph.window,
            focus = focus?.takeIf { now.isBefore(it.until) }?.let { f ->
                all.firstOrNull { it.id == f.itemId && it.isOpen }?.let { FocusView(it, f.until, placement.nextWall(all, now)) }
            },
            marks = Marks.on(all, now.toLocalDate()),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BoardUi())

    /** A wait about to start, after its grace; cancelled by a new tick. */
    private var waitJob: Job? = null

    private val _flash = MutableStateFlow<Flash?>(null)
    val flash: StateFlow<Flash?> = _flash.asStateFlow()
    private var flashJob: Job? = null

    /** The day whose report is open, saved so it survives a restart; null when closed. */
    val report: StateFlow<LocalDate?> = saved.getStateFlow(REPORT, "")
        .map { s -> s.takeIf { it.isNotEmpty() }?.let(LocalDate::parse) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The open report's lines, kept up to date. */
    val reportLines: StateFlow<List<ReportLine>> = combine(repo.items, report) { all, day -> all to day }
        .map { (all, day) -> day?.let { Report.of(all, it, LocalDate.now(), repo.movedOn(it, all)) }.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The open report's day's markers, for its top line. */
    val reportMarks: StateFlow<List<Item>> = combine(repo.items, report) { all, day -> day?.let { Report.marks(all, it, LocalDate.now()) }.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Whether the report shows notes; off by default, since notes are often private. */
    val reportNotes: StateFlow<Boolean> = saved.getStateFlow(REPORT_NOTES, false)

    fun openReport(day: LocalDate) {
        saved[REPORT] = day.toString()
    }

    fun closeReport() {
        saved[REPORT] = ""
    }

    fun setReportNotes(on: Boolean) {
        saved[REPORT_NOTES] = on
    }

    private val _dupe = MutableStateFlow<Pair<Parsed, Item>?>(null)

    /** A new series that matches one already running, and that one: asks Replace or Keep both. */
    val dupe: StateFlow<Pair<Parsed, Item>?> = _dupe.asStateFlow()

    /**
     * Every running series, each with its next open time (null when none is made
     * yet), for the Repeats list in Plan ahead.
     */
    val repeats: StateFlow<List<Pair<Item, Item?>>> = repo.items.map { all ->
        val today = LocalDate.now()
        all.filter { it.isTemplate && it.status != Status.DROPPED }.sortedBy { it.title.lowercase() }.map { t ->
            t to all.filter { it.templateId == t.id && it.isOpen && it.occurrenceDate?.isBefore(today) == false }.minByOrNull { it.occurrenceDate!! }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _ask = MutableStateFlow<Parsed?>(null)
    /** A quick-add the parser couldn't place: "Anchor or float?" */
    val ask: StateFlow<Parsed?> = _ask.asStateFlow()

    /** The paired person whose page is open over settings; -1 for none. Kept across a restart. */
    val pairOpen: StateFlow<Long> = saved.getStateFlow(PAIR, -1L)

    /** What could be shared, for the What they see lists. */
    val shareable: StateFlow<List<Shareable>> = combine(repo.items, ui) { all, u -> Sharing.shareable(all, u.forecast, u.now) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The person whose pairmark is in front; -1 for the first. Kept across a restart. */
    val pairFront: StateFlow<Long> = saved.getStateFlow(PAIR_FRONT, -1L)

    fun setPairFront(id: Long) {
        saved[PAIR_FRONT] = id
    }

    fun openPair(id: Long) {
        saved[PAIR] = id
    }

    fun closePair() {
        saved[PAIR] = -1L
    }

    /** Kept across a restart, so the drawer or settings reopen where they were. */
    val overlay: StateFlow<Overlay> = saved.getStateFlow(OVERLAY, Overlay.NONE)

    /**
     * Done on an item with steps still open asks first: All done, or Not yet.
     * The item, while that question is up; kept across a restart.
     */
    val confirming: StateFlow<Item?> = combine(saved.getStateFlow(CONFIRM, NONE), repo.items) { id, all ->
        all.firstOrNull { it.id == id && it.isOpen }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The dock slot an app is being picked for, while the picker is open. */
    val pickSlot: StateFlow<Int> = saved.getStateFlow(PICK_SLOT, -1)

    private val _opened = MutableStateFlow(0)
    /** Bumps each time the board comes to the front, so the screen can reset focus. */
    val opened: StateFlow<Int> = _opened.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                // On the minute and half minute, so the clock on the board turns over on time.
                delay(TICK_MS - System.currentTimeMillis() % TICK_MS)
                val now = LocalDateTime.now()
                clock.value = now
                // A lapse or a due recopy shows even while the board stays open.
                withContext(Dispatchers.IO) {
                    recap.load(graph.prefs, now)
                    recopy.load(graph.prefs, now)
                }
            }
        }
        // Start the duration clock the first time a float shows as Now; a task that
        // stops being Now without being finished is set aside, its clock stopped.
        viewModelScope.launch {
            var shown: Long? = null
            ui.collect { u ->
                val f = u.board?.now?.item
                val before = shown
                shown = f?.id
                if (before != null && before != f?.id) items.firstOrNull { it.id == before }?.let { repo.setAside(it, u.now) }
                if (f != null && f.kind == Kind.FLOAT && f.activeSince == null) repo.markActive(f, u.now)
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
                settings.load(prefs, now.toLocalDate())
                pairs.load(prefs)
                names.load(prefs)
                order.value = prefs.taskOrder
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

    /** Done, unless steps are still open: then the board asks first. */
    fun done(item: Item) {
        if (item.nextStep != null) {
            saved[CONFIRM] = item.id
        } else {
            finish(item)
        }
    }

    /** All done: the item and every step. */
    fun allDone() {
        val item = confirming.value
        saved[CONFIRM] = NONE
        if (item != null) finish(item)
    }

    /** Not yet: the item stays as it is. */
    fun notYet() {
        saved[CONFIRM] = NONE
    }

    fun toggleStep(item: Item, index: Int) {
        val step = item.steps.getOrNull(index) ?: return
        acted()
        val ticking = !step.done
        viewModelScope.launch { repo.setStep(item, index, ticking) }
        if (ticking && step.waitMinutes > 0) {
            // A grace, with Undo, before the item steps aside, so a mis-tap costs nothing.
            val wait = Formats.countdown(Duration.ofMinutes(step.waitMinutes.toLong()))
            show(Flash(getApplication<Application>().getString(R.string.flash_waits, item.title, wait), undo = item))
            waitJob?.cancel()
            waitJob = viewModelScope.launch {
                delay(UNDO_MS)
                val latest = items.firstOrNull { it.id == item.id } ?: return@launch
                val now = LocalDateTime.now()
                // Undo unticks the step, so the wait doesn't start.
                if (latest.steps.getOrNull(index)?.done == true && !latest.waiting(now)) repo.startWait(latest, step.waitMinutes, now)
            }
        }
    }

    /**
     * A waitmark tapped: that item becomes Now (its wait ends, if it hadn't), and
     * the task under way goes back into the day's order.
     */
    fun switchTo(target: Item) {
        acted()
        viewModelScope.launch {
            repo.switchTo(target, ui.value.board?.now?.item, LocalDateTime.now())
            clock.value = LocalDateTime.now()
        }
    }

    private fun finish(item: Item) = act(item) { repo.done(item, it); text(R.string.flash_done) }
    fun skip(item: Item) = act(item) { repo.skip(item, items, it) ?: text(R.string.flash_anchor_skipped) }
    fun tomorrow(item: Item) = act(item) { repo.tomorrow(item, it) }

    /**
     * Dragged in the day panel to [index] among the other entries. It lands there
     * only if it fits before the next anchor's warning, and never over an anchor;
     * otherwise it stays put. Its new place also becomes the tie breaker.
     */
    fun moveTask(item: Item, index: Int): Boolean {
        // Worked out afresh: the forecast on screen can be up to a tick old.
        val now = LocalDateTime.now()
        val others = placement.forecast(items, now, order.value).slots.filter { it.item.id != item.id }
        val at = placement.dropTime(others, index, now)
        if (!placement.canPin(item, items, at, now)) {
            show(Flash(text(R.string.doesnt_fit)))
            return false
        }
        // Your order: the day's tasks as they'll stand, then the rest as they were.
        val dayTasks = others.map { it.item }.filter { !it.isAnchor }.map { it.id }.toMutableList()
        dayTasks.add(others.take(index).count { !it.item.isAnchor }, item.id)
        val mine = dayTasks + order.value.filter { it !in dayTasks }
        order.value = mine
        acted()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { graph.prefs.taskOrder = mine }
            repo.pin(item, at)
            show(Flash(getApplication<Application>().getString(R.string.moved_to, Formats.clock(at))))
        }
        return true
    }

    /**
     * On it: the task is in hand, not skipped. A fixed event's alarms stop, as
     * from the notification; duration learning counts from now.
     */
    fun onIt(item: Item) {
        acted()
        viewModelScope.launch {
            if (item.isAnchor) {
                withContext(Dispatchers.IO) { AlarmActions.onIt(getApplication(), item.id) }
            } else {
                repo.onIt(item, LocalDateTime.now())
            }
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

    /** Takes back the last Done, Skip, or Tomorrow. */
    fun undo() {
        val f = _flash.value?.takeIf { it.canUndo } ?: return
        viewModelScope.launch {
            repo.reinsert(f.readd)
            f.undo?.let { repo.restore(it) }
            f.undoAlso.forEach { repo.restore(it) }
            repo.ensureInstances(LocalDateTime.now())
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

    /**
     * Added from a day in the Log: the line is read as for quick-add, on that
     * day unless it names another. A line that would ask "anchor or float?"
     * becomes a task, since the day is already chosen. True when understood.
     */
    fun addOn(text: String, day: LocalDate): Boolean {
        val parsed = Parser.parse(text, LocalDateTime.now(), on = day) ?: return false
        save(parsed)
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

    /** "Anchor or float?" answered Marker: all day, on the day it named. */
    fun answerMarker() {
        val p = _ask.value ?: return
        val day = p.item.earliestStart ?: LocalDate.now()
        save(p.copy(item = p.item.copy(kind = Kind.MARK, start = day.atStartOfDay(), earliestStart = null, leadMinutes = 0, icon = Parser.iconFor(p.item.title))))
        _ask.value = null
    }

    fun cancelAsk() {
        _ask.value = null
    }

    fun setDrawer(open: Boolean) {
        if (open) refresh()
        saved[OVERLAY] = if (open) Overlay.DRAWER else Overlay.NONE
    }

    /** Home: closes the drawer, settings, the edit form, the Log, or a recopy walk, back to the board. */
    fun home() {
        recopy.stop()
        edit.cancel()
        log.close()
        notYet()
        closePair()
        if (pairs.pairing.value != null) pairs.cancelPairing()
        saved[OVERLAY] = Overlay.NONE
    }

    /** The Log, from the Plan ahead panel: today's Day view. */
    fun openLog() {
        acted()
        log.show()
    }

    /** Initiative's own settings screen (as opposed to the phone's, [LauncherModel.openSettings]). */
    fun showSettings() {
        saved[OVERLAY] = Overlay.SETTINGS
    }

    /** The manual, How Initiative works, from the apps list. */
    fun showManual() {
        saved[OVERLAY] = Overlay.MANUAL
    }

    /**
     * Back steps out one level: edit form or recopy walk, the Done question, the
     * Log, then settings to the drawer, then the drawer to the board.
     */
    fun back() {
        if (report.value != null && !edit.isOpen) {
            closeReport()
        } else if (edit.isOpen) {
            edit.cancel()
        } else if (recopy.walking) {
            recopy.stop()
        } else if (confirming.value != null) {
            notYet()
        } else if (log.isOpen) {
            log.close()
        } else if (pairs.pairing.value != null) {
            pairs.cancelPairing()
        } else if (pairOpen.value >= 0) {
            closePair()
        } else {
            saved[OVERLAY] = if (overlay.value == Overlay.SETTINGS || overlay.value == Overlay.MANUAL) Overlay.DRAWER else Overlay.NONE
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

    /**
     * A new series with the same name as one already running asks first: two of
     * them would fill the days twice. Replace stops the old one from today.
     */
    private fun save(parsed: Parsed) {
        if (parsed.item.recurrence != null) {
            items.firstOrNull { it.isTemplate && it.status != Status.DROPPED && it.titleKey == parsed.item.titleKey }?.let {
                _dupe.value = parsed to it
                return
            }
        }
        add(parsed)
    }

    private fun add(parsed: Parsed) {
        acted()
        viewModelScope.launch {
            repo.add(parsed, LocalDateTime.now())
            show(Flash(getApplication<Application>().getString(R.string.flash_added, parsed.item.title)))
        }
    }

    fun dupeReplace() {
        val (parsed, old) = _dupe.value ?: return
        _dupe.value = null
        acted()
        viewModelScope.launch {
            repo.stopSeries(old, LocalDate.now())
            repo.add(parsed, LocalDateTime.now())
            show(Flash(getApplication<Application>().getString(R.string.flash_added, parsed.item.title)))
        }
    }

    fun dupeKeep() {
        val (parsed, _) = _dupe.value ?: return
        _dupe.value = null
        add(parsed)
    }

    fun dupeCancel() {
        _dupe.value = null
    }

    /** From the Repeats list: the series makes no more times from today, with Undo. */
    fun stopSeries(template: Item) {
        acted()
        viewModelScope.launch {
            val off = repo.stopSeries(template, LocalDate.now())
            show(Flash(text(R.string.series_stopped), undo = template, readd = off))
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
            delay(if (flash.canUndo) UNDO_MS else FLASH_MS)
            _flash.value = null
        }
    }

    private fun text(@StringRes id: Int): String = getApplication<Application>().getString(id)

    private companion object {
        const val TICK_MS = 30_000L
        const val FLASH_MS = 4_000L
        const val UNDO_MS = 8_000L
        const val OVERLAY = "overlay"
        const val PAIR = "pair"
        const val PAIR_FRONT = "pair_front"
        const val REPORT = "report"
        const val REPORT_NOTES = "report_notes"
        const val PICK_SLOT = "pick_slot"
        const val CONFIRM = "confirm_done"
        const val NONE = -1L
    }
}
