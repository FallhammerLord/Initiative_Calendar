package net.fallhammer.initiative.ui

import initiative.core.Holiday
import initiative.core.Lapse
import initiative.core.Recopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.fallhammer.initiative.alarms.Scheduler
import net.fallhammer.initiative.data.Prefs
import net.fallhammer.initiative.data.QuietSetting
import net.fallhammer.initiative.data.SetupStep
import java.time.LocalDate

/** Board on unlock: whether it's switched on, and whether Android allows it yet. */
data class UnlockSetting(val enabled: Boolean = true, val permitted: Boolean = true)

/** Initiative's own settings, and the setup steps still to grant. */
class SettingsModel(private val parts: BoardParts) {
    private val graph get() = parts.graph

    private val _unlock = MutableStateFlow(UnlockSetting())
    val unlock: StateFlow<UnlockSetting> = _unlock.asStateFlow()

    private val _setup = MutableStateFlow<List<SetupStep>>(emptyList())
    /** Permissions still to grant, in order. */
    val setup: StateFlow<List<SetupStep>> = _setup.asStateFlow()

    private val _lockCard = MutableStateFlow(true)
    val lockCard: StateFlow<Boolean> = _lockCard.asStateFlow()

    private val _quiet = MutableStateFlow(QuietSetting())
    val quiet: StateFlow<QuietSetting> = _quiet.asStateFlow()

    private val _lapseHours = MutableStateFlow(Lapse.UNTOUCHED_HOURS.toInt())
    /** Hours with no action before "Previously on". */
    val lapseHours: StateFlow<Int> = _lapseHours.asStateFlow()

    private val _recopyDays = MutableStateFlow(Recopy.EVERY_DAYS.toInt())
    /** Days between recopies. */
    val recopyDays: StateFlow<Int> = _recopyDays.asStateFlow()

    private val _look = MutableStateFlow<String?>(null)
    /** The chosen theme's id; null until settings load, which shows the default. */
    val look: StateFlow<String?> = _look.asStateFlow()

    private val _ornaments = MutableStateFlow(true)
    /** Whether the look's ornaments show. */
    val ornaments: StateFlow<Boolean> = _ornaments.asStateFlow()

    private val _texture = MutableStateFlow(50)
    /** The shown look's background pattern strength, in percent; each look keeps its own. */
    val texture: StateFlow<Int> = _texture.asStateFlow()

    private val _holidayLooks = MutableStateFlow(false)
    /** Whether holidays wear their own look. Off unless turned on. */
    val holidayLooks: StateFlow<Boolean> = _holidayLooks.asStateFlow()

    private val _holidays = MutableStateFlow(Holiday.entries.toSet())
    /** The holidays ticked to wear their look. */
    val holidays: StateFlow<Set<Holiday>> = _holidays.asStateFlow()

    private val _shown = MutableStateFlow<String?>(null)
    /** The look on screen: today's holiday's, while holiday looks are on, or else the chosen one. */
    val shown: StateFlow<String?> = _shown.asStateFlow()

    private var today: LocalDate = LocalDate.now()

    /** Reads settings and permission state for [date]. Call off the main thread. */
    fun load(prefs: Prefs, date: LocalDate) {
        today = date
        _unlock.value = UnlockSetting(prefs.boardOnUnlock, prefs.canJumpToBoard())
        _setup.value = graph.setup.missing(prefs)
        _lockCard.value = prefs.lockCard
        _look.value = prefs.lookId
        _ornaments.value = prefs.ornaments
        _holidayLooks.value = prefs.holidayLooks
        _holidays.value = prefs.holidays
        _texture.value = prefs.texture(showing(prefs.lookId))
        _shown.value = showing(prefs.lookId)
        _quiet.value = prefs.quiet
        _lapseHours.value = prefs.lapseHours
        _recopyDays.value = prefs.recopyDays
    }

    fun setLapseHours(hours: Int) {
        _lapseHours.value = hours
        parts.scope.launch(Dispatchers.IO) { graph.prefs.lapseHours = hours }
    }

    fun setRecopyDays(days: Int) {
        _recopyDays.value = days
        parts.scope.launch(Dispatchers.IO) { graph.prefs.recopyDays = days }
    }

    fun setBoardOnUnlock(on: Boolean) {
        _unlock.value = _unlock.value.copy(enabled = on)
        parts.scope.launch(Dispatchers.IO) {
            graph.prefs.boardOnUnlock = on
            _setup.value = graph.setup.missing(graph.prefs)
        }
    }

    fun setLook(id: String) {
        _look.value = id
        reshow()
        parts.scope.launch(Dispatchers.IO) { graph.prefs.lookId = id }
    }

    fun setHolidayLooks(on: Boolean) {
        _holidayLooks.value = on
        reshow()
        parts.scope.launch(Dispatchers.IO) { graph.prefs.holidayLooks = on }
    }

    fun setHoliday(holiday: Holiday, on: Boolean) {
        val chosen = if (on) _holidays.value + holiday else _holidays.value - holiday
        _holidays.value = chosen
        reshow()
        parts.scope.launch(Dispatchers.IO) { graph.prefs.holidays = chosen }
    }

    /** The look to show for [chosen] today: a holiday's, while holiday looks are on. */
    private fun showing(chosen: String?): String {
        val holiday = if (_holidayLooks.value) Holiday.on(today, _holidays.value) else null
        return holiday?.let { Looks.of(it).id } ?: Looks.byId(chosen).id
    }

    /** Shows the look for the settings as they stand, with that look's own pattern strength. */
    private fun reshow() {
        val id = showing(_look.value)
        _shown.value = id
        parts.scope.launch(Dispatchers.IO) { _texture.value = graph.prefs.texture(id) }
    }

    fun setOrnaments(on: Boolean) {
        _ornaments.value = on
        parts.scope.launch(Dispatchers.IO) { graph.prefs.ornaments = on }
    }

    fun setTexture(percent: Int) {
        if (percent == _texture.value) return
        _texture.value = percent
        val look = showing(_look.value)
        parts.scope.launch(Dispatchers.IO) { graph.prefs.setTexture(look, percent) }
    }

    fun setLockCard(on: Boolean) {
        _lockCard.value = on
        parts.scope.launch(Dispatchers.IO) {
            graph.prefs.lockCard = on
            Scheduler.sync(parts.app)
        }
    }

    fun setQuiet(quiet: QuietSetting) {
        _quiet.value = quiet
        parts.scope.launch(Dispatchers.IO) {
            graph.prefs.quiet = quiet
            Scheduler.sync(parts.app)
        }
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
}
