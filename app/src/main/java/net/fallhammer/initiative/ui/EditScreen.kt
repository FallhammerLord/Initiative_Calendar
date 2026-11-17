package net.fallhammer.initiative.ui

import initiative.core.SharedTasks
import net.fallhammer.initiative.data.PeerRow
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.alpha
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import initiative.core.Formats
import initiative.core.Freq
import initiative.core.Item
import initiative.core.Kind
import initiative.core.MINUTES_A_DAY
import initiative.core.MarkIcon
import initiative.core.MissPolicy
import initiative.core.Priority
import initiative.core.Recurrence
import initiative.core.Status
import initiative.core.Step
import initiative.core.Steps
import initiative.core.WallStrength
import net.fallhammer.initiative.R
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * Every field of one item. Choices change the draft straight away; title and
 * notes are typed here and handed over on Save. One primary button: Save.
 */
@Composable
fun EditScreen(
    draft: Item,
    edit: EditModel,
    peers: List<PeerRow> = emptyList(),
    names: List<String> = emptyList(),
    onForget: (String) -> Unit = {},
    onUnforget: (String) -> Unit = {},
) {
    // A shared task from someone else is theirs to change: shown, done, or set aside here, never edited.
    if (draft.fromThem) {
        SharedFromThem(draft, peers, edit)
        return
    }
    val paired = peers.isNotEmpty()
    val context = LocalContext.current
    val rule by edit.rule.collectAsStateWithLifecycle()
    val dupe by edit.dupe.collectAsStateWithLifecycle()
    val scopeAsk by edit.scopeAsk.collectAsStateWithLifecycle()
    // Remove on a time of a series asks which: just this time, this and later, or every time.
    var asking by rememberSaveable(draft.id) { mutableStateOf(false) }
    // Which line's choices are open, if any, and whether More is unfolded.
    var openLine by rememberSaveable(draft.id) { mutableStateOf<String?>(null) }
    var moreOpen by rememberSaveable(draft.id) { mutableStateOf(false) }
    // Keyed by the item, so opening another item starts fresh; saved across a restart.
    // Kept with its cursor, so a picked name leaves the cursor after it.
    var title by rememberSaveable(draft.id, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(draft.title)) }
    var titleFocused by remember { mutableStateOf(false) }
    var notes by rememberSaveable(draft.id) { mutableStateOf(draft.notes) }
    // Steps too, one line each (see core's Steps), so a tick made on the board is kept.
    var steps by rememberSaveable(draft.id) { mutableStateOf(draft.steps.map(Steps::line)) }

    // A step just added gets the cursor, ready to type.
    val newStep = remember { FocusRequester() }
    var focusNewStep by remember { mutableStateOf(false) }
    LaunchedEffect(steps.size) {
        if (focusNewStep) {
            focusNewStep = false
            runCatching { newStep.requestFocus() }
        }
    }

    fun pickTime(initial: LocalTime, onPicked: (LocalTime) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(LocalTime.of(h, m)) }, initial.hour, initial.minute, false).show()
    }

    fun pickDate(initial: LocalDate, onPicked: (LocalDate) -> Unit) {
        DatePickerDialog(context, { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d)) }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
    }

    Screen {
        Strip {
            Label(stringResource(R.string.edit_label), Modifier.weight(1f))
            QuietButton(stringResource(R.string.edit_cancel), edit::cancel)
        }
        Spacer(Modifier.height(Space.panel))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                label = { Text(stringResource(R.string.edit_title), style = Type.bodySmall) },
                textStyle = Type.body.copy(color = Ink.text),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth().onFocusChanged { titleFocused = it.isFocused },
            )
            // Names used before, once the name is being changed.
            if (titleFocused && title.text != draft.title) {
                Spacer(Modifier.height(Space.inside))
                Suggestions(title.text, names, onPick = { name -> title = TextFieldValue(name, TextRange(name.length)) }, onForget, onUnforget)
            }
            Spacer(Modifier.height(Space.s))
            Field(stringResource(R.string.edit_notes), notes, { notes = it }, singleLine = false)
            // A marker is context, not work: no steps.
            if (!draft.isMark) Part {
                Text(stringResource(R.string.edit_steps), style = Type.bodySmall, color = Ink.text)
                // One line per step: its text, then a clock for a wait after it and a cross to
                // remove it. At large font sizes the two buttons sit under the text.
                val large = LocalDensity.current.fontScale > LARGE_FONT
                steps.forEachIndexed { i, line ->
                    val step = Steps.parse(line)
                    val last = i == steps.lastIndex
                    val field = @Composable { m: Modifier ->
                        Field(
                            stringResource(R.string.edit_step_n, i + 1),
                            step.title,
                            { text -> steps = steps.toMutableList().also { it[i] = Steps.line(step.copy(title = text)) } },
                            singleLine = true,
                            modifier = m.then(if (last) Modifier.focusRequester(newStep) else Modifier).keepAboveKeyboard(),

                            // Enter on the last step starts the next, so a list goes in without leaving the keyboard.
                            onNext = if (last) ({ focusNewStep = true; steps = steps + Steps.line(Step("")) }) else null,
                        )
                    }
                    val buttons = @Composable {
                        // A wait after the step (a washer's run): each tap steps to the next length, then back to none.
                        val wait = step.waitMinutes
                        val length = Formats.countdown(Duration.ofMinutes(wait.toLong()))
                        // As wide as the longest wait it can show, set or not, so every step's text lines up.
                        Box {
                            GlyphButton("", {}, Modifier.alpha(0f).clearAndSetSemantics { }, text = WIDEST_WAIT) { clock(it) }
                            GlyphButton(
                                if (wait == 0) stringResource(R.string.edit_step_wait_add, i + 1) else stringResource(R.string.edit_step_wait_set, length, i + 1),
                                {
                                    val after = DURATIONS.firstOrNull { it > wait } ?: 0
                                    steps = steps.toMutableList().also { it[i] = Steps.line(step.copy(waitMinutes = after)) }
                                },
                                Modifier.matchParentSize(),
                                text = if (wait == 0) null else length,
                            ) { clock(it) }
                        }
                        Spacer(Modifier.width(Space.inside))
                        GlyphButton(stringResource(R.string.edit_step_remove_n, i + 1), { steps = steps.filterIndexed { j, _ -> j != i } }) { cross(it) }
                    }
                    if (large) {
                        field(Modifier.fillMaxWidth())
                        Row(Modifier.padding(top = Space.xs)) { buttons() }
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            field(Modifier.weight(1f))
                            Spacer(Modifier.width(Space.inside))
                            buttons()
                        }
                    }
                }
                SecondaryButton(
                    stringResource(R.string.edit_step_add),
                    { focusNewStep = true; steps = steps + Steps.line(Step("")) },
                    Modifier.fillMaxWidth(),
                    height = Tap.min,
                )
            }
            // Each setting is a line with its value; a tap opens its choices under it, one at a time.
            fun toggle(line: String) { openLine = if (openLine == line) null else line }
            val start = draft.start
            Part {
                if (draft.isMark && start != null) {
                    SettingLine(stringResource(R.string.edit_line_day), Formats.day(start.toLocalDate()), { pickDate(start.toLocalDate(), edit::setMarkDay) }, first = true)
                    DateLine(
                        stringResource(R.string.edit_line_until),
                        draft.end?.let { Formats.day(it.toLocalDate()) },
                        stringResource(R.string.edit_line_one_day),
                        open = openLine == UNTIL,
                        onOpen = { toggle(UNTIL) },
                        onPick = { pickDate((draft.end ?: start).toLocalDate()) { d -> openLine = null; edit.setMarkUntil(d) } },
                        onClear = { openLine = null; edit.setMarkUntil(null) },
                    )
                    val icons = MarkIcon.entries
                    SettingLine(stringResource(R.string.edit_icon), stringResource(iconName(draft.icon)), { toggle(ICON) }, open = openLine == ICON) {
                        PickRow(icons.map { stringResource(iconName(it)) }, icons.indexOf(draft.icon)) { i -> edit.change { it.copy(icon = icons[i]) } }
                    }
                    val ahead = (HEADS_UP + draft.headsUpDays).distinct().sorted()
                    val aheadLabel = @Composable { n: Int -> if (n == 0) stringResource(R.string.edit_heads_up_none) else pluralStringResource(R.plurals.days, n, n) }
                    SettingLine(stringResource(R.string.edit_heads_up), aheadLabel(draft.headsUpDays), { toggle(AHEAD) }, open = openLine == AHEAD) {
                        PickRow(ahead.map { aheadLabel(it) }, ahead.indexOf(draft.headsUpDays)) { i -> edit.change { it.copy(leadMinutes = ahead[i] * MINUTES_A_DAY) } }
                    }
                } else if (draft.isAnchor && start != null) {
                    SettingLine(stringResource(R.string.edit_line_date), Formats.day(start.toLocalDate()), { pickDate(start.toLocalDate(), edit::setDate) }, first = true)
                    SettingLine(stringResource(R.string.edit_line_starts), Formats.clock(start), { pickTime(start.toLocalTime(), edit::setStart) })
                    val end = draft.end
                    SettingLine(
                        stringResource(R.string.edit_line_ends),
                        end?.let { Formats.clock(it) } ?: stringResource(R.string.edit_line_no_end),
                        { pickTime((end ?: draft.anchorEnd ?: start).toLocalTime(), edit::setEnd) },
                    )
                } else {
                    val lengths = (DURATIONS + draft.durationMinutes).distinct().sorted()
                    SettingLine(stringResource(R.string.edit_duration), minutes(draft.durationMinutes), { toggle(LENGTH) }, open = openLine == LENGTH, first = true) {
                        PickRow(lengths.map { minutes(it) }, lengths.indexOf(draft.durationMinutes)) { i -> edit.change { it.copy(durationMinutes = lengths[i]) } }
                    }
                    DateLine(
                        stringResource(R.string.edit_line_due),
                        draft.deadline?.let { Formats.day(it) },
                        stringResource(R.string.edit_line_no_due),
                        open = openLine == DUE,
                        onOpen = { toggle(DUE) },
                        onPick = { pickDate(draft.deadline ?: LocalDate.now()) { d -> openLine = null; edit.change { it.copy(deadline = d) } } },
                        onClear = { openLine = null; edit.change { it.copy(deadline = null) } },
                    )
                    DateLine(
                        stringResource(R.string.edit_line_first),
                        draft.earliestStart?.let { Formats.day(it) },
                        stringResource(R.string.edit_line_any_day),
                        open = openLine == FIRST,
                        onOpen = { toggle(FIRST) },
                        onPick = { pickDate(draft.earliestStart ?: LocalDate.now()) { d -> openLine = null; edit.change { it.copy(earliestStart = d) } } },
                        onClear = { openLine = null; edit.change { it.copy(earliestStart = null) } },
                    )
                }
                // The usual rules, plus the item's own when it's something else (say, every other Tuesday).
                val presets = REPEATS.map { it.second }.let { if (rule in it) it else it + rule }
                val labels = REPEATS.map { stringResource(it.first) } + if (presets.size > REPEATS.size) listOf(stringResource(R.string.repeat_custom)) else emptyList()
                SettingLine(stringResource(R.string.edit_repeat), labels[presets.indexOf(rule).coerceAtLeast(0)], { toggle(REPEAT) }, open = openLine == REPEAT, changed = rule != edit.originalRule) {
                    PickRow(labels, presets.indexOf(rule)) { edit.setRule(presets[it]) }
                    // A change to how a series repeats covers this time and later.
                    if (draft.templateId != null && rule != edit.originalRule) {
                        Spacer(Modifier.height(Space.inside))
                        Body(stringResource(R.string.edit_repeat_hint), style = Type.bodySmall)
                    }
                }
            }

            // The rarely changed settings, folded away. The line names any moved off its default.
            Part {
                val priorities = Priority.entries
                val priorityLabels = listOf(R.string.priority_auto, R.string.priority_low, R.string.priority_normal, R.string.priority_high).map { stringResource(it) }
                val policies = MissPolicy.entries
                val policyLabels = listOf(R.string.miss_reroll, R.string.miss_drop, R.string.miss_ask).map { stringResource(it) }
                val walls = WallStrength.entries
                val peer = peers.firstOrNull { it.id == draft.pairId }
                val changes = buildList {
                    if (!draft.isMark && !draft.isAnchor && draft.priority != Priority.AUTO) add(stringResource(R.string.edit_more_priority, priorityLabels[priorities.indexOf(draft.priority)]))
                    if (draft.isAnchor && draft.wall != WallStrength.FIRM) add(stringResource(R.string.edit_more_wall, Formats.wall(draft.wall)))
                    if (draft.isAnchor && draft.leadMinutes != Item.DEFAULT_LEAD) add(stringResource(R.string.edit_more_lead, minutes(draft.leadMinutes)))
                    if (!draft.isMark && draft.missPolicy != MissPolicy.REROLL) add(policyLabels[policies.indexOf(draft.missPolicy)])
                    if (peer != null) add(stringResource(R.string.edit_more_shared, peer.nickname))
                    if (draft.neverShare) add(stringResource(R.string.edit_more_never))
                }
                val hint = when {
                    draft.isMark -> if (paired) R.string.edit_more_marker else R.string.edit_more_marker_solo
                    draft.isAnchor -> if (paired) R.string.edit_more_anchor else R.string.edit_more_anchor_solo
                    else -> if (paired) R.string.edit_more_task else R.string.edit_more_task_solo
                }
                SettingLine(
                    stringResource(R.string.edit_more),
                    changes.joinToString(" · ").ifEmpty { null },
                    { moreOpen = !moreOpen; openLine = null },
                    open = moreOpen,
                    changed = changes.isNotEmpty(),
                    first = true,
                    hint = if (moreOpen) null else stringResource(hint),
                ) {
                    val kinds = listOf(Kind.ANCHOR, Kind.FLOAT, Kind.MARK)
                    val kindLabels = listOf(stringResource(R.string.edit_anchor), stringResource(R.string.edit_float), stringResource(R.string.edit_marker))
                    SettingLine(stringResource(R.string.edit_type), kindLabels[kinds.indexOf(draft.kind)], { toggle(TYPE) }, open = openLine == TYPE, first = true) {
                        PickRow(kindLabels, kinds.indexOf(draft.kind)) { edit.setKind(kinds[it]) }
                    }
                    if (draft.isAnchor) {
                        SettingLine(stringResource(R.string.edit_wall), Formats.wall(draft.wall), { toggle(WALL) }, open = openLine == WALL, changed = draft.wall != WallStrength.FIRM) {
                            PickRow(walls.map(Formats::wall), walls.indexOf(draft.wall)) { i -> edit.change { it.copy(wall = walls[i]) } }
                        }
                        val leads = (LEADS + draft.leadMinutes).distinct().sorted()
                        SettingLine(stringResource(R.string.edit_lead), minutes(draft.leadMinutes), { toggle(LEAD) }, open = openLine == LEAD, changed = draft.leadMinutes != Item.DEFAULT_LEAD) {
                            PickRow(leads.map { minutes(it) }, leads.indexOf(draft.leadMinutes)) { i -> edit.change { it.copy(leadMinutes = leads[i]) } }
                        }
                    } else if (!draft.isMark) {
                        SettingLine(stringResource(R.string.edit_priority), priorityLabels[priorities.indexOf(draft.priority)], { toggle(PRIORITY) }, open = openLine == PRIORITY, changed = draft.priority != Priority.AUTO) {
                            PickRow(priorityLabels, priorities.indexOf(draft.priority)) { i -> edit.change { it.copy(priority = priorities[i]) } }
                        }
                    }
                    if (!draft.isMark) {
                        SettingLine(stringResource(R.string.edit_missed), policyLabels[policies.indexOf(draft.missPolicy)], { toggle(MISSED) }, open = openLine == MISSED, changed = draft.missPolicy != MissPolicy.REROLL) {
                            PickRow(policyLabels, policies.indexOf(draft.missPolicy)) { i -> edit.change { it.copy(missPolicy = policies[i]) } }
                        }
                    }
                    // Once anyone is paired: who it's shared with, and keeping this one home. A shared
                    // task: both see it and each has a box. For a time of a series, sharing covers the series.
                    if (paired) {
                        val choices = listOf(stringResource(R.string.edit_only_me)) + peers.map { it.nickname }
                        val at = peers.indexOfFirst { it.id == draft.pairId }.let { if (it < 0) 0 else it + 1 }
                        SettingLine(stringResource(R.string.edit_shared_with), choices[at], { toggle(SHARE) }, open = openLine == SHARE, changed = at != 0) {
                            PickRow(choices, at) { i ->
                                edit.change {
                                    if (i == 0) it.copy(uid = null, pairId = null, forThem = false) else it.copy(uid = it.uid ?: SharedTasks.newUid(), pairId = peers[i - 1].id)
                                }
                            }
                            // Shared, it can be theirs alone: it goes to them and stays off this board.
                            peer?.let { p ->
                                Spacer(Modifier.height(Space.inside))
                                SettingSwitch(stringResource(R.string.edit_for_them, p.nickname), draft.forThem) { on -> edit.change { it.copy(forThem = on) } }
                            }
                        }
                        LineRule()
                        SettingSwitch(stringResource(R.string.edit_never_share), draft.neverShare) { on -> edit.change { it.copy(neverShare = on) } }
                    }
                }
            }
            Spacer(Modifier.height(Space.l))
        }
        Spacer(Modifier.height(Space.m))
        val typed = { steps.map(Steps::parse) }
        val matched = dupe
        when {
            // Saving would start a series named like one already running.
            matched != null -> Question(
                stringResource(R.string.dupe_ask, matched.title),
                Action { SecondaryButton(stringResource(R.string.dupe_replace), { edit.answerDupe(replace = true) }, it) },
                Action { SecondaryButton(stringResource(R.string.dupe_keep), { edit.answerDupe(replace = false) }, it) },
                onCancel = edit::cancelDupe,
            )
            // Saving a change to one time of a series.
            scopeAsk -> Question(
                stringResource(R.string.scope_ask),
                Action { SecondaryButton(stringResource(R.string.scope_this), { edit.answerScope(later = false) }, it) },
                Action { SecondaryButton(stringResource(R.string.scope_later), { edit.answerScope(later = true) }, it) },
                onCancel = edit::cancelScope,
            )
            asking -> Question(
                stringResource(R.string.remove_which),
                Action { SecondaryButton(stringResource(R.string.remove_this), { edit.remove(RemoveScope.THIS) }, it) },
                Action { SecondaryButton(stringResource(R.string.remove_later), { edit.remove(RemoveScope.LATER) }, it) },
                Action { SecondaryButton(stringResource(R.string.remove_every), { edit.remove(RemoveScope.EVERY) }, it) },
                onCancel = { asking = false },
            )
            // A marker can't be done: it only informs.
            draft.isMark && draft.isOpen -> ActionRow(
                Action(2f) { PrimaryButton(stringResource(R.string.edit_save), { edit.save(title.text, notes, typed()) }, it) },
                Action {
                    SecondaryButton(
                        stringResource(R.string.edit_remove),
                        { if (draft.templateId != null) asking = true else edit.remove() },
                        it,
                    )
                },
            )
            draft.isOpen -> ActionRow(
                Action(2f) { PrimaryButton(stringResource(R.string.edit_save), { edit.save(title.text, notes, typed()) }, it) },
                // Done here works for any open item, Now or not.
                Action { SecondaryButton(stringResource(R.string.edit_done), { edit.done(title.text, notes, typed()) }, it) },
                Action {
                    SecondaryButton(
                        stringResource(R.string.edit_remove),
                        { if (draft.templateId != null) asking = true else edit.remove() },
                        it,
                    )
                },
            )
            // A done or removed item can go back on the board; a removed one can also go for good.
            draft.status == Status.DROPPED -> ActionRow(
                Action(2f) { PrimaryButton(stringResource(R.string.edit_save), { edit.save(title.text, notes, typed()) }, it) },
                Action { SecondaryButton(stringResource(R.string.edit_put_back), edit::putBack, it) },
                Action { SecondaryButton(stringResource(R.string.edit_delete), edit::deleteForGood, it) },
            )
            else -> ActionRow(
                Action(2f) { PrimaryButton(stringResource(R.string.edit_save), { edit.save(title.text, notes, typed()) }, it) },
                Action { SecondaryButton(stringResource(R.string.edit_put_back), edit::putBack, it) },
            )
        }
    }
}

