package net.fallhammer.initiative.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import initiative.core.Freq
import initiative.core.Item
import initiative.core.Kind
import initiative.core.Placement
import initiative.core.Recurrence
import initiative.core.Status
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** "This and later" edits, stopping a series, and starting one, against a real database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SeriesEditTest {
    // Monday.
    private val day = LocalDate.of(2026, 10, 19)
    private fun at(d: Long, h: Int) = day.plusDays(d).atTime(h, 0)
    private val now = at(0, 6)

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
    private suspend fun instances(templateId: Long) = all().filter { it.templateId == templateId }.sortedBy { it.start ?: it.earliestStart?.atStartOfDay() }

    private suspend fun dailyGym(): Item {
        val id = dao.insert(Item(title = "gym", kind = Kind.ANCHOR, start = at(0, 7), recurrence = Recurrence(Freq.DAILY), createdAt = at(-1, 9)).toRow())
        repo.ensureInstances(now)
        return all().single { it.id == id }
    }

    @Test fun thisAndLaterChangesTheSeriesFromThatTime() = runBlocking {
        val t = dailyGym()
        val thursday = instances(t.id).single { it.start == at(3, 7) }
        repo.editSeries(thursday, thursday.copy(start = at(3, 18)), Recurrence(Freq.WEEKLY), now)

        val times = instances(t.id).filter { it.status == Status.PENDING }.map { it.start }
        // Monday to Wednesday stay at 7; from Thursday it's weekly at 6 PM.
        assertEquals(listOf(at(0, 7), at(1, 7), at(2, 7), at(3, 18), at(10, 18), at(17, 18)), times)
        assertEquals(Recurrence(Freq.WEEKLY), all().single { it.id == t.id }.recurrence)
    }

    @Test fun stoppingASeriesKeepsThisTimeAsAOneOff() = runBlocking {
        val t = dailyGym()
        val tuesday = instances(t.id).single { it.start == at(1, 7) }
        repo.editSeries(tuesday, tuesday.copy(title = "last gym"), null, now)

        assertEquals(Status.DROPPED, all().single { it.id == t.id }.status)
        assertEquals(listOf(at(0, 7)), instances(t.id).map { it.start })
        val kept = all().single { it.id == tuesday.id }
        assertNull(kept.templateId)
        assertEquals("last gym", kept.title)
        // A stopped series makes no new times.
        repo.ensureInstances(now.plusDays(1))
        assertEquals(listOf(at(0, 7)), instances(t.id).map { it.start })
    }

    @Test fun aOneOffStartsRepeatingWithoutADoubleForToday() = runBlocking {
        val id = dao.insert(Item(title = "water plants", kind = Kind.FLOAT, createdAt = at(-2, 9)).toRow())
        val plants = all().single { it.id == id }
        repo.editSeries(plants, plants, Recurrence(Freq.DAILY), now)

        val template = all().single { it.isTemplate }
        val joined = all().single { it.id == id }
        assertEquals(template.id, joined.templateId)
        assertEquals(day, joined.earliestStart)
        val dates = instances(template.id).map { it.earliestStart }
        assertEquals(dates.distinct(), dates)
        assertNotNull(dates.firstOrNull { it == day.plusDays(1) })
        assertEquals(1, dates.count { it == day })
    }
}
