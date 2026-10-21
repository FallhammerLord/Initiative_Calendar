package net.fallhammer.initiative.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import initiative.core.Effort
import initiative.core.Formats
import initiative.core.Freq
import initiative.core.Item
import initiative.core.Kind
import initiative.core.MissPolicy
import initiative.core.Priority
import initiative.core.Recurrence
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
) {
    val context = LocalContext.current
    val rule by edit.rule.collectAsStateWithLifecycle()
    val later by edit.later.collectAsStateWithLifecycle()
    // Keyed by the item, so opening another item starts fresh; saved across a restart.
    var title by rememberSaveable(draft.id) { mutableStateOf(draft.title) }
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Label(stringResource(R.string.edit_label), Modifier.weight(1f))
            QuietButton(stringResource(R.string.edit_cancel), edit::cancel)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Field(stringResource(R.string.edit_title), title, { title = it }, singleLine = true)
            Spacer(Modifier.height(Space.s))
            Field(stringResource(R.string.edit_notes), notes, { notes = it }, singleLine = false)
            Part {
                Text(stringResource(R.string.edit_steps), style = Type.bodySmall, color = Ink.text)
                steps.forEachIndexed { i, line ->
                    val step = Steps.parse(line)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val last = i == steps.lastIndex
                        Field(
                            stringResource(R.string.edit_step_n, i + 1),
                            step.title,
                            { text -> steps = steps.toMutableList().also { it[i] = Steps.line(step.copy(title = text)) } },
                            singleLine = true,
                            modifier = Modifier.weight(1f).then(if (last) Modifier.focusRequester(newStep) else Modifier),
                        )
                        QuietButton(stringResource(R.string.edit_step_remove), { steps = steps.filterIndexed { j, _ -> j != i } })
                    }
                    // A wait after the step (a washer's run): each tap steps to the next length, then back to none.
                    val wait = step.waitMinutes
                    QuietButton(
                        if (wait == 0) stringResource(R.string.edit_step_wait_none)
                        else stringResource(R.string.edit_step_wait, Formats.countdown(Duration.ofMinutes(wait.toLong()))),
                        {
                            val after = DURATIONS.firstOrNull { it > wait } ?: 0
                            steps = steps.toMutableList().also { it[i] = Steps.line(step.copy(waitMinutes = after)) }
                        },
                        color = if (wait == 0) Ink.dim else Ink.accent,
                    )
                }
                SecondaryButton(
                    stringResource(R.string.edit_step_add),
                    { focusNewStep = true; steps = steps + Steps.line(Step("")) },
                    Modifier.fillMaxWidth(),
                    height = Tap.min,
                )
            }
            Part {
                ChoiceRow(
                    stringResource(R.string.edit_type),
                    listOf(stringResource(R.string.edit_anchor), stringResource(R.string.edit_float)),
                    if (draft.isAnchor) 0 else 1,
                ) { edit.setKind(if (it == 0) Kind.ANCHOR else Kind.FLOAT) }
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
            if (draft.isAnchor && start != null) {
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

            Part {
                val policies = MissPolicy.entries
                val labels = listOf(R.string.miss_reroll, R.string.miss_drop, R.string.miss_ask)
                ChoiceRow(stringResource(R.string.edit_missed), labels.map { stringResource(it) }, policies.indexOf(draft.missPolicy)) { i ->
                    edit.change { it.copy(missPolicy = policies[i]) }
                }
            }
            Spacer(Modifier.height(Space.l))
        }
        Spacer(Modifier.height(Space.m))
        ActionRow(
            Action(2f) { PrimaryButton(stringResource(R.string.edit_save), { edit.save(title, notes, steps.map(Steps::parse)) }, it) },
            // A done or removed item can go back on the board instead.
            if (draft.isOpen) {
                Action { SecondaryButton(stringResource(R.string.edit_remove), edit::remove, it) }
            } else {
                Action { SecondaryButton(stringResource(R.string.edit_put_back), edit::putBack, it) }
            },
        )
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, singleLine: Boolean, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        label = { Text(label, style = Type.bodySmall) },
        textStyle = Type.body.copy(color = Ink.text),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
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
    // Form fields sit Space.l apart; the controls of one field, Space.inside.
    Spacer(Modifier.height(Space.l))
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.inside)) { content() }
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
