package net.fallhammer.initiative.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import net.fallhammer.initiative.R
import net.fallhammer.initiative.data.AppEntry

/**
 * Every installed app, plus the two exits to the normal phone: its Settings,
 * and the system screen for switching back to another home app.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawer(
    apps: List<AppEntry>,
    pinned: List<String>,
    unlock: UnlockSetting,
    onBoardOnUnlock: (Boolean) -> Unit,
    lockCard: Boolean,
    onLockCard: (Boolean) -> Unit,
    lookId: String,
    onLook: (String) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onTogglePin: (AppEntry) -> Unit,
    onSettings: () -> Unit,
    onHomeChooser: () -> Unit,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = apps.filter { it.label.contains(query.trim(), ignoreCase = true) }

    // Catch taps on empty space so they never reach the board underneath.
    Screen(Modifier.pointerInput(Unit) { detectTapGestures { } }) {
        Row(Modifier.fillMaxWidth().onSwipeDown(onClose), verticalAlignment = Alignment.CenterVertically) {
            Title(stringResource(R.string.drawer_title), style = Type.title, modifier = Modifier.weight(1f))
            QuietButton(stringResource(R.string.drawer_close), onClose)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.drawer_search), style = Type.bodySmall, color = Ink.dim) },
            textStyle = Type.body.copy(color = Ink.text),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { shown.firstOrNull()?.let(onLaunch) }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            SecondaryButton(stringResource(R.string.drawer_phone_settings), onSettings, height = Tap.min)
            SecondaryButton(stringResource(R.string.drawer_change_home), onHomeChooser, height = Tap.min)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(shown, key = { it.key }) { app ->
                val isPinned = app.key in pinned
                val spoken = if (isPinned) stringResource(R.string.drawer_pinned, app.label) else app.label
                Text(
                    text = if (isPinned) "${app.label}  •" else app.label,
                    style = Type.heading,
                    color = Ink.text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Tap.min)
                        .combinedClickable(onClick = { onLaunch(app) }, onLongClick = { onTogglePin(app) })
                        .semantics { contentDescription = spoken }
                        .padding(vertical = Space.m),
                )
            }
        }
        SettingSwitch(stringResource(R.string.setting_board_after_unlock), unlock.enabled, onBoardOnUnlock)
        SettingSwitch(stringResource(R.string.setting_lock_card), lockCard, onLockCard)
        ChoiceRow(
            stringResource(R.string.setting_theme),
            Looks.all.map { stringResource(it.label) },
            Looks.all.indexOfFirst { it.id == lookId },
        ) { onLook(Looks.all[it].id) }
        Text(stringResource(R.string.drawer_pin_hint), style = Type.caption, color = Ink.dim)
    }
}
