package net.fallhammer.initiative.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import initiative.core.Item
import initiative.core.Kind
import initiative.core.ShareLevel
import initiative.core.Shareable
import initiative.core.Shared
import initiative.core.Sharing
import net.fallhammer.initiative.data.PeerRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * A paired person's page, as a screen: sharing levels, ticking what they see,
 * and Unpair asking first. A stand-in keeps the edits, as the pairs model would.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PairScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun SemanticsNodeInteraction.tap() = performSemanticsAction(SemanticsActions.OnClick)

    private val now = LocalDateTime.of(2026, 10, 29, 12, 0)
    private val pickup = Item(id = 4, title = "pick up the girls", kind = Kind.ANCHOR, start = now.plusHours(3), createdAt = now)
    private val shareable = listOf(Shareable(pickup, Shared(Kind.ANCHOR, pickup.title, pickup.start!!, pickup.start!!.plusMinutes(30))))

    private var peer by mutableStateOf(PeerRow(id = 1, peerId = "a", nickname = "Partner", initial = "P", color = 0))
    private var ticked by mutableStateOf(emptySet<Long>())
    private var unpaired = false
    private var closed = false

    private val edits = object : PairEdits {
        override fun rename(peer: PeerRow, nickname: String) { this@PairScreenTest.peer = peer.copy(nickname = nickname) }
        override fun setColor(peer: PeerRow, color: Int) { this@PairScreenTest.peer = peer.copy(color = color) }
        override fun setLevel(peer: PeerRow, level: ShareLevel) { this@PairScreenTest.peer = peer.copy(level = level.name) }
        override fun setShareReport(peer: PeerRow, on: Boolean) { this@PairScreenTest.peer = peer.copy(shareReport = on) }
        override fun setShowMark(peer: PeerRow, on: Boolean) { this@PairScreenTest.peer = peer.copy(showMark = on) }
        override fun setShowLine(peer: PeerRow, on: Boolean) { this@PairScreenTest.peer = peer.copy(showLine = on) }
        override fun setShowMarks(peer: PeerRow, on: Boolean) { this@PairScreenTest.peer = peer.copy(showMarks = on) }
        override fun setSyncBluetooth(peer: PeerRow, on: Boolean) { this@PairScreenTest.peer = peer.copy(syncBluetooth = on) }
        override fun setSyncWifi(peer: PeerRow, on: Boolean) { this@PairScreenTest.peer = peer.copy(syncWifi = on) }
        override fun tick(peer: Long, item: Item, on: Boolean) { ticked = if (on) ticked + Sharing.tickKey(item) else ticked - Sharing.tickKey(item) }
        override fun unpair(peer: Long) { unpaired = true }
    }

    private fun show() {
        compose.setContent {
            InitiativeTheme(Looks.SciFi) {
                PairScreen(peer, null, now, shareable, ticked, edits, onClose = { closed = true })
            }
        }
    }

    @Test fun sharingLevelsChangeWhatTheHintSays() {
        show()
        compose.onNodeWithText("Only the items you tick go to Partner.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Everything").performScrollTo().tap()
        assertEquals(ShareLevel.EVERYTHING.name, peer.level)
        compose.onNodeWithText("All of these go to Partner.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Nothing").performScrollTo().tap()
        compose.onNodeWithText("Nothing goes to Partner.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("pick up the girls").assertDoesNotExist()
    }

    @Test fun tickingSharesAnItem() {
        show()
        compose.onNodeWithText("pick up the girls").performScrollTo().tap()
        assertEquals(setOf(4L), ticked)
    }

    @Test fun unpairAsksFirst() {
        show()
        compose.onNodeWithText("Unpair").performScrollTo().tap()
        compose.onNodeWithText("Keep").performScrollTo().tap()
        assertEquals(false, unpaired)
        compose.onNodeWithText("Unpair").performScrollTo().tap()
        compose.onNodeWithText("pair again in person", substring = true).performScrollTo().assertIsDisplayed()
        // The question's own Unpair button: the only one left.
        compose.onNodeWithText("Unpair").performScrollTo().tap()
        assertTrue(unpaired && closed)
    }

    @Test fun syncAlwaysTurnsOnForEachWay() {
        var asked = 0
        compose.setContent {
            InitiativeTheme(Looks.SciFi) {
                PairScreen(peer, null, now, shareable, ticked, edits, onClose = {}, bluetoothReady = false, onAskBluetooth = { asked++ })
            }
        }
        compose.onNodeWithText("On the same Wi-Fi, never the internet", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sync over Bluetooth, always").performScrollTo().tap()
        assertTrue(peer.syncBluetooth)
        // Bluetooth not ready: it asks, and offers to again.
        assertEquals(1, asked)
        compose.onNodeWithText("Allow Bluetooth").performScrollTo().tap()
        assertEquals(2, asked)
        compose.onNodeWithText("Sync at home over Wi-Fi").performScrollTo().tap()
        assertTrue(peer.syncWifi)
    }
}
