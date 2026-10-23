package net.fallhammer.initiative.ui

import android.content.ComponentName
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import net.fallhammer.initiative.data.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Dock trays: the slot's label, the tray sliding open and shut, and the picker's list of what a slot holds. */
@RunWith(RobolectricTestRunner::class)
// A phone-sized screen, so the tray has room above the dock.
@Config(sdk = [34], qualifiers = "w412dp-h915dp")
class DockTrayTest {
    @get:Rule val compose = createComposeRule()

    private fun SemanticsNodeInteraction.tap() = performSemanticsAction(SemanticsActions.OnClick)

    private fun app(name: String) = AppEntry(name, ComponentName("pkg.$name", "pkg.$name.Main"))
    private val mail = app("Mail")
    private val maps = app("Maps")
    private val music = app("Music")
    private val slots = listOf(listOf(mail, maps, music), listOf(app("Camera"))) + List(4) { emptyList<AppEntry>() }

    private var launched: AppEntry? = null

    /** The dock along the bottom, and its tray layer over it, as on the board. */
    private fun board() {
        compose.setContent {
            InitiativeTheme(Looks.SciFi) {
                var open by remember { mutableIntStateOf(-1) }
                val slotAt = remember { mutableStateMapOf<Int, Rect>() }
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        Dock(
                            slots.map { it.firstOrNull() }, emptyMap(),
                            onTap = { i -> if (slots[i].size > 1) open = if (open == i) -1 else i else launched = slots[i].firstOrNull() },
                            onHold = {},
                            more = slots.map { it.size > 1 }, open = open, onPlaced = { i, at -> slotAt[i] = at },
                        )
                    }
                    DockTrayLayer(open, slots, emptyMap(), slotAt, onTap = { launched = it; open = -1 }, onClose = { open = -1 })
                }
            }
        }
    }

    @Test fun aSlotWithMoreSaysSo() {
        board()
        compose.onNodeWithContentDescription("Mail, opens a tray").assertIsDisplayed()
        compose.onNodeWithContentDescription("Camera").assertIsDisplayed()
    }

    @Test fun aTapOpensTheTrayAndAnAppInItOpens() {
        board()
        compose.onNodeWithContentDescription("Maps").assertDoesNotExist()
        compose.onNodeWithContentDescription("Mail, opens a tray").tap()
        compose.onNodeWithContentDescription("Mail, tray open").assertIsDisplayed()
        compose.onNodeWithContentDescription("Music").assertIsDisplayed()
        compose.onNodeWithContentDescription("Maps").tap()
        assertEquals(maps, launched)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Maps").assertDoesNotExist()
    }

    @Test fun aTapOutsideShutsIt() {
        board()
        compose.onNodeWithContentDescription("Mail, opens a tray").tap()
        compose.onNodeWithContentDescription("Maps").assertIsDisplayed()
        // The top of the screen: the dimmed board, well away from the tray.
        compose.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(centerX, 40f)) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Maps").assertDoesNotExist()
    }

    @Test fun aSlotWithOneAppOpensItStraightAway() {
        board()
        compose.onNodeWithContentDescription("Camera").tap()
        assertEquals("Camera", launched?.label)
    }

    @Test fun thePickerListsWhatTheSlotHolds() {
        var removed: AppEntry? = null
        var moved = -1
        compose.setContent {
            InitiativeTheme(Looks.SciFi) {
                AppDrawer(
                    apps = listOf(mail, maps, music, app("Notes")), pinned = emptyList(), onLaunch = {}, onTogglePin = {},
                    onSettings = {}, onHomeChooser = {}, onAppSettings = {}, onClose = {},
                    picking = true, onClearSlot = {}, slotApps = slots[0],
                    onRemove = { removed = it }, onMoveDown = { moved = it },
                )
            }
        }
        compose.onNodeWithText("In this space").assertIsDisplayed()
        compose.onNodeWithText("Mail, in the space").assertIsDisplayed()
        compose.onNodeWithText("Tap an app below to add it here.").assertIsDisplayed()
        compose.onAllNodesWithText("Move down")[1].tap()
        assertEquals(2, moved)
        compose.onAllNodesWithText("Remove")[0].tap()
        assertEquals(mail, removed)
    }
}
