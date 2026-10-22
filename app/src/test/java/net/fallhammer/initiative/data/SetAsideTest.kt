package net.fallhammer.initiative.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import initiative.core.Item
import initiative.core.Kind
import initiative.core.Placement
import initiative.core.Status
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** A task that stops being Now gives up its pin, On it or not, against a real database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SetAsideTest {
    private val day = LocalDate.of(2026, 10, 19)
    private val now = day.atTime(12, 21)

    private lateinit var db: InitiativeDb
    private lateinit var dao: ItemDao
    private lateinit var repo: Repository

    @Before fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, InitiativeDb::class.java).allowMainThreadQueries().build()
        dao = db.items()
        repo = Repository(dao, Placement())
    }

    @After fun close() = db.close()

    private suspend fun load(id: Long) = dao.all().map { it.toItem() }.single { it.id == id }

    private suspend fun pinned(onIt: Boolean): Item {
        val item = Item(
            title = "water plants", kind = Kind.FLOAT, pinnedSlot = day.atTime(12, 5), createdAt = day.atTime(7, 0),
            status = if (onIt) Status.ACTIVE else Status.PENDING, activeSince = if (onIt) day.atTime(12, 5) else null,
        )
        return load(dao.insert(item.toRow()))
    }

    @Test fun aPinnedTaskNeverOnItGivesUpItsPin() = runBlocking {
        val plants = pinned(onIt = false)
        repo.setAside(plants, now)
        assertNull(load(plants.id).pinnedSlot)
    }

    @Test fun aTaskOnItAlsoBanksItsTime() = runBlocking {
        val plants = pinned(onIt = true)
        repo.setAside(plants, now)
        val after = load(plants.id)
        assertNull(after.pinnedSlot)
        assertNull(after.activeSince)
        assertEquals(16, after.workedMinutes)
    }
}
