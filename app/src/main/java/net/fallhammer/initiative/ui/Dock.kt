package net.fallhammer.initiative.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import initiative.core.Trio
import net.fallhammer.initiative.R
import net.fallhammer.initiative.data.AppEntry
import kotlin.math.roundToInt

/**
 * Favourite apps along the bottom of the board, in fixed slots. Tap to open;
 * an empty slot shows + and opens the picker; hold a slot to change or empty it.
 * A slot with [more] apps in its tray carries a small mark, turned over while
 * its tray is [open]; [onPlaced] reports where each slot sits, for the tray.
 * In the Sci-fi look the slots are nested hexagon trios; otherwise plain squares.
 */
@Composable
fun Dock(
    slots: List<AppEntry?>,
    icons: Map<String, ImageBitmap>,
    onTap: (Int) -> Unit,
    onHold: (Int) -> Unit,
    more: List<Boolean> = emptyList(),
    open: Int = -1,
    onPlaced: (Int, Rect) -> Unit = { _, _ -> },
) {
    val marks = DockMarks(more, open, onPlaced)
    if (LocalLook.current.dockTrio) {
        TrioDock(slots, icons, onTap, onHold, marks)
    } else {
        SquareDock(slots, icons, onTap, onHold, marks)
    }
}

/** Which slots have a tray, which one is open, and where to report each slot's place. */
private class DockMarks(val more: List<Boolean>, val open: Int, val onPlaced: (Int, Rect) -> Unit)

/*
 * Nested trios overlap their neighbours' boxes, so the row takes the touches
 * itself and asks Trio which tile's real outline a tap fell in. Each tile still
 * carries its own click and hold actions for TalkBack.
 */
