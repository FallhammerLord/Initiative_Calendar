package net.fallhammer.initiative.pairs

import androidx.annotation.StringRes
import initiative.core.PairWire
import initiative.core.Peek
import initiative.core.PeekFormat
import initiative.core.Report
import initiative.core.ShareLevel
import initiative.core.SharedTasks
import initiative.core.Sharing
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.fallhammer.initiative.R
import net.fallhammer.initiative.data.PeekRow
import net.fallhammer.initiative.data.PeerRow
import net.fallhammer.initiative.data.Prefs
import net.fallhammer.initiative.data.Repository
import net.fallhammer.initiative.data.ItemDao
import initiative.core.Placement
import java.time.LocalDateTime

/**
 * One sync with a paired phone, however the two met: each proves which pair
 * it is, sends what it shares with the other (its peek, its day report when
 * shared, and its shared tasks), and keeps what comes back. Used by Ping, and
 * by the always-on syncs in the background. One at a time, so two ways of
 * meeting at once can't write over each other.
 */
class PairSync(
    private val dao: ItemDao,
    private val repo: Repository,
    private val placement: Placement,
    private val prefs: Prefs,
    /** This phone's keys for a paired person (their ID, their row); from Android's key store. */
    private val keyOf: (String, Long) -> PairWire.PairKeys?,
) {
    /** How a sync went: who it was with, and, when builds differ, which side should update. */
    data class Synced(val nickname: String, @StringRes val update: Int?)

    private val lock = Mutex()

    /**
     * Syncs over [link]; null when the other phone isn't paired with this one.
     * [allowed] narrows who to sync with: an always-on sync only with people it's on for.
     */
    suspend fun over(link: Link, allowed: (PeerRow) -> Boolean = { true }): Synced? = lock.withLock {
        val peers = dao.peers().filter(allowed)
        val mine = peers.mapNotNull { keyOf(it.peerId, it.id) }
        if (mine.isEmpty()) return null
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val all = repo.items.first()
        val ticked = dao.shares().groupBy({ it.peer }, { it.itemId }).mapValues { it.value.toSet() }
        val forecast = placement.forecast(all, now, prefs.taskOrder, prefs.hold)
        val shareable = Sharing.shareable(all, forecast, now)
        val report = if (peers.any { it.shareReport }) Report.of(all, today, today, repo.movedOn(today, all)) else emptyList()
        // Read before the meeting: the wire asks for each person's text while it talks.
        val shared = peers.associate { it.id to repo.sharedOut(it.id, today) }
        val got = PairWire.ping(link.input, link.output, mine, link.initiator) { id ->
            val peer = peers.first { it.id == id }
            val theirs = ticked[id].orEmpty()
            val level = levelOf(peer)
            val day = if (peer.shareReport) Sharing.report(report, level, theirs) else null
            val tasks = shared.getValue(id)
            // Tasks shared with them are on their own board already, so they aren't on your line too.
            val seen = shareable.filter { it.item.pairId != id }
            PeekFormat.encode(Peek(now, Sharing.outgoing(seen, level, theirs), day, tasks.tasks, tasks.done))
        } ?: return null
        val peer = peers.firstOrNull { it.id == got.peer } ?: return null
        // Different builds can't share tasks safely: said, so both can update.
        val theirVersion = PeekFormat.version(got.text)
        val update = if (theirVersion == PeekFormat.VERSION) {
            null
        } else {
            theirVersion?.let { if (it > PeekFormat.VERSION) R.string.pair_update_mine else R.string.pair_update_theirs }
        }
        // Kept only if it reads; one this build can't read waits for an update.
        val peek = PeekFormat.decode(got.text) ?: return null
        dao.putPeek(PeekRow(peer.id, got.text))
        dao.updatePeer(peer.copy(seenAt = now.toString()))
        if (theirVersion == PeekFormat.VERSION) repo.applyShared(peer.id, SharedTasks.Out(peek.tasks, peek.done), now)
        Synced(peer.nickname, update)
    }

    private fun levelOf(peer: PeerRow): ShareLevel = ShareLevel.entries.firstOrNull { it.name == peer.level } ?: ShareLevel.TICKED
}
