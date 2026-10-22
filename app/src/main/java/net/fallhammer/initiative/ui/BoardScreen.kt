package net.fallhammer.initiative.ui

import android.Manifest
import android.util.Log
import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import kotlin.math.atan2
import kotlin.math.sqrt
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.key
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat
import androidx.compose.ui.platform.LocalView
import initiative.core.Formats
import initiative.core.Kind
import initiative.core.Item
import initiative.core.MarkKind
import initiative.core.WallStrength
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
    val dupe by vm.dupe.collectAsStateWithLifecycle()
    val repeats by vm.repeats.collectAsStateWithLifecycle()
    val reportDay by vm.report.collectAsStateWithLifecycle()
    val reportLines by vm.reportLines.collectAsStateWithLifecycle()
    val reportNotes by vm.reportNotes.collectAsStateWithLifecycle()
    val reportMarks by vm.reportMarks.collectAsStateWithLifecycle()
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
    val ornaments by vm.settings.ornaments.collectAsStateWithLifecycle()
    val texture by vm.settings.texture.collectAsStateWithLifecycle()
    val chosenLook by vm.settings.look.collectAsStateWithLifecycle()
    val holidayLooks by vm.settings.holidayLooks.collectAsStateWithLifecycle()
    val holidays by vm.settings.holidays.collectAsStateWithLifecycle()
    val peers by vm.pairs.peers.collectAsStateWithLifecycle()
    val peeks by vm.pairs.peeks.collectAsStateWithLifecycle()
    val ticks by vm.pairs.ticks.collectAsStateWithLifecycle()
    val pairOpen by vm.pairOpen.collectAsStateWithLifecycle()
    val shareable by vm.shareable.collectAsStateWithLifecycle()
    val pairsShown by vm.pairs.shown.collectAsStateWithLifecycle()
    val pairFront by vm.pairFront.collectAsStateWithLifecycle()
    val pairing by vm.pairs.pairing.collectAsStateWithLifecycle()
    // Pair and Ping ask for Nearby devices, and for Bluetooth on, first.
    val ping = rememberBluetoothGate { vm.pairs.ping() }
    val pair = rememberBluetoothGate { vm.pairs.startPairing() }
    val pairViews = peers.map { PairView(it, peeks[it.id]) }
    // Today's shared times for each person shown with a line, at most three.
    val palette = LocalLook.current.palette
    val dialLines = if (!pairsShown) emptyList() else pairViews.filter { it.peer.showLine && it.peek != null }.take(MAX_LINES).map { p ->
        val today = ui.now.toLocalDate()
        val spans = p.peek!!.items.filter { it.kind != Kind.MARK && it.start.toLocalDate() == today }
        DialLine(
            p.peer.nickname,
            PairColors.of(p.peer.color, palette),
            spans.map { Triple(it.start, it.end, it.title) },
            p.peek.probably(ui.now)?.let { it.start to it.end },
        )
    }
    val recap by vm.recap.state.collectAsStateWithLifecycle()
    val recopyDue by vm.recopy.due.collectAsStateWithLifecycle()
    val recopyItem by vm.recopy.item.collectAsStateWithLifecycle()
    val backup by vm.backup.state.collectAsStateWithLifecycle()
    val draft by vm.edit.draft.collectAsStateWithLifecycle()
    val look = LocalLook.current
    val opened by vm.opened.collectAsStateWithLifecycle()
    val confirming by vm.confirming.collectAsStateWithLifecycle()
    val logOpen by vm.log.open.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    // All done sends one ring of light across the background grid, from the Now card.
    val glow = remember { Animatable(1f) }
    var cardCenter by remember { mutableStateOf(Offset.Zero) }
    // The pairmark row's height: a tall Now card stops below it while pairmarks show.
    var pairmarkHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val focus = LocalFocusManager.current

    // Coming back home should show the board, not an open keyboard.
    LaunchedEffect(opened) { focus.clearFocus() }

    // Where the quick-add field sits, so a touch outside it can let it go.
    var fieldArea by remember { mutableStateOf(Rect.Zero) }
    var boardAt by remember { mutableStateOf(Offset.Zero) }

    // Hiding the keyboard lets go of the text field, so it doesn't stay lit.
    val keyboardUp = WindowInsets.isImeVisible
    LaunchedEffect(keyboardUp) { if (!keyboardUp) focus.clearFocus(force = true) }

    // Home screens never close on Back, and Back never ends focus by accident.
    BackHandler {
        when {
            overlay != Overlay.NONE || recopyItem != null || draft != null || confirming != null || logOpen || reportDay != null -> vm.back()
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
        val reportOn = reportDay
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
                EditScreen(editing, vm.edit, paired = peers.isNotEmpty())
            } else if (reportOn != null) {
                ReportScreen(reportOn, reportLines, reportNotes, onNotes = vm::setReportNotes, onClose = vm::closeReport, marks = reportMarks)
            } else if (logOpen) {
                LogScreen(
                    vm.log,
                    ui.board?.now?.item,
                    forecast = ui.forecast,
                    flash = flash,
                    onAdd = vm::addOn,
                    onUndo = vm::undo,
                    onEdit = vm.edit::open,
                    onClose = vm.log::close,
                    repeats = repeats,
                    onStopSeries = vm::stopSeries,
                    onReport = vm::openReport,
                )
            } else if (focusView != null) {
                FocusScreen(focusView, ui.now, onDone = vm::doneWithIt, onEnd = vm::endFocus)
            } else Box(Modifier.fillMaxSize()) {
                // A touch anywhere but the text field lets go of it. It only watches,
                // so the touch still reaches whatever it landed on.
                Screen(
                    pulse = { glow.value.takeIf { it < 1f }?.let { Pulse(cardCenter, it) } },
                    modifier = Modifier
                        .onGloballyPositioned { boardAt = it.positionInRoot() }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                if (!fieldArea.contains(down.position + boardAt)) focus.clearFocus()
                            }
                        },
                ) {
                    // Glance at the top; act at the bottom, in the thumb's reach. The
                    // Now card sits on the bottom group and grows upward, so Done
                    // and Skip stay in one place whatever the card holds.
                    // While typing, the board keeps only what typing needs: the panel
                    // goes, and the card shrinks to its title, so nothing is squeezed.
                    if (!keyboardUp) PlanPanel(ui, onOpen = vm::openLog, lines = dialLines)
                    val pairmarksShown = !keyboardUp && pairsShown && pairViews.any { it.peer.showMark }
                    Box(Modifier.weight(1f).fillMaxWidth().padding(top = Space.zone), contentAlignment = Alignment.BottomCenter) {
                      Column(Modifier.padding(top = if (pairmarksShown) pairmarkHeight + Space.inside * 2 else 0.dp)) {
                        // Items stepped aside for a wait: bookmarks from the screen's edge, above the card.
                        if (!keyboardUp) {
                            Waitmarks(ui.board?.waits.orEmpty(), ui.now, onTap = vm::switchTo)
                            if (ui.board?.waits.orEmpty().isNotEmpty()) Spacer(Modifier.height(Space.inside))
                        }
                        NowCard(
                            modifier = Modifier.onGloballyPositioned { cardCenter = it.boundsInRoot().center },
                            ui = ui,
                            flash = flash,
                            onDone = vm::done,
                            onSkip = vm::skip,
                            onTomorrow = vm::tomorrow,
                            onUndo = vm::undo,
                            onFocus = vm::startFocus,
                            onEdit = vm.edit::open,
                            onStep = vm::toggleStep,
                            compact = keyboardUp,
                            onOnIt = vm::onIt,
                        )
                      }
                      // Paired people, up high on the left, under the marker bubbles.
                      if (!keyboardUp && peers.isNotEmpty()) {
                          PairmarkRow(
                              pairViews.filter { it.peer.showMark },
                              front = pairFront,
                              now = ui.now,
                              shown = pairsShown,
                              onFront = vm::setPairFront,
                              onPing = ping,
                              onShown = vm.pairs::setShown,
                              modifier = Modifier
                                  .align(Alignment.TopStart)
                                  .padding(top = Space.inside)
                                  .onSizeChanged { pairmarkHeight = with(density) { it.height.toDp() } },
                          )
                      }
                      // Markers hang off the Plan ahead panel's bottom edge, over the open space.
                      if (!keyboardUp) {
                          MarkHud(
                              ui.marks,
                              onEdit = vm.edit::open,
                              onMore = vm::openLog,
                              modifier = Modifier.align(Alignment.TopStart).offset(y = -(Space.zone + BUBBLE / 2)).padding(start = Space.m),
                          )
                      }
                    }
                    Spacer(Modifier.height(Space.panel))
                    // Folded, the day panel is just the Next line: swipe it up or tap it to open.
                    DayBar(ui, open = false, onToggle = { expanded = true }, modifier = Modifier.onSwipeUp { expanded = true })
                    Spacer(Modifier.height(Space.panel))
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
                            onMarker = vm::answerMarker,
                        )
                    }
                    dupe?.let { (_, old) ->
                        DupeChips(old.title, onReplace = vm::dupeReplace, onKeep = vm::dupeKeep, onCancel = vm::dupeCancel)
                    }
                    // One setup step at a time, until all are done.
                    setup.firstOrNull()?.let { step -> PromptLine(stringResource(step.text)) { openStep(step) } }
                    QuickAdd(onSubmit = vm::submit, modifier = Modifier.onGloballyPositioned { fieldArea = it.boundsInRoot() })
                    Spacer(Modifier.height(Space.panel))
                    // The way to the app drawer: a swipe up that starts in this bottom band,
                    // which sits above Android's own gesture strip, or the Apps handle.
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = DRAWER_BAND)
                            .onSwipeUp(DRAWER_SWIPE) { vm.setDrawer(true) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Space.panel, Alignment.Bottom),
                    ) {
                        Dock(dock, icons, onTap = vm::tapSlot, onHold = vm::holdSlot)
                        // The Apps button, on its look's fixture: the swipe's visible twin.
                        AppsHandle(stringResource(R.string.apps_open), onClick = { vm.setDrawer(true) })
                    }
                }
                // Open, the day panel lies over the board, so the Now card keeps its shape.
                // It unfolds upward out of the Next strip, and folds back down into it.
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(tween(FOLD_MS), expandFrom = Alignment.Bottom) + fadeIn(tween(FOLD_MS)),
                    exit = shrinkVertically(tween(FOLD_MS), shrinkTowards = Alignment.Bottom) + fadeOut(tween(FOLD_MS)),
                ) {
                    DaySheet(ui, onClose = { expanded = false }, onEdit = { item -> expanded = false; vm.edit.open(item) }, onMove = vm::moveTask)
                }
                confirming?.let { item ->
                    StepsCheck(
                        item,
                        onStep = { i -> vm.toggleStep(item, i) },
                        onAllDone = {
                            haptics.confirm()
                            vm.allDone()
                            scope.launch {
                                glow.snapTo(0f)
                                glow.animateTo(1f, tween(PULSE_MS, easing = LinearOutSlowInEasing))
                            }
                        },
                        onNotYet = vm::notYet,
                    )
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
            // A paired person's page opens over settings; Back returns to them.
            val openPeer = peers.firstOrNull { it.id == pairOpen }
            val pairingNow = pairing
            if (pairingNow != null) {
                PairingScreen(
                    pairingNow,
                    onYes = { vm.pairs.answer(true) },
                    onNo = { vm.pairs.answer(false) },
                    onName = vm.pairs::finishPairing,
                    onAgain = pair,
                    onClose = vm.pairs::cancelPairing,
                )
            } else if (openPeer != null) {
                PairScreen(openPeer, peeks[openPeer.id], ui.now, shareable, ticks[openPeer.id].orEmpty(), vm.pairs, onClose = vm::closePair)
            } else SettingsScreen(
                unlock = unlock,
                onBoardOnUnlock = vm.settings::setBoardOnUnlock,
                lockCard = lockCard,
                onLockCard = vm.settings::setLockCard,
                // The chosen look; the one shown may be a holiday's.
                lookId = Looks.byId(chosenLook).id,
                shownId = look.id,
                onLook = vm.settings::setLook,
                holidayLooks = holidayLooks,
                onHolidayLooks = vm.settings::setHolidayLooks,
                holidays = holidays,
                onHoliday = vm.settings::setHoliday,
                peers = peers,
                peeks = peeks,
                now = ui.now,
                onOpenPair = vm::openPair,
                onPair = pair,
                ornaments = ornaments,
                onOrnaments = vm.settings::setOrnaments,
                texture = texture,
                onTexture = vm.settings::setTexture,
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

/**
 * Waitmarks: bookmarks for items stepped aside for a wait, fixed to the screen's
 * right edge above the Now card, each pointing in with its look's end (see
 * [BookmarkShape]). Dim with a countdown while waiting; lit once ready. They
 * slide in from the edge; the soonest sits nearest the card. Tapping one makes
 * it Now (ending its wait early, if it hadn't); the task under way goes back
 * into the day's order. Long names are cut short on screen, never for TalkBack.
 */
@Composable
private fun Waitmarks(waits: List<Item>, now: LocalDateTime, onTap: (Item) -> Unit) {
    if (waits.isEmpty()) return
    val look = LocalLook.current
    val shape = remember(look.handle) { BookmarkShape(look.handle) }
    val haptics = rememberHaptics()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widest = maxWidth * BOOKMARK_SHARE
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            waits.take(MAX_WAITMARKS).reversed().forEach { w ->
                key(w.id) {
                    val ready = w.ready(now)
                    val next = w.nextStep
                    val text = when {
                        !ready -> stringResource(R.string.waitmark_waiting, w.title, Formats.countdown(now, w.waitUntil!!))
                        next != null -> stringResource(R.string.waitmark_ready, w.title, next.title)
                        else -> stringResource(R.string.waitmark_ready_plain, w.title)
                    }
                    val ink = if (ready) Ink.onAccent else Ink.dim
                    val seed = look.palette.jewels.firstOrNull()
                    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
                    AnimatedVisibility(shown, enter = slideInHorizontally(tween(BOOKMARK_MS)) { it } + fadeIn(tween(BOOKMARK_MS))) {
                        Row(
                            Modifier
                                // Past the board's side margin, to the screen's edge.
                                .offset(x = Space.l)
                                .widthIn(max = widest)
                                .heightIn(min = Tap.min)
                                .clip(shape)
                                .background(if (ready) Ink.accent else Ink.surface)
                                .then(if (ready) Modifier else Modifier.border(1.dp, Ink.frame, shape))
                                .clickable(onClickLabel = stringResource(R.string.waitmark_switch, w.title)) {
                                    haptics.tick()
                                    onTap(w)
                                }
                                .padding(start = Space.xxl, end = Space.xl),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Glass marks a wait with a pomegranate seed; the other looks, a clock.
                            Box(
                                Modifier
                                    .size(BOOKMARK_ICON)
                                    .drawBehind {
                                        val line = BOOKMARK_LINE.toPx()
                                        if (look.pointer == Pointer.SEED && seed != null) {
                                            val r = size.minDimension / 2
                                            val c = center
                                            val p = Path().apply {
                                                moveTo(c.x, c.y - r)
                                                cubicTo(c.x - r, c.y, c.x - r * 0.6f, c.y + r, c.x, c.y + r)
                                                cubicTo(c.x + r * 0.6f, c.y + r, c.x + r, c.y, c.x, c.y - r)
                                                close()
                                            }
                                            if (ready) drawPath(p, seed)
                                            drawPath(p, ink, style = Stroke(line))
                                        } else {
                                            val r = size.minDimension / 2 - line
                                            drawCircle(ink, radius = r, style = Stroke(line))
                                            drawLine(ink, center, Offset(center.x, center.y - r * 0.6f), line)
                                            drawLine(ink, center, Offset(center.x + r * 0.45f, center.y + r * 0.3f), line)
                                        }
                                    },
                            )
                            Text(
                                text,
                                style = if (ready) Type.label else Type.caption,
                                color = ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false).padding(start = Space.s),
                            )
                            if (ready) {
                                Text(
                                    stringResource(R.string.waitmark_mark),
                                    style = Type.body,
                                    color = ink,
                                    modifier = Modifier.padding(start = Space.xs).clearAndSetSemantics { },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The top of the board, for a glance: the time and date, and the day's dial.
 * The dial is a low arc across the planning day. Anchors sit on it as pips,
 * Hard ones heavier; things done are small ticks. A pointer under the arc
 * tracks now, so it never hides a pip; outside the planning day it rests at
 * the nearer end, hollow. Tap the panel to plan ahead in the Log. At large
 * font sizes the date steps aside; the dial stays.
 */
@Composable
private fun PlanPanel(ui: BoardUi, onOpen: () -> Unit, lines: List<DialLine> = emptyList()) {
    val label = stringResource(R.string.plan_ahead)
    val large = LocalDensity.current.fontScale > LARGE_FONT
    val now = ui.now
    Strip(Modifier.clickable(onClickLabel = label, onClick = onOpen)) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // At large font sizes the clock steps aside (the status bar has it), so Now has room.
                Column(Modifier.weight(1f)) {
                    if (!large) {
                        Text(Formats.clock(now), style = Type.title, color = Ink.text)
                        Text(Formats.longDay(now.toLocalDate()), style = Type.caption, color = Ink.dim)
                    }
                }
                // A button, like everything tappable; the whole panel opens the Log too.
                QuietButton(stringResource(R.string.plan_ahead_button), onOpen, color = Ink.accent)
            }
            Spacer(Modifier.height(Space.xs))
            DayDial(ui, lines)
            // A key for the lines under the dial: each person's color and nickname.
            if (lines.isNotEmpty()) {
                Row(Modifier.padding(top = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    lines.forEach { l ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clearAndSetSemantics { }) {
                            Box(Modifier.size(KEY_W, KEY_H).background(l.color))
                            Text(l.name, style = Type.caption, color = Ink.dim, modifier = Modifier.padding(start = Space.xs))
                        }
                    }
                }
            }
        }
    }
}

/**
 * The dial itself: the day's order as spans along the arc, each from its start
 * to its end. Anchors are solid (Hard ones thicker); tasks, being a forecast,
 * are outlined; Now is lit in the accent color. Done items are ticks at the
 * time they were done. It shows time only, never progress. TalkBack hears Now
 * with its end, then the day's line, walls and all.
 */
@Composable
private fun DayDial(ui: BoardUi, lines: List<DialLine> = emptyList()) {
    val start = ui.window.start.toSecondOfDay()
    val length = (ui.window.end.toSecondOfDay() - start).coerceAtLeast(1)
    fun frac(t: LocalDateTime): Float = (t.toLocalTime().toSecondOfDay() - start).toFloat() / length
    val nowAt = frac(ui.now)
    // Eased between the clock's ticks, so the pointer glides rather than steps.
    val pointerAt by animateFloatAsState(nowAt.coerceIn(0f, 1f), tween(POINTER_EASE_MS), label = "pointer")
    val nowItem = ui.board?.now?.item
    val waits = ui.board?.waits.orEmpty()
    // Now's span: its forecast slot, or an anchor under way, which the forecast has passed.
    val nowSlot = ui.forecast.slots.firstOrNull { it.item.id == nowItem?.id }
    val nowEnd = nowSlot?.end ?: nowItem?.takeIf { it.isAnchor }?.anchorEnd
    val nowSpan = nowItem?.let { i -> (nowSlot?.at ?: i.start)?.let { s -> nowEnd?.let { e -> s to e } } }

    val walls = ui.ledger.filter { it.kind != MarkKind.DONE }.map {
        stringResource(R.string.ledger_wall, it.item.title, Formats.clock(it.at), Formats.wall(it.item.wall))
    }
    val doneCount = ui.ledger.count { it.kind == MarkKind.DONE }
    val nowLine = if (nowItem != null && nowEnd != null) stringResource(R.string.ledger_now, nowItem.title, Formats.clock(nowEnd)) + " " else ""
    val description = stringResource(
        R.string.ledger_description,
        nowLine + pluralStringResource(R.plurals.ledger_done, doneCount, doneCount),
        if (walls.isEmpty()) stringResource(R.string.ledger_no_walls) else stringResource(R.string.ledger_walls, walls.joinToString("; ")),
    ) + lines.map { l ->
        // map, not joinToString: stringResource needs an inline lambda.
        " " + stringResource(R.string.dial_pair_line, l.name, l.spans.joinToString("; ") { (a, _, t) -> "$t ${Formats.clock(a)}" })
    }.joinToString("")

    val lineColor = Ink.line
    val doneColor = Ink.dim
    val anchorColor = Ink.text
    val taskColor = Ink.frame
    val hollowColor = Ink.surface
    val nowColor = Ink.accent
    // Each look's own marks: a pomegranate seed or a heart or star for now; coins, hearts, or sparkles for things done.
    val pointerKind = LocalLook.current.pointer
    val doneKind = LocalLook.current.doneMark
    val seedColor = LocalLook.current.palette.jewels.firstOrNull() ?: nowColor
    val ornament = Ink.ornament
    val jewels = LocalLook.current.palette.jewels
    val ghostColor = jewels.getOrNull(3) ?: Ink.text
    val jewelLast = jewels.lastOrNull() ?: nowColor
    // Candy corn's yellow base: Halloween's fifth jewel.
    val cornBase = jewels.getOrNull(4) ?: nowColor
    val coinColor = Ink.accent
    val coinHole = Ink.surface
    Canvas(Modifier.fillMaxWidth().height(DIAL_HEIGHT + LINE_GAP * lines.size).semantics { contentDescription = description }) {
        // Each paired person's line sits under yours; the pointer goes under them all.
        val under = LINE_GAP.toPx() * lines.size
        val base = size.height - POINTER_ROOM.toPx() - under
        val rise = DIAL_RISE.toPx()
        val w = size.width
        // On this curve, left to right is time, evenly: an hour is the same width anywhere.
        fun y(f: Float): Float = base - 4 * rise * f * (1 - f)
        val arc = Path().apply {
            moveTo(0f, base)
            quadraticBezierTo(w / 2, base - 2 * rise, w, base)
        }
        drawPath(arc, lineColor, style = Stroke(ARC_WIDTH.toPx()))

        // A span along the curve, a hairline short of each end so neighbours don't merge,
        // and never thinner than a short mark.
        fun band(from: LocalDateTime, to: LocalDateTime, color: Color, width: Dp, lift: Float = 0f, effect: PathEffect? = null) {
            val gap = BAND_GAP.toPx()
            val x0 = frac(from).coerceIn(0f, 1f) * w + gap
            val x1 = maxOf(frac(to).coerceIn(0f, 1f) * w - gap, x0 + BAND_MIN.toPx())
            val steps = maxOf(2, ((x1 - x0) / BAND_STEP.toPx()).toInt())
            val path = Path()
            for (i in 0..steps) {
                val x = x0 + (x1 - x0) * i / steps
                val py = y(x / w) - lift
                if (i == 0) path.moveTo(x, py) else path.lineTo(x, py)
            }
            drawPath(path, color, style = Stroke(width.toPx(), pathEffect = effect))
        }

        // A running wait: dotted, just above the arc, so the tasks filling it stay on the line.
        val dots = PathEffect.dashPathEffect(floatArrayOf(WAIT_DOT.toPx(), WAIT_GAP.toPx()))
        waits.forEach { wait ->
            val from = wait.waitStart ?: return@forEach
            val until = wait.waitUntil ?: return@forEach
            if (until.isAfter(ui.now)) band(from, until, doneColor, ARC_WIDTH, lift = WAIT_LIFT.toPx(), effect = dots)
        }

        // Paired people's lines: faint for free, solid for their shared times, bright for what they're probably doing now.
        lines.forEachIndexed { i, l ->
            val drop = -LINE_GAP.toPx() * (i + 1)
            val faint = Path().apply {
                moveTo(0f, base - drop)
                quadraticBezierTo(w / 2, base - 2 * rise - drop, w, base - drop)
            }
            drawPath(faint, l.color.copy(alpha = LINE_FAINT), style = Stroke(LINE_THIN.toPx()))
            l.spans.forEach { (from, to, _) -> band(from, to, l.color.copy(alpha = LINE_SOLID), LINE_SPAN, lift = drop) }
            l.bright?.let { (from, to) -> band(from, to, l.color, LINE_BRIGHT, lift = drop) }
        }

        ui.forecast.slots.forEach { slot ->
            val item = slot.item
            when {
                item.id == nowItem?.id -> Unit
                item.isAnchor -> band(slot.at, slot.end, anchorColor, if (item.wall == WallStrength.HARD) BAND_HARD else BAND_ANCHOR)
                else -> {
                    band(slot.at, slot.end, taskColor, BAND_TASK)
                    band(slot.at, slot.end, hollowColor, BAND_HOLLOW)
                }
            }
        }
        nowSpan?.let { (from, to) -> band(from, to, nowColor, BAND_TASK) }
        ui.ledger.filter { it.kind == MarkKind.DONE }.forEach { mark ->
            val f = frac(mark.at).coerceIn(0f, 1f)
            val c = Offset(f * w, y(f))
            when (doneKind) {
                // An obol, the ferryman's coin.
                DoneMark.COIN -> {
                    drawCircle(coinColor, radius = COIN.toPx(), center = c)
                    drawCircle(coinHole, radius = COIN.toPx() * COIN_RING, center = c, style = Stroke(ARC_WIDTH.toPx() / 2))
                }
                DoneMark.HEART -> drawMark(heartPath(c, COIN.toPx()), coinColor)
                DoneMark.SPARKLE -> drawMark(sparklePath(c, COIN.toPx() * SPARKLE_REACH), ornament)
                DoneMark.GHOST -> ghost(Offset(c.x, c.y - COIN.toPx() * 0.3f), COIN.toPx() * GHOST_SIZE, ghostColor, hollowColor)
                DoneMark.SNOWFLAKE -> snowflake(c, COIN.toPx() * SPARKLE_REACH * 0.8f, anchorColor, ARC_WIDTH.toPx() * 0.6f)
                DoneMark.TICK ->
                    drawLine(doneColor, Offset(c.x, c.y - TICK.toPx()), Offset(c.x, c.y + TICK.toPx()), strokeWidth = ARC_WIDTH.toPx())
            }
        }

        // The pointer: a small triangle under the arc, on the curve's perpendicular at
        // now, pointing at it, so it leans with the arc as a dimension arrow would.
        val inDay = nowAt in 0f..1f
        val f = pointerAt
        val px = f * w
        val slope = -4 * rise * (1 - 2 * f) / w
        val norm = sqrt(1 + slope * slope)
        val nx = -slope / norm
        val ny = 1 / norm
        val tx = 1 / norm
        val ty = slope / norm
        val gap = POINTER_GAP.toPx() + under
        val tall = POINTER_HEIGHT.toPx()
        val half = POINTER_WIDTH.toPx() / 2
        val tipX = px + nx * gap
        val tipY = y(f) + ny * gap
        val backX = tipX + nx * tall
        val backY = tipY + ny * tall
        val pointer = Path().apply {
            moveTo(tipX, tipY)
            lineTo(backX - tx * half, backY - ty * half)
            lineTo(backX + tx * half, backY + ty * half)
            close()
        }
        // A heart or star, turned so its point faces now; their shapes point down and up.
        val mid = Offset((tipX + backX) / 2, (tipY + backY) / 2)
        val facing = Math.toDegrees(atan2(-ny, -nx).toDouble()).toFloat()
        fun charm(path: Path, color: Color, pointsAt: Float) = rotate(facing - pointsAt, mid) {
            if (inDay) drawPath(path, color)
            drawPath(path, nowColor, style = Stroke(ARC_WIDTH.toPx() / 2))
        }
        if (pointerKind == Pointer.HEART) {
            charm(heartPath(mid, tall * CHARM_SIZE), nowColor, DOWN)
        } else if (pointerKind == Pointer.STAR) {
            charm(starPath(mid, tall * CHARM_SIZE * 1.2f), ornament, UP)
        } else if (pointerKind == Pointer.TREE) {
            charm(treePath(Offset(mid.x, mid.y - tall * 0.5f), tall), jewelLast, UP)
        } else if (pointerKind == Pointer.CANDY_CORN) {
            // A candy corn: the pointer's own triangle in three bands, white at the tip.
            val corn = listOf(1f to cornBase, 0.66f to ornament, 0.33f to anchorColor)
            corn.forEach { (share, color) ->
                val bx = tipX + nx * tall * share
                val by = tipY + ny * tall * share
                val band = Path().apply {
                    moveTo(tipX, tipY)
                    lineTo(bx - tx * half * share, by - ty * half * share)
                    lineTo(bx + tx * half * share, by + ty * half * share)
                    close()
                }
                if (inDay) drawPath(band, color) else if (share == 1f) drawPath(band, nowColor, style = Stroke(ARC_WIDTH.toPx() / 2))
            }
        } else if (pointerKind == Pointer.SEED) {
            // A pomegranate seed: round at the back, coming to a point at now.
            val seed = Path().apply {
                moveTo(tipX, tipY)
                cubicTo(
                    tipX + nx * tall * SEED_SHOULDER - tx * half * SEED_WIDTH, tipY + ny * tall * SEED_SHOULDER - ty * half * SEED_WIDTH,
                    backX - tx * half * SEED_WIDTH, backY - ty * half * SEED_WIDTH,
                    backX, backY,
                )
                cubicTo(
                    backX + tx * half * SEED_WIDTH, backY + ty * half * SEED_WIDTH,
                    tipX + nx * tall * SEED_SHOULDER + tx * half * SEED_WIDTH, tipY + ny * tall * SEED_SHOULDER + ty * half * SEED_WIDTH,
                    tipX, tipY,
                )
                close()
            }
            if (inDay) drawPath(seed, seedColor)
            drawPath(seed, nowColor, style = Stroke(ARC_WIDTH.toPx() / 2))
        } else if (inDay) {
            drawPath(pointer, nowColor)
        } else {
            drawPath(pointer, nowColor, style = Stroke(ARC_WIDTH.toPx()))
        }
    }
}

/**
 * Done on an item with steps still open: tick what's done, then All done
 * finishes it, steps and all, or Not yet leaves it as it is. Back, or a tap
 * outside, is Not yet.
 */
@Composable
private fun StepsCheck(item: Item, onStep: (Int) -> Unit, onAllDone: () -> Unit, onNotYet: () -> Unit) {
    val notYet = stringResource(R.string.steps_not_yet)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = notYet, onClick = onNotYet)
                .semantics { contentDescription = notYet },
        )
        Box(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = Space.l, vertical = Space.m),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Taps on the card stay on it; only a tap outside is Not yet.
            Card(Modifier.pointerInput(Unit) { detectTapGestures { } }) {
                Title(item.title, style = Type.title)
                Spacer(Modifier.height(Space.s))
                Body(stringResource(R.string.steps_check))
                Spacer(Modifier.height(Space.s))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    item.steps.forEachIndexed { i, step -> StepRow(step.title, step.done) { onStep(i) } }
                }
                Spacer(Modifier.height(Space.xl))
                PrimaryButton(stringResource(R.string.steps_all_done), onAllDone, Modifier.fillMaxWidth())
                Spacer(Modifier.height(Space.s))
                SecondaryButton(notYet, onNotYet, Modifier.fillMaxWidth())
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
    // The next thing in the day's order: an anchor with its countdown, a task with its time.
    val next = ui.board?.next
    val start = next?.start
    val taskAt = ui.board?.nextAt
    val text = when {
        next == null -> stringResource(R.string.nothing_next)
        next.isAnchor && start != null -> stringResource(R.string.next_anchor, next.title, Formats.countdown(ui.now, start))
        taskAt != null -> stringResource(R.string.next_task_at, next.title, Formats.clock(taskAt))
        else -> stringResource(R.string.next_float, next.title)
    }
    val tap = modifier.clickable(onClickLabel = stringResource(if (open) R.string.day_close else R.string.day_open), onClick = onToggle)
    // One line at large font sizes, so the Now card above keeps room for its title.
    val lines = if (LocalDensity.current.fontScale > LARGE_FONT) 1 else 2
    val content: @Composable RowScope.() -> Unit = {
        Text(text, style = Type.body, color = Ink.text, maxLines = lines, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
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
private fun DaySheet(ui: BoardUi, onClose: () -> Unit, onEdit: (Item) -> Unit, onMove: (Item, Int) -> Boolean) {
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
                ForecastList(ui.forecast, Modifier.weight(1f, fill = false), onEdit, onMove)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AskChips(title: String, onFloat: () -> Unit, onAnchor: (LocalTime) -> Unit, onCancel: () -> Unit, onMarker: () -> Unit) {
    val context = LocalContext.current
    // In a panel, so the question never sits on the background pattern.
    Card(Modifier.padding(bottom = Space.s), inner = Space.l, arrangement = Arrangement.spacedBy(Space.inside)) {
        Body(stringResource(R.string.ask_kind, title), color = Ink.text, style = Type.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            SecondaryButton(stringResource(R.string.ask_float), onFloat, height = Tap.min)
            SecondaryButton(
                stringResource(R.string.ask_anchor),
                { TimePickerDialog(context, { _, h, m -> onAnchor(LocalTime.of(h, m)) }, 9, 0, false).show() },
                height = Tap.min,
            )
            SecondaryButton(stringResource(R.string.ask_marker), onMarker, height = Tap.min)
            QuietButton(stringResource(R.string.ask_cancel), onCancel)
        }
    }
}

/** A new series named like one already running: replace that one, or keep both. */
@Composable
private fun DupeChips(title: String, onReplace: () -> Unit, onKeep: () -> Unit, onCancel: () -> Unit) {
    // In a panel, so the question never sits on the background pattern.
    Card(Modifier.padding(bottom = Space.s), inner = Space.l, arrangement = Arrangement.spacedBy(Space.inside)) {
        Body(stringResource(R.string.dupe_ask, title), color = Ink.text, style = Type.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton(stringResource(R.string.dupe_replace), onReplace, height = Tap.min)
            SecondaryButton(stringResource(R.string.dupe_keep), onKeep, height = Tap.min)
            QuietButton(stringResource(R.string.ask_cancel), onCancel)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickAdd(onSubmit: (String) -> Boolean, modifier: Modifier = Modifier) {
    // Saved across process restarts, so a half-typed line survives.
    var text by rememberSaveable { mutableStateOf("") }
    // Once the keyboard has been up while the field has focus, the keyboard going
    // down lets the field go too, so it never stays lit with no keyboard. Forced,
    // so nothing can hold on to it. The board's own check backs this up.
    // It reads the window's own keyboard state, checked while the field has focus,
    // rather than Compose's keyboard signal, which didn't always arrive.
    val focus = LocalFocusManager.current
    val view = LocalView.current
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        var sawKeyboard = false
        // Checked for as long as the field has focus, so a release that doesn't take is tried again.
        while (focused) {
            val up = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
            if (up) {
                sawKeyboard = true
            } else if (sawKeyboard) {
                Log.i(LOG_TAG, "quick-add: keyboard closed, letting go")
                focus.clearFocus(force = true)
                delay(KEYBOARD_CHECK_MS)
                if (focused) {
                    // Still held: clear focus from the whole screen.
                    Log.i(LOG_TAG, "quick-add: still focused, clearing the view")
                    view.clearFocus()
                }
                sawKeyboard = false
            }
            delay(KEYBOARD_CHECK_MS)
        }
    }
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
        colors = fieldColors(),
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
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

// The day dial: its height, the arc's rise and line, the pips, and the pointer under it.
private val DIAL_HEIGHT = 32.dp
private val DIAL_RISE = 8.dp
private val ARC_WIDTH = 2.dp
private val TICK = 6.dp
private val BAND_HARD = 8.dp
private val BAND_ANCHOR = 5.dp
private val BAND_TASK = 6.dp
private val BAND_HOLLOW = 3.dp
private val BAND_MIN = 4.dp
private val BAND_GAP = 1.5.dp
private val BAND_STEP = 4.dp
private val POINTER_ROOM = 12.dp
private val POINTER_GAP = 3.dp
private val POINTER_WIDTH = 12.dp
private val POINTER_HEIGHT = 8.dp

/** The app's log tag, as the emulator test reads it. */
private const val LOG_TAG = "Initiative"

// The dial's marks: a coin's size (also the hearts' and sparkles') and its inner ring; the seed's shoulders and width.
private val COIN = 4.dp
private const val COIN_RING = 0.55f
private const val SEED_SHOULDER = 0.35f
private const val SEED_WIDTH = 1.3f

// The cute looks' dial marks: a done sparkle's reach (in coins), a pointer charm's size (a share of
// the pointer's height), and which way each shape points as drawn, in degrees.
private const val SPARKLE_REACH = 1.6f
private const val CHARM_SIZE = 0.55f
private const val DOWN = 90f
private const val GHOST_SIZE = 3f
private const val UP = -90f

/** Waitmarks shown at once; the rest wait their turn. */
private const val MAX_WAITMARKS = 2

// The waitmark bookmarks: how wide at most (a share of the board), their icon and its line, and the slide in.
private const val BOOKMARK_SHARE = 0.85f
private val BOOKMARK_ICON = 18.dp
private val BOOKMARK_LINE = 2.dp
private const val BOOKMARK_MS = 300

// A running wait on the dial: how far above the arc, and its dots.
private val WAIT_LIFT = 7.dp
private val WAIT_DOT = 2.dp
private val WAIT_GAP = 3.dp

/** While quick-add has focus, how often it checks whether the keyboard has closed. */
private const val KEYBOARD_CHECK_MS = 300L

/** The day panel's fold, open and closed. */
private const val FOLD_MS = 250

/** How long the dial's pointer takes to glide to the clock's new place. */
private const val POINTER_EASE_MS = 1_000

/** The All done pulse's run across the screen. */
private const val PULSE_MS = 700

/** The open day panel takes at most this share of the screen. */
private const val SHEET_SHARE = 0.7f

/** A paired person's line under the dial: their spans today, and the one they're probably in now. */
data class DialLine(
    val name: String,
    val color: Color,
    val spans: List<Triple<LocalDateTime, LocalDateTime, String>>,
    val bright: Pair<LocalDateTime, LocalDateTime>?,
)

// Paired people's lines under the dial: the gap between lines, their strokes and strengths, and the key's swatch.
private val LINE_GAP = 9.dp
private const val MAX_LINES = 3
private val LINE_THIN = 1.5.dp
private val LINE_SPAN = 3.dp
private val LINE_BRIGHT = 4.dp
private const val LINE_FAINT = 0.45f
private const val LINE_SOLID = 0.75f
private val KEY_W = 18.dp
private val KEY_H = 4.dp