@Composable
private fun TrioDock(slots: List<AppEntry?>, icons: Map<String, ImageBitmap>, onTap: (Int) -> Unit, onHold: (Int) -> Unit, marks: DockMarks) {
    val density = LocalDensity.current
    val lattice = LocalHexLattice.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val hex = Trio.hexFor(constraints.maxWidth.toFloat(), slots.size)
        val tileWidth = with(density) { Trio.width(hex).toDp() }
        val tileHeight = with(density) { Trio.height(hex).toDp() }
        val (iconX, iconY) = Trio.iconCenter(hex)
        val iconSize = with(density) { (hex * ICON_SHARE).toDp() }
        val iconOffset = IntOffset((iconX - hex * ICON_SHARE / 2).roundToInt(), (iconY - hex * ICON_SHARE / 2).roundToInt())
        Box(
            Modifier
                .fillMaxWidth()
                .height(tileHeight)
                // The background honeycomb lines up with the dock's hexagons.
                .onGloballyPositioned { c ->
                    val at = c.positionInRoot()
                    lattice.value = HexLattice(hex, Offset(at.x + hex / 2, at.y + Trio.hexHeight(hex) / 2))
                }
                .pointerInput(hex, slots.size) {
                    detectTapGestures(
                        onTap = { p -> Trio.tileAt(p.x, p.y, hex, slots.size)?.let(onTap) },
                        onLongPress = { p -> Trio.tileAt(p.x, p.y, hex, slots.size)?.let(onHold) },
                    )
                },
        ) {
            slots.forEachIndexed { i, app ->
                Tile(
                    app = app,
                    icon = app?.let { icons[it.key] },
                    onTap = { onTap(i) },
                    onHold = { onHold(i) },
                    tray = marks.more.getOrElse(i) { false },
                    open = marks.open == i,
                    // Over the icon, inside the tile's top hexagon.
                    markAt = { Offset(iconX, iconY - hex * ICON_SHARE / 2 - MARK_GAP.toPx()) },
                    modifier = Modifier
                        .offset { IntOffset((i * Trio.advance(hex)).roundToInt(), 0) }
                        .size(tileWidth, tileHeight)
                        .onGloballyPositioned { c ->
                            // The wide left part, where the icon sits, so the tray rises over the icon.
                            val b = c.boundsInRoot()
                            marks.onPlaced(i, Rect(b.left, b.top, b.left + b.width * ICON_PART, b.bottom))
                        }
                        .clip(TrioShape)
                        .background(Ink.surface)
                        // Heavier than the honeycomb behind it, so the tiles stand out from it.
                        .border(TRIO_LINE, Ink.frame, TrioShape),
                ) {
                    Box(Modifier.offset { iconOffset }.size(iconSize), contentAlignment = Alignment.Center) { it() }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SquareDock(slots: List<AppEntry?>, icons: Map<String, ImageBitmap>, onTap: (Int) -> Unit, onHold: (Int) -> Unit, marks: DockMarks) {
    val shape = LocalLook.current.buttonShape
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        slots.forEachIndexed { i, app ->
            Tile(
                app = app,
                icon = app?.let { icons[it.key] },
                onTap = { onTap(i) },
                onHold = { onHold(i) },
                tray = marks.more.getOrElse(i) { false },
                open = marks.open == i,
                // Over the icon, in the tile's top margin.
                markAt = { Offset(size.width / 2, (size.height - SQUARE_ICON.toPx()) / 4) },
                modifier = Modifier
                    .weight(1f)
                    .height(Tap.primary)
                    .onGloballyPositioned { marks.onPlaced(i, it.boundsInRoot()) }
                    .clip(shape)
                    .background(Ink.surface)
                    .border(1.dp, Ink.frame, shape)
                    .combinedClickable(onClick = { onTap(i) }, onLongClick = { onHold(i) }),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(SQUARE_ICON), contentAlignment = Alignment.Center) { it() }
                }
            }
        }
    }
}

/**
 * One slot: the app's icon, or + when empty. Named for TalkBack, with tap and
 * hold actions. With a [tray], a small mark sits at [markAt], turned over while [open].
 */
@Composable
private fun Tile(
    app: AppEntry?,
    icon: ImageBitmap?,
    onTap: () -> Unit,
    onHold: () -> Unit,
    modifier: Modifier,
    tray: Boolean = false,
    open: Boolean = false,
    markAt: DrawScope.() -> Offset = { Offset.Zero },
    place: @Composable (content: @Composable () -> Unit) -> Unit,
) {
    val label = app?.label ?: stringResource(R.string.dock_add)
    val name = when {
        !tray -> label
        open -> stringResource(R.string.dock_tray_open, label)
        else -> stringResource(R.string.dock_tray, label)
    }
    val change = stringResource(R.string.dock_change)
    val kind = LocalLook.current.more
    val markColor = Ink.accent
    val tones = listOf(LocalLook.current.palette.jewels.getOrNull(4) ?: Ink.accent, Ink.accent, Ink.text)
    Box(
        modifier
            .drawWithContent {
                drawContent()
                if (tray) {
                    val at = markAt()
                    rotate(if (open) 180f else 0f, at) { moreMark(kind, at, MARK_WIDTH.toPx(), markColor, tones) }
                }
            }
            .semantics {
            contentDescription = name
            role = Role.Button
            onClick { onTap(); true }
            onLongClick(change) { onHold(); true }
        },
        contentAlignment = Alignment.TopStart,
    ) {
        place {
            when {
                icon != null -> Image(icon, contentDescription = null, modifier = Modifier.clearAndSetSemantics { })
                app == null -> Text(stringResource(R.string.dock_add_mark), style = Type.title, color = Ink.accent, modifier = Modifier.clearAndSetSemantics { })
                else -> Text(app.label.take(1), style = Type.title, color = Ink.text, modifier = Modifier.clearAndSetSemantics { })
            }
        }
    }
}

/** The "more" mark's width, and its gap above the icon. */
private val MARK_WIDTH = 12.dp
private val MARK_GAP = 5.dp

/** The trio tiles' outline: twice the background grid's hairline. */
private val TRIO_LINE = 2.dp

/** The share of a trio tile's width that its icon is centered in: 1.5 of its 1.75 hexagon widths. */
private const val ICON_PART = 1.5f / 1.75f

/** How much of a hexagon's width the icon takes. */
private const val ICON_SHARE = 0.8f
private val SQUARE_ICON = 40.dp

/**
 * A slot's tray: an upright panel of the look's card shape on the dark surface,
 * holding the apps past the slot's own, nearest the slot at the bottom. No
 * words; each app is named for TalkBack.
 */
@Composable
fun DockTray(apps: List<AppEntry>, icons: Map<String, ImageBitmap>, onTap: (AppEntry) -> Unit, modifier: Modifier = Modifier) {
    val shape = LocalLook.current.cardShape
    val title = stringResource(R.string.tray_label)
    Column(
        modifier
            .width(TRAY_WIDTH)
            .clip(shape)
            .background(Ink.surface)
            .border(LocalLook.current.came, Ink.frame, shape)
            .padding(vertical = Space.inside)
            .semantics { paneTitle = title },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        apps.asReversed().forEach { app ->
            val icon = icons[app.key]
            Box(
                Modifier
                    .size(Tap.row)
                    .clickable(onClickLabel = null, role = Role.Button) { onTap(app) }
                    .semantics { contentDescription = app.label },
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) {
                    Image(icon, contentDescription = null, modifier = Modifier.size(SQUARE_ICON).clearAndSetSemantics { })
                } else {
                    Text(app.label.take(1), style = Type.title, color = Ink.text, modifier = Modifier.clearAndSetSemantics { })
                }
            }
        }
    }
}