/** A question in place of the form's buttons: its answers, and Cancel. */
@Composable
private fun Question(text: String, vararg answers: Action, onCancel: () -> Unit) {
    Card(inner = Space.l, arrangement = Arrangement.spacedBy(Space.inside)) {
        Body(text, color = Ink.text, style = Type.bodySmall)
        ActionRow(*answers)
        QuietButton(stringResource(R.string.ask_cancel), onCancel)
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    singleLine: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onNext: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        label = { Text(label, style = Type.bodySmall) },
        textStyle = Type.body.copy(color = Ink.text),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = if (onNext != null) ImeAction.Next else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions(onNext = { onNext?.invoke() }),
        colors = fieldColors(),
        modifier = modifier,
    )
}

/**
 * An optional date as a line. Unset, a tap opens the date picker; set, a tap
 * opens Change and Clear under it.
 */
@Composable
private fun DateLine(
    label: String,
    value: String?,
    unset: String,
    open: Boolean,
    onOpen: () -> Unit,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    if (value == null) {
        SettingLine(label, unset, onPick)
        return
    }
    SettingLine(label, value, onOpen, open = open, changed = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton(stringResource(R.string.edit_line_change), onPick, Modifier.weight(1f))
            QuietButton(stringResource(R.string.edit_clear), onClear)
        }
    }
}

