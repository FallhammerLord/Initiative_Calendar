package net.fallhammer.initiative.ui

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import initiative.core.Formats
import initiative.core.Item
import initiative.core.MarkKind
import net.fallhammer.initiative.R
import net.fallhammer.initiative.data.SetupStep
import java.time.LocalDateTime
import java.time.LocalTime

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoardScreen(vm: BoardViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val flash by vm.flash.collectAsStateWithLifecycle()
    val ask by vm.ask.collectAsStateWithLifecycle()
    val overlay by vm.overlay.collectAsStateWithLifecycle()
    val quiet by vm.settings.quiet.collectAsStateWithLifecycle()
    val lapseHours by vm.settings.lapseHours.collectAsStateWithLifecycle()
    val recopyDays by vm.settings.recopyDays.collectAsStateWithLifecycle()
    val apps by vm.launcher.installed.collectAsStateWithLifecycle()
    val pinned by vm.launcher.pinned.collectAsStateWithLifecycle()
    val dock by vm.launcher.dock.collectAsStateWithLifecycle()
    val icons by vm.launcher.icons.collectAsStateWithLifecycle()
    val pickSlot by vm.pickSlot.collectAsStateWithLifecycle()
    val unlock by vm.settings.unlock.collectAsStateWithLifecycle()
    val setup by vm.settings.setup.collectAsStateWithLifecycle()
    val lockCard by vm.settings.lockCard.collectAsStateWithLifecycle()
    val recap by vm.recap.state.collectAsStateWithLifecycle()
    val recopyDue by vm.recopy.due.collectAsStateWithLifecycle()
    val recopyItem by vm.recopy.item.collectAsStateWithLifecycle()
    val backup by vm.backup.state.collectAsStateWithLifecycle()
    val draft by vm.edit.draft.collectAsStateWithLifecycle()
    val look = LocalLook.current
    val opened by vm.opened.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    // Coming back home should show the board, not an open keyboard.
    LaunchedEffect(opened) { focus.clearFocus() }

    // Where the quick-add field sits, so a touch outside it can let it go.
    var fieldArea by remember { mutableStateOf(Rect.Zero) }
    var boardAt by remember { mutableStateOf(Offset.Zero) }

    // Hiding the keyboard lets go of the text field, so it doesn't stay lit.
    val keyboardUp = WindowInsets.isImeVisible
    LaunchedEffect(keyboardUp) { if (!keyboardUp) focus.clearFocus() }

    // Home screens never close on Back, and Back never ends focus by accident.
    BackHandler {
        when {
            overlay != Overlay.NONE || recopyItem != null || draft != null -> vm.back()
            expanded -> expanded = false
        }
    }

    // Android 13+ has a one-tap notification prompt; after it's been asked once,
    // the setup line opens Settings instead.
    val notificationPrompt = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }
    fun openStep(step: SetupStep) {
        if (step == SetupStep.NOTIFICATIONS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && vm.settings.useNotificationPrompt()) {
            notificationPrompt.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.settings.openSetup(step)
        }
    }

    Box(Modifier.fillMaxSize().background(Ink.bg)) {
        val focusView = ui.focus
        val shownRecap = recap
        val walkItem = recopyItem
        val editing = draft
        // Each of these takes the board's place, so nothing hides underneath:
        // "Previously on" after a lapse, then the recopy walk, then focus.
        // Under the drawer or settings, the screen below is hidden from TalkBack,
        // so it never reads controls that are covered.
        Box(Modifier.fillMaxSize().then(if (overlay != Overlay.NONE) Modifier.clearAndSetSemantics { } else Modifier)) {
            if (shownRecap != null) {
                RecapScreen(shownRecap, onKeep = vm.recap::keep, onDrop = vm.recap::drop, onDropAll = vm.recap::dropAll, onBack = vm.recap::close)
            } else if (walkItem != null) {
                RecopyScreen(
                    walkItem,
                    onKeep = { vm.recopy.keep() },
                    onThisWeek = vm.recopy::laterThisWeek,
                    onNextWeek = vm.recopy::nextWeek,
                    onDrop = vm.recopy::drop,
                    onStop = vm.recopy::stop,
                )
            } else if (editing != null) {
                EditScreen(editing, vm.edit)
            } else if (focusView != null) {
                FocusScreen(focusView, ui.now, onDone = vm::doneWithIt, onEnd = vm::endFocus)
            } else Box(Modifier.fillMaxSize()) {
                // A touch anywhere but the text field lets go of it. It only watches,
                // so the touch still reaches whatever it landed on.
                Screen(
                    Modifier
                        .onGloballyPositioned { boardAt = it.positionInRoot() }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                if (!fieldArea.contains(down.position + boardAt)) focus.clearFocus()
                            }
                        },
                ) {
                    NowCard(
                        ui = ui,
                        flash = flash,
                        modifier = Modifier.weight(1f),
                        onDone = vm::done,
                        onSkip = vm::skip,
                        onTomorrow = vm::tomorrow,
                        onUndo = vm::undo,
                        onFocus = vm::startFocus,
                        onEdit = vm.edit::open,
                    )
                    Spacer(Modifier.height(Space.zone))
                    // Folded, the day panel is just the Next line: swipe it up or tap it to open.
                    DayBar(ui, open = false, onToggle = { expanded = true }, modifier = Modifier.onSwipeUp { expanded = true })
                    Spacer(Modifier.height(Space.zone))
                    // The weekly recopy waits while an anchor is Now.
                    if (recopyDue && ui.board?.now?.item?.isAnchor != true) {
                        RecopyPrompt(onStart = vm.recopy::start, onLater = vm.recopy::later)
                    }
                    ask?.let { parsed ->
                        AskChips(
                            title = parsed.item.title,
                            onFloat = vm::answerFloat,
                            onAnchor = vm::answerAnchor,
                            onCancel = vm::cancelAsk,
                        )
                    }
                    // One setup step at a time, until all are done.
                    setup.firstOrNull()?.let { step -> PromptLine(stringResource(step.text)) { openStep(step) } }
                    QuickAdd(onSubmit = vm::submit, modifier = Modifier.onGloballyPositioned { fieldArea = it.boundsInRoot() })
                    Spacer(Modifier.height(Space.inside))
                    // The way to the app drawer: a swipe up that starts in this bottom band,
                    // which sits above Android's own gesture strip, or the Apps handle.
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = DRAWER_BAND)
                            .onSwipeUp(DRAWER_SWIPE) { vm.setDrawer(true) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Space.inside, Alignment.Bottom),
                    ) {
                        Dock(dock, icons, onTap = vm::tapSlot, onHold = vm::holdSlot)
                        // The Apps button, as a slim pull tab: the swipe's visible twin.
                        Handle(stringResource(R.string.day_open_mark), stringResource(R.string.apps_open), onClick = { vm.setDrawer(true) })
                    }
                }
                // Open, the day panel lies over the board, so the Now card keeps its shape.
                if (expanded) {
                    DaySheet(ui, onClose = { expanded = false }, onEdit = { item -> expanded = false; vm.edit.open(item) }, onMove = vm::moveTask)
                }
            }
        }

        AnimatedVisibility(
            visible = overlay == Overlay.PICK,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            AppDrawer(
                apps = apps,
                pinned = pinned,
                onLaunch = vm::pickApp,
                onTogglePin = {},
                onSettings = {},
                onHomeChooser = {},
                onAppSettings = {},
                onClose = { vm.setDrawer(false) },
                picking = true,
                onClearSlot = if (dock.getOrNull(pickSlot) != null) vm::clearSlot else null,
            )
        }

        AnimatedVisibility(
            visible = overlay == Overlay.DRAWER,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            AppDrawer(
                apps = apps,
                pinned = pinned,
                onLaunch = vm::launch,
                onTogglePin = vm.launcher::togglePin,
                onSettings = vm.launcher::openSettings,
                onHomeChooser = vm.launcher::openHomeChooser,
                onAppSettings = vm::showSettings,
                onClose = { vm.setDrawer(false) },
            )
        }

        AnimatedVisibility(
            visible = overlay == Overlay.SETTINGS,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            SettingsScreen(
                unlock = unlock,
                onBoardOnUnlock = vm.settings::setBoardOnUnlock,
                lockCard = lockCard,
                onLockCard = vm.settings::setLockCard,
                lookId = look.id,
                onLook = vm.settings::setLook,
                quiet = quiet,
                onQuiet = vm.settings::setQuiet,
                lapseHours = lapseHours,
                onLapseHours = vm.settings::setLapseHours,
                recopyDays = recopyDays,
                onRecopyDays = vm.settings::setRecopyDays,
                backup = backup,
                onBackupFolder = vm.backup::setFolder,
                onBackUpNow = vm.backup::backUpNow,
                onRestoreFile = vm.backup::pickRestore,
                onRestoreConfirm = vm.backup::confirmRestore,
                onRestoreCancel = vm.backup::cancelRestore,
                // Close goes to the board; the Back gesture steps back to the drawer.
                onClose = { vm.setDrawer(false) },
            )
        }

    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NowCard(
    ui: BoardUi,
    flash: Flash?,
    modifier: Modifier,
    onDone: (Item) -> Unit,
    onSkip: (Item) -> Unit,
    onTomorrow: (Item) -> Unit,
    onUndo: () -> Unit,
    onFocus: (Item) -> Unit,
    onEdit: (Item) -> Unit,
) {
    val board = ui.board
    val item = board?.now?.item
    // Scrolls only when its content overflows (large system fonts), so the buttons
    // are never pushed off the card. Otherwise a swipe up on the card passes
    // through to open the drawer, since a scrollable area catches every swipe.
    val scroll = rememberScrollState()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        Card(
            Modifier.verticalScroll(scroll, enabled = scroll.maxValue > 0).heightIn(min = maxHeight),
            arrangement = Arrangement.SpaceBetween,
        ) {
            // Tapping the title area opens the edit form; holding it starts focus,
            // with the Focus button as the visible twin. TalkBack reads both actions.
            val editLabel = stringResource(R.string.edit_action)
            val focusLabel = stringResource(R.string.focus_action)
            Column(
                if (item == null) {
                    Modifier
                } else {
                    Modifier.combinedClickable(
                        onClickLabel = editLabel,
                        onClick = { onEdit(item) },
                        onLongClickLabel = if (item.isAnchor) null else focusLabel,
                        onLongClick = if (item.isAnchor) null else ({ onFocus(item) }),
                    )
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label(stringResource(R.string.now_label), Modifier.weight(1f))
                    if (item != null && !item.isAnchor) {
                        QuietButton(stringResource(R.string.focus_action), { onFocus(item) }, color = Ink.accent)
                    }
                }
                Spacer(Modifier.height(Space.s))
                // Until the database loads, the card stays blank rather than showing a wrong "Free time".
                if (board != null) {
                    Title(item?.title ?: stringResource(R.string.free_time))
                    if (item != null && item.isAnchor) Body(Formats.anchorLine(item), style = Type.bodySmall)
                    Spacer(Modifier.height(Space.m))
                    Body(board.now.reason)
                }
            }
            Column(Modifier.padding(top = Space.xl)) {
                flash?.let {
                    FlashLine(it.text, if (it.undo != null) stringResource(R.string.action_undo) else null, onUndo)
                    Spacer(Modifier.height(Space.m))
                }
                // A done anchor still showing until its end has nothing left to act on.
                if (item != null && item.isOpen) {
                    val done = stringResource(R.string.action_done)
                    val skip = stringResource(R.string.action_skip)
                    val tomorrow = stringResource(R.string.action_tomorrow)
                    if (item.isAnchor) {
                        ActionRow(
                            Action { PrimaryButton(done, { onDone(item) }, it) },
                            Action { SecondaryButton(skip, { onSkip(item) }, it) },
                        )
                    } else {
                        ActionRow(
                            Action { PrimaryButton(done, { onDone(item) }, it) },
                            Action { SecondaryButton(skip, { onSkip(item) }, it) },
                            Action(1.3f) { SecondaryButton(tomorrow, { onTomorrow(item) }, it) },
                        )
                    }
                }
            }
        }
    }
}

