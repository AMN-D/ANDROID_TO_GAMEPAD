package com.peri.android_to_gamepad

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.OutputStream
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

    fun connect(onResult: (String) -> Unit) {
        scope.launch {
            try {
                socket?.close() // close any previous connection first
                socket = Socket("127.0.0.1", 5005).apply { tcpNoDelay = true }
                outputStream = socket?.getOutputStream()
                onResult("Connected to PC!")
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