package net.fallhammer.initiative.ui

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import initiative.core.Forecast
import initiative.core.Item
import initiative.core.Kind
import initiative.core.Status
import initiative.core.WallStrength
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The Log, checked as a screen: zooms, paging, Repeats, + Add, search, and the
 * way back. Its days are worked out off the main thread, so checks wait for them.
 */
@RunWith(RobolectricTestRunner::class)
// A phone-sized screen: on the small default, the list gets no room below the header.
@Config(sdk = [34], qualifiers = "w412dp-h915dp")
class LogScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun SemanticsNodeInteraction.tap() = performSemanticsAction(SemanticsActions.OnClick)

    private val day = LocalDate.of(2026, 10, 19)
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val parts = BoardParts(ApplicationProvider.getApplicationContext<Application>(), scope, show = {}, acted = {})

    private val dentist = Item(id = 1, title = "dentist call", kind = Kind.ANCHOR, start = day.atTime(15, 0), end = day.atTime(15, 30), wall = WallStrength.HARD, createdAt = day.atTime(7, 0))
    private val landlord = Item(id = 2, title = "call landlord", kind = Kind.FLOAT, status = Status.DONE, doneAt = day.atTime(9, 0), createdAt = day.atTime(7, 0))

    private var closed = false
    private var added: Pair<String, LocalDate>? = null

    @After fun stop() = scope.cancel()

    private fun show() {
        val log = LogModel(parts, SavedStateHandle(), flowOf(listOf(dentist, landlord)), MutableStateFlow(day.atTime(12, 0)))
        log.show()
        compose.setContent {
            InitiativeTheme(Looks.SciFi) {
                LogScreen(
                    model = log, nowItem = null, forecast = Forecast(emptyList(), emptyList()), flash = null,
                    onAdd = { text, on -> added = text to on; true }, onUndo = {}, onEdit = {}, onClose = { closed = true },
                )
            }
        }
    }

    // The header and its buttons stay put; only the entries scroll.
    private fun waitFor(text: String) = compose.waitUntil(5_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test fun opensOnTodayWithItsItems() {
        show()
        waitFor("dentist call")
        compose.onNodeWithText("dentist call", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Day report").performScrollTo().assertIsDisplayed()
    }

    @Test fun zoomsOutAndPagesTheYear() {
        show()
        compose.onNodeWithText("Week").tap()
        waitFor("dentist call")
        compose.onNodeWithText("Month").tap()
        waitFor("dentist call")
        compose.onNodeWithText("Year").tap()
        waitFor("2026")
        compose.onNodeWithContentDescription("Later").tap()
        waitFor("2027")
        compose.onNodeWithText("2027").assertIsDisplayed()
    }

    @Test fun repeatsSwapsForTheDaysAndBack() {
        show()
        compose.onNodeWithText("Repeats").tap()
        compose.onNodeWithText("Days").tap()
        compose.onNodeWithContentDescription("Earlier").assertIsDisplayed()
    }

    @Test fun addTakesATaskForTheDayInViewAndSearchReturns() {
        show()
        compose.onNodeWithText("+ Add").tap()
        compose.onNodeWithText("Add to today").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("pack lunch")
        compose.onNodeWithText("Add").tap()
        assertTrue(added?.first == "pack lunch" && added?.second == day)
        compose.onNodeWithText("Search").tap()
        compose.onNodeWithText("Search everything").assertIsDisplayed()
    }

    @Test fun searchFindsDoneItems() {
        show()
        compose.onNode(hasSetTextAction()).performTextInput("landlord")
        waitFor("call landlord · done")
        compose.onNodeWithText("call landlord · done").performScrollTo().assertIsDisplayed()
    }

    @Test fun backToBoardCloses() {
        show()
        compose.onNodeWithText("Back to board").tap()
        assertTrue(closed)
    }
}
