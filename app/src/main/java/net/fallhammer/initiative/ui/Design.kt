package net.fallhammer.initiative.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The design system. Screens are assembled from these tokens and components and
 * never set sizes, colors, or spacing by hand. Colors and shapes live in the
 * current theme (Theme.kt), read through Ink and LocalLook.
 */

/** Spacing steps. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /*
     * Layout rhythm. Panels group by distance: a wide gap between zones (what's
     * Now, the day, getting to things), a narrow one inside a zone. Form fields
     * sit [l] apart. Touch targets keep at least [inside] between them.
     */
    val zone = xl
    val inside = s
}

/** Touch-target heights. */
object Tap {
    val min = 48.dp
    val primary = 64.dp
    val alarm = 88.dp
}


/** The type scale. Body text never goes below 18 sp; captions stay at 16 sp. */
object Type {
    private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = Atkinson, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

    val hero = style(48, 54, FontWeight.Bold)
    val display = style(40, 46, FontWeight.Bold)
    val title = style(28, 34, FontWeight.Bold)
    val heading = style(22, 28)
    val body = style(20, 26)
    val bodySmall = style(18, 24)
    val button = style(20, 24, FontWeight.Bold)
    val caption = style(16, 22)
    val label = style(16, 22, FontWeight.Bold)
}

/** Every screen's frame: background, system bars, keyboard space, and margins. */
@Composable
fun Screen(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxSize()
            .background(Ink.bg)
            .then(if (LocalLook.current.hexGrid) Modifier.hexGrid(Ink.pattern) else Modifier)
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = Space.l, vertical = Space.m),
        content = content,
    )
}

/**
 * A one-line panel, framed like a [Card], with the same side padding so its text
 * lines up with the card's. The folded day panel is one.
 */
@Composable
fun Strip(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val look = LocalLook.current
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Tap.min)
            .clip(look.cardShape)
            .background(Ink.surface)
            .then(if (look.framed) Modifier.border(Hairline, Ink.frame, look.cardShape) else Modifier)
            .padding(horizontal = Space.xl, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * A slim pull tab along the bottom edge: just a mark, with a full-size touch
 * target and a spoken [label].
 */
@Composable
fun Handle(mark: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = Tap.min)
            .clickable(onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(mark, style = Type.title, color = Ink.accent, modifier = Modifier.clearAndSetSemantics { })
    }
}

/** The raised surface the Now card and alarm sit on. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    arrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val look = LocalLook.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(look.cardShape)
            .background(Ink.surface)
            .then(if (look.framed) Modifier.border(Hairline, Ink.frame, look.cardShape) else Modifier)
            .padding(Space.xl),
        verticalArrangement = arrangement,
        content = content,
    )
}

private val Hairline = 1.dp

/** A faint hexagon grid behind a screen, as in the sci-fi quest log. Built once per size. */
private fun Modifier.hexGrid(color: Color): Modifier = drawWithCache {
    val r = 22.dp.toPx()
    val w = r * 1.5f
    val h = sqrt(3f) * r
    val path = Path()
    var col = 0
    var x = 0f
    while (x < size.width + r) {
        var y = if (col % 2 == 0) 0f else h / 2
        while (y < size.height + h) {
            for (i in 0..6) {
                val a = Math.toRadians(60.0 * i).toFloat()
                val px = x + r * cos(a)
                val py = y + r * sin(a)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            y += h
        }
        x += w
        col++
    }
    onDrawBehind { drawPath(path, color, style = Stroke(1.dp.toPx())) }
}

/** The small "Now" over a card's title. Sentence case: all caps is hard to read. */
@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Type.label, color = Ink.label, modifier = modifier)
}

/** A screen or card title, announced as a heading by TalkBack. */
@Composable
fun Title(text: String, style: TextStyle = Type.display, modifier: Modifier = Modifier) {
    Text(text, style = style, color = Ink.text, modifier = modifier.semantics { heading() })
}

@Composable
fun Body(text: String, modifier: Modifier = Modifier, color: Color = Ink.dim, style: TextStyle = Type.body) {
    Text(text, style = style, color = color, modifier = modifier)
}

/** The one filled action on a screen. */
@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = Tap.primary) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = height),
        shape = LocalLook.current.buttonShape,
        contentPadding = ButtonPadding,
        colors = ButtonDefaults.buttonColors(containerColor = Ink.accent, contentColor = Ink.onAccent),
    ) { ButtonText(label) }
}

/** Every other action. */
@Composable
fun SecondaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = Tap.primary) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = height),
        shape = LocalLook.current.buttonShape,
        border = BorderStroke(Hairline, Ink.frame),
        contentPadding = ButtonPadding,
    ) {
        ButtonText(label, color = Ink.text, weight = FontWeight.Normal)
    }
}

/** A text-only action, for Close, Cancel, and Apps. */
@Composable
fun QuietButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Ink.dim) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = Tap.min)) {
        Text(label, style = Type.caption, color = color)
    }
}

private val ButtonPadding = PaddingValues(horizontal = Space.s, vertical = Space.s)

@Composable
private fun ButtonText(label: String, color: Color = Color.Unspecified, weight: FontWeight = FontWeight.Bold) {
    // Wraps at word breaks rather than clipping when the system font is large.
    Text(label, style = Type.button.copy(fontWeight = weight), color = color, textAlign = TextAlign.Center, maxLines = 2)
}

/**
 * A row of actions. At large system font sizes the buttons stack full-width,
 * so no label is ever cut mid-word.
 */
@Composable
fun ActionRow(vararg actions: Action) {
    if (LocalDensity.current.fontScale > LARGE_FONT) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.fillMaxWidth()) {
            actions.forEach { it.content(Modifier.fillMaxWidth()) }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.fillMaxWidth()) {
            actions.forEach { it.content(Modifier.weight(it.weight)) }
        }
    }
}

/** One button in an [ActionRow]; [weight] sets its share of the row. */
class Action(val weight: Float = 1f, val content: @Composable (Modifier) -> Unit)

private const val LARGE_FONT = 1.15f

/** One accent line, such as the next setup step. */
@Composable
fun PromptLine(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = Tap.min)) {
        Text(text, style = Type.caption, color = Ink.accent, modifier = Modifier.fillMaxWidth())
    }
}

/** A labelled on/off setting. The whole row is one switch for TalkBack and touch. */
@Composable
fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Tap.min)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Type.bodySmall, color = Ink.text, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = Ink.accent, checkedThumbColor = Ink.onAccent),
        )
    }
}

/**
 * A labelled pick-one setting, such as the theme. Each option is a radio button
 * for TalkBack. Options wrap onto more lines when they don't fit.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceRow(label: String, options: List<String>, chosen: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().selectableGroup()) {
        Text(label, style = Type.bodySmall, color = Ink.text)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.s),
            modifier = Modifier.padding(top = Space.xs),
        ) {
            options.forEachIndexed { i, option ->
                val mod = Modifier.semantics { role = Role.RadioButton; selected = i == chosen }
                if (i == chosen) {
                    PrimaryButton(option, { onSelect(i) }, mod, height = Tap.min)
                } else {
                    SecondaryButton(option, { onSelect(i) }, mod, height = Tap.min)
                }
            }
        }
    }
}

/** A short note after an action, with Undo when the action can be taken back. */
@Composable
fun FlashLine(text: String, undoLabel: String?, onUndo: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = Type.bodySmall, color = Ink.accent, modifier = Modifier.weight(1f))
        if (undoLabel != null) QuietButton(undoLabel, onUndo, color = Ink.text)
    }
}
