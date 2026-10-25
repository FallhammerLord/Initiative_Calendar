package net.fallhammer.initiative.ui

import android.bluetooth.BluetoothSocket
import android.util.Log
import androidx.annotation.StringRes
import initiative.core.Kind
import initiative.core.PairWire
import initiative.core.ReportLine
import initiative.core.Item
import initiative.core.Peek
import initiative.core.Outcome
import initiative.core.SharedLine
import initiative.core.PeekFormat
import initiative.core.ShareLevel
import initiative.core.Shareable
import initiative.core.Shared
import initiative.core.Sharing
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.fallhammer.initiative.R
import net.fallhammer.initiative.data.PeekRow
import net.fallhammer.initiative.data.PeerRow
import net.fallhammer.initiative.data.Prefs
import net.fallhammer.initiative.data.ShareRow
import net.fallhammer.initiative.pairs.Nearby
import java.time.LocalDateTime

/** Where pairing has got to, for the pairing screen. */
sealed interface Pairing {
    /** Findable and searching, for up to two minutes. */
    data object Searching : Pairing

    /** Both screens show this code; does it match? */
    data class Code(val code: String) : Pairing

    /** This phone said yes; waiting for the other's answer. */
    data object Waiting : Pairing

    /** Paired: give them a nickname. */
    data class Naming(val peer: Long) : Pairing

    /** It didn't work, and why. */
    data class Failed(@StringRes val reason: Int) : Pairing
}

/**
 * The people paired with this phone: pairing them, in person over Bluetooth,
 * how each is shown, what's shared with each, and Ping for their peeks.
 */
class PairsModel(
    private val parts: BoardParts,
    private val shareable: () -> List<Shareable>,
    /** Today's report, read when a ping happens, for whoever it's shared with. */
    private val todayReport: suspend () -> List<ReportLine> = { emptyList() },
) : PairEdits {
    private val dao get() = parts.graph.dao
    private val nearby get() = parts.graph.nearby
    private val keys get() = parts.graph.pairKeys

    private val _pairing = MutableStateFlow<Pairing?>(null)
    /** Pairing under way; null when not pairing. A Bluetooth meeting can't outlast the app, so it isn't saved. */
    val pairing: StateFlow<Pairing?> = _pairing.asStateFlow()
    private var pairJob: Job? = null
    private var pairSocket: BluetoothSocket? = null
    private var decision = CompletableDeferred<Boolean>()

    private var pinging = false

    val peers: StateFlow<List<PeerRow>> = dao.observePeers().stateIn(parts.scope, SharingStarted.Eagerly, emptyList())

    /** Item ids ticked for each person. */
    val ticks: StateFlow<Map<Long, Set<Long>>> = dao.observeShares()
        .map { rows -> rows.groupBy(ShareRow::peer) { it.itemId }.mapValues { it.value.toSet() } }
        .stateIn(parts.scope, SharingStarted.Eagerly, emptyMap())

    /** Each person's last peek, read back; one that can't be read is left out. */
    val peeks: StateFlow<Map<Long, Peek>> = dao.observePeeks()
        .map { rows -> rows.mapNotNull { r -> PeekFormat.decode(r.body)?.let { r.peer to it } }.toMap() }
        .stateIn(parts.scope, SharingStarted.Eagerly, emptyMap())

    private val _shown = MutableStateFlow(true)
    /** Whether pairs show on the board; the board's own on/off. */
    val shown: StateFlow<Boolean> = _shown.asStateFlow()

    /** Reads the on/off. Call off the main thread. */
    fun load(prefs: Prefs) {
        _shown.value = prefs.pairsShown
    }

    fun setShown(on: Boolean) {
        _shown.value = on
        parts.scope.launch(Dispatchers.IO) { parts.graph.prefs.pairsShown = on }
    }

    /**
     * Pair with the phone close by that's also pairing: find it, agree a code,
     * and once both people say it matches, keep the pair's keys and add them.
     */
    fun startPairing() {
        pairJob?.cancel()
        decision = CompletableDeferred()
        _pairing.value = Pairing.Searching
        pairJob = parts.scope.launch {
            val (met, problem) = nearby.meet(Nearby.PAIR, PAIR_MS)
            if (met == null) {
                _pairing.value = Pairing.Failed(reasonFor(problem))
                return@launch
            }
            pairSocket = met.socket
            try {
                withContext(Dispatchers.IO) { pairOver(met) }
            } catch (e: Exception) {
                if (_pairing.value != null) _pairing.value = Pairing.Failed(R.string.pairing_broke)
            } finally {
                runCatching { met.socket.close() }
                pairSocket = null
            }
        }
    }

    private suspend fun pairOver(met: Nearby.Met) {
        val input = met.socket.inputStream
        val out = met.socket.outputStream
        val agreed = PairWire.agree(input, out, parts.graph.prefs.instanceId, met.initiator)
        if (dao.peers().any { it.peerId == agreed.theirId }) {
            PairWire.confirm(input, out, false)
            _pairing.value = Pairing.Failed(R.string.pairing_already)
            return
        }
        if (dao.peers().size >= MAX_PAIRS) {
            PairWire.confirm(input, out, false)
            _pairing.value = Pairing.Failed(R.string.pairing_full)
            return
        }
        _pairing.value = Pairing.Code(agreed.code)
        val yes = withTimeoutOrNull(ANSWER_MS) { decision.await() } ?: false
        if (yes) _pairing.value = Pairing.Waiting
        if (!PairWire.confirm(input, out, yes)) {
            _pairing.value = Pairing.Failed(R.string.pairing_declined)
            return
        }
        keys.save(agreed.theirId, agreed.macKey, agreed.sealKey)
        val used = dao.peers().map { it.color }.toSet()
        val color = (0 until PairColors.count).firstOrNull { it !in used } ?: 0
        val name = parts.text(R.string.pairing_default_name)
        val id = dao.insertPeer(PeerRow(peerId = agreed.theirId, nickname = name, initial = initialOf(name, "?"), color = color))
        _pairing.value = Pairing.Naming(id)
    }

    /** The person's answer to "does the code match?". */
    fun answer(yes: Boolean) {
        decision.complete(yes)
        if (!yes) _pairing.value = Pairing.Failed(R.string.pairing_declined)
    }

    /** Names the newly paired person, and closes pairing. */
    fun finishPairing(peer: Long, nickname: String) {
        val name = nickname.trim()
        if (name.isNotEmpty()) {
            parts.scope.launch(Dispatchers.IO) {
                dao.peers().firstOrNull { it.id == peer }?.let { dao.updatePeer(it.copy(nickname = name, initial = initialOf(name, it.initial))) }
            }
        }
        _pairing.value = null
    }

    fun cancelPairing() {
        decision.complete(false)
        runCatching { pairSocket?.close() }
        pairJob?.cancel()
        _pairing.value = null
    }

    /**
     * Ping: findable and searching for a minute. When the other phone pings
     * too, each proves which pair it is and sends what it shares with the
     * other; their peek is kept, and the light comes on.
     */
    fun ping() {
        if (pinging) return
        pinging = true
        parts.show(Flash(parts.text(R.string.pair_pinging)))
        parts.scope.launch {
            try {
                val (met, problem) = nearby.meet(Nearby.PING, PING_MS)
                if (met == null) {
                    Log.i(TAG, "ping: ${problem ?: "none"}")
                    parts.show(Flash(parts.text(reasonFor(problem))))
                    return@launch
                }
                val name = withContext(Dispatchers.IO) { met.socket.use { pingOver(met) } }
                Log.i(TAG, if (name != null) "ping: swapped" else "ping: stranger")
                parts.show(Flash(name?.let { parts.app.getString(R.string.pair_pinged, it) } ?: parts.text(R.string.pair_stranger)))
            } catch (e: Exception) {
                Log.i(TAG, "ping: dropped")
                parts.show(Flash(parts.text(R.string.pairing_broke)))
            } finally {
                pinging = false
            }
        }
    }

    /** The exchange itself; returns the person's nickname, or null for a phone not paired with this one. */
    private suspend fun pingOver(met: Nearby.Met): String? {
        val peers = dao.peers()
        val ticked = ticks.value
        val mine = peers.mapNotNull { keys.load(it.peerId, it.id) }
        if (mine.isEmpty()) return null
        val now = LocalDateTime.now()
        val report = if (peers.any { it.shareReport }) todayReport() else emptyList()
        val got = PairWire.ping(met.socket.inputStream, met.socket.outputStream, mine, met.initiator) { id ->
            val peer = peers.first { it.id == id }
            val theirs = ticked[id].orEmpty()
            val day = if (peer.shareReport) Sharing.report(report, levelOf(peer), theirs) else null
            PeekFormat.encode(Peek(now, Sharing.outgoing(shareable(), levelOf(peer), theirs), day))
        } ?: return null
        val peer = peers.firstOrNull { it.id == got.peer } ?: return null
        // Kept only if it reads; one this build can't read waits for an update.
        PeekFormat.decode(got.text) ?: return null
        dao.putPeek(PeekRow(peer.id, got.text))
        dao.updatePeer(peer.copy(seenAt = now.toString()))
        return peer.nickname
    }

    @StringRes
    private fun reasonFor(problem: Nearby.Problem?): Int = when (problem) {
        Nearby.Problem.NO_BLUETOOTH -> R.string.nearby_none
        Nearby.Problem.OFF -> R.string.nearby_off
        Nearby.Problem.NOT_ALLOWED -> R.string.nearby_not_allowed
        else -> R.string.nearby_not_found
    }

    private fun save(peer: PeerRow) {
        parts.scope.launch(Dispatchers.IO) { dao.updatePeer(peer) }
    }

    override fun rename(peer: PeerRow, nickname: String) = save(peer.copy(nickname = nickname, initial = initialOf(nickname, peer.initial)))

    override fun setColor(peer: PeerRow, color: Int) = save(peer.copy(color = color))

    override fun setLevel(peer: PeerRow, level: ShareLevel) = save(peer.copy(level = level.name))

    override fun setShareReport(peer: PeerRow, on: Boolean) = save(peer.copy(shareReport = on))

    override fun setShowMark(peer: PeerRow, on: Boolean) = save(peer.copy(showMark = on))

    override fun setShowLine(peer: PeerRow, on: Boolean) = save(peer.copy(showLine = on))

    override fun setShowMarks(peer: PeerRow, on: Boolean) = save(peer.copy(showMarks = on))

    /** A tick is kept against a repeating item's series, so every time of it is shared. */
    override fun tick(peer: Long, item: Item, on: Boolean) {
        val key = Sharing.tickKey(item)
        parts.scope.launch(Dispatchers.IO) {
            if (on) {
                dao.share(ShareRow(peer, key))
            } else {
                dao.unshare(peer, key)
                dao.unshare(peer, item.id)
            }
        }
    }

    /** Unpairs at once on this phone: the person, their keys, their ticks, and their last peek go. */
    override fun unpair(peer: Long) {
        parts.scope.launch(Dispatchers.IO) {
            dao.peers().firstOrNull { it.id == peer }?.let { keys.delete(it.peerId) }
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
                // Their day report too, so the panel shows one.
                listOf(
                    SharedLine(parts.text(R.string.pair_pretend_done), now.minusHours(2), Outcome.DONE),
                    SharedLine(parts.text(R.string.pair_pretend_task), null, Outcome.OPEN),
                ),
            )
            dao.putPeek(PeekRow(id, PeekFormat.encode(peek)))
        }
    }

    companion object {
        const val PRETEND = "pretend-partner"
        const val MAX_PAIRS = 3
        private const val PAIR_MS = 120_000L
        private const val PING_MS = 60_000L
        private const val ANSWER_MS = 90_000L
        private const val TAG = "Initiative"

        /** A nickname's initial: its first letter or digit, or [fallback] for none. */
        fun initialOf(nickname: String, fallback: String): String =
            nickname.firstOrNull { it.isLetterOrDigit() }?.titlecase() ?: fallback
    }
}
