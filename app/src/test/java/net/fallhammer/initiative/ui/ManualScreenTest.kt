package net.fallhammer.initiative.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The manual: each part opens to what you do there, one part at a time. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ManualScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun SemanticsNodeInteraction.tap() = performSemanticsAction(SemanticsActions.OnClick)

    private var closed = false

    private fun show() {
        compose.setContent { InitiativeTheme(Looks.SciFi) { ManualScreen(onClose = { closed = true }) } }
    }

    // Each part, and one thing it explains.
    private val parts = listOf(
        "The Now card" to "Hold the card",
        "Today's list" to "During an appointment",
        "Plan ahead" to "Day report",
        "Typing a task" to "Names you've used",
        "Alarms" to "Quiet hours",
        "Pairs" to "Ping",
        "Looks" to "Idle effects",
        "Backup" to "Restore",
        "Getting around" to "Previously on",
    )

    @Test fun everyPartOpensToWhatItExplains() {
        show()
        parts.forEach { (part, entry) ->
            compose.onNodeWithText(part).performScrollTo().tap()
            compose.onNodeWithText(entry).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(part).performScrollTo().tap()
            compose.onNodeWithText(entry).assertDoesNotExist()
        }
    }

    @Test fun onePartAtATime() {
        show()
        compose.onNodeWithText("The Now card").performScrollTo().tap()
        compose.onNodeWithText("Alarms").performScrollTo().tap()
        compose.onNodeWithText("Hold the card").assertDoesNotExist()
        compose.onNodeWithText("Quiet hours").performScrollTo().assertIsDisplayed()
    }

    @Test fun closeCloses() {
        show()
        compose.onNodeWithText("Close").tap()
        assertTrue(closed)
    }
}
