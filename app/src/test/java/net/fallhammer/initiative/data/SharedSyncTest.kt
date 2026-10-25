package net.fallhammer.initiative.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import initiative.core.Item
import initiative.core.Kind
import initiative.core.Peek
import initiative.core.PeekFormat
import initiative.core.Placement
import initiative.core.SharedTasks
import initiative.core.Status
import initiative.core.Step
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Two phones' databases, synced as a Ping does it: each side's shared tasks go
 * out in a peek's text and come in on the other side.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedSyncTest {
    private val day = LocalDate.of(2026, 10, 25)
    private val now = day.atTime(12, 0)

    private lateinit var airDb: InitiativeDb
    private lateinit var myDb: InitiativeDb
    private lateinit var air: Repository
    private lateinit var me: Repository

    // On each phone, the other person's id in its own peers table.
    private val meOnAirs = 1L
    private val airOnMine = 2L

    @Before fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        airDb = Room.inMemoryDatabaseBuilder(context, InitiativeDb::class.java).allowMainThreadQueries().build()
        myDb = Room.inMemoryDatabaseBuilder(context, InitiativeDb::class.java).allowMainThreadQueries().build()
        air = Repository(airDb.items(), Placement())
        me = Repository(myDb.items(), Placement())
    }

    @After fun close() {
        airDb.close()
        myDb.close()
    }

    /** One Ping, both ways, through the peek's text. */
    private suspend fun ping() {
        val fromAir = PeekFormat.encode(air.sharedOut(meOnAirs, day).let { Peek(now, emptyList(), null, it.tasks, it.done) })
        val fromMe = PeekFormat.encode(me.sharedOut(airOnMine, day).let { Peek(now, emptyList(), null, it.tasks, it.done) })
        PeekFormat.decode(fromAir)!!.let { me.applyShared(airOnMine, SharedTasks.Out(it.tasks, it.done), now) }
        PeekFormat.decode(fromMe)!!.let { air.applyShared(meOnAirs, SharedTasks.Out(it.tasks, it.done), now) }
    }

    private suspend fun mine() = myDb.items().all().map { it.toItem() }
    private suspend fun airs() = airDb.items().all().map { it.toItem() }

    @Test fun aSharedTaskTravelsAndBothBoxesFollow() = runBlocking {
        airDb.items().insert(
            Item(title = "Take out trash", kind = Kind.FLOAT, uid = "u-trash", pairId = meOnAirs, steps = listOf(Step("bins")), createdAt = now).toRow(),
        )
        ping()
        val arrived = mine().single()
        assertEquals("Take out trash", arrived.title)
        assertTrue(arrived.fromThem)
        assertEquals(airOnMine, arrived.pairId)

        // I do it; Air sees my box ticked, and Air's own stays open.
        me.done(arrived, now)
        ping()
        assertTrue(airs().single().theirDone)
        assertEquals(Status.PENDING, airs().single().status)
        assertFalse(mine().single().theirDone)

        // Air renames it: mine follows, and stays done.
        val theirs = airs().single()
        air.edit(theirs, theirs.copy(title = "Trash night"))
        ping()
        assertEquals("Trash night", mine().single().title)
        assertEquals(Status.DONE, mine().single().status)
    }

    @Test fun removedByItsOwnerGoesFromTheOtherPhone() = runBlocking {
        val id = airDb.items().insert(Item(title = "Water plants", kind = Kind.FLOAT, uid = "u-plants", pairId = meOnAirs, createdAt = now).toRow())
        ping()
        assertEquals(1, mine().size)
        air.deleteForGood(airs().single { it.id == id })
        ping()
        assertTrue(mine().isEmpty())
    }

    @Test fun unpairedTheirsCanBeClaimed() = runBlocking {
        airDb.items().insert(Item(title = "Take out trash", kind = Kind.FLOAT, uid = "u-trash", pairId = meOnAirs, createdAt = now).toRow())
        ping()
        me.unpairShared(airOnMine, day)
        val left = mine().single()
        assertTrue(SharedTasks.claimable(left))
        me.claim(left)
        val claimed = mine().single()
        assertTrue(claimed.owned)
        assertNull(claimed.uid)
    }
}
