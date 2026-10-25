package net.fallhammer.initiative.pairs

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import initiative.core.Item
import initiative.core.Kind
import initiative.core.PairWire
import initiative.core.Placement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.fallhammer.initiative.data.InitiativeDb
import net.fallhammer.initiative.data.PeerRow
import net.fallhammer.initiative.data.Prefs
import net.fallhammer.initiative.data.Repository
import net.fallhammer.initiative.data.toItem
import net.fallhammer.initiative.data.toRow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.time.LocalDateTime
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * Two phones syncing as the always-on syncs and Ping do, over a real socket:
 * each proves the pair, and the shared task, the peek, and the time seen arrive.
 * And the local-network rule and the ten-minute windows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PairSyncTest {
    private lateinit var airDb: InitiativeDb
    private lateinit var myDb: InitiativeDb

    // The pair's two keys, the same on both phones.
    private val mac = SecretKeySpec(Random.nextBytes(32), "HmacSHA256")
    private val seal = SecretKeySpec(Random.nextBytes(16), "AES")

    @Before fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        airDb = Room.inMemoryDatabaseBuilder(context, InitiativeDb::class.java).allowMainThreadQueries().build()
        myDb = Room.inMemoryDatabaseBuilder(context, InitiativeDb::class.java).allowMainThreadQueries().build()
    }

    @After fun close() {
        airDb.close()
        myDb.close()
    }

    private fun sync(db: InitiativeDb): PairSync {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return PairSync(db.items(), Repository(db.items(), Placement()), Placement(), Prefs(context)) { _, peer -> PairWire.PairKeys(peer, mac, seal) }
    }

    /** Both sides at once over a socket on this machine; what each side's sync returned. */
    private suspend fun meet(air: suspend (Link) -> PairSync.Synced?, me: suspend (Link) -> PairSync.Synced?) = withContext(Dispatchers.IO) {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val theirs = async { server.accept().let { s -> Link(s.getInputStream(), s.getOutputStream(), false) { s.close() } }.use { air(it) } }
            val mine = Socket(InetAddress.getLoopbackAddress(), server.localPort).let { s -> Link(s.getInputStream(), s.getOutputStream(), true) { s.close() } }.use { me(it) }
            theirs.await() to mine
        }
    }

    @Test fun aSyncBringsTheirSharedTaskAndPeek() = runBlocking {
        val meOnAirs = airDb.items().insertPeer(PeerRow(peerId = "me", nickname = "Me", initial = "M", color = 0, syncWifi = true))
        val airOnMine = myDb.items().insertPeer(PeerRow(peerId = "air", nickname = "Air", initial = "A", color = 1, syncWifi = true))
        airDb.items().insert(Item(title = "Take out trash", kind = Kind.FLOAT, uid = "u-trash", pairId = meOnAirs, createdAt = LocalDateTime.now()).toRow())

        val (onAirs, onMine) = meet({ sync(airDb).over(it) { p -> p.syncWifi } }, { sync(myDb).over(it) { p -> p.syncWifi } })
        assertEquals("Me", onAirs?.nickname)
        assertEquals("Air", onMine?.nickname)
        val arrived = myDb.items().all().map { it.toItem() }.single()
        assertEquals("Take out trash", arrived.title)
        assertTrue(arrived.fromThem)
        assertNotNull(myDb.items().peers().single().seenAt)
        assertEquals(1, myDb.items().observePeeks().first().size)
        assertEquals(airOnMine, myDb.items().peers().single().id)
    }

    @Test fun offForThemIsAStranger() = runBlocking {
        airDb.items().insertPeer(PeerRow(peerId = "me", nickname = "Me", initial = "M", color = 0, syncWifi = true))
        myDb.items().insertPeer(PeerRow(peerId = "air", nickname = "Air", initial = "A", color = 1, syncWifi = false))
        // Off on my phone: I have no one to sync with, so Air finds no match either.
        val (onAirs, onMine) = meet(
            { runCatching { sync(airDb).over(it) { p -> p.syncWifi } }.getOrNull() },
            { sync(myDb).over(it) { p -> p.syncWifi } },
        )
        assertNull(onMine)
        assertNull(onAirs)
        assertTrue(myDb.items().observePeeks().first().isEmpty())
    }

    @Test fun onlyTheHomeNetworkIsLetIn() {
        listOf("192.168.1.20", "10.0.0.5", "172.16.4.2", "fe80::1", "fd12:3456::1").forEach {
            assertTrue(it, LocalNet.isLocal(InetAddress.getByName(it)))
        }
        listOf("8.8.8.8", "142.250.1.1", "2001:4860::8888").forEach {
            assertTrue(it, !LocalNet.isLocal(InetAddress.getByName(it)))
        }
    }

    @Test fun windowsOpenOnTheTenMinuteMarks() {
        val ten = SyncService.EVERY_MS
        assertEquals(ten, SyncService.untilNextWindow(0))
        assertEquals(60_000L, SyncService.untilNextWindow(9 * 60_000L))
        // Two phones a few seconds apart still meet in the same minute.
        assertEquals(SyncService.untilNextWindow(3 * ten + 5_000) + 5_000, SyncService.untilNextWindow(3 * ten))
    }
}
