package net.fallhammer.initiative.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import initiative.core.Freq
import initiative.core.Item
import initiative.core.Kind
import initiative.core.Recurrence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The edit form, checked as a screen: what it offers, Repeats, Remove on a
 * series, and Cancel. Saving writes the database; the emulator covers that.
 */
@RunWith(RobolectricTestRunner::class)
// A phone-sized screen, as on the phone; lower parts still scroll.
@Config(sdk = [34], qualifiers = "w412dp-h915dp")
class EditScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun SemanticsNodeInteraction.tap() = performSemanticsAction(SemanticsActions.OnClick)

    private val day = LocalDate.of(2026, 10, 19)
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val parts = BoardParts(ApplicationProvider.getApplicationContext<Application>(), scope, show = {}, acted = {})

    private val template = Item(id = 1, title = "gym", kind = Kind.ANCHOR, start = day.atTime(7, 0), recurrence = Recurrence(Freq.DAILY), createdAt = day.atTime(6, 0))
    private val gym = Item(id = 2, title = "gym", kind = Kind.ANCHOR, start = day.atTime(7, 0), end = day.atTime(8, 0), templateId = 1, createdAt = day.atTime(6, 0))
    private val dentist = Item(id = 3, title = "dentist", kind = Kind.ANCHOR, start = day.atTime(15, 0), end = day.atTime(15, 30), createdAt = day.atTime(6, 0))
    private val edit = EditModel(parts, SavedStateHandle()) { listOf(template, gym, dentist) }

    @After fun stop() = scope.cancel()

    private fun show(item: Item) {
        edit.open(item)
        compose.setContent {
            InitiativeTheme(Looks.SciFi) {
                val draft by edit.draft.collectAsStateWithLifecycle()
                draft?.let { EditScreen(it, edit) }
            }
        }
    }

    @Test fun offersTheFormsParts() {
        show(dentist)
        compose.onNodeWithText("dentist").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Type").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Add a step").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Remove").assertIsDisplayed()
    }

    @Test fun aStepIsOneLineWithAWaitAndARemove() {
        show(dentist.copy(steps = listOf(initiative.core.Step("find the card"), initiative.core.Step("call"))))
        compose.onNodeWithContentDescription("Add a wait after step 1").performScrollTo().tap()
        compose.onNodeWithContentDescription("Wait 15m after step 1, change").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove step 2").performScrollTo().tap()
        compose.onNodeWithContentDescription("Remove step 2").assertDoesNotExist()
        // The step's text and its two buttons share a line.
        val field = compose.onNodeWithText("find the card").getUnclippedBoundsInRoot()
        val cross = compose.onNodeWithContentDescription("Remove step 1").getUnclippedBoundsInRoot()
        assertTrue("the buttons should sit beside the step", cross.top < field.bottom && cross.left > field.left)
    }

    @Test fun stepsLineUpWithOrWithoutAWait() {
        show(dentist.copy(steps = listOf(initiative.core.Step("find the card", waitMinutes = 90), initiative.core.Step("call"))))
        val set = compose.onNodeWithContentDescription("Wait 1h 30m after step 1, change").performScrollTo().getUnclippedBoundsInRoot()
        val unset = compose.onNodeWithContentDescription("Add a wait after step 2").performScrollTo().getUnclippedBoundsInRoot()
        assertEquals(set.left, unset.left)
        assertEquals(set.right, unset.right)
    }

    @Test fun everyDayMakesItRepeat() {
        show(dentist)
        compose.onNodeWithText("Every day").performScrollTo().tap()
        assertEquals(Recurrence(Freq.DAILY), edit.rule.value)
    }

    @Test fun removeOnASeriesAsksWhich() {
        show(gym)
        compose.onNodeWithText("Remove").tap()
        compose.onNodeWithText("Remove which?").assertIsDisplayed()
        compose.onNodeWithText("Just this time").assertIsDisplayed()
        // The scope row says it too: the question adds the second.
        compose.onAllNodesWithText("This and later").assertCountEquals(2)
    }

    @Test fun cancelClosesWithoutSaving() {
        show(dentist)
        compose.onNodeWithText("Cancel").tap()
        assertFalse(edit.isOpen)
    }
}
