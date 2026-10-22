package net.fallhammer.initiative.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import initiative.core.Forecast
import initiative.core.Formats
import initiative.core.Item
import initiative.core.WallStrength
import java.time.Duration
import net.fallhammer.initiative.R

/**
 * The rest of today, in order, with each entry's time, then what doesn't fit
 * today under Later. Tap an entry to edit it. Hold a task and drag it to a new
 * place; [onMove] gets the task and its new place among the other entries. It
 * lands there only if it fits; anchors don't move. TalkBack gets Move earlier
 * and Move later instead of dragging.
 */
@Composable
fun ForecastList(forecast: Forecast, modifier: Modifier, onEdit: (Item) -> Unit, onMove: (Item, Int) -> Boolean) {
    if (forecast.slots.isEmpty() && forecast.later.isEmpty()) {
        Body(stringResource(R.string.queue_empty), modifier = Modifier.padding(Space.s))
        return
    }
    // Each entry's top and bottom on screen, for working out where a drag ends.
    // Measured before the drag's own offset, so a moving row keeps its home position.
    val bounds = remember { mutableStateMapOf<Long, ClosedFloatingPointRange<Float>>() }
    var dragging by remember { mutableStateOf<Long?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    // A tick each time the dragged task passes another entry; a settle where it lands, or a "no".
    val haptics = rememberHaptics()
    var passing by remember { mutableStateOf<Int?>(null) }

    fun dropIndex(item: Item): Int? {
        val own = bounds[item.id] ?: return null
        val center = (own.start + own.endInclusive) / 2 + dragY
        val others = forecast.slots.filter { it.item.id != item.id }
        val lastBottom = others.mapNotNull { bounds[it.item.id]?.endInclusive }.maxOrNull() ?: return 0
        // Dropped below the day's entries, into Later: no move.
        if (center > lastBottom) return null
        return others.count { s -> bounds[s.item.id]?.let { (it.start + it.endInclusive) / 2 < center } ?: false }
    }

    val earlier = stringResource(R.string.move_earlier)
    val later = stringResource(R.string.move_later)
    val edit = stringResource(R.string.edit_action)
    val shape = LocalLook.current.buttonShape

    @Composable
    fun Entry(item: Item, time: String?, place: Int?) {
        val drag = if (item.isAnchor) {
            Modifier
        } else {
            Modifier.pointerInput(item.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragging = item.id; dragY = 0f; passing = dropIndex(item); haptics.pickUp() },
                    onDrag = { change, delta ->
                        change.consume()
                        dragY += delta.y
                        val at = dropIndex(item)
                        if (at != passing) {
                            passing = at
                            haptics.pass()
                        }
                    },
                    onDragEnd = {
                        val at = dropIndex(item)
                        when {
                            at == null -> haptics.drop()
                            onMove(item, at) -> haptics.drop()
                            else -> haptics.reject()
                        }
                        dragging = null
                        dragY = 0f
                    },
                    onDragCancel = { dragging = null; dragY = 0f },
                )
            }
        }
        val actions = if (item.isAnchor) {
            emptyList()
        } else {
            // A place counts among the other entries: before the one above, or after the one below.
            // From Later, Move earlier tries the end of the day.
            val at = place ?: (forecast.slots.size + 1)
            listOfNotNull(
                CustomAccessibilityAction(earlier) { onMove(item, at - 1); true }.takeIf { at > 0 },
                CustomAccessibilityAction(later) { onMove(item, at + 1); true }.takeIf { place != null && at + 1 < forecast.slots.size },
            )
        }
        Row(
            Modifier
                .onGloballyPositioned { c ->
                    val top = c.positionInRoot().y
                    bounds[item.id] = top..(top + c.size.height)
                }
                .fillMaxWidth()
                .heightIn(min = Tap.min)
                .zIndex(if (dragging == item.id) 1f else 0f)
                .graphicsLayer { translationY = if (dragging == item.id) dragY else 0f }
                // A button, like everything tappable: it opens the edit form.
                .clip(shape)
                .background(Ink.surface)
                .border(1.dp, if (dragging == item.id) Ink.accent else Ink.frame, shape)
                .then(drag)
                .clickable(onClickLabel = edit) { onEdit(item) }
                .semantics { customActions = actions }
                .padding(horizontal = Space.m, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(time.orEmpty(), style = Type.bodySmall, color = Ink.dim, modifier = Modifier.width(TIME_COLUMN))
            Text(
                item.title,
                style = Type.bodySmall.copy(fontWeight = if (item.isAnchor && item.wall == WallStrength.HARD) FontWeight.Bold else FontWeight.Normal),
                color = if (dragging == item.id) Ink.accent else Ink.text,
                modifier = Modifier.weight(1f, fill = false),
            )
            // A task's usual length, so whether it fits shows before it's dragged.
            if (!item.isAnchor) {
                Text(
                    stringResource(R.string.forecast_length, Formats.countdown(Duration.ofMinutes(item.durationMinutes.toLong()))),
                    style = Type.bodySmall,
                    color = Ink.dim,
                    modifier = Modifier.padding(start = Space.xs),
                )
            }
        }
    }

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.xs),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        forecast.slots.forEachIndexed { i, slot -> Entry(slot.item, Formats.clock(slot.at), i) }
        if (forecast.later.isNotEmpty()) {
            Spacer(Modifier.height(Space.inside))
            Label(stringResource(R.string.forecast_later))
            forecast.later.forEach { Entry(it, null, null) }
        }
    }
}

private val TIME_COLUMN = 104.dp
