package net.fallhammer.initiative.data

import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The dock's slots and their trays, as saved. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DockTraysTest {
    private lateinit var context: Context
    private lateinit var apps: Apps

    private fun entry(name: String) = AppEntry(name, ComponentName("pkg.$name", "pkg.$name.Main"))
    private fun key(name: String) = entry(name).key

    @Before fun open() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("apps", Context.MODE_PRIVATE).edit().clear().commit()
        apps = Apps(context)
    }

    @Test fun aSlotHoldsItsOwnAppAndFourMore() {
        listOf("mail", "a", "b", "c", "d").forEach { apps.add(0, key(it)) }
        assertEquals(listOf("mail", "a", "b", "c", "d").map(::key), apps.slots()[0])
        assertFalse(apps.add(0, key("e")))
        assertEquals(key("mail"), apps.dock()[0])
    }

    @Test fun removingTheSlotsOwnAppBringsTheNearestDown() {
        listOf("mail", "a", "b").forEach { apps.add(1, key(it)) }
        apps.remove(1, key("mail"))
        assertEquals(listOf("a", "b").map(::key), apps.slots()[1])
    }

    @Test fun moveDownSwapsWithTheOneNearer() {
        listOf("mail", "a", "b").forEach { apps.add(2, key(it)) }
        apps.moveDown(2, 2)
        assertEquals(listOf("mail", "b", "a").map(::key), apps.slots()[2])
        apps.moveDown(2, 1)
        assertEquals(listOf("b", "mail", "a").map(::key), apps.slots()[2])
    }

    @Test fun anAppLivesInOnePlace() {
        apps.add(0, key("mail"))
        apps.add(3, key("mail"))
        assertEquals(emptyList<String>(), apps.slots()[0])
        assertEquals(listOf(key("mail")), apps.slots()[3])
    }

    @Test fun holdingInTheDrawerFillsEmptySlotsThenTrays() {
        (1..6).forEach { apps.togglePin(entry("app$it")) }
        assertEquals((1..6).map { key("app$it") }, apps.dock())
        apps.togglePin(entry("extra"))
        assertEquals(listOf("app1", "extra").map(::key), apps.slots()[0])
        apps.togglePin(entry("extra"))
        assertEquals(listOf(key("app1")), apps.slots()[0])
    }

    @Test fun emptyingASlotEmptiesItsTray() {
        listOf("mail", "a").forEach { apps.add(4, key(it)) }
        apps.setSlot(4, null)
        assertEquals(emptyList<String>(), apps.slots()[4])
        assertEquals(null, apps.dock()[4])
    }

    @Test fun anOlderDockCarriesOver() {
        context.getSharedPreferences("apps", Context.MODE_PRIVATE).edit().putString("dock", "${key("mail")}||${key("maps")}").commit()
        assertEquals(listOf(listOf(key("mail")), emptyList(), listOf(key("maps"))), apps.slots().take(3))
    }
}
