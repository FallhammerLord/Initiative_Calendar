package net.fallhammer.initiative.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/** The backup file holds every table, every field, and a format number, and restores exactly. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupTest {
    private val item = ItemRow(
        id = 7, title = "call landlord", titleKey = "call landlord", notes = "", kind = "FLOAT", start = null, end = null,
        wall = "FIRM", leadMinutes = 15, durationMinutes = 30, earliestStart = null, deadline = "2026-10-23",
        priority = "AUTO", effort = "QUICK", skipCount = 2, pinnedSlot = null, notBefore = null, recFreq = null, recInterval = 1,
        recDays = "", templateId = null, missPolicy = "REROLL", status = "PENDING", createdAt = "2026-10-19T09:00",
        doneAt = null, activeSince = null,
    )
    private val events = listOf(EventRow(1, 7, null, "PENDING", "2026-10-19T09:00", "auto-skip"))
    private val runs = listOf(RunRow(1, "call landlord", 25, "2026-10-19T10:00"))
    private val alarms = listOf(AlarmRow(7, null, 1, null, 2))
    private val exportedAt = LocalDateTime.of(2026, 10, 20, 3, 0)

    @Test
    fun jsonHoldsEveryTable() {
        val json = Backup.json(listOf(item), events, runs, alarms, exportedAt)
        // Through text and back, as it's read on restore.
        val back = JSONObject(json.toString(2))
        assertEquals(1, back.getInt("format"))
        assertEquals("2026-10-20T03:00", back.getString("exportedAt"))
        val row = back.getJSONArray("items").getJSONObject(0)
        assertEquals("call landlord", row.getString("title"))
        assertEquals(2, row.getInt("skipCount"))
        assertEquals("2026-10-23", row.getString("deadline"))
        assertTrue("empty fields stay as null", row.has("start") && row.isNull("start"))
        assertEquals("auto-skip", back.getJSONArray("events").getJSONObject(0).getString("note"))
        assertEquals(25, back.getJSONArray("runs").getJSONObject(0).getInt("minutes"))
        assertEquals(2, back.getJSONArray("alarms").getJSONObject(0).getInt("lastRung"))
    }

    @Test
    fun pairsKeepTheirNamesAndTicks() {
        val peers = listOf(PeerRow(id = 1, peerId = "abc", nickname = "Partner", initial = "P", color = 2, level = "EVERYTHING"))
        val shares = listOf(ShareRow(1, 7))
        val back = Backup.parse(Backup.json(listOf(item.copy(neverShare = true)), events, runs, alarms, exportedAt, peers, shares).toString(2))
        assertEquals(peers, back.peers)
        assertEquals(shares, back.shares)
        assertEquals(true, back.items.single().neverShare)
        // A backup from before pairs reads with none.
        val old = Backup.json(listOf(item), events, runs, alarms, exportedAt).apply { remove("peers"); remove("shares") }
        assertEquals(emptyList<PeerRow>(), Backup.parse(old.toString()).peers)
    }

    @Test
    fun readsBackExactlyAsWritten() {
        val text = Backup.json(listOf(item), events, runs, alarms, exportedAt).toString(2)
        val snapshot = Backup.parse(text)
        assertEquals(exportedAt, snapshot.exportedAt)
        assertEquals(listOf(item), snapshot.items)
        assertEquals(events, snapshot.events)
        assertEquals(runs, snapshot.runs)
        assertEquals(alarms, snapshot.alarms)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesAnUnknownFormat() {
        Backup.parse("""{"format": 99}""")
    }

    @Test
    fun restoreReplacesEverything() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, InitiativeDb::class.java).allowMainThreadQueries().build()
        runBlocking {
            val dao = db.items()
            // Something already there that the backup doesn't have.
            dao.insert(item.copy(id = 0, title = "old item", titleKey = "old item"))
            dao.putAlarm(AlarmRow(99, null, 0, null, 1))

            Backup.restore(db, Backup.parse(Backup.json(listOf(item), events, runs, alarms, exportedAt).toString()))

            assertEquals(listOf(item), dao.all())
            assertEquals(events, dao.events())
            assertEquals(runs, dao.runs())
            assertEquals(alarms, dao.alarms())
        }
        db.close()
    }
}
