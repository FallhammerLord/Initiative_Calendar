package net.fallhammer.initiative.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import initiative.core.Formats
import initiative.core.Kind
import initiative.core.Peek
import initiative.core.ShareLevel
import initiative.core.Shareable
import net.fallhammer.initiative.R
import net.fallhammer.initiative.data.PeerRow
import java.time.LocalDate
import java.time.LocalDateTime

/** A paired person's badge: their initial on their color. */
@Composable
fun PairBadge(peer: PeerRow, modifier: Modifier = Modifier) {
    val palette = LocalLook.current.palette
    Box(
        modifier
            .size(BADGE)
            .clip(CircleShape)
            .background(PairColors.of(peer.color, palette))
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(peer.initial, style = Type.label, color = PairColors.ink(palette))
    }
}

/** The light: lit, in the person's color, for a fresh peek; a hollow ring otherwise. */
@Composable
fun PairLight(peer: PeerRow, lit: Boolean) {
    val color = PairColors.of(peer.color, LocalLook.current.palette)
    val dim = Ink.dim
    Box(
        Modifier
            .size(LIGHT)
            .clearAndSetSemantics { }
            .drawBehind {
                val r = size.minDimension / 2
                if (lit) {
                    drawCircle(color.copy(alpha = GLOW_ALPHA), r)
                    drawCircle(color, r * 0.6f)
                } else {
                    drawCircle(dim, r * 0.55f, style = Stroke(1.5.dp.toPx()))
                }
            },
    )
}

/** "now", "seen at 2:10", or "seen Tuesday": how fresh a person's last peek is. */
@Composable
fun seenText(peek: Peek?, now: LocalDateTime): String = when {
    peek == null -> stringResource(R.string.pair_not_seen)
    peek.lit(now) -> stringResource(R.string.pair_now)
    peek.at.toLocalDate() == now.toLocalDate() -> stringResource(R.string.pair_seen_at, Formats.clock(peek.at))
    else -> stringResource(R.string.pair_seen_on, Formats.day(peek.at.toLocalDate()))
}

@Composable
fun levelText(level: ShareLevel): String = stringResource(
    when (level) {
        ShareLevel.EVERYTHING -> R.string.share_everything
        ShareLevel.TICKED -> R.string.share_ticked
        ShareLevel.NOTHING -> R.string.share_nothing
    },
)

fun levelOf(peer: PeerRow): ShareLevel = runCatching { ShareLevel.valueOf(peer.level) }.getOrDefault(ShareLevel.TICKED)

/** One person in the Pairs list: badge, nickname, light and freshness, and what's shared. Opens their page. */
@Composable
fun PairRow(peer: PeerRow, peek: Peek?, now: LocalDateTime, onOpen: () -> Unit) {
    val lit = peek?.lit(now) == true
    val seen = seenText(peek, now)
    val sharing = stringResource(R.string.pair_sharing, levelText(levelOf(peer)))
    val shape = LocalLook.current.buttonShape
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Tap.primary)
            .clip(shape)
            .background(Ink.surface)
            .border(1.dp, Ink.frame, shape)
            .clickable(onClickLabel = stringResource(R.string.pair_open)) { onOpen() }
            .semantics { contentDescription = "${peer.nickname}, $seen. $sharing" }
            .padding(horizontal = Space.l, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PairBadge(peer)
        Column(Modifier.weight(1f).padding(start = Space.m).clearAndSetSemantics { }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                Text(peer.nickname, style = Type.bodySmall.copy(fontWeight = FontWeight.Bold), color = Ink.text)
                PairLight(peer, lit)
                Text(seen, style = Type.caption, color = Ink.dim)
            }
            Text(sharing, style = Type.caption, color = Ink.dim)
        }
    }
}

/**
 * A paired person's page: nickname and color, what's shared with them (ticked
 * here, the list being exactly what leaves the phone), what of theirs shows on
 * the board, the day report, and Unpair.
 */
