package net.fallhammer.initiative.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import net.fallhammer.initiative.R
import net.fallhammer.initiative.data.Apps
import net.fallhammer.initiative.data.AppEntry

/**
 * Every installed app, plus the two exits to the normal phone: its Settings,
 * and the system screen for switching back to another home app.
 *
 * With [picking], it's the dock's picker instead: tapping an app puts it in the
 * slot, [onClearSlot] empties the slot, and the exits are hidden. Opened by
 * holding a slot ([editing]), it stays open to fill the slot's tray: each app
 * tapped is added, the search box clears for the next, and [slotApps] lists
 * what the slot holds, its own app first, each to move nearer the slot or
 * remove. While the keyboard is up, only the search and the apps show.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
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
    slotApps: List<AppEntry> = emptyList(),
    onRemove: (AppEntry) -> Unit = {},
    onMoveDown: (Int) -> Unit = {},
    editing: Boolean = false,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    val typing = WindowInsets.isImeVisible
    // What a tap just did, said for a moment under the search box.
    var said by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(said) { if (said != null) { delay(SAID_MS); said = null } }
    val full = slotApps.size >= Apps.TRAY_SIZE
    val fullText = stringResource(R.string.tray_full)
    val addedText = stringResource(R.string.tray_added)
    // Filling a tray: add, clear the search, and stay for the next.
    val pick: (AppEntry) -> Unit = { app ->
        onLaunch(app)
        if (editing) {
            if (full && slotApps.none { it.key == app.key }) {
                said = fullText
            } else {
                said = addedText.format(app.label)
                query = ""
            }
        }
    }

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
            keyboardActions = KeyboardActions(onGo = { shown.firstOrNull()?.let(pick) }),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.s))
        if (!picking) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                SecondaryButton(stringResource(R.string.drawer_phone_settings), onSettings, height = Tap.min)
                SecondaryButton(stringResource(R.string.drawer_change_home), onHomeChooser, height = Tap.min)
            }
        } else if (onClearSlot != null && !typing) {
            SecondaryButton(stringResource(R.string.pick_clear), onClearSlot, height = Tap.min)
        }
        said?.let {
            Spacer(Modifier.height(Space.xs))
            Text(it, style = Type.bodySmall, color = Ink.accent, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (picking && slotApps.isNotEmpty() && !typing) {
            Spacer(Modifier.height(Space.s))
            SlotApps(slotApps, onRemove, onMoveDown)
        }
        Spacer(Modifier.height(Space.s))
        // Each app is a button, like everything tappable.
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            items(shown, key = { it.key }) { app ->
                val isPinned = app.key in pinned
                val spoken = if (isPinned) stringResource(R.string.drawer_pinned, app.label) else app.label
                RowButton(
                    { pick(app) },
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

/**
 * What a held slot holds: its own app, then its tray's, nearest the slot first,
 * one line each with an arrow (nearer the slot; the nearest becomes the slot's
 * own) and a cross (remove). About three lines show; the rest scroll. At large
 * font sizes the buttons sit under the name.
 */
@Composable
private fun SlotApps(apps: List<AppEntry>, onRemove: (AppEntry) -> Unit, onMoveDown: (Int) -> Unit) {
    val large = LocalDensity.current.fontScale > LARGE_FONT
    val scroll = rememberScrollState()
    val hint = Ink.accent
    val surface = Ink.surface
    Card {
        Text(stringResource(R.string.tray_title), style = Type.heading, color = Ink.text, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Space.inside))
        Column(
            Modifier
                .heightIn(max = Tap.row * SLOT_ROWS)
                .scrollHint(scroll, hint, surface)
                .verticalScroll(scroll, enabled = scroll.maxValue > 0),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            apps.forEachIndexed { i, app ->
                val name = if (i == 0) stringResource(R.string.tray_own, app.label) else app.label
                val buttons = @Composable {
                    if (i > 0) {
                        GlyphButton(stringResource(R.string.tray_move_down_n, app.label), { onMoveDown(i) }) { arrowDown(it) }
                        Spacer(Modifier.width(Space.inside))
                    }
                    GlyphButton(stringResource(R.string.tray_remove_n, app.label), { onRemove(app) }) { cross(it) }
                }
                if (large) {
                    Text(name, style = Type.body, color = Ink.text)
                    Row { buttons() }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = Type.body, color = Ink.text, modifier = Modifier.weight(1f))
                        buttons()
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.inside))
        Text(
            stringResource(if (apps.size < Apps.TRAY_SIZE) R.string.tray_hint else R.string.tray_full),
            style = Type.bodySmall,
            color = Ink.dim,
        )
    }
}

/** Lines of the slot's list in view before it scrolls; how long a "just added" line stays. */
private const val SLOT_ROWS = 3.3f
private const val SAID_MS = 3000L
