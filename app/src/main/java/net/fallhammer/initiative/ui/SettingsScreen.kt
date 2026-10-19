package net.fallhammer.initiative.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import initiative.core.Formats
import net.fallhammer.initiative.R
import net.fallhammer.initiative.data.QuietSetting
import java.time.LocalTime

/** Initiative's own settings, grouped by what they change. */
@Composable
fun SettingsScreen(
    unlock: UnlockSetting,
    onBoardOnUnlock: (Boolean) -> Unit,
    lockCard: Boolean,
    onLockCard: (Boolean) -> Unit,
    lookId: String,
    onLook: (String) -> Unit,
    quiet: QuietSetting,
    onQuiet: (QuietSetting) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    fun pickTime(initial: LocalTime, onPicked: (LocalTime) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(LocalTime.of(h, m)) }, initial.hour, initial.minute, false).show()
    }

    // Catch taps on empty space so they never reach the board underneath.
    Screen(Modifier.pointerInput(Unit) { detectTapGestures { } }) {
        Row(Modifier.fillMaxWidth().onSwipeDown(onClose), verticalAlignment = Alignment.CenterVertically) {
            Title(stringResource(R.string.settings_title), style = Type.title, modifier = Modifier.weight(1f))
            QuietButton(stringResource(R.string.drawer_close), onClose)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Section(stringResource(R.string.section_board)) {
                SettingSwitch(stringResource(R.string.setting_board_after_unlock), unlock.enabled, onBoardOnUnlock)
                SettingSwitch(stringResource(R.string.setting_lock_card), lockCard, onLockCard)
            }
            Section(stringResource(R.string.section_look)) {
                ChoiceRow(
                    stringResource(R.string.setting_theme),
                    Looks.all.map { stringResource(it.label) },
                    Looks.all.indexOfFirst { it.id == lookId },
                ) { onLook(Looks.all[it].id) }
            }
            Section(stringResource(R.string.section_alarms)) {
                SettingSwitch(stringResource(R.string.setting_quiet), quiet.on) { onQuiet(quiet.copy(on = it)) }
                Body(stringResource(R.string.quiet_hint), style = Type.bodySmall)
                if (quiet.on) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.fillMaxWidth()) {
                        SecondaryButton(
                            stringResource(R.string.quiet_from, Formats.clock(quiet.start)),
                            { pickTime(quiet.start) { onQuiet(quiet.copy(start = it)) } },
                            Modifier.weight(1f),
                            height = Tap.min,
                        )
                        SecondaryButton(
                            stringResource(R.string.quiet_to, Formats.clock(quiet.end)),
                            { pickTime(quiet.end) { onQuiet(quiet.copy(end = it)) } },
                            Modifier.weight(1f),
                            height = Tap.min,
                        )
                    }
                }
            }
        }
    }
}

/** A labelled group of settings. */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(Space.l))
    Label(title)
    Spacer(Modifier.height(Space.xs))
    Column(Modifier.fillMaxWidth()) { content() }
}
