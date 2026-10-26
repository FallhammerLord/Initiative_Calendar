package net.fallhammer.initiative.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.width
import initiative.core.Board
import initiative.core.Item
import initiative.core.Kind
import initiative.core.NowPick
import initiative.core.Status
import initiative.core.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * The Now card, as a screen: words first, and one row of buttons that turns
 * with the task. Up next: On it and Later. Once on it: Done, Later, Tomorrow,
 * and Focus (an anchor: Done and Later).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NowCardTest {
    @get:Rule val compose = createComposeRule()

    private fun SemanticsNodeInteraction.tap() = performSemanticsAction(SemanticsActions.OnClick)

    private val now = LocalDateTime.of(2026, 10, 29, 13, 18)
    private val reason = "High priority, fits before pickup"
    private val taxes = Item(id = 1, title = "taxes", kind = Kind.FLOAT, createdAt = now.minusDays(1))
    private val underWay = taxes.copy(status = Status.ACTIVE, activeSince = now.minusMinutes(6))
    private val dentist = Item(
        id = 2, title = "dentist", kind = Kind.ANCHOR, createdAt = now.minusDays(3),
        start = now.minusMinutes(5), end = now.plusMinutes(40),
    )

    private val tapped = mutableListOf<String>()

    private fun show(
        item: Item,
        fontScale: Float = 1f,
        modifier: Modifier = Modifier,
        why: String = reason,
        keep: Boolean = false,
        peers: List<net.fallhammer.initiative.data.PeerRow> = emptyList(),
    ) {
        val ui = BoardUi(now = now, board = Board(NowPick(item, why, keep), next = null))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                InitiativeTheme(Looks.SciFi) {
                    NowCard(
                        modifier = modifier,
                        ui = ui,
                        flash = null,
                        onDone = { tapped += "done" },
                        onSkip = { tapped += "later" },
                        onTomorrow = { tapped += "tomorrow" },
                        onUndo = {},
                        onFocus = { tapped += "focus" },
                        onEdit = { tapped += "edit" },
                        onStep = { _, _ -> },
                        compact = false,
                        onOnIt = { tapped += "on it" },
                        peers = peers,
                    )
                }
            }
        }
    }

    @Test fun upNextOffersOnItAndLater() {
        show(taxes)
        compose.onNodeWithText("On it").assertIsDisplayed()
        compose.onNodeWithText("Later").assertIsDisplayed()
        compose.onNodeWithText("Done").assertDoesNotExist()
        compose.onNodeWithText("Tomorrow").assertDoesNotExist()
        compose.onNodeWithText("Focus").assertDoesNotExist()
    }

    @Test fun onItAndLaterDoWhatTheySay() {
        show(taxes)
        compose.onNodeWithText("On it").tap()
        compose.onNodeWithText("Later").tap()
        assertEquals(listOf("on it", "later"), tapped)
    }

    @Test fun onceOnItTheRowTurns() {
        show(underWay)
        compose.onNodeWithText("Done").tap()
        compose.onNodeWithText("Later").tap()
        compose.onNodeWithText("Tomorrow").tap()
        compose.onNodeWithText("Focus").tap()
        assertEquals(listOf("done", "later", "tomorrow", "focus"), tapped)
        compose.onNodeWithText("On it").assertDoesNotExist()
    }

    @Test fun onItSinceTakesTheReasonsPlace() {
        // The reason was seen and accepted with On it; the line says when instead.
        show(underWay)
        compose.onNodeWithText("On it since 1:12", substring = true).assertIsDisplayed()
        compose.onNodeWithText(reason, substring = true).assertDoesNotExist()
    }

    @Test fun aHeadsUpStaysOnceOnIt() {
        val warning = "Runs into pickup's warning at 2:45 PM"
        show(underWay, why = warning, keep = true)
        compose.onNodeWithText("On it since 1:12", substring = true).assertIsDisplayed()
        compose.onNodeWithText(warning, substring = true).assertIsDisplayed()
    }

    @Test fun aSharedTaskShowsBothBoxes() {
        val air = net.fallhammer.initiative.data.PeerRow(id = 7, peerId = "a", nickname = "Air", initial = "A", color = 0)
        show(taxes.copy(uid = "u-t", pairId = 7, owned = false, theirDone = true), peers = listOf(air))
        compose.onNodeWithContentDescription("Shared · from Air. You: not yet. Air: done.").assertIsDisplayed()
    }

    @Test fun tickedStepsSinkBelowOpenOnes() {
        show(taxes.copy(steps = listOf(Step("find the forms", done = true), Step("sign them"), Step("post them"))))
        val ticked = compose.onNodeWithText("find the forms").getUnclippedBoundsInRoot()
        val open = compose.onNodeWithText("post them").getUnclippedBoundsInRoot()
        assertTrue("ticked steps should sit below open ones", open.top < ticked.top)
    }

    @Test fun aTickedStepHoldsItsPlaceThenSinks() {
        // Seen ticked where it was tapped first, then slid below the open ones.
        var item by mutableStateOf(taxes.copy(steps = listOf(Step("find the forms"), Step("sign them"))))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            InitiativeTheme(Looks.SciFi) {
                NowCard(
                    modifier = Modifier,
                    ui = BoardUi(now = now, board = Board(NowPick(item, reason), next = null)),
                    flash = null, onDone = {}, onSkip = {}, onTomorrow = {}, onUndo = {}, onFocus = {}, onEdit = {},
                    onStep = { it, i -> item = it.copy(steps = it.steps.mapIndexed { j, s -> if (j == i) s.copy(done = !s.done) else s }) },
                    compact = false,
                )
            }
        }
        fun top(text: String) = compose.onNodeWithText(text).getUnclippedBoundsInRoot().top
        compose.onNodeWithText("find the forms").tap()
        // Past a slide's length but inside the hold: still in place, so it's the hold that keeps it there.
        compose.mainClock.advanceTimeBy(400)
        assertTrue("a ticked step should hold its place at first", top("find the forms") < top("sign them"))
        compose.mainClock.advanceTimeBy(2_000)
        assertTrue("then sink below the open one", top("sign them") < top("find the forms"))
    }

    @Test fun anchorUnderWayOffersDoneAndLater() {
        show(dentist.copy(status = Status.ACTIVE, activeSince = now))
        compose.onNodeWithText("Done").assertIsDisplayed()
        compose.onNodeWithText("Later").assertIsDisplayed()
        compose.onNodeWithText("Tomorrow").assertDoesNotExist()
        compose.onNodeWithText("Focus").assertDoesNotExist()
    }

    @Test fun noNowLabelButTalkBackHearsNow() {
        show(taxes)
        compose.onNodeWithText("Now").assertDoesNotExist()
        compose.onNodeWithContentDescription("Now: taxes").assertIsDisplayed()
    }

    @Test fun titleAndReasonStayWhileTheStepsScroll() {
        val steps = (1..14).map { Step("step $it") }
        show(taxes.copy(steps = steps), modifier = Modifier.height(360.dp))
        compose.onNodeWithText("step 14").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Now: taxes").assertIsDisplayed()
        compose.onNodeWithText(reason).assertIsDisplayed()
    }

    @Test fun reasonSitsAboveTheSteps() {
        show(taxes.copy(steps = listOf(Step("find the forms"), Step("sign them"))))
        val said = compose.onNodeWithText(reason).getUnclippedBoundsInRoot()
        val step = compose.onNodeWithText("find the forms").getUnclippedBoundsInRoot()
        assertTrue("the reason should come before the steps", said.top < step.top)
    }

    @Test fun largeFontGivesDoneItsOwnRow() {
        show(underWay, fontScale = 2f)
        val done = compose.onNodeWithText("Done").getUnclippedBoundsInRoot()
        val later = compose.onNodeWithText("Later").getUnclippedBoundsInRoot()
        assertTrue("Done should sit above the rest", done.bottom <= later.top)
        assertTrue("Done should take the whole row", done.width > later.width)
    }
}
