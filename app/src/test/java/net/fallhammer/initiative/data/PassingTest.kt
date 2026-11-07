package net.fallhammer.initiative.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import initiative.core.Freq
import initiative.core.Item
import initiative.core.Kind
import initiative.core.Placement
import initiative.core.Prep
import initiative.core.Recurrence
import initiative.core.Status
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Missed repeats pass instead of piling up; prep tasks for a repeating marker, against a real database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PassingTest {
    private val day = LocalDate.of(2026, 10, 19)
    private fun at(d: Long, h: Int) = day.plusDays(d).atTime(h, 0)

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

    private suspend fun all() = dao.all().map { it.toItem() }

    @Test fun aWeekOfMissedDishesLeavesOnlyTodays() = runBlocking {
        val id = dao.insert(Item(title = "dishes", kind = Kind.FLOAT, earliestStart = day, recurrence = Recurrence(Freq.DAILY), createdAt = at(0, 6)).toRow())
        repo.ensureInstances(at(0, 6))
        // A week on, none of them done.
        repo.ensureInstances(at(7, 6))
        val times = all().filter { it.templateId == id }
        val open = times.filter { it.isOpen && !it.earliestStart!!.isAfter(day.plusDays(7)) }
        assertEquals(listOf(day.plusDays(7)), open.map { it.earliestStart })
        assertEquals(7, times.count { it.status == Status.MISSED })
    }

    @Test fun aRepeatingMarkersPrepTaskIsMadeForThisTime() = runBlocking {
        val id = dao.insert(Item(title = "Birthday", kind = Kind.MARK, start = at(5, 0), recurrence = Recurrence(Freq.YEARLY), leadMinutes = 0, createdAt = at(0, 6)).toRow())
        repo.ensureInstances(at(0, 6))
        val thisTime = all().single { it.templateId == id }
        repo.addPrep(thisTime, Item(title = "order cake", kind = Kind.FLOAT, createdAt = at(0, 6)), at(0, 6))
        // The first prep task gave the marker three days to work in.
        assertEquals(Prep.DEFAULT_HEADS_UP_DAYS, all().single { it.id == thisTime.id }.headsUpDays)
        val marker = all().single { it.id == thisTime.id }
        val copies = Prep.of(all(), marker)
        assertEquals(listOf("order cake"), copies.map { it.title })
        assertEquals(day.plusDays(2), copies.single().earliestStart)
        assertTrue(all().any { it.isTemplate && it.markerId == id })
        // Removing the marker keeps its task, now an ordinary one.
        val unlinked = repo.unlinkPrep(marker)
        assertEquals(1, unlinked.size)
        assertEquals(null, all().single { it.id == unlinked.single().id }.markerId)
    }
}