@Composable
fun PairScreen(
    peer: PeerRow,
    peek: Peek?,
    now: LocalDateTime,
    shareable: List<Shareable>,
    ticked: Set<Long>,
    pairs: PairsModel,
    onClose: () -> Unit,
) {
    var name by rememberSaveable(peer.id) { mutableStateOf(peer.nickname) }
    var unpairing by rememberSaveable(peer.id) { mutableStateOf(false) }
    val level = levelOf(peer)
    val palette = LocalLook.current.palette
    Screen {
        Strip {
            Title(peer.nickname, style = Type.title, modifier = Modifier.weight(1f))
            QuietButton(stringResource(R.string.pair_back), onClose)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(Space.panel))
            Card(inner = Space.l) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    PairBadge(peer)
                    PairLight(peer, peek?.lit(now) == true)
                    Body(seenText(peek, now), style = Type.bodySmall)
                }
                Spacer(Modifier.height(Space.m))
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) pairs.rename(peer, it.trim())
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.pair_nickname), style = Type.bodySmall) },
                    textStyle = Type.body.copy(color = Ink.text),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Space.m))
                Body(stringResource(R.string.pair_color), style = Type.bodySmall, color = Ink.text)
                Spacer(Modifier.height(Space.xs))
                val names = listOf(R.string.pair_color_amber, R.string.pair_color_pink, R.string.pair_color_green, R.string.pair_color_lilac, R.string.pair_color_sky)
                    .map { stringResource(it) }
                ColorChoice((0 until PairColors.count).map { PairColors.of(it, palette) }, names, peer.color) { pairs.setColor(peer, it) }
            }
            Spacer(Modifier.height(Space.panel))
            Card(inner = Space.l) {
                Label(stringResource(R.string.pair_share_heading, peer.nickname))
                Spacer(Modifier.height(Space.xs))
                ChoiceRow(null, ShareLevel.entries.map { levelText(it) }, ShareLevel.entries.indexOf(level)) {
                    pairs.setLevel(peer, ShareLevel.entries[it])
                }
                Body(
                    stringResource(
                        when (level) {
                            ShareLevel.EVERYTHING -> R.string.share_everything_hint
                            ShareLevel.TICKED -> R.string.share_ticked_hint
                            ShareLevel.NOTHING -> R.string.share_nothing_hint
                        },
                        peer.nickname,
                    ),
                    style = Type.bodySmall,
                )
                if (level != ShareLevel.NOTHING) {
                    Spacer(Modifier.height(Space.s))
                    Label(stringResource(R.string.pair_sees, peer.nickname))
                    if (shareable.isEmpty()) Body(stringResource(R.string.pair_sees_none), style = Type.bodySmall)
                    shareable.forEach { s ->
                        val whenText = whenOf(s, now.toLocalDate())
                        if (level == ShareLevel.TICKED) {
                            CheckRow(s.shared.title, whenText, s.item.id in ticked) { pairs.tick(peer.id, s.item.id, it) }
                        } else {
                            Column(Modifier.fillMaxWidth().heightIn(min = Tap.min).padding(vertical = Space.xs)) {
                                Text(s.shared.title, style = Type.bodySmall, color = Ink.text)
                                Text(whenText, style = Type.caption, color = Ink.dim)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.panel))
            Card(inner = Space.l) {
                Label(stringResource(R.string.pair_show_heading))
                SettingSwitch(stringResource(R.string.pair_show_mark), peer.showMark) { pairs.setShowMark(peer, it) }
                SettingSwitch(stringResource(R.string.pair_show_line), peer.showLine) { pairs.setShowLine(peer, it) }
                SettingSwitch(stringResource(R.string.pair_show_marks), peer.showMarks) { pairs.setShowMarks(peer, it) }
                Spacer(Modifier.height(Space.s))
                SettingSwitch(stringResource(R.string.pair_share_report), peer.shareReport) { pairs.setShareReport(peer, it) }
            }
            Spacer(Modifier.height(Space.panel))
            Card(inner = Space.l) {
                if (!unpairing) {
                    SecondaryButton(stringResource(R.string.pair_unpair), { unpairing = true }, Modifier.fillMaxWidth(), height = Tap.min)
                } else {
                    Body(stringResource(R.string.pair_unpair_ask, peer.nickname), color = Ink.text)
                    Spacer(Modifier.height(Space.s))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.fillMaxWidth()) {
                        SecondaryButton(stringResource(R.string.pair_unpair_yes), { pairs.unpair(peer.id); onClose() }, Modifier.weight(1f), height = Tap.min)
                        QuietButton(stringResource(R.string.pair_unpair_no), { unpairing = false })
                    }
                }
            }
        }
    }
}

/** When a shared entry is: its time today, or its day and time, or a marker's day. */
@Composable
private fun whenOf(s: Shareable, today: LocalDate): String {
    val day = s.shared.start.toLocalDate()
    return when {
        s.shared.kind == Kind.MARK -> stringResource(R.string.pair_when_day, Formats.day(day))
        day == today -> Formats.clock(s.shared.start)
        else -> stringResource(R.string.pair_when, Formats.day(day), Formats.clock(s.shared.start))
    }
}

private val BADGE = 32.dp
private val LIGHT = 16.dp
private const val GLOW_ALPHA = 0.3f
