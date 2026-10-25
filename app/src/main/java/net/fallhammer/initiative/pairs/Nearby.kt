package net.fallhammer.initiative.pairs

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Finding another Initiative close by, over Bluetooth Low Energy, and opening a
 * stream to it. Only while someone taps Pair or Ping, or, with Sync over
 * Bluetooth always on, for one minute in every ten: the phone is findable and
 * searching for that minute, then neither.
 *
 * Each side listens on a Bluetooth channel (L2CAP, Android 10 and later), and
 * advertises Initiative's service with what's needed to reach it: the kind of
 * meeting (pair or ping), the channel number, and a random number. Each side
 * also scans for the same service, filtered, in one scan per meeting (Android
 * quietly drops scans started too often). When both see each other, the one
 * with the lower random number connects; the other accepts. Bluetooth sockets
 * need no internet permission, so Initiative stays offline.
 *
 * Practices from Android's guides: one scan per meeting, filtered to the
 * service; addresses rotate, so phones are known by their own IDs, never their
 * Bluetooth address; a failed connect is closed and not retried in a loop.
 */
class Nearby(private val context: Context) {
    private val manager get() = context.getSystemService(BluetoothManager::class.java)
    private val random = SecureRandom()

    /** Why a meeting couldn't start or find anyone. */
    enum class Problem { NO_BLUETOOTH, OFF, NOT_ALLOWED, NOT_FOUND }

    /** A meeting's result: an open stream, and whether this phone made the connection. */
    class Met(val socket: BluetoothSocket, val initiator: Boolean) {
        fun link() = Link(socket.inputStream, socket.outputStream, initiator) { socket.close() }
    }

    fun allowed(): Boolean = PERMISSIONS.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    fun problem(): Problem? {
        // The Nearby devices permission, and so Pairs, needs Android 12 or later.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return Problem.NO_BLUETOOTH
        val adapter = manager?.adapter ?: return Problem.NO_BLUETOOTH
        if (!allowed()) return Problem.NOT_ALLOWED
        if (!adapter.isEnabled) return Problem.OFF
        return null
    }

    /**
     * Findable and searching for [timeoutMs], until another Initiative doing the
     * same [kind] of meeting is met. Returns the open stream, or a problem.
     */
    @SuppressLint("MissingPermission") // Checked by problem() first.
    suspend fun meet(kind: Byte, timeoutMs: Long): Pair<Met?, Problem?> = coroutineScope {
        problem()?.let { return@coroutineScope null to it }
        val adapter = manager!!.adapter
        val advertiser = adapter.bluetoothLeAdvertiser ?: return@coroutineScope null to Problem.NO_BLUETOOTH
        val scanner = adapter.bluetoothLeScanner ?: return@coroutineScope null to Problem.NO_BLUETOOTH
        val server = runCatching { adapter.listenUsingInsecureL2capChannel() }.getOrNull()
            ?: return@coroutineScope null to Problem.NO_BLUETOOTH
        val mine = random.nextInt()
        val result = CompletableDeferred<Met>()
        val connecting = AtomicBoolean(false)
        // A connect under way, closed if the minute runs out first.
        val trying = AtomicReference<BluetoothSocket?>(null)

        val advertised = object : AdvertiseCallback() {
            override fun onStartFailure(errorCode: Int) {
                Log.i(TAG, "advertise failed: $errorCode")
            }
        }
        val scanned = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, r: ScanResult) {
                val data = r.scanRecord?.getManufacturerSpecificData(MAKER) ?: return
                if (data.size < 7 || data[0] != kind) return
                val psm = ((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF)
                val theirs = ((data[3].toInt() and 0xFF) shl 24) or ((data[4].toInt() and 0xFF) shl 16) or
                    ((data[5].toInt() and 0xFF) shl 8) or (data[6].toInt() and 0xFF)
                // The lower number connects; the higher waits to be connected to. Once only.
                if (theirs == mine || mine > theirs || !connecting.compareAndSet(false, true)) return
                this@coroutineScope.launch(Dispatchers.IO) {
                    val socket = r.device.createInsecureL2capChannel(psm)
                    trying.set(socket)
                    runCatching { socket.connect() }
                        .onSuccess { if (!result.complete(Met(socket, initiator = true))) socket.close() }
                        .onFailure {
                            Log.i(TAG, "connect failed: ${it.message}")
                            socket.close()
                            connecting.set(false)
                        }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.i(TAG, "scan failed: $errorCode")
            }
        }

        val accepting = launch(Dispatchers.IO) {
            runCatching { server.accept() }.onSuccess { if (!result.complete(Met(it, initiator = false))) it.close() }
        }
        val record = byteArrayOf(
            kind,
            (server.psm shr 8).toByte(), server.psm.toByte(),
            (mine shr 24).toByte(), (mine shr 16).toByte(), (mine shr 8).toByte(), mine.toByte(),
        )
        try {
            advertiser.startAdvertising(
                AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setConnectable(true)
                    .setTimeout(timeoutMs.coerceAtMost(MAX_ADVERTISE_MS).toInt())
                    .build(),
                AdvertiseData.Builder().addServiceUuid(SERVICE).build(),
                AdvertiseData.Builder().addManufacturerData(MAKER, record).build(),
                advertised,
            )
            scanner.startScan(
                listOf(ScanFilter.Builder().setServiceUuid(SERVICE).build()),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                scanned,
            )
            val met = withTimeoutOrNull(timeoutMs) { result.await() }
            met to (if (met == null) Problem.NOT_FOUND else null)
        } finally {
            runCatching { scanner.stopScan(scanned) }
            runCatching { advertiser.stopAdvertising(advertised) }
            // Closing the channel ends a waiting accept; an unfinished connect is closed too.
            runCatching { server.close() }
            if (!result.isCompleted) runCatching { trying.get()?.close() }
            accepting.cancel()
        }
    }

    companion object {
        const val PAIR: Byte = 1
        const val PING: Byte = 2

        /** The always-on sync's meetings, apart from Ping's, so a tap and a background sync don't meet. */
        const val SYNC: Byte = 3

        /** Initiative's own Bluetooth service ID. */
        private val SERVICE = ParcelUuid(UUID.fromString("7a1c0e52-4b1d-4f0e-9c3a-1e2b5d6f8a90"))

        /** Bluetooth's company ID kept for testing and private use. */
        private const val MAKER = 0xFFFF

        /** Android stops an advertisement by itself after at most three minutes. */
        private const val MAX_ADVERTISE_MS = 180_000L
        private const val TAG = "Initiative"

        val PERMISSIONS = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    }
}
