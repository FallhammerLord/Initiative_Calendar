package net.fallhammer.initiative.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import initiative.core.Holiday
import net.fallhammer.initiative.data.QuietSetting
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * Settings, checked as a screen: found by what things say, scrolled to before
 * they're touched, so a longer screen never breaks the test. Taps use each
 * thing's own click action, as TalkBack does; real touch is the emulator's part.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun SemanticsNodeInteraction.tap() = performSemanticsAction(SemanticsActions.OnClick)

    private var look = ""
    private var holidayLooks: Boolean? = null
    private var paired = false
    private var idle: Boolean? = null

    private fun show(holidaysOn: Boolean = false, lookId: String = "scifi") {
        compose.setContent {
            InitiativeTheme(Looks.SciFi) {
                SettingsScreen(
                    unlock = UnlockSetting(), onBoardOnUnlock = {}, lockCard = true, onLockCard = {},
                    lookId = lookId, shownId = lookId, onLook = { look = it },
                    holidayLooks = holidaysOn, onHolidayLooks = { holidayLooks = it },
                    holidays = Holiday.entries.toSet(), onHoliday = { _, _ -> },
                    peers = emptyList(), peeks = emptyMap(), now = LocalDateTime.of(2026, 10, 29, 12, 0), onOpenPair = {}, onPair = { paired = true },
                    ornaments = true, onOrnaments = {}, idleFx = false, onIdleFx = { idle = it }, texture = 50, onTexture = {},
                    quiet = QuietSetting(), onQuiet = {}, lapseHours = 24, onLapseHours = {}, recopyDays = 7, onRecopyDays = {},
                    backup = BackupState(), onBackupFolder = {}, onBackUpNow = {}, onRestoreFile = {},
                    onRestoreConfirm = {}, onRestoreCancel = {}, onClose = {},
                )
            }
        }
    }

    @Test fun themeDropdownPicksALook() {
        show()
        compose.onNodeWithContentDescription("Theme, Sci-fi").performScrollTo().tap()
        compose.onNodeWithText("Halloween").tap()
        assertEquals("halloween", look)
    }

    @Test fun holidayLooksStartOffAndListTheirDays() {
        show()
        compose.onNodeWithText("Change with the holidays").performScrollTo().tap()
        assertEquals(true, holidayLooks)
        compose.onNodeWithText("Oct 1 to Nov 1").assertDoesNotExist()
    }

    @Test fun holidayDaysShowOnceOn() {
        show(holidaysOn = true)
        compose.onNodeWithText("Oct 1 to Nov 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Dec 1 to 26").performScrollTo().assertIsDisplayed()
    }

    @Test fun pairWithSomeoneStartsPairing() {
        show()
        compose.onNodeWithText("Pair with someone").performScrollTo().tap()
        assertEquals(true, paired)
    }

    @Test fun yourColorsStartsRedAndKeepsItsColor() {
        show()
        compose.onNodeWithContentDescription("Theme, Sci-fi").performScrollTo().tap()
        compose.onNodeWithText("Your colors").tap()
        assertEquals("yours-red", look)
    }

    @Test fun yourColorsOffersEightColors() {
        show(lookId = "yours-red")
        compose.onNodeWithText("Your color").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Blue").performScrollTo().tap()
        assertEquals("yours-blue", look)
    }

    @Test fun colorsHideForOtherLooks() {
        show()
        compose.onNodeWithText("Your color").assertDoesNotExist()
    }

    @Test fun idleEffectsStartOff() {
        show()
        compose.onNodeWithText("Idle effects").performScrollTo().tap()
        assertEquals(true, idle)
    }

    @Test fun endsWithUpdate() {
        show()
        compose.onNodeWithText("Update Initiative").performScrollTo().assertIsDisplayed()
    }
}
