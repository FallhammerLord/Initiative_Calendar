package net.fallhammer.initiative.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The design system. Screens are assembled from these tokens and components and
 * never set sizes, colors, or spacing by hand. Colors live in Theme.kt (Ink).
 */

/** Spacing steps. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Touch-target heights. */
object Tap {
    val min = 48.dp
    val primary = 64.dp
    val alarm = 88.dp
}

/** Corner shapes. */
object Corner {
    val card = RoundedCornerShape(20.dp)
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
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = Space.l, vertical = Space.m),
        content = content,
    )
}

/** The raised surface the Now card and alarm sit on. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    arrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().clip(Corner.card).background(Ink.surface).padding(Space.xl),
        verticalArrangement = arrangement,
        content = content,
    )
}

/** The small "Now" over a card's title. Sentence case: all caps is hard to read. */
@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Type.label, color = Ink.dim, modifier = modifier)
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
        contentPadding = ButtonPadding,
        colors = ButtonDefaults.buttonColors(containerColor = Ink.accent, contentColor = Ink.bg),
    ) { ButtonText(label) }
}

/** Every other action. */
@Composable
fun SecondaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = Tap.primary) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = height), contentPadding = ButtonPadding) {
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
            colors = SwitchDefaults.colors(checkedTrackColor = Ink.accent, checkedThumbColor = Ink.bg),
        )
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
