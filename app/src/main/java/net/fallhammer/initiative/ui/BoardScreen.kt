package net.fallhammer.initiative.ui

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import initiative.core.Item
import initiative.core.LedgerMark
import initiative.core.MarkKind
import initiative.core.WallStrength
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val CLOCK = DateTimeFormatter.ofPattern("h:mm a")

@Composable
fun BoardScreen(vm: BoardViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val flash by vm.flash.collectAsStateWithLifecycle()
    val ask by vm.ask.collectAsStateWithLifecycle()
    val drawerOpen by vm.drawerOpen.collectAsStateWithLifecycle()
    val apps by vm.appList.collectAsStateWithLifecycle()
    val pinned by vm.pinned.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }

    // Home screens never close on Back.
    BackHandler {
        when {
            drawerOpen -> vm.setDrawer(false)
            expanded -> expanded = false
        }
    }

    Box(Modifier.fillMaxSize().background(Ink.bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .onSwipeUp { vm.setDrawer(true) },
        ) {
            NowCard(
                ui = ui,
                flash = flash,
                modifier = Modifier.weight(1f),
                onDone = vm::done,
                onSkip = vm::skip,
                onTomorrow = vm::tomorrow,
            )
            Spacer(Modifier.height(12.dp))
            NextPeek(ui)
            Spacer(Modifier.height(8.dp))
            LedgerLine(ui, onClick = { expanded = !expanded })
            if (expanded) DayQueue(ui.queue, Modifier.heightIn(max = 240.dp))
            Spacer(Modifier.height(12.dp))
            ask?.let { parsed ->
                AskChips(
                    title = parsed.item.title,
                    onFloat = vm::answerFloat,
                    onAnchor = vm::answerAnchor,
                    onCancel = vm::cancelAsk,
                )
            }
            QuickAdd(onSubmit = vm::submit)
            PinnedRow(apps.filter { it.key in pinned }, onLaunch = vm::launch)
            TextButton(onClick = { vm.setDrawer(true) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Apps ⌃", fontSize = 16.sp, color = Ink.dim)
            }
        }

        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            AppDrawer(
                apps = apps,
                pinned = pinned,
                onLaunch = vm::launch,
                onTogglePin = vm::togglePin,
                onSettings = vm::openSettings,
                onHomeChooser = vm::openHomeChooser,
                onClose = { vm.setDrawer(false) },
            )
        }
    }
}

@Composable
private fun NowCard(
    ui: BoardUi,
    flash: String?,
    modifier: Modifier,
    onDone: (Item) -> Unit,
    onSkip: (Item) -> Unit,
    onTomorrow: (Item) -> Unit,
) {
    val pick = ui.board?.now
    val item = pick?.item
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Ink.surface)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("NOW", fontSize = 14.sp, color = Ink.dim, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        Text(item?.title ?: "Free time", fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold, color = Ink.text)
        if (item?.start != null) {
            Text("${item.start!!.format(CLOCK)} · ${wallLabel(item.wall)}", fontSize = 18.sp, color = Ink.dim)
        }
        Spacer(Modifier.height(12.dp))
        Text(pick?.reason.orEmpty(), fontSize = 20.sp, lineHeight = 26.sp, color = Ink.dim)
        Spacer(Modifier.weight(1f))
        flash?.let {
            Text(it, fontSize = 18.sp, color = Ink.accent)
            Spacer(Modifier.height(12.dp))
        }
        if (item != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                BigButton("Done", primary = true, modifier = Modifier.weight(1f)) { onDone(item) }
                BigButton("Skip", modifier = Modifier.weight(1f)) { onSkip(item) }
                if (!item.isAnchor) BigButton("Tomorrow", modifier = Modifier.weight(1.3f)) { onTomorrow(item) }
            }
        }
    }
}

@Composable
private fun BigButton(label: String, primary: Boolean = false, modifier: Modifier, onClick: () -> Unit) {
    val m = modifier.height(64.dp)
    if (primary) {
        Button(onClick = onClick, modifier = m, colors = ButtonDefaults.buttonColors(containerColor = Ink.accent, contentColor = Ink.bg)) {
            Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = m) {
            Text(label, fontSize = 20.sp, color = Ink.text)
        }
    }
}

@Composable
private fun NextPeek(ui: BoardUi) {
    val next = ui.board?.next ?: return
    val text = if (next.isAnchor && next.start != null) {
        "Next: ${next.title} in ${countdown(ui.now, next.start!!)}"
    } else {
        "Next: ${next.title}"
    }
    Text(text, fontSize = 20.sp, color = Ink.text, modifier = Modifier.padding(horizontal = 4.dp))
}

