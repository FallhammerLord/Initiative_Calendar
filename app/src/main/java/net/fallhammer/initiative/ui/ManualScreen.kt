package net.fallhammer.initiative.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import net.fallhammer.initiative.R

/** One place in the app, and what you can do there: each action's name, then what it does. */
private class Part(@StringRes val name: Int, val entries: List<Pair<Int, Int>>)

private val PARTS = listOf(
    Part(
        R.string.manual_now,
        listOf(
            R.string.manual_now_1 to R.string.manual_now_1_does,
            R.string.manual_now_2 to R.string.manual_now_2_does,
            R.string.manual_now_3 to R.string.manual_now_3_does,
            R.string.manual_now_4 to R.string.manual_now_4_does,
            R.string.manual_now_5 to R.string.manual_now_5_does,
            R.string.manual_now_6 to R.string.manual_now_6_does,
            R.string.manual_now_7 to R.string.manual_now_7_does,
            R.string.manual_now_8 to R.string.manual_now_8_does,
            R.string.manual_now_9 to R.string.manual_now_9_does,
        ),
    ),
    Part(
        R.string.manual_list,
        listOf(
            R.string.manual_list_1 to R.string.manual_list_1_does,
            R.string.manual_list_2 to R.string.manual_list_2_does,
            R.string.manual_list_6 to R.string.manual_list_6_does,
            R.string.manual_list_7 to R.string.manual_list_7_does,
            R.string.manual_list_8 to R.string.manual_list_8_does,
            R.string.manual_list_3 to R.string.manual_list_3_does,
            R.string.manual_list_4 to R.string.manual_list_4_does,
            R.string.manual_list_5 to R.string.manual_list_5_does,
        ),
    ),
    Part(
        R.string.manual_plan,
        listOf(
            R.string.manual_plan_1 to R.string.manual_plan_1_does,
            R.string.manual_plan_2 to R.string.manual_plan_2_does,
            R.string.manual_plan_3 to R.string.manual_plan_3_does,
            R.string.manual_plan_4 to R.string.manual_plan_4_does,
            R.string.manual_plan_5 to R.string.manual_plan_5_does,
            R.string.manual_plan_6 to R.string.manual_plan_6_does,
        ),
    ),
    Part(
        R.string.manual_typing,
        listOf(
            R.string.manual_typing_1 to R.string.manual_typing_1_does,
            R.string.manual_typing_2 to R.string.manual_typing_2_does,
            R.string.manual_typing_3 to R.string.manual_typing_3_does,
            R.string.manual_typing_4 to R.string.manual_typing_4_does,
            R.string.manual_typing_5 to R.string.manual_typing_5_does,
            R.string.manual_typing_6 to R.string.manual_typing_6_does,
            R.string.manual_typing_7 to R.string.manual_typing_7_does,
            R.string.manual_typing_8 to R.string.manual_typing_8_does,
        ),
    ),
    Part(
        R.string.manual_alarms,
        listOf(
            R.string.manual_alarms_1 to R.string.manual_alarms_1_does,
            R.string.manual_alarms_2 to R.string.manual_alarms_2_does,
            R.string.manual_alarms_3 to R.string.manual_alarms_3_does,
            R.string.manual_alarms_4 to R.string.manual_alarms_4_does,
            R.string.manual_alarms_5 to R.string.manual_alarms_5_does,
            R.string.manual_alarms_6 to R.string.manual_alarms_6_does,
            R.string.manual_alarms_7 to R.string.manual_alarms_7_does,
        ),
    ),
    Part(
        R.string.manual_pairs,
        listOf(
            R.string.manual_pairs_1 to R.string.manual_pairs_1_does,
            R.string.manual_pairs_2 to R.string.manual_pairs_2_does,
            R.string.manual_pairs_3 to R.string.manual_pairs_3_does,
            R.string.manual_pairs_4 to R.string.manual_pairs_4_does,
            R.string.manual_pairs_5 to R.string.manual_pairs_5_does,
            R.string.manual_pairs_6 to R.string.manual_pairs_6_does,
            R.string.manual_pairs_7 to R.string.manual_pairs_7_does,
            R.string.manual_pairs_8 to R.string.manual_pairs_8_does,
        ),
    ),
    Part(
        R.string.manual_looks,
        listOf(
            R.string.manual_looks_1 to R.string.manual_looks_1_does,
            R.string.manual_looks_2 to R.string.manual_looks_2_does,
            R.string.manual_looks_3 to R.string.manual_looks_3_does,
            R.string.manual_looks_4 to R.string.manual_looks_4_does,
        ),
    ),
    Part(
        R.string.manual_backup,
        listOf(
            R.string.manual_backup_1 to R.string.manual_backup_1_does,
            R.string.manual_backup_2 to R.string.manual_backup_2_does,
            R.string.manual_backup_3 to R.string.manual_backup_3_does,
            R.string.manual_backup_4 to R.string.manual_backup_4_does,
        ),
    ),
    Part(
        R.string.manual_around,
        listOf(
            R.string.manual_around_1 to R.string.manual_around_1_does,
            R.string.manual_around_2 to R.string.manual_around_2_does,
            R.string.manual_around_8 to R.string.manual_around_8_does,
            R.string.manual_around_3 to R.string.manual_around_3_does,
            R.string.manual_around_4 to R.string.manual_around_4_does,
            R.string.manual_around_5 to R.string.manual_around_5_does,
            R.string.manual_around_6 to R.string.manual_around_6_does,
            R.string.manual_around_7 to R.string.manual_around_7_does,
        ),
    ),
)

/**
 * The manual: how Initiative works, place by place. One part opens at a time;
 * each lists what you do, then what happens. Opened from the apps list.
 */
@Composable
fun ManualScreen(onClose: () -> Unit) {
    // Which part is open; -1 for none. Kept across a restart, like anything opened.
    var open by rememberSaveable { mutableIntStateOf(-1) }
    Screen {
        Strip {
            Title(stringResource(R.string.manual_title), style = Type.title, modifier = Modifier.weight(1f))
            QuietButton(stringResource(R.string.drawer_close), onClose)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            PARTS.forEachIndexed { i, part ->
                Spacer(Modifier.height(Space.panel))
                ManualPart(part, open == i) { open = if (open == i) -1 else i }
            }
        }
    }
}

@Composable
private fun ManualPart(part: Part, open: Boolean, onToggle: () -> Unit) {
    val name = stringResource(part.name)
    val state = stringResource(if (open) R.string.manual_hide else R.string.manual_show)
    Card(inner = Space.l) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Tap.min)
                .clickable(role = Role.Button, onClickLabel = state, onClick = onToggle)
                .semantics { heading(); stateDescription = state },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, style = Type.heading.copy(fontWeight = FontWeight.Bold), color = Ink.text, modifier = Modifier.weight(1f))
            Text(state, style = Type.caption, color = Ink.accent)
        }
        if (open) {
            part.entries.forEach { (action, does) ->
                Column(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
                    Text(stringResource(action), style = Type.body.copy(fontWeight = FontWeight.Bold), color = Ink.accent)
                    Body(stringResource(does), color = Ink.text)
                }
            }
        }
    }
}
