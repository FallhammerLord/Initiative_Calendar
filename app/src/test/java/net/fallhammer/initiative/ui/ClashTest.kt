package net.fallhammer.initiative.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import initiative.core.Clashes
import initiative.core.Item
import initiative.core.Kind
import initiative.core.WallStrength
import net.fallhammer.initiative.data.PeerRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** A double-booking: its marker in the HUD, and its choice. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp")
class ClashTest {
    @get:Rule val compose = createComposeRule()

    private fun SemanticsNodeInteraction.tap() = performSemanticsAction(SemanticsActions.OnClick)

    private val day = LocalDate.of(2026, 10, 25)
    private val air = PeerRow(id = 7, peerId = "a", nickname = "Air", initial = "A", color = 0)
    private val dentist = Item(id = 1, title = "Dentist", kind = Kind.ANCHOR, start = day.atTime(15, 0), end = day.atTime(16, 0), wall = WallStrength.HARD, createdAt = day.atTime(8, 0))
    private val pickup = Item(
        id = 2, title = "School pickup", kind = Kind.ANCHOR, start = day.atTime(15, 0), end = day.atTime(15, 30), createdAt = day.atTime(8, 0),
        uid = "u", pairId = 7, owned = false,
    )
    private val clash = Clashes.Clash(dentist, pickup)

    @Test fun theMarkerShowsItsLabelThenOpensTheChoice() {
        var opened: Clashes.Clash? = null
        compose.setContent {
            InitiativeTheme(Looks.SciFi) {
                MarkHud(emptyList(), onEdit = {}, onMore = {}, clashes = listOf(clash), today = day.minusDays(2), onClash = { opened = it })
            }
        }
        // Two days ahead: dim, and says so.
        val label = "Double-booked: Dentist and School pickup in 2 days"
        compose.onNodeWithContentDescription(label).tap()
        compose.onNodeWithText(label).assertIsDisplayed()
        compose.onNodeWithContentDescription(label).tap()
        assertEquals(clash, opened)
    }

    @Test fun theChoiceNamesBothAndEachButtonDoesItsPart() {
        val picked = mutableListOf<String>()
        compose.setContent {
            InitiativeTheme(Looks.SciFi) {
                ClashSheet(clash, listOf(air), onKeep = { picked += "keep" }, onSkip = { picked += "skip" }, onMove = { picked += "move" }, onClose = { picked += "close" })
            }
        }
        compose.onNodeWithText("Double-booked").assertIsDisplayed()
        compose.onNodeWithText("Yours · Hard").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("From Air · Firm").performScrollTo().assertIsDisplayed()
        listOf("Keep both", "Skip theirs, for me", "Move mine", "Not now").forEach { compose.onNodeWithText(it).performScrollTo().tap() }
        assertEquals(listOf("keep", "skip", "move", "close"), picked)
    }
}
