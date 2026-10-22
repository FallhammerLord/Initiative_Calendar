package net.fallhammer.initiative.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Upgrades a database built exactly as version 1 (the first release) left it,
 * through every version since, and checks the data survives, the alarms table
 * works, items start with no steps and no wait, and the pairs tables work. Room validates
 * every table against the current code while it opens, so a layout mismatch
 * fails here before it can reach a phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    @Test
    fun version1ToLatestKeepsItemsAndAddsAlarmsStepsAndSideTasks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)

        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { db ->
            V1_TABLES.forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO items (title, titleKey, notes, kind, wall, leadMinutes, partnerAlert, durationMinutes, " +
                    "priority, effort, skipCount, recInterval, recDays, missPolicy, status, createdAt) VALUES " +
                    "('call landlord', 'call landlord', '', 'FLOAT', 'FIRM', 15, 0, 30, 'AUTO', 'QUICK', 0, 1, '', " +
                    "'REROLL', 'PENDING', '2026-10-19T09:00')",
            )
            db.version = 1
        }

        val room = Room.databaseBuilder(context, InitiativeDb::class.java, name)
            .addMigrations(InitiativeDb.ADD_ALARMS, InitiativeDb.ADD_STEPS, InitiativeDb.ADD_WAITS, InitiativeDb.ADD_MARKS, InitiativeDb.ADD_PAIRS, InitiativeDb.ADD_SIDE)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            val dao = room.items()
            assertEquals(listOf("call landlord"), dao.all().map { it.title })
            assertEquals("", dao.all().single().steps)
            assertEquals(null, dao.all().single().waitUntil)
            assertEquals(0, dao.all().single().workedMinutes)
            assertEquals("FLAG", dao.all().single().icon)
            assertEquals(false, dao.all().single().neverShare)
            // Version 7: no side task yet, and the item keeps its id, so a new one numbers on from it.
            assertEquals(false, dao.all().single().side)
            assertEquals(1L, dao.all().single().id)
            dao.putAlarm(AlarmRow(itemId = 1, ackedAt = null, snoozes = 1, snoozedUntil = null, lastRung = 2))
            assertNotNull(dao.alarm(1))
            // The pairs tables work: a person, a tick, and a peek.
            val peer = dao.insertPeer(PeerRow(peerId = "abc", nickname = "Partner", initial = "P", color = 0))
            dao.share(ShareRow(peer, 1))
            dao.putPeek(PeekRow(peer, "initiative-peek 1"))
            assertEquals(listOf(ShareRow(peer, 1)), dao.shares())
            val second = dao.insert(dao.all().single().copy(id = 0, title = "taxes", titleKey = "taxes"))
            assertEquals(2L, second)
        }
        room.close()
    }

    private companion object {
        // The tables as version 1 created them.
        val V1_TABLES = listOf(
            "CREATE TABLE IF NOT EXISTS `items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                "`titleKey` TEXT NOT NULL, `notes` TEXT NOT NULL, `kind` TEXT NOT NULL, `start` TEXT, `end` TEXT, " +
                "`wall` TEXT NOT NULL, `leadMinutes` INTEGER NOT NULL, `partnerAlert` INTEGER NOT NULL, " +
                "`durationMinutes` INTEGER NOT NULL, `earliestStart` TEXT, `deadline` TEXT, `priority` TEXT NOT NULL, " +
                "`effort` TEXT NOT NULL, `skipCount` INTEGER NOT NULL, `pinnedSlot` TEXT, `notBefore` TEXT, " +
                "`recFreq` TEXT, `recInterval` INTEGER NOT NULL, `recDays` TEXT NOT NULL, `templateId` INTEGER, " +
                "`missPolicy` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `doneAt` TEXT, " +
                "`activeSince` TEXT)",
            "CREATE TABLE IF NOT EXISTS `events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `itemId` INTEGER NOT NULL, " +
                "`fromStatus` TEXT, `toStatus` TEXT NOT NULL, `at` TEXT NOT NULL, `note` TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `runs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `titleKey` TEXT NOT NULL, " +
                "`minutes` INTEGER NOT NULL, `at` TEXT NOT NULL)",
        )
    }
}