/** Focus: the task, a countdown to the wall that ends it, and the two ways out. */
@Composable
private fun FocusScreen(focus: FocusView, now: LocalDateTime, onDone: (Item) -> Unit, onEnd: () -> Unit) {
    val wall = focus.wall
    val wallStart = wall?.start
    Screen {
        Card(Modifier.weight(1f), arrangement = Arrangement.SpaceBetween) {
            Column {
                Label(stringResource(R.string.focus_label))
                Spacer(Modifier.height(Space.s))
                Title(focus.item.title)
            }
            Column {
                Text(Formats.countdown(now, focus.until), style = Type.hero, color = Ink.accent)
                Body(
                    if (wall != null && wallStart != null) {
                        stringResource(R.string.focus_until_wall, wall.title, Formats.clock(wallStart))
                    } else {
                        stringResource(R.string.focus_until_day_end, Formats.clock(focus.until))
                    },
                )
            }
            Column {
                PrimaryButton(stringResource(R.string.focus_done), { onDone(focus.item) }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(Space.m))
                SecondaryButton(stringResource(R.string.focus_end), onEnd, Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * The day panel's handle: the Next line, with a mark showing it opens or folds.
 * Tap it, or swipe it, to open or fold the panel.
 */
@Composable
private fun DayBar(ui: BoardUi, open: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    // Folded, it's a framed strip lined up with the Now card; open, it heads the sheet.
    val next = ui.board?.next
    val start = next?.start
    val text = when {
        next == null -> stringResource(R.string.nothing_next)
        next.isAnchor && start != null -> stringResource(R.string.next_anchor, next.title, Formats.countdown(ui.now, start))
        else -> stringResource(R.string.next_float, next.title)
    }
    val tap = modifier.clickable(onClickLabel = stringResource(if (open) R.string.day_close else R.string.day_open), onClick = onToggle)
    val content: @Composable RowScope.() -> Unit = {
        Body(text, Modifier.weight(1f), color = Ink.text)
        Text(
            stringResource(if (open) R.string.day_close_mark else R.string.day_open_mark),
            style = Type.body,
            color = Ink.accent,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
    if (open) {
        Row(tap.fillMaxWidth().heightIn(min = Tap.min), verticalAlignment = Alignment.CenterVertically, content = content)
    } else {
        Strip(tap, content)
    }
}

/**
 * The open day panel: Next, today's line, and the day's forecast, on a sheet over the
 * board. Swipe its handle down, tap outside it, or press Back to fold it.
 */
@Composable
private fun DaySheet(ui: BoardUi, onClose: () -> Unit, onEdit: (Item) -> Unit, onMove: (Item, Int) -> Unit) {
    val closeLabel = stringResource(R.string.day_close)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = closeLabel, onClick = onClose)
                .semantics { contentDescription = closeLabel },
        )
        BoxWithConstraints(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = Space.l, vertical = Space.m),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Card(Modifier.heightIn(max = maxHeight * SHEET_SHARE)) {
                DayBar(ui, open = true, onToggle = onClose, modifier = Modifier.onSwipeDown(onClose))
                Spacer(Modifier.height(Space.s))
                LedgerLine(ui)
                Spacer(Modifier.height(Space.s))
                ForecastList(ui.forecast, Modifier.weight(1f, fill = false), onEdit, onMove)
            }
        }
    }
}

/** The day as one line: done ticks, walls as markers, a bar for now. */
@Composable
private fun LedgerLine(ui: BoardUi) {
    val startMin = ui.window.start.toSecondOfDay() / 60f
    val endMin = ui.window.end.toSecondOfDay() / 60f
    fun frac(t: LocalDateTime): Float = ((t.toLocalTime().toSecondOfDay() / 60f - startMin) / (endMin - startMin)).coerceIn(0f, 1f)

    // TalkBack can't see a drawing, so the line describes itself.
    val walls = ui.ledger.filter { it.kind != MarkKind.DONE }.map {
        stringResource(R.string.ledger_wall, it.item.title, Formats.clock(it.at), Formats.wall(it.item.wall))
    }
    val doneCount = ui.ledger.count { it.kind == MarkKind.DONE }
    val description = stringResource(
        R.string.ledger_description,
        pluralStringResource(R.plurals.ledger_done, doneCount, doneCount),
        if (walls.isEmpty()) stringResource(R.string.ledger_no_walls) else stringResource(R.string.ledger_walls, walls.joinToString("; ")),
    )

    val lineColor = Ink.line
    val doneColor = Ink.dim
    val wallColor = Ink.text
    val nowColor = Ink.accent

    Column(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = Space.xs, vertical = Space.xs),
    ) {
        Canvas(Modifier.fillMaxWidth().height(32.dp)) {
            val mid = size.height / 2
            drawLine(lineColor, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 2.dp.toPx())
            ui.ledger.forEach { mark ->
                val x = frac(mark.at) * size.width
                when (mark.kind) {
                    MarkKind.DONE -> drawLine(doneColor, Offset(x, mid - 6.dp.toPx()), Offset(x, mid + 6.dp.toPx()), strokeWidth = 2.dp.toPx())
                    MarkKind.SOFT -> drawCircle(wallColor, radius = 5.dp.toPx(), center = Offset(x, mid), style = Stroke(2.dp.toPx()))
                    MarkKind.FIRM -> drawCircle(wallColor, radius = 6.dp.toPx(), center = Offset(x, mid))
                    MarkKind.HARD -> drawRect(
                        wallColor,
                        topLeft = Offset(x - 4.dp.toPx(), mid - 12.dp.toPx()),
                        size = Size(8.dp.toPx(), 24.dp.toPx()),
                    )
                }
            }
            val nx = frac(ui.now) * size.width
            drawLine(nowColor, Offset(nx, 0f), Offset(nx, size.height), strokeWidth = 3.dp.toPx())
        }
        Text(stringResource(R.string.ledger_hint), style = Type.caption, color = Ink.dim)
    }
}

@Composable
private fun AskChips(title: String, onFloat: () -> Unit, onAnchor: (LocalTime) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(bottom = Space.s)) {
        Body(stringResource(R.string.ask_kind, title), color = Ink.text, style = Type.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton(stringResource(R.string.ask_float), onFloat, height = Tap.min)
            SecondaryButton(
                stringResource(R.string.ask_anchor),
                { TimePickerDialog(context, { _, h, m -> onAnchor(LocalTime.of(h, m)) }, 9, 0, false).show() },
                height = Tap.min,
            )
            QuietButton(stringResource(R.string.ask_cancel), onCancel)
        }
    }
}

@Composable
private fun QuickAdd(onSubmit: (String) -> Boolean, modifier: Modifier = Modifier) {
    // Saved across process restarts, so a half-typed line survives.
    var text by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        placeholder = { Text(stringResource(R.string.quick_add_hint), style = Type.bodySmall, color = Ink.dim) },
        textStyle = Type.body.copy(color = Ink.text),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (onSubmit(text)) text = "" }),
        // Enter adds too; the button is there for anyone who'd rather tap.
        trailingIcon = if (text.isBlank()) {
            null
        } else {
            { QuietButton(stringResource(R.string.quick_add_button), { if (onSubmit(text)) text = "" }, color = Ink.accent) }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/** A swipe up of at least [travel]. */
private fun Modifier.onSwipeUp(travel: Dp = SWIPE, action: () -> Unit) = pointerInput(Unit) {
    val need = travel.toPx()
    var total = 0f
    detectVerticalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = { if (total < -need) action() },
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
private val SWIPE = 48.dp

/*
 * The drawer opens only from a swipe that starts in the bottom band and travels
 * about a thumb's length. The band sits above Android's gesture strip, which
 * keeps swipes from the very bottom edge for Home and Back.
 */
private val DRAWER_BAND = 72.dp
private val DRAWER_SWIPE = 64.dp

/** The open day panel takes at most this share of the screen. */
private const val SHEET_SHARE = 0.7f
