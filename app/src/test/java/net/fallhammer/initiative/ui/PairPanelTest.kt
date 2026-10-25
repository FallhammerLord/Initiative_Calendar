package net.fallhammer.initiative.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import initiative.core.Kind
import initiative.core.Outcome
import initiative.core.Peek
import initiative.core.Shared
import initiative.core.SharedLine
import net.fallhammer.initiative.data.PeerRow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/** A paired person's panel: their day report, their month, Ping, and the way out. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp")
class PairPanelTest {
    @get:Rule val compose = createComposeRule()

    private fun SemanticsNodeInteraction.tap() = performSemanticsAction(SemanticsActions.OnClick)

    private val now = LocalDateTime.of(2026, 10, 25, 12, 0)
    private val air = PeerRow(id = 1, peerId = "a", nickname = "Air", initial = "A", color = 0)
    private val items = listOf(
        Shared(Kind.ANCHOR, "School pickup", now.withHour(15), now.withHour(15).plusMinutes(30)),
        Shared(Kind.ANCHOR, "Dentist", now.plusDays(3).withHour(10), now.plusDays(3).withHour(11)),
        Shared(Kind.ANCHOR, "Haircut", now.plusDays(10).withHour(10), now.plusDays(10).withHour(11)),
    )
    private val report = listOf(SharedLine("Work morning", now.withHour(9), Outcome.DONE), SharedLine("Take out trash", null, Outcome.OPEN))

    private var closed = false
    private var pinged = false

    private fun show(peek: Peek?) {
        compose.setContent {
            InitiativeTheme(Looks.SciFi) {
                PairPanel(PairView(air, peek), now, onPing = { pinged = true }, onClose = { closed = true })
            }
        }
    }

    @Test fun theirDayReportShows() {
        show(Peek(now.minusMinutes(5), items, report))
        compose.onNodeWithText("Air's day report").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Work morning").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Done").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Take out trash").performScrollTo().assertIsDisplayed()
    }

    @Test fun noReportSaysSo() {
        show(Peek(now.minusMinutes(5), items))
        compose.onNodeWithText("Air doesn't share a day report.").performScrollTo().assertIsDisplayed()
    }

    @Test fun anOlderReportSaysItsDay() {
        show(Peek(now.minusDays(2), items, report))
        compose.onNodeWithText("Air's day report · ", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun theMonthListsADayWhenTapped() {
        show(Peek(now.minusMinutes(5), items, report))
        // Today first: the pickup.
        compose.onNodeWithText("School pickup").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Wednesday, October 28, has something shared").performScrollTo().tap()
        compose.onNodeWithText("Dentist").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("School pickup").assertDoesNotExist()
        // Into next month: sharing reaches a month ahead.
        compose.onNodeWithContentDescription("Later").performScrollTo().tap()
        compose.onNodeWithText("November 2026").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Wednesday, November 4, has something shared").performScrollTo().tap()
        compose.onNodeWithText("Haircut").performScrollTo().assertIsDisplayed()
    }

    @Test fun pingAndCloseDoWhatTheySay() {
        show(Peek(now.minusMinutes(5), items, report))
        compose.onNodeWithContentDescription("Ping").performScrollTo().tap()
        compose.onNodeWithText("Close").performScrollTo().tap()
        assertTrue(pinged)
        assertTrue(closed)
    }
}
