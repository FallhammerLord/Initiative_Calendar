package net.fallhammer.initiative.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import initiative.core.Formats
import initiative.core.Item
import initiative.core.SharedTasks
import net.fallhammer.initiative.R
import net.fallhammer.initiative.data.PeerRow

/** Who a shared task is shared with, by name; a fallback once they're no longer paired. */
@Composable
fun sharedName(item: Item, peers: List<PeerRow>): String =
    peers.firstOrNull { it.id == item.pairId }?.nickname ?: stringResource(R.string.shared_someone)

/**
 * A shared task's line: whose it is, and both boxes, yours and theirs, each
 * plain: ticked or not, never late or counted.
 */
@Composable
fun SharedBoxes(item: Item, name: String, color: Color, modifier: Modifier = Modifier) {
    val tag = if (item.fromThem) stringResource(R.string.shared_from_tag, name) else stringResource(R.string.shared_with_tag, name)
    val mine = stringResource(if (item.status == initiative.core.Status.DONE) R.string.shared_box_you_done else R.string.shared_box_you)
    val theirs = stringResource(if (item.theirDone) R.string.shared_box_them_done else R.string.shared_box_them, name)
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = "$tag. $mine. $theirs." }) {
        Text(tag, style = Type.caption, color = color, modifier = Modifier.clearAndSetSemantics { })
        Spacer(Modifier.height(Space.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.l), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clearAndSetSemantics { }) {
            ShareBox(item.status == initiative.core.Status.DONE, Ink.accent, stringResource(R.string.shared_you))
            ShareBox(item.theirDone, color, name)
        }
    }
}

@Composable
private fun ShareBox(ticked: Boolean, color: Color, label: String) {
    val mark = Ink.onAccent
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(BOX)
                .border(BOX_EDGE, color)
                .then(if (ticked) Modifier.background(color) else Modifier)
                .drawBehind { if (ticked) tick(mark) },
        )
        Text(label, style = Type.bodySmall, color = Ink.text, modifier = Modifier.padding(start = Space.xs))
    }
}

/**
 * A shared task from someone else: theirs to change, so shown as it is, with
 * what can be done here. Done, or set aside for yourself, while shared; Claim
 * or Remove once no longer shared.
 */
@Composable
fun SharedFromThem(draft: Item, peers: List<PeerRow>, edit: EditModel) {
    val name = sharedName(draft, peers)
    val color = PairColors.of(peers.firstOrNull { it.id == draft.pairId }?.color ?: 0, LocalLook.current.palette)
    val claimable = SharedTasks.claimable(draft)
    Screen {
        Strip {
            Label(stringResource(R.string.shared_from, name), Modifier.weight(1f))
            QuietButton(stringResource(R.string.edit_cancel), edit::cancel)
        }
        Spacer(Modifier.height(Space.panel))
        Card(Modifier.weight(1f, fill = false)) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Title(draft.title)
                when {
                    draft.isAnchor && draft.start != null -> Body(Formats.anchorLine(draft), style = Type.bodySmall)
                    !draft.isMark -> Body(Formats.countdown(java.time.Duration.ofMinutes(draft.durationMinutes.toLong())), style = Type.bodySmall)
                }
                if (draft.notes.isNotBlank()) {
                    Spacer(Modifier.height(Space.inside))
                    Body(draft.notes)
                }
                draft.steps.forEach { step ->
                    Text(
                        stringResource(if (step.done) R.string.log_step_done else R.string.log_step_open, step.title),
                        style = Type.body,
                        color = if (step.done) Ink.dim else Ink.text,
                    )
                }
                Spacer(Modifier.height(Space.s))
                if (claimable) {
                    Body(stringResource(R.string.shared_claimable, name), style = Type.bodySmall)
                } else {
                    SharedBoxes(draft, name, color)
                    Spacer(Modifier.height(Space.inside))
                    Body(stringResource(R.string.shared_only_owner, name), style = Type.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(Space.zone))
        when {
            claimable -> ActionRow(
                Action(2f) { PrimaryButton(stringResource(R.string.shared_claim), edit::claim, it) },
                Action { SecondaryButton(stringResource(R.string.edit_remove), { edit.remove() }, it) },
            )
            draft.isOpen -> ActionRow(
                Action(2f) { PrimaryButton(stringResource(R.string.edit_done), { edit.done(draft.title, draft.notes, draft.steps) }, it) },
                Action { SecondaryButton(stringResource(R.string.shared_remove_mine), { edit.remove() }, it) },
            )
            else -> ActionRow(Action { SecondaryButton(stringResource(R.string.edit_put_back), edit::putBack, it) })
        }
    }
}

private val BOX = Space.l
private val BOX_EDGE = 2.dp
