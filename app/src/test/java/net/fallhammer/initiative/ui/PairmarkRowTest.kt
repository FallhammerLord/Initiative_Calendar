package net.fallhammer.initiative.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import initiative.core.Kind
import initiative.core.Peek
import initiative.core.Shared
import net.fallhammer.initiative.data.PeerRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/** The pairmark row, as a screen: the front person, tabs for the rest, and pairs on/off. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PairmarkRowTest {
    @get:Rule val compose = createComposeRule()

    private fun SemanticsNodeInteraction.tap() = performSemanticsAction(SemanticsActions.OnClick)

    private val now = LocalDateTime.of(2026, 10, 29, 12, 0)
    private val partner = PairView(
        PeerRow(id = 1, peerId = "a", nickname = "Partner", initial = "P", color = 0),
        Peek(now.minusMinutes(1), listOf(Shared(Kind.FLOAT, "groceries", now.minusMinutes(10), now.plusMinutes(20)))),
    )
    private val kid = PairView(
        PeerRow(id = 2, peerId = "b", nickname = "Kid", initial = "K", color = 1),
        Peek(now.minusHours(2), listOf(Shared(Kind.ANCHOR, "school", now.minusHours(3), now.plusHours(2)))),
    )
    private var front = -1L
    private var shown: Boolean? = null

    private fun show(pairsOn: Boolean = true) {
        compose.setContent {
            InitiativeTheme(Looks.SciFi) {
                PairmarkRow(listOf(partner, kid), front = 1, now = now, shown = pairsOn, onFront = { front = it }, onPing = {}, onShown = { shown = it })
            }
        }
    }

    @Test fun frontSaysWhatTheyreDoing() {
        show()
        // Lit (a minute old): what they're doing, plainly.
        compose.onNodeWithContentDescription("Partner, now: groceries").assertIsDisplayed()
    }

    @Test fun aTabBringsSomeoneForward() {
        show()
        compose.onNodeWithContentDescription("Kid, ", substring = true).tap()
        assertEquals(2L, front)
    }

    @Test fun hideAndShow() {
        show()
        compose.onNodeWithContentDescription("Hide pairs").tap()
        assertEquals(false, shown)
    }

    @Test fun hiddenLeavesOnlyTheButtons() {
        show(pairsOn = false)
        compose.onNodeWithContentDescription("Partner, now: groceries").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show pairs").assertIsDisplayed()
        compose.onNodeWithContentDescription("Ping").assertIsDisplayed()
    }
}
