package net.fallhammer.initiative.ui

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

    /** Reads settings and permission state. Call off the main thread. */
    fun load(prefs: Prefs) {
        _unlock.value = UnlockSetting(prefs.boardOnUnlock, prefs.canJumpToBoard())
        _setup.value = graph.setup.missing(prefs)
        _lockCard.value = prefs.lockCard
        _look.value = prefs.lookId
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
        parts.scope.launch(Dispatchers.IO) { graph.prefs.lookId = id }
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