@Composable
private fun Part(content: @Composable () -> Unit) {
    // Each part of the form is a panel, so its words never sit on the background
    // pattern. Parts sit Space.panel apart; the controls of one part, Space.inside.
    Spacer(Modifier.height(Space.panel))
    Card(inner = Space.l, arrangement = Arrangement.spacedBy(Space.inside)) { content() }
}

private fun minutes(n: Int): String = Formats.countdown(Duration.ofMinutes(n.toLong()))

private val LEADS = listOf(5, 10, 15, 30, 60)

private val REPEATS: List<Pair<Int, Recurrence?>> = listOf(
    R.string.repeat_none to null,
    R.string.repeat_daily to Recurrence(Freq.DAILY),
    R.string.repeat_weekdays to Recurrence(Freq.WEEKDAYS),
    R.string.repeat_weekly to Recurrence(Freq.WEEKLY),
    R.string.repeat_biweekly to Recurrence(Freq.WEEKLY, interval = 2),
    R.string.repeat_monthly to Recurrence(Freq.MONTHLY_DATE),
    R.string.repeat_yearly to Recurrence(Freq.YEARLY),
)
private val DURATIONS = listOf(15, 30, 45, 60, 90, 120)

/** The longest wait a step's button shows, which sets the button's width. */
private val WIDEST_WAIT = DURATIONS.map { Formats.countdown(Duration.ofMinutes(it.toLong())) }.maxBy { it.length }

/** How far ahead a marker can show, in days. */
private val HEADS_UP = listOf(0, 1, 2, 3, 7)

// The form's lines that open, as saved in which one is open.
private const val UNTIL = "until"
private const val ICON = "icon"
private const val AHEAD = "ahead"
private const val LENGTH = "length"
private const val DUE = "due"
private const val FIRST = "first"
private const val REPEAT = "repeat"
private const val TYPE = "type"
private const val WALL = "wall"
private const val LEAD = "lead"
private const val PRIORITY = "priority"
private const val MISSED = "missed"
private const val SHARE = "share"

/** A marker icon's name, as the edit form lists it. */
internal fun iconName(icon: MarkIcon): Int = when (icon) {
    MarkIcon.STAR -> R.string.icon_star
    MarkIcon.CAKE -> R.string.icon_cake
    MarkIcon.DOOR -> R.string.icon_door
    MarkIcon.PLANE -> R.string.icon_plane
    MarkIcon.COIN -> R.string.icon_coin
    MarkIcon.FLAG -> R.string.icon_flag
}
