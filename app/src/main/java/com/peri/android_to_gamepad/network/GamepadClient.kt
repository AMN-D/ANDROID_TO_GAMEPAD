package com.peri.android_to_gamepad.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

sealed class ConnectionStatus {
    object Idle : ConnectionStatus()
    object Connecting : ConnectionStatus()
    data class Connected(val ip: String = "") : ConnectionStatus()
    object Authenticated : ConnectionStatus()
    object Unauthorized : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
}

class GamepadClient {
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val commandQueue = Channel<String>(Channel.BUFFERED)
    private var connectionStatus: ConnectionStatus = ConnectionStatus.Idle
    private var connectionJob: Job? = null

    init {
        scope.launch {
            for (command in commandQueue) {
                if (connectionStatus != ConnectionStatus.Authenticated && !command.startsWith("AUTH:")) continue
                try {
                    outputStream?.write((command + "\n").toByteArray(StandardCharsets.US_ASCII))
                    outputStream?.flush()
                } catch (_: Exception) {}
            }
        }
    }

    fun connect(ip: String = "127.0.0.1", port: Int = 5005, pin: String = "", onResult: (ConnectionStatus) -> Unit) {
        connectionJob?.cancel()
        connectionJob = scope.launch {
            try {
                updateStatus(ConnectionStatus.Connecting, onResult)
                socket?.close()
                socket = Socket()
                socket?.connect(InetSocketAddress(ip, port), 2000)
                socket?.tcpNoDelay = true
                outputStream = BufferedOutputStream(socket?.getOutputStream() ?: throw Exception("Stream null"))
                val reader = BufferedReader(InputStreamReader(socket?.getInputStream()))

                // Send Auth
                outputStream?.write(("AUTH:$pin\n").toByteArray(StandardCharsets.US_ASCII))
                outputStream?.flush()

                // Wait for response
                val response = withTimeout(3000) { reader.readLine() }
                if (response == "AUTH_OK") {
                    updateStatus(ConnectionStatus.Authenticated, onResult)
                } else {
                    updateStatus(ConnectionStatus.Unauthorized, onResult)
                    disconnect()
                }
            } catch (e: Exception) {
                updateStatus(ConnectionStatus.Error(e.message ?: "Unknown Error"), onResult)
            }
        }
    }

    private fun updateStatus(status: ConnectionStatus, callback: (ConnectionStatus) -> Unit) {
        connectionStatus = status
        callback(status)
    }

    fun connect(server: DiscoveredServer, pin: String = "", onResult: (ConnectionStatus) -> Unit) {
        connect(ip = server.ip, port = server.port, pin = pin, onResult = onResult)
    }

    fun sendCommand(command: String) {
        commandQueue.trySend(command)
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        try {
            socket?.close()
            socket = null
            outputStream = null
            connectionStatus = ConnectionStatus.Idle
        } catch (_: Exception) {}
    }
}
