package com.peri.android_to_gamepad.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

sealed class ConnectionStatus {
    object Idle : ConnectionStatus()
    object Connecting : ConnectionStatus()
    data class Connected(val ip: String = "") : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
}

class GamepadClient {
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val commandQueue = Channel<String>(Channel.BUFFERED)

    init {
        scope.launch {
            for (command in commandQueue) {
                try {
                    outputStream?.write((command + "\n").toByteArray(StandardCharsets.US_ASCII))
                    outputStream?.flush()
                } catch (_: Exception) {}
            }
        }
    }

    fun connect(ip: String = "127.0.0.1", port: Int = 5005, onResult: (ConnectionStatus) -> Unit) {
        scope.launch {
            try {
                onResult(ConnectionStatus.Connecting)
                socket?.close()
                socket = Socket()
                socket?.connect(InetSocketAddress(ip, port), 2000)
                socket?.tcpNoDelay = true
                outputStream = BufferedOutputStream(socket?.getOutputStream() ?: throw Exception("Stream null"))
                onResult(ConnectionStatus.Connected(ip = ip))
            } catch (e: Exception) {
                onResult(ConnectionStatus.Error(e.message ?: "Unknown Error"))
            }
        }
    }

    fun connect(server: DiscoveredServer, onResult: (ConnectionStatus) -> Unit) {
        connect(ip = server.ip, port = server.port, onResult = onResult)
    }

    fun sendCommand(command: String) {
        commandQueue.trySend(command)
    }

    fun disconnect() {
        try {
            socket?.close()
            socket = null
            outputStream = null
        } catch (_: Exception) {}
    }
}