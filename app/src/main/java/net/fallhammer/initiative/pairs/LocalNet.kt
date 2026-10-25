package net.fallhammer.initiative.pairs

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Finding another Initiative on the same Wi-Fi, and opening a stream to it:
 * the home-network twin of [Nearby]. Each side listens on a port of its own,
 * and announces Initiative's service on the local network (mDNS, Android's
 * network service discovery), named with a random number. Each side also looks
 * for the same service; when both see each other, the one with the lower number
 * connects. Only addresses on the local network are ever reached or let in:
 * Android's network permission covers the internet too, so the app keeps to the
 * home network itself.
 */
class LocalNet(private val context: Context) {
    private val nsd get() = context.getSystemService(NsdManager::class.java)
    private val random = SecureRandom()

    /** Whether this phone is on Wi-Fi now. */
    fun onWifi(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /** Announced and looking for [timeoutMs], until another Initiative on the Wi-Fi is met; null if none is. */
    @Suppress("DEPRECATION") // resolveService: its replacement needs Android 14, and this runs from Android 10.
    suspend fun meet(timeoutMs: Long): Link? = coroutineScope {
        if (!onWifi()) return@coroutineScope null
        val manager = nsd ?: return@coroutineScope null
        val server = runCatching { ServerSocket(0) }.getOrNull() ?: return@coroutineScope null
        val mine = random.nextInt() and Int.MAX_VALUE
        val myName = "$PREFIX$mine"
        val result = CompletableDeferred<Link>()
        val connecting = AtomicBoolean(false)

        val registered = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) {
                Log.i(TAG, "wifi: announce failed $error")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) = Unit
        }
        val found = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                val theirs = info.serviceName.removePrefix(PREFIX).toIntOrNull() ?: return
                // The lower number connects; the higher waits to be connected to. Once only.
                if (theirs == mine || mine > theirs || !connecting.compareAndSet(false, true)) return
                manager.resolveService(
                    info,
                    object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, error: Int) {
                            connecting.set(false)
                        }

                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val host = info.host
                            if (host == null || !isLocal(host)) {
                                Log.i(TAG, "wifi: not a local address, skipped")
                                connecting.set(false)
                                return
                            }
                            this@coroutineScope.launch(Dispatchers.IO) {
                                val socket = Socket()
                                runCatching {
                                    socket.connect(InetSocketAddress(host, info.port), CONNECT_MS)
                                    socket.soTimeout = READ_MS
                                }
                                    .onSuccess { if (!result.complete(socket.link(initiator = true))) socket.close() }
                                    .onFailure {
                                        Log.i(TAG, "wifi: connect failed: ${it.message}")
                                        socket.close()
                                        connecting.set(false)
                                    }
                            }
                        }
                    },
                )
            }

            override fun onDiscoveryStarted(type: String) = Unit
            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, error: Int) {
                Log.i(TAG, "wifi: look failed $error")
            }
            override fun onStopDiscoveryFailed(type: String, error: Int) = Unit
        }

        val accepting = launch(Dispatchers.IO) {
            // Anyone not on the local network is turned away, and the wait goes on.
            while (!result.isCompleted) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                if (!isLocal(socket.inetAddress)) {
                    socket.close()
                    continue
                }
                socket.soTimeout = READ_MS
                if (!result.complete(socket.link(initiator = false))) socket.close()
            }
        }
        val info = NsdServiceInfo().apply {
            serviceName = myName
            serviceType = TYPE
            port = server.localPort
        }
        try {
            manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registered)
            manager.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, found)
            withTimeoutOrNull(timeoutMs) { result.await() }
        } finally {
            runCatching { manager.stopServiceDiscovery(found) }
            runCatching { manager.unregisterService(registered) }
            runCatching { server.close() }
            accepting.cancel()
        }
    }

    private fun Socket.link(initiator: Boolean) = Link(getInputStream(), getOutputStream(), initiator) { close() }

    companion object {
        private const val TYPE = "_initiative._tcp"
        private const val PREFIX = "initiative-"
        private const val CONNECT_MS = 5_000
        private const val READ_MS = 20_000
        private const val TAG = "Initiative"

        /** On the home network: a private, link-local, or unique-local address; never the internet. */
        fun isLocal(address: InetAddress): Boolean {
            if (address.isSiteLocalAddress || address.isLinkLocalAddress) return true
            // IPv6 unique local addresses, fc00::/7, are the home network's own too.
            return address is Inet6Address && (address.address[0].toInt() and 0xFE) == 0xFC
        }
    }
}
