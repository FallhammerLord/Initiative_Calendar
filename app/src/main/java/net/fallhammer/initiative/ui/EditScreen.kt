package net.fallhammer.initiative.ui

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
import initiative.core.Effort
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
    paired: Boolean = false,
    names: List<String> = emptyList(),
    onForget: (String) -> Unit = {},
    onUnforget: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val rule by edit.rule.collectAsStateWithLifecycle()
    val later by edit.later.collectAsStateWithLifecycle()
    val dupe by edit.dupe.collectAsStateWithLifecycle()
    // Remove on a time of a series asks which: just this time, or this and later.
    var asking by rememberSaveable(draft.id) { mutableStateOf(false) }
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
            // Once anyone is paired: keep this one home, whatever is shared.
            if (paired) {
                Spacer(Modifier.height(Space.s))
                SettingSwitch(stringResource(R.string.edit_never_share), draft.neverShare) { on -> edit.change { it.copy(neverShare = on) } }
            }
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
                            modifier = m.then(if (last) Modifier.focusRequester(newStep) else Modifier),
                            // Enter on the last step starts the next, so a list goes in without leaving the keyboard.
                            onNext = if (last) ({ focusNewStep = true; steps = steps + Steps.line(Step("")) }) else null,
                        )
                    }
                    val buttons = @Composable {
                        // A wait after the step (a washer's run): each tap steps to the next length, then back to none.
                        val wait = step.waitMinutes
                        val length = Formats.countdown(Duration.ofMinutes(wait.toLong()))
                        GlyphButton(
                            if (wait == 0) stringResource(R.string.edit_step_wait_add, i + 1) else stringResource(R.string.edit_step_wait_set, length, i + 1),
                            {
                                val after = DURATIONS.firstOrNull { it > wait } ?: 0
                                steps = steps.toMutableList().also { it[i] = Steps.line(step.copy(waitMinutes = after)) }
                            },
                            text = if (wait == 0) null else length,
                        ) { clock(it) }
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
            Part {
                val kinds = listOf(Kind.ANCHOR, Kind.FLOAT, Kind.MARK)
                ChoiceRow(
                    stringResource(R.string.edit_type),
                    listOf(stringResource(R.string.edit_anchor), stringResource(R.string.edit_float), stringResource(R.string.edit_marker)),
                    kinds.indexOf(draft.kind),
                ) { edit.setKind(kinds[it]) }
            }

            Part {
                // The usual rules, plus the item's own when it's something else (say, every other Tuesday).
                val presets = REPEATS.map { it.second }.let { if (rule in it) it else it + rule }
                val labels = REPEATS.map { stringResource(it.first) } + if (presets.size > REPEATS.size) listOf(stringResource(R.string.repeat_custom)) else emptyList()
                ChoiceRow(stringResource(R.string.edit_repeat), labels, presets.indexOf(rule)) { edit.setRule(presets[it]) }
                if (draft.templateId != null) {
                    if (rule == edit.originalRule) {
                        ChoiceRow(
                            stringResource(R.string.edit_scope),
                            listOf(stringResource(R.string.scope_this), stringResource(R.string.scope_later)),
                            if (later) 1 else 0,
                        ) { edit.setLater(it == 1) }
                    } else {
                        Body(stringResource(R.string.edit_repeat_hint), style = Type.bodySmall)
                    }
                }
            }

            val start = draft.start
            if (draft.isMark && start != null) {
                // A marker: its day or days, its icon, and how far ahead it shows.
                Part {
                    PickButton(stringResource(R.string.edit_mark_on, Formats.day(start.toLocalDate()))) {
                        pickDate(start.toLocalDate(), edit::setMarkDay)
                    }
                    DateLine(
                        set = draft.end?.let { stringResource(R.string.edit_mark_until, Formats.day(it.toLocalDate())) },
                        unset = stringResource(R.string.edit_mark_one_day),
                        onPick = { pickDate((draft.end ?: start).toLocalDate()) { d -> edit.setMarkUntil(d) } },
                        onClear = { edit.setMarkUntil(null) },
                    )
                }
                Part {
                    val icons = MarkIcon.entries
                    ChoiceRow(stringResource(R.string.edit_icon), icons.map { stringResource(iconName(it)) }, icons.indexOf(draft.icon)) { i ->
                        edit.change { it.copy(icon = icons[i]) }
                    }
                }
                Part {
                    val ahead = (HEADS_UP + draft.headsUpDays).distinct().sorted()
                    ChoiceRow(
                        stringResource(R.string.edit_heads_up),
                        ahead.map { if (it == 0) stringResource(R.string.edit_heads_up_none) else pluralStringResource(R.plurals.days, it, it) },
                        ahead.indexOf(draft.headsUpDays),
                    ) { i -> edit.change { it.copy(leadMinutes = ahead[i] * MINUTES_A_DAY) } }
                }
            } else if (draft.isAnchor && start != null) {
                Part {
                    PickButton(stringResource(R.string.edit_date, Formats.day(start.toLocalDate()))) {
                        pickDate(start.toLocalDate(), edit::setDate)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.fillMaxWidth()) {
                        PickButton(stringResource(R.string.edit_starts, Formats.clock(start)), Modifier.weight(1f)) {
                            pickTime(start.toLocalTime(), edit::setStart)
                        }
                        val end = draft.end
                        PickButton(
                            if (end != null) stringResource(R.string.edit_ends, Formats.clock(end)) else stringResource(R.string.edit_set_end),
                            Modifier.weight(1f),
                        ) { pickTime((end ?: draft.anchorEnd ?: start).toLocalTime(), edit::setEnd) }
                    }
                }
                Part {
                    val walls = WallStrength.entries
                    ChoiceRow(stringResource(R.string.edit_wall), walls.map(Formats::wall), walls.indexOf(draft.wall)) { i ->
                        edit.change { it.copy(wall = walls[i]) }
                    }
                }
                Part {
                    val leads = (LEADS + draft.leadMinutes).distinct().sorted()
                    ChoiceRow(stringResource(R.string.edit_lead), leads.map { minutes(it) }, leads.indexOf(draft.leadMinutes)) { i ->
                        edit.change { it.copy(leadMinutes = leads[i]) }
                    }
                }
            } else {
                Part {
                    val lengths = (DURATIONS + draft.durationMinutes).distinct().sorted()
                    ChoiceRow(stringResource(R.string.edit_duration), lengths.map { minutes(it) }, lengths.indexOf(draft.durationMinutes)) { i ->
                        edit.change { it.copy(durationMinutes = lengths[i]) }
                    }
                }
                Part {
                    val priorities = Priority.entries
                    val labels = listOf(R.string.priority_auto, R.string.priority_low, R.string.priority_normal, R.string.priority_high)
                    ChoiceRow(stringResource(R.string.edit_priority), labels.map { stringResource(it) }, priorities.indexOf(draft.priority)) { i ->
                        edit.change { it.copy(priority = priorities[i]) }
                    }
                }
                Part {
                    val efforts = Effort.entries
                    val labels = listOf(R.string.effort_quick, R.string.effort_focus)
                    ChoiceRow(stringResource(R.string.edit_effort), labels.map { stringResource(it) }, efforts.indexOf(draft.effort)) { i ->
                        edit.change { it.copy(effort = efforts[i]) }
                    }
                }
                Part {
                    DateLine(
                        set = draft.deadline?.let { stringResource(R.string.edit_deadline, Formats.day(it)) },
                        unset = stringResource(R.string.edit_no_deadline),
                        onPick = { pickDate(draft.deadline ?: LocalDate.now()) { d -> edit.change { it.copy(deadline = d) } } },
                        onClear = { edit.change { it.copy(deadline = null) } },
                    )
                    DateLine(
                        set = draft.earliestStart?.let { stringResource(R.string.edit_after, Formats.day(it)) },
                        unset = stringResource(R.string.edit_no_after),
                        onPick = { pickDate(draft.earliestStart ?: LocalDate.now()) { d -> edit.change { it.copy(earliestStart = d) } } },
                        onClear = { edit.change { it.copy(earliestStart = null) } },
                    )
                }
            }

            if (!draft.isMark) Part {
                val policies = MissPolicy.entries
                val labels = listOf(R.string.miss_reroll, R.string.miss_drop, R.string.miss_ask)
                ChoiceRow(stringResource(R.string.edit_missed), labels.map { stringResource(it) }, policies.indexOf(draft.missPolicy)) { i ->
                    edit.change { it.copy(missPolicy = policies[i]) }
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
            asking -> Question(
                stringResource(R.string.remove_which),
                Action { SecondaryButton(stringResource(R.string.remove_this), { edit.remove(later = false) }, it) },
                Action { SecondaryButton(stringResource(R.string.remove_later), { edit.remove(later = true) }, it) },
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

/** A button that opens a date or time picker, labelled with the current value. */
@Composable
private fun PickButton(label: String, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    SecondaryButton(label, onClick, modifier, height = Tap.min)
}

/** An optional date: the value with Clear, or a button to set one. */
@Composable
private fun DateLine(set: String?, unset: String, onPick: () -> Unit, onClear: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PickButton(set ?: unset, Modifier.weight(1f), onPick)
        if (set != null) QuietButton(stringResource(R.string.edit_clear), onClear)
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

/** How far ahead a marker can show, in days. */
private val HEADS_UP = listOf(0, 1, 2, 3, 7)

/** A marker icon's name, as the edit form lists it. */
internal fun iconName(icon: MarkIcon): Int = when (icon) {
    MarkIcon.STAR -> R.string.icon_star
    MarkIcon.CAKE -> R.string.icon_cake
    MarkIcon.DOOR -> R.string.icon_door
    MarkIcon.PLANE -> R.string.icon_plane
    MarkIcon.COIN -> R.string.icon_coin
    MarkIcon.FLAG -> R.string.icon_flag
}
