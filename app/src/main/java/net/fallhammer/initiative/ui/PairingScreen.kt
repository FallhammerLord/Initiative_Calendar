package net.fallhammer.initiative.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import net.fallhammer.initiative.R

/**
 * Pairing, in person: both phones open Pair, find each other, and show the
 * same 6-digit code. Each person checks it matches and taps Yes on their own
 * phone; then they name each other. One primary button at each step.
 */
@Composable
fun PairingScreen(
    pairing: Pairing,
    onYes: () -> Unit,
    onNo: () -> Unit,
    onName: (Long, String) -> Unit,
    onAgain: () -> Unit,
    onClose: () -> Unit,
) {
    Screen {
        Strip {
            Title(stringResource(R.string.pairing_title), style = Type.title, modifier = Modifier.weight(1f))
            QuietButton(stringResource(R.string.pairing_cancel), onClose)
        }
        Spacer(Modifier.height(Space.panel))
        Card(inner = Space.l) {
            when (pairing) {
                Pairing.Searching -> {
                    Body(stringResource(R.string.pairing_hold), color = Ink.text)
                    Spacer(Modifier.height(Space.s))
                    Body(stringResource(R.string.pairing_searching), style = Type.bodySmall)
                }
                is Pairing.Code -> {
                    Body(stringResource(R.string.pairing_match), color = Ink.text)
                    Spacer(Modifier.height(Space.m))
                    // Spaced digits, read one by one by TalkBack.
                    Text(
                        pairing.code.chunked(3).joinToString("  "),
                        style = Type.title,
                        color = Ink.accent,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = pairing.code.toList().joinToString(" ") },
                    )
                    Spacer(Modifier.height(Space.m))
                    ActionRow(
                        Action(2f) { PrimaryButton(stringResource(R.string.pairing_yes), onYes, it) },
                        Action { SecondaryButton(stringResource(R.string.pairing_no), onNo, it) },
                    )
                }
                Pairing.Waiting -> Body(stringResource(R.string.pairing_waiting), color = Ink.text)
                is Pairing.Naming -> {
                    var name by rememberSaveable(pairing.peer) { mutableStateOf("") }
                    Body(stringResource(R.string.pairing_paired), color = Ink.text)
                    Spacer(Modifier.height(Space.s))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.pair_nickname), style = Type.bodySmall) },
                        textStyle = Type.body.copy(color = Ink.text),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Space.s))
                    Body(stringResource(R.string.pairing_nothing_yet), style = Type.bodySmall)
                    Spacer(Modifier.height(Space.m))
                    PrimaryButton(stringResource(R.string.pairing_done), { onName(pairing.peer, name) }, Modifier.fillMaxWidth())
                }
                is Pairing.Failed -> {
                    Body(stringResource(pairing.reason), color = Ink.text)
                    Spacer(Modifier.height(Space.m))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.fillMaxWidth()) {
                        PrimaryButton(stringResource(R.string.pairing_again), onAgain, Modifier.weight(1f))
                        QuietButton(stringResource(R.string.pairing_close), onClose)
                    }
                }
            }
        }
    }
}
