package net.fallhammer.initiative.ui

import initiative.core.Kind
import initiative.core.Peek
import initiative.core.PeekFormat
import initiative.core.ShareLevel
import initiative.core.Shared
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.fallhammer.initiative.R
import net.fallhammer.initiative.data.PeekRow
import net.fallhammer.initiative.data.PeerRow
import net.fallhammer.initiative.data.ShareRow
import java.time.LocalDateTime

/**
 * The people paired with this phone: how each is shown, what's shared with
 * each, and their last peeks. Pairing itself, over Bluetooth, comes later; until
 * then the only way to add someone is the emulator's pretend partner.
 */
class PairsModel(private val parts: BoardParts) {
    private val dao get() = parts.graph.dao

    val peers: StateFlow<List<PeerRow>> = dao.observePeers().stateIn(parts.scope, SharingStarted.Eagerly, emptyList())

    /** Item ids ticked for each person. */
    val ticks: StateFlow<Map<Long, Set<Long>>> = dao.observeShares()
        .map { rows -> rows.groupBy(ShareRow::peer) { it.itemId }.mapValues { it.value.toSet() } }
        .stateIn(parts.scope, SharingStarted.Eagerly, emptyMap())

    /** Each person's last peek, read back; one that can't be read is left out. */
    val peeks: StateFlow<Map<Long, Peek>> = dao.observePeeks()
        .map { rows -> rows.mapNotNull { r -> PeekFormat.decode(r.body)?.let { r.peer to it } }.toMap() }
        .stateIn(parts.scope, SharingStarted.Eagerly, emptyMap())

    private fun save(peer: PeerRow) {
        parts.scope.launch(Dispatchers.IO) { dao.updatePeer(peer) }
    }

    fun rename(peer: PeerRow, nickname: String) = save(peer.copy(nickname = nickname, initial = initialOf(nickname, peer.initial)))

    fun setColor(peer: PeerRow, color: Int) = save(peer.copy(color = color))

    fun setLevel(peer: PeerRow, level: ShareLevel) = save(peer.copy(level = level.name))

    fun setShareReport(peer: PeerRow, on: Boolean) = save(peer.copy(shareReport = on))

    fun setShowMark(peer: PeerRow, on: Boolean) = save(peer.copy(showMark = on))

    fun setShowLine(peer: PeerRow, on: Boolean) = save(peer.copy(showLine = on))

    fun setShowMarks(peer: PeerRow, on: Boolean) = save(peer.copy(showMarks = on))

    fun tick(peer: Long, item: Long, on: Boolean) {
        parts.scope.launch(Dispatchers.IO) { if (on) dao.share(ShareRow(peer, item)) else dao.unshare(peer, item) }
    }

    /** Unpairs at once on this phone: the person, their ticks, and their last peek go. */
    fun unpair(peer: Long) {
        parts.scope.launch(Dispatchers.IO) {
            dao.deleteSharesOf(peer)
            dao.deletePeekOf(peer)
            dao.deletePeer(peer)
        }
    }

    /**
     * Emulator only: a pretend partner, with a peek from a minute ago, so the
     * emulator test can see the Pairs screens before Bluetooth pairing exists.
     */
    fun pretend(now: LocalDateTime) {
        parts.scope.launch(Dispatchers.IO) {
            if (dao.peers().any { it.peerId == PRETEND }) return@launch
            val name = parts.text(R.string.pair_pretend_name)
            val id = dao.insertPeer(PeerRow(peerId = PRETEND, nickname = name, initial = initialOf(name, "?"), color = 0, seenAt = now.toString()))
            val peek = Peek(
                now.minusMinutes(1),
                listOf(
                    Shared(Kind.FLOAT, parts.text(R.string.pair_pretend_task), now.minusMinutes(10), now.plusMinutes(20)),
                    Shared(Kind.ANCHOR, parts.text(R.string.pair_pretend_anchor), now.plusMinutes(40), now.plusMinutes(70)),
                ),
            )
            dao.putPeek(PeekRow(id, PeekFormat.encode(peek)))
        }
    }

    companion object {
        const val PRETEND = "pretend-partner"

        /** A nickname's initial: its first letter or digit, or [fallback] for none. */
        fun initialOf(nickname: String, fallback: String): String =
            nickname.firstOrNull { it.isLetterOrDigit() }?.titlecase() ?: fallback
    }
}
