package net.fallhammer.initiative.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
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

    // A choice under an open line; the line itself also says the chosen one.
    private fun choice(text: String) = compose.onNode(
        androidx.compose.ui.test.hasText(text) and androidx.compose.ui.test.SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.RadioButton,
        ),
    )

    private val day = LocalDate.of(2026, 10, 19)
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val parts = BoardParts(ApplicationProvider.getApplicationContext<Application>(), scope, show = {}, acted = {})

    private val template = Item(id = 1, title = "gym", kind = Kind.ANCHOR, start = day.atTime(7, 0), recurrence = Recurrence(Freq.DAILY), createdAt = day.atTime(6, 0))
    private val gym = Item(id = 2, title = "gym", kind = Kind.ANCHOR, start = day.atTime(7, 0), end = day.atTime(8, 0), templateId = 1, createdAt = day.atTime(6, 0))
    private val dentist = Item(id = 3, title = "dentist", kind = Kind.ANCHOR, start = day.atTime(15, 0), end = day.atTime(15, 30), createdAt = day.atTime(6, 0))
    private val edit = EditModel(parts, SavedStateHandle()) { listOf(template, gym, dentist) }

    @After fun stop() = scope.cancel()

    private val air = net.fallhammer.initiative.data.PeerRow(id = 7, peerId = "a", nickname = "Air", initial = "A", color = 0)

    private fun show(item: Item, peers: List<net.fallhammer.initiative.data.PeerRow> = emptyList()) {
        edit.open(item)
        compose.setContent {
            InitiativeTheme(Looks.SciFi) {
                val draft by edit.draft.collectAsStateWithLifecycle()
                draft?.let { EditScreen(it, edit, peers = peers) }
            }
        }
    }

    @Test fun aTaskCanBeSharedWithSomeonePaired() {
        show(dentist, listOf(air))
        compose.onNodeWithText("Shared with").assertDoesNotExist()
        compose.onNodeWithText("More").performScrollTo().tap()
        compose.onNodeWithText("Shared with").performScrollTo().tap()
        compose.onNodeWithText("Air").performScrollTo().tap()
        val shared = edit.draft.value!!
        assertEquals(7L, shared.pairId)
        assertTrue(shared.uid != null)
        // Shared, it can be just for them: off this board.
        compose.onNodeWithText("Just for Air, not on my board").performScrollTo().tap()
        assertTrue(edit.draft.value!!.forThem)
        // The line now says who, and More names it once folded.
        choice("Only me").performScrollTo().tap()
        assertEquals(null, edit.draft.value!!.uid)
        assertEquals(false, edit.draft.value!!.forThem)
        compose.onNodeWithText("Just for Air, not on my board").assertDoesNotExist()
    }

    @Test fun aChangeToOneTimeOfASeriesAsksOnSave() {
        show(gym)
        // Nothing changed: no question.
        compose.onNodeWithText("Starts").performScrollTo().assertIsDisplayed()
        edit.change { it.copy(wall = initiative.core.WallStrength.HARD) }
        compose.onNodeWithText("Save").tap()
        compose.onNodeWithText("Save changes to").assertIsDisplayed()
        compose.onNodeWithText("This time").assertIsDisplayed()
        compose.onNodeWithText("This and later").assertIsDisplayed()
        // The question's own Cancel, below the form's.
        compose.onAllNodesWithText("Cancel")[1].tap()
        compose.onNodeWithText("Save changes to").assertDoesNotExist()
        assertTrue(edit.isOpen)
    }

    @Test fun aTaskFromThemIsShownNotEdited() {
        show(dentist.copy(uid = "u-d", pairId = 7, owned = false, theirDone = true), listOf(air))
        compose.onNodeWithText("From Air").assertIsDisplayed()
        compose.onNodeWithText("Only Air can change this", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Shared · from Air. You: not yet. Air: done.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Done").assertIsDisplayed()
        compose.onNodeWithText("Remove for me").assertIsDisplayed()
        compose.onNodeWithText("Save").assertDoesNotExist()
    }

    @Test fun noLongerSharedItCanBeClaimed() {
        show(dentist.copy(uid = "u-d", pairId = null, owned = false))
        compose.onNodeWithText("Claim it to make it yours", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Claim").assertIsDisplayed()
    }

    @Test fun offersTheFormsParts() {
        show(dentist)
        compose.onNodeWithText("dentist").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Add a step").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Starts").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Repeats").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Remove").assertIsDisplayed()
        // The rare ones are folded under More.
        compose.onNodeWithText("Type").assertDoesNotExist()
        compose.onNodeWithText("Wall").assertDoesNotExist()
        compose.onNodeWithText("More").performScrollTo().tap()
        compose.onNodeWithText("Type").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Wall").performScrollTo().assertIsDisplayed()
    }

    @Test fun aTaskHasNoEffortAndOneLineOpensAtATime() {
        val laundry = Item(id = 4, title = "laundry", kind = Kind.FLOAT, durationMinutes = 30, createdAt = day.atTime(6, 0))
        val edit = EditModel(parts, SavedStateHandle()) { listOf(laundry) }
        edit.open(laundry)
        compose.setContent {
            InitiativeTheme(Looks.SciFi) {
                val draft by edit.draft.collectAsStateWithLifecycle()
                draft?.let { EditScreen(it, edit) }
            }
        }
        compose.onNodeWithText("Effort").assertDoesNotExist()
        compose.onNodeWithText("Quick").assertDoesNotExist()
        compose.onNodeWithText("How long").performScrollTo().tap()
        compose.onNodeWithText("45m").performScrollTo().tap()
        assertEquals(45, edit.draft.value!!.durationMinutes)
        // Opening Repeats folds How long.
        compose.onNodeWithText("Repeats").performScrollTo().tap()
        compose.onNodeWithText("1h 30m").assertDoesNotExist()
        compose.onNodeWithText("Every day").performScrollTo().assertIsDisplayed()
    }

    @Test fun moreNamesWhatWasChanged() {
        val laundry = Item(id = 4, title = "laundry", kind = Kind.FLOAT, priority = initiative.core.Priority.HIGH, createdAt = day.atTime(6, 0))
        val edit = EditModel(parts, SavedStateHandle()) { listOf(laundry) }
        edit.open(laundry)
        compose.setContent {
            InitiativeTheme(Looks.SciFi) {
                val draft by edit.draft.collectAsStateWithLifecycle()
                draft?.let { EditScreen(it, edit) }
            }
        }
        compose.onNodeWithText("High priority").performScrollTo().assertIsDisplayed()
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
        compose.onNodeWithText("Every day").assertDoesNotExist()
        compose.onNodeWithText("Repeats").performScrollTo().tap()
        compose.onNodeWithText("Every day").performScrollTo().tap()
        assertEquals(Recurrence(Freq.DAILY), edit.rule.value)
    }

    @Test fun removeOnASeriesAsksWhich() {
        show(gym)
        compose.onNodeWithText("Remove").tap()
        compose.onNodeWithText("Remove which?").assertIsDisplayed()
        compose.onNodeWithText("Just this time").assertIsDisplayed()
        compose.onNodeWithText("This and later").assertIsDisplayed()
        compose.onNodeWithText("Every time").assertIsDisplayed()
    }

    @Test fun cancelClosesWithoutSaving() {
        show(dentist)
        compose.onNodeWithText("Cancel").tap()
        assertFalse(edit.isOpen)
    }
}
