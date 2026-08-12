package com.garagepi.telemetry.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

private const val TAG = "Elm327"

/**
 * Classic Bluetooth SPP link to an ELM327 OBD2 adapter. One connection is one
 * command/response session — callers serialize their own command sends.
 */
class Elm327Connection private constructor(
    private val socket: BluetoothSocket,
    private val input: InputStream,
    private val output: OutputStream,
) {
    val isConnected: Boolean get() = socket.isConnected

    /** Sends a command and reads until the ELM327 '>' prompt. Blocking; call off the main thread. */
    suspend fun sendCommand(command: String, timeoutMs: Long = 5_000): String = withContext(Dispatchers.IO) {
        // Drop anything left in the buffer from a previous command that timed out. Without this
        // a single slow response desyncs every later read — each one returns the *previous*
        // command's leftovers — and the session never recovers until reconnect.
        var stale = 0
        while (input.available() > 0) {
            if (input.read() == -1) break
            stale++
        }
        if (stale > 0) Log.w(TAG, "discarded $stale stale byte(s) before '$command'")

        output.write("$command\r".toByteArray())
        output.flush()

        val buffer = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        var sawPrompt = false
        while (System.currentTimeMillis() < deadline) {
            if (input.available() > 0) {
                val byte = input.read()
                if (byte == -1) break
                val ch = byte.toChar()
                if (ch == '>') {
                    sawPrompt = true
                    break
                }
                buffer.append(ch)
            } else {
                Thread.sleep(5)
            }
        }

        val response = buffer.toString()
            .replace("\r", "\n")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "OK" }
            .joinToString(" ")
        // Raw responses are the only way to diagnose adapter/vehicle quirks in the car.
        if (sawPrompt) Log.d(TAG, "$command -> '$response'")
        else Log.w(TAG, "$command -> TIMEOUT after ${timeoutMs}ms, partial='$response'")
        response
    }

    fun close() {
        runCatching { socket.close() }
    }

    companion object {
        /**
         * Connects to the adapter, trying each known-good strategy in turn.
         *
         * The plain secure-SPP path fails on many ELM327 clones — their SDP records are
         * missing or malformed (the Bluetooth stack logs `SDP_CFG_FAILED`), and
         * `connect()` then dies with "read failed, socket might closed or timeout,
         * read ret: -1". The insecure variant skips pairing-level encryption that some
         * clones don't implement, and the reflection path bypasses SDP entirely by
         * hardwiring RFCOMM channel 1, which is where these adapters always listen.
         */
        @SuppressLint("MissingPermission")
        suspend fun connect(device: BluetoothDevice, adapter: BluetoothAdapter?): Elm327Connection =
            withContext(Dispatchers.IO) {
                // Discovery starves the radio and makes connect() fail; always stop it first.
                runCatching { adapter?.cancelDiscovery() }

                val strategies: List<Pair<String, () -> BluetoothSocket>> = listOf(
                    "secure SPP" to { device.createRfcommSocketToServiceRecord(SPP_UUID) },
                    "insecure SPP" to { device.createInsecureRfcommSocketToServiceRecord(SPP_UUID) },
                    "reflection channel 1" to {
                        device.javaClass
                            .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                            .invoke(device, 1) as BluetoothSocket
                    },
                )

                var lastError: Exception? = null
                for ((name, createSocket) in strategies) {
                    val socket = try {
                        createSocket()
                    } catch (e: Exception) {
                        Log.w(TAG, "could not create socket ($name): ${e.message}")
                        lastError = e
                        continue
                    }
                    try {
                        socket.connect()
                        Log.i(TAG, "connected to ${device.address} via $name")
                        return@withContext Elm327Connection(socket, socket.inputStream, socket.outputStream)
                    } catch (e: Exception) {
                        Log.w(TAG, "connect via $name failed: ${e.message}")
                        lastError = e
                        runCatching { socket.close() }
                        Thread.sleep(300) // let the stack settle before the next attempt
                    }
                }
                throw IOException(
                    "Could not connect to ${device.address}: ${lastError?.message ?: "unknown error"}",
                    lastError,
                )
            }
    }
}
