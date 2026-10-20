package net.fallhammer.initiative.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * In the Sci-fi look the slots are nested hexagon trios; otherwise plain squares.
 */
@Composable
fun Dock(slots: List<AppEntry?>, icons: Map<String, ImageBitmap>, onTap: (Int) -> Unit, onHold: (Int) -> Unit) {
    if (LocalLook.current.dockTrio) {
        TrioDock(slots, icons, onTap, onHold)
    } else {
        SquareDock(slots, icons, onTap, onHold)
    }
}

/*
 * Nested trios overlap their neighbours' boxes, so the row takes the touches
 * itself and asks Trio which tile's real outline a tap fell in. Each tile still
 * carries its own click and hold actions for TalkBack.
 */
@Composable
private fun TrioDock(slots: List<AppEntry?>, icons: Map<String, ImageBitmap>, onTap: (Int) -> Unit, onHold: (Int) -> Unit) {
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
                    modifier = Modifier
                        .offset { IntOffset((i * Trio.advance(hex)).roundToInt(), 0) }
                        .size(tileWidth, tileHeight)
                        .clip(TrioShape)
                        .background(Ink.surface)
                        .border(1.dp, Ink.frame, TrioShape),
                ) {
                    Box(Modifier.offset { iconOffset }.size(iconSize), contentAlignment = Alignment.Center) { it() }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SquareDock(slots: List<AppEntry?>, icons: Map<String, ImageBitmap>, onTap: (Int) -> Unit, onHold: (Int) -> Unit) {
    val shape = LocalLook.current.buttonShape
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        slots.forEachIndexed { i, app ->
            Tile(
                app = app,
                icon = app?.let { icons[it.key] },
                onTap = { onTap(i) },
                onHold = { onHold(i) },
                modifier = Modifier
                    .weight(1f)
                    .height(Tap.primary)
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

/** One slot: the app's icon, or + when empty. Named for TalkBack, with tap and hold actions. */
@Composable
private fun Tile(
    app: AppEntry?,
    icon: ImageBitmap?,
    onTap: () -> Unit,
    onHold: () -> Unit,
    modifier: Modifier,
    place: @Composable (content: @Composable () -> Unit) -> Unit,
) {
    val name = app?.label ?: stringResource(R.string.dock_add)
    val change = stringResource(R.string.dock_change)
    Box(
        modifier.semantics {
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

/** How much of a hexagon's width the icon takes. */
private const val ICON_SHARE = 0.8f
private val SQUARE_ICON = 40.dp