/** A tray's width: one app's touch target and a margin each side. */
private val TRAY_WIDTH = 72.dp

/**
 * The open tray over the board: the board dims, and the tray slides up out of
 * its slot (at [slotAt], in root coordinates); shut, it slides back down into
 * it. A tap outside it shuts it. With Android's animations off, it just appears.
 */
@Composable
fun DockTrayLayer(
    open: Int,
    slots: List<List<AppEntry>>,
    icons: Map<String, ImageBitmap>,
    slotAt: Map<Int, Rect>,
    onTap: (AppEntry) -> Unit,
    onClose: () -> Unit,
) {
    // Kept while it slides shut, so the closing tray keeps its apps and place.
    var shown by remember { mutableIntStateOf(-1) }
    if (open >= 0) shown = open
    val motion = rememberMotionOn()
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    var width by remember { mutableIntStateOf(0) }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                origin = it.positionInRoot()
                width = it.size.width
            },
    ) {
        AnimatedVisibility(
            visible = open >= 0,
            enter = if (motion) fadeIn(tween(TRAY_MS)) else EnterTransition.None,
            exit = if (motion) fadeOut(tween(TRAY_MS)) else ExitTransition.None,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Ink.scrim)
                    .pointerInput(Unit) { detectTapGestures { onClose() } },
            )
        }
        val slot = slotAt[shown] ?: return@Box
        val apps = slots.getOrNull(shown).orEmpty().drop(1)
        val trayPx = with(density) { TRAY_WIDTH.toPx() }
        val x = (slot.center.x - origin.x - trayPx / 2).coerceIn(0f, (width - trayPx).coerceAtLeast(0f))
        // Everything above the slot, clipped, so the tray rises out of the slot's top edge.
        val above = with(density) { (slot.top - origin.y).coerceAtLeast(0f).toDp() }
        Box(Modifier.fillMaxWidth().height(above).clipToBounds()) {
            AnimatedVisibility(
                visible = open >= 0,
                modifier = Modifier.align(Alignment.BottomStart).offset { IntOffset(x.roundToInt(), 0) },
                enter = if (motion) slideInVertically(tween(TRAY_MS, easing = FastOutSlowInEasing)) { it } else EnterTransition.None,
                exit = if (motion) slideOutVertically(tween(TRAY_MS, easing = FastOutSlowInEasing)) { it } else ExitTransition.None,
            ) {
                DockTray(apps, icons, onTap)
            }
        }
    }
}

/** How long a tray takes to slide open or shut. */
private const val TRAY_MS = 220
