package net.fallhammer.initiative.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import net.fallhammer.initiative.R
import net.fallhammer.initiative.data.AppEntry

/**
 * Every installed app, plus the two exits to the normal phone: its Settings,
 * and the system screen for switching back to another home app.
 *
 * With [picking], it's the dock's picker instead: tapping an app puts it in the
 * slot, [onClearSlot] empties the slot, and the exits are hidden.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawer(
    apps: List<AppEntry>,
    pinned: List<String>,
    onLaunch: (AppEntry) -> Unit,
    onTogglePin: (AppEntry) -> Unit,
    onSettings: () -> Unit,
    onHomeChooser: () -> Unit,
    onAppSettings: () -> Unit,
    onClose: () -> Unit,
    onManual: () -> Unit = {},
    picking: Boolean = false,
    onClearSlot: (() -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = apps.filter { it.label.contains(query.trim(), ignoreCase = true) }

    // Catch taps on empty space so they never reach the board underneath.
    Screen(Modifier.pointerInput(Unit) { detectTapGestures { } }) {
        // Manual and Settings sit beside Close; at large font sizes they drop to a row of their own.
        val large = LocalDensity.current.fontScale > LARGE_FONT
        Strip(Modifier.onSwipeDown(onClose)) {
            Title(stringResource(if (picking) R.string.pick_title else R.string.drawer_title), style = Type.title, modifier = Modifier.weight(1f))
            if (!picking && !large) {
                QuietButton(stringResource(R.string.manual_open), onManual, color = Ink.accent)
                Spacer(Modifier.width(Space.inside))
                QuietButton(stringResource(R.string.drawer_app_settings), onAppSettings, color = Ink.accent)
                Spacer(Modifier.width(Space.inside))
            }
            QuietButton(stringResource(R.string.drawer_close), onClose)
        }
        if (!picking && large) {
            Spacer(Modifier.height(Space.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                QuietButton(stringResource(R.string.manual_open), onManual, color = Ink.accent)
                QuietButton(stringResource(R.string.drawer_app_settings), onAppSettings, color = Ink.accent)
            }
        }
        Spacer(Modifier.height(Space.s))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.drawer_search), style = Type.bodySmall, color = Ink.dim) },
            textStyle = Type.body.copy(color = Ink.text),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { shown.firstOrNull()?.let(onLaunch) }),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.s))
        if (!picking) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                SecondaryButton(stringResource(R.string.drawer_phone_settings), onSettings, height = Tap.min)
                SecondaryButton(stringResource(R.string.drawer_change_home), onHomeChooser, height = Tap.min)
            }
        } else if (onClearSlot != null) {
            SecondaryButton(stringResource(R.string.pick_clear), onClearSlot, height = Tap.min)
        }
        Spacer(Modifier.height(Space.s))
        // Each app is a button, like everything tappable.
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            items(shown, key = { it.key }) { app ->
                val isPinned = app.key in pinned
                val spoken = if (isPinned) stringResource(R.string.drawer_pinned, app.label) else app.label
                RowButton(
                    { onLaunch(app) },
                    Modifier.semantics { contentDescription = spoken },
                    onLongClick = if (picking) null else ({ onTogglePin(app) }),
                ) {
                    Text(
                        text = if (isPinned) "${app.label}  •" else app.label,
                        style = Type.heading,
                        color = Ink.text,
                        modifier = Modifier.padding(vertical = Space.xs),
                    )
                }
            }
        }
        if (!picking) {
            Spacer(Modifier.height(Space.xs))
            Strip { Text(stringResource(R.string.drawer_pin_hint), style = Type.caption, color = Ink.dim) }
        }
    }
}