/** The day as one line: done ticks, walls as markers, a bar for now. Tap to expand. */
@Composable
private fun LedgerLine(ui: BoardUi, onClick: () -> Unit) {
    val startMin = ui.window.start.toSecondOfDay() / 60f
    val endMin = ui.window.end.toSecondOfDay() / 60f
    fun frac(t: LocalDateTime): Float = ((t.toLocalTime().toSecondOfDay() / 60f - startMin) / (endMin - startMin)).coerceIn(0f, 1f)

    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(32.dp)) {
            val mid = size.height / 2
            drawLine(Ink.line, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 2.dp.toPx())
            ui.ledger.forEach { mark: LedgerMark ->
                val x = frac(mark.at) * size.width
                when (mark.kind) {
                    MarkKind.DONE -> drawLine(Ink.dim, Offset(x, mid - 6.dp.toPx()), Offset(x, mid + 6.dp.toPx()), strokeWidth = 2.dp.toPx())
                    MarkKind.SOFT -> drawCircle(Ink.text, radius = 5.dp.toPx(), center = Offset(x, mid), style = Stroke(2.dp.toPx()))
                    MarkKind.FIRM -> drawCircle(Ink.text, radius = 6.dp.toPx(), center = Offset(x, mid))
                    MarkKind.HARD -> drawRect(
                        Ink.text,
                        topLeft = Offset(x - 4.dp.toPx(), mid - 12.dp.toPx()),
                        size = Size(8.dp.toPx(), 24.dp.toPx()),
                    )
                }
            }
            val nx = frac(ui.now) * size.width
            drawLine(Ink.accent, Offset(nx, 0f), Offset(nx, size.height), strokeWidth = 3.dp.toPx())
        }
        Text("Today · tap for the queue", fontSize = 14.sp, color = Ink.dim)
    }
}

@Composable
private fun DayQueue(queue: List<Item>, modifier: Modifier) {
    if (queue.isEmpty()) {
        Text("Nothing else today.", fontSize = 18.sp, color = Ink.dim, modifier = Modifier.padding(8.dp))
        return
    }
    LazyColumn(modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        items(queue, key = { it.id }) { item ->
            val lead = item.start?.format(CLOCK) ?: "${item.durationMinutes}m"
            Row(Modifier.padding(vertical = 6.dp)) {
                Text(lead, fontSize = 18.sp, color = Ink.dim, modifier = Modifier.width(96.dp))
                Text(item.title, fontSize = 18.sp, color = Ink.text, fontWeight = if (item.wall == WallStrength.HARD && item.isAnchor) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun AskChips(title: String, onFloat: () -> Unit, onAnchor: (LocalTime) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text("“$title”: anchor or float?", fontSize = 18.sp, color = Ink.text)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onFloat) { Text("Float", fontSize = 18.sp, color = Ink.text) }
            OutlinedButton(onClick = {
                TimePickerDialog(context, { _, h, m -> onAnchor(LocalTime.of(h, m)) }, 9, 0, false).show()
            }) { Text("Anchor…", fontSize = 18.sp, color = Ink.text) }
            TextButton(onClick = onCancel) { Text("Cancel", fontSize = 18.sp, color = Ink.dim) }
        }
    }
}

@Composable
private fun QuickAdd(onSubmit: (String) -> Boolean) {
    var text by remember { mutableStateOf("") }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        placeholder = { Text("Add anything: dentist thu 3pm", fontSize = 18.sp, color = Ink.dim) },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, color = Ink.text, fontFamily = Atkinson),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (onSubmit(text)) text = "" }),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PinnedRow(apps: List<net.fallhammer.initiative.data.AppEntry>, onLaunch: (net.fallhammer.initiative.data.AppEntry) -> Unit) {
    if (apps.isEmpty()) return
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        apps.forEach { app ->
            TextButton(onClick = { onLaunch(app) }) { Text(app.label, fontSize = 18.sp, color = Ink.text) }
        }
    }
}

private fun Modifier.onSwipeUp(action: () -> Unit) = pointerInput(Unit) {
    var total = 0f
    detectVerticalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = { if (total < -SWIPE_PX) action() },
    ) { _, dy -> total += dy }
}

internal fun Modifier.onSwipeDown(action: () -> Unit) = pointerInput(Unit) {
    var total = 0f
    detectVerticalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = { if (total > SWIPE_PX) action() },
    ) { _, dy -> total += dy }
}

private const val SWIPE_PX = 150f

private fun wallLabel(w: WallStrength) = when (w) {
    WallStrength.SOFT -> "Soft"
    WallStrength.FIRM -> "Firm"
    WallStrength.HARD -> "Hard"
}

private fun countdown(now: LocalDateTime, at: LocalDateTime): String {
    val mins = Duration.between(now, at).toMinutes().coerceAtLeast(0)
    val h = mins / 60
    val m = mins % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
