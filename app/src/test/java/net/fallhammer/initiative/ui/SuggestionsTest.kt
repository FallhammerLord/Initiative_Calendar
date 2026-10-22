package net.fallhammer.initiative.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Names used before, offered while typing: tap fills, hold forgets with Undo. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SuggestionsTest {
    @get:Rule val compose = createComposeRule()

    private fun SemanticsNodeInteraction.tap() = performSemanticsAction(SemanticsActions.OnClick)
    private fun SemanticsNodeInteraction.hold() = performSemanticsAction(SemanticsActions.OnLongClick)

    private val names = listOf("Dinner with Sam", "Make dinner", "Dinner", "Call landlord")
    private val picked = mutableListOf<String>()
    private val forgot = mutableListOf<String>()
    private val back = mutableListOf<String>()

    private fun show(typed: String) {
        compose.setContent {
            InitiativeTheme(Looks.SciFi) {
                Suggestions(typed, names, onPick = { picked += it }, onForget = { forgot += it }, onUnforget = { back += it })
            }
        }
    }

    @Test fun offersNamesThatMatch() {
        show("din")
        compose.onNodeWithText("Dinner with Sam").assertIsDisplayed()
        compose.onNodeWithText("Make dinner").assertIsDisplayed()
        compose.onNodeWithText("Call landlord").assertDoesNotExist()
    }

    @Test fun nothingUnderTwoLetters() {
        show("d")
        compose.onNodeWithText("Dinner").assertDoesNotExist()
    }

    @Test fun tapUsesTheName() {
        show("cal")
        compose.onNodeWithText("Call landlord").tap()
        assertEquals(listOf("Call landlord"), picked)
    }

    @Test fun holdForgetsWithUndo() {
        show("din")
        compose.onNodeWithText("Make dinner").hold()
        assertEquals(listOf("Make dinner"), forgot)
        compose.onNodeWithText("Forgot \"Make dinner\"").assertIsDisplayed()
        compose.onNodeWithText("Undo").tap()
        assertEquals(listOf("Make dinner"), back)
    }
}
