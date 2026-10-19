package net.fallhammer.initiative.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onLaunch: (AppEntry) -> Unit,
    onTogglePin: (AppEntry) -> Unit,
    onSettings: () -> Unit,
    onHomeChooser: () -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = apps.filter { it.label.contains(query.trim(), ignoreCase = true) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.bg)
            // Catch taps on empty space so they never reach the board underneath.
            .pointerInput(Unit) { detectTapGestures { } }
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth().onSwipeDown(onClose), verticalAlignment = Alignment.CenterVertically) {
            Text("Apps", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink.text, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Close", fontSize = 18.sp, color = Ink.dim) }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search apps", fontSize = 18.sp, color = Ink.dim) },
            textStyle = TextStyle(fontSize = 20.sp, color = Ink.text, fontFamily = Atkinson),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { shown.firstOrNull()?.let(onLaunch) }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSettings) { Text("Phone settings", fontSize = 16.sp, color = Ink.text) }
            OutlinedButton(onClick = onHomeChooser) { Text("Change home app", fontSize = 16.sp, color = Ink.text) }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(shown, key = { it.key }) { app ->
                Text(
                    text = if (app.key in pinned) "${app.label}  •" else app.label,
                    fontSize = 22.sp,
                    color = Ink.text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(onClick = { onLaunch(app) }, onLongClick = { onTogglePin(app) })
                        .padding(vertical = 12.dp),
                )
            }
        }
        SettingSwitch("Board after unlock", unlock.enabled, onBoardOnUnlock)
        SettingSwitch("Lock-screen card", lockCard, onLockCard)
        Text("Hold an app to pin it to the board.", fontSize = 14.sp, color = Ink.dim)
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 18.sp, color = Ink.text, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Ink.accent, checkedThumbColor = Ink.bg),
        )
    }
}
