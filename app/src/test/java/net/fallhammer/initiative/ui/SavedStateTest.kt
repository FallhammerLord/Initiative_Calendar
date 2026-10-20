package net.fallhammer.initiative.ui

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import initiative.core.Freq
import initiative.core.Item
import initiative.core.Kind
import initiative.core.Recurrence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

/**
 * The view model's parts keep what a person opened in a SavedStateHandle, so it
 * comes back after Android reclaims the app. Each test builds a part, works it,
 * then builds a fresh one from the same handle, as a restart does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavedStateTest {
    private val day = LocalDate.of(2026, 10, 19)
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val parts = BoardParts(ApplicationProvider.getApplicationContext<Application>(), scope, show = {}, acted = {})

    private val template = Item(id = 1, title = "gym", kind = Kind.ANCHOR, start = day.atTime(7, 0), recurrence = Recurrence(Freq.DAILY), createdAt = day.atTime(6, 0))
    private val gym = Item(id = 2, title = "gym", kind = Kind.ANCHOR, start = day.atTime(7, 0), end = day.atTime(8, 0), templateId = 1, createdAt = day.atTime(6, 0))
    private val plants = Item(id = 3, title = "water plants", kind = Kind.FLOAT, createdAt = day.atTime(6, 0))
    private val items = listOf(template, gym, plants)

    @After fun stop() = scope.cancel()

    @Test fun anEditInProgressSurvivesARestart() {
        val saved = SavedStateHandle()
        val edit = EditModel(parts, saved) { items }
        edit.open(gym)
        edit.setStart(LocalTime.of(9, 0))
        edit.setRule(Recurrence(Freq.WEEKLY))
        edit.setLater(true)

        val again = EditModel(parts, saved) { items }
        assertTrue(again.isOpen)
        assertEquals(day.atTime(9, 0), again.draft.value?.start)
        // The length stays the same when the start moves.
        assertEquals(day.atTime(10, 0), again.draft.value?.end)
        assertEquals(Recurrence(Freq.WEEKLY), again.rule.value)
        assertEquals(Recurrence(Freq.DAILY), again.originalRule)
        assertTrue(again.later.value)

        again.cancel()
        assertFalse(EditModel(parts, saved) { items }.isOpen)
    }

    @Test fun draftRules() {
        val edit = EditModel(parts, SavedStateHandle()) { items }
        edit.open(plants)
        assertNull(edit.rule.value)
        edit.setKind(Kind.ANCHOR)
        assertEquals(Kind.ANCHOR, edit.draft.value?.kind)
        assertEquals(0, edit.draft.value?.start?.minute)
        // An end before the start clears it.
        edit.setEnd(LocalTime.of(0, 0))
        assertNull(edit.draft.value?.end)
        edit.setKind(Kind.FLOAT)
        assertNull(edit.draft.value?.start)
    }

    @Test fun aRecopyWalkSurvivesARestart() {
        val saved = SavedStateHandle()
        val floats = listOf(plants, plants.copy(id = 4, title = "call landlord", createdAt = day.atTime(5, 0)))
        val clock = flowOf(day.atTime(12, 0))
        val walk = RecopyModel(parts, flowOf(floats), clock, saved) { floats }
        walk.start()
        // Oldest first.
        assertEquals("call landlord", walk.item.value?.title)
        walk.keep()
        assertEquals("water plants", walk.item.value?.title)

        val again = RecopyModel(parts, flowOf(floats), clock, saved) { floats }
        assertTrue(again.walking)
        assertEquals("water plants", again.item.value?.title)
    }
}
