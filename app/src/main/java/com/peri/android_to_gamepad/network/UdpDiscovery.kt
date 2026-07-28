package com.peri.android_to_gamepad.network

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets

data class DiscoveredServer(val ip: String, val port: Int, val name: String)

class UdpDiscovery(private val context: Context) {
    companion object {
        private const val DISCOVERY_PORT = 5006
        private const val MAGIC_PREFIX = "AGP_HELLO|"
        private const val SOCKET_TIMEOUT_MS = 1000
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun start(
        timeoutMs: Long = 15_000,
        onFound: (DiscoveredServer) -> Unit,
        onTimeout: () -> Unit = {},
    ) {
        stop()
        job = scope.launch {
            var socket: DatagramSocket? = null
            try {
                val wifiManager = context.applicationContext
                    .getSystemService(Context.WIFI_SERVICE) as? WifiManager
                multicastLock = wifiManager?.createMulticastLock("gamepad_discovery")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }

                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(DISCOVERY_PORT))
                    soTimeout = SOCKET_TIMEOUT_MS
                }

                val result = withTimeoutOrNull(timeoutMs) { listenFor(socket) }
                if (result != null) onFound(result) else onTimeout()
            } catch (_: Exception) {
                onTimeout()
            } finally {
                releaseLock()
                socket?.close()
            }
        }
    }

    private fun listenFor(socket: DatagramSocket): DiscoveredServer? {
        val buf = ByteArray(128)
        val packet = DatagramPacket(buf, buf.size)
        val prefixBytes = MAGIC_PREFIX.toByteArray(StandardCharsets.UTF_8)

        while (true) {
            packet.length = buf.size
            try {
                socket.receive(packet)
            } catch (_: SocketTimeoutException) {
                continue
            }

            if (packet.length < prefixBytes.size) continue
            if (!buf.startsWith(prefixBytes, packet.length)) continue

            val ip = packet.address?.hostAddress ?: continue
            val name = packet.address?.hostName ?: "Unknown Device"
            val port = String(buf, prefixBytes.size, packet.length - prefixBytes.size, StandardCharsets.UTF_8)
                .trim().toIntOrNull() ?: continue

            return DiscoveredServer(ip, port, name)
        }
    }

    private fun ByteArray.startsWith(prefix: ByteArray, length: Int): Boolean {
        if (length < prefix.size) return false
        for (i in prefix.indices) if (this[i] != prefix[i]) return false
        return true
    }

    fun stop() {
        job?.cancel()
        job = null
        releaseLock()
    }

    private fun releaseLock() {
        try {
            multicastLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {}
        multicastLock = null
    }
}