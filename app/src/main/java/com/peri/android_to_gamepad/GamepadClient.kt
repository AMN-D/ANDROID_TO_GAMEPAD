package com.peri.android_to_gamepad

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class GamepadClient {
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val commandQueue = Channel<String>(Channel.UNLIMITED)

    init {
        // single writer loop -> guarantees commands are sent in the order queued
        scope.launch {
            for (command in commandQueue) {
                try {
                    outputStream?.write("$command\n".toByteArray())
                    outputStream?.flush()
                } catch (e: Exception) { /* dropped, connection likely dead */ }
            }
        }
    }

    // NEW: Accepts an IP address, defaults to 127.0.0.1 for wired/ADB
    fun connect(ip: String = "127.0.0.1", onResult: (String) -> Unit) {
        scope.launch {
            try {
                socket?.close() // close any previous connection first
                socket = Socket()

                // NEW: 2-second timeout. Prevents infinite hanging if wireless IP is wrong
                socket?.connect(InetSocketAddress(ip, 5005), 2000)
                socket?.tcpNoDelay = true

                outputStream = socket?.getOutputStream()
                onResult("Connected")
            } catch (e: Exception) {
                onResult("Failed: ${e.message}")
            }
        }
    }

    fun sendCommand(command: String) {
        commandQueue.trySend(command) // non-blocking, never spawns a new coroutine
    }

    fun disconnect() {
        scope.cancel()
        try { socket?.close() } catch (e: Exception) {}
    }
}