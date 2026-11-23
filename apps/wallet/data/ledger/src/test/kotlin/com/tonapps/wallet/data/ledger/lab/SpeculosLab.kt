package com.tonapps.wallet.data.ledger.lab

import com.tonapps.ledgerkit.LedgerProgress
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.LedgerPhase
import com.tonapps.ledgerkit.apdu.ApduResponse
import com.tonapps.ledgerkit.controller.LedgerController
import com.tonapps.ledgerkit.controller.LedgerOperationState
import com.tonapps.ledgerkit.transport.LedgerLink
import com.tonapps.ledgerkit.transport.LedgerModel
import com.tonapps.ledgerkit.transport.LedgerTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import kotlin.time.Duration

object SpeculosLab {

    private const val HOST = "127.0.0.1"
    private const val APDU_PORT = 9990
    private const val CONTROL_PORT = 9989

    val isRunning: Boolean by lazy {
        try {
            Socket().use { it.connect(InetSocketAddress(HOST, CONTROL_PORT), 500) }
            true
        } catch (e: IOException) {
            false
        }
    }

    val seed: String by lazy { Json.parseToJsonElement(get("/seed")).jsonObject.getValue("seed").jsonPrimitive.content }

    fun controller(): LedgerController {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = LedgerController(LabTransport(), scope)
        scope.launch {
            controller.state.collect { state ->
                if (state is LedgerOperationState.Failed) {
                    controller.dismiss()
                }
            }
        }
        return controller
    }

    fun policy(mode: String, vararg apps: String) {
        val body = mutableMapOf<String, JsonElement>("mode" to JsonPrimitive(mode))
        if (apps.isNotEmpty()) {
            body["apps"] = JsonArray(apps.map(::JsonPrimitive))
        }
        post("/policy", JsonObject(body))
    }

    fun set(vararg values: Pair<String, JsonElement>) {
        post("/set", JsonObject(values.toMap()))
    }

    fun restart(app: String) {
        post("/restart", JsonObject(mapOf("app" to JsonPrimitive(app))))
    }

    fun toggleSetting(app: String, setting: String) {
        val response = post("/toggle", JsonObject(mapOf("app" to JsonPrimitive(app), "setting" to JsonPrimitive(setting))))
        check(Json.parseToJsonElement(response).jsonObject["ok"]?.jsonPrimitive?.content == "true") {
            "The lab could not toggle '$setting' in $app"
        }
    }

    private fun get(path: String): String = request(path, null)

    private fun post(path: String, body: JsonObject): String = request(path, body.toString())

    private fun request(path: String, body: String?): String {
        val connection = URL("http://$HOST:$CONTROL_PORT$path").openConnection() as HttpURLConnection
        connection.connectTimeout = 2_000
        connection.readTimeout = 60_000
        if (body != null) {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toByteArray()) }
        }
        return connection.inputStream.use { it.readBytes().decodeToString() }
    }

    private class LabTransport : LedgerTransport {

        override suspend fun connect(deviceId: String, onProgress: (LedgerProgress) -> Unit): LedgerLink = withContext(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(HOST, APDU_PORT), 2_000)
                socket.tcpNoDelay = true
                LabLink(deviceId, socket)
            } catch (e: IOException) {
                throw LedgerFailureException(LedgerFailure.DeviceNotFound)
            }
        }
    }

    private class LabLink(override val deviceId: String, private val socket: Socket) : LedgerLink {

        private val input = DataInputStream(socket.getInputStream())
        private val output = DataOutputStream(socket.getOutputStream())
        private val mutex = Mutex()

        @Volatile
        private var closed = false

        override val model: LedgerModel = LedgerModel.Stax

        override val isConnected: Boolean
            get() = !closed && !socket.isClosed

        override suspend fun exchange(apdu: ByteArray, timeout: Duration?): ApduResponse = mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    socket.soTimeout = timeout?.inWholeMilliseconds?.toInt() ?: 0
                    output.writeInt(apdu.size)
                    output.write(apdu)
                    output.flush()
                    val size = input.readInt()
                    val data = ByteArray(size + 2)
                    input.readFully(data)
                    val statusWord = ((data[size].toInt() and 0xff) shl 8) or (data[size + 1].toInt() and 0xff)
                    ApduResponse(data.copyOfRange(0, size), statusWord)
                } catch (e: SocketTimeoutException) {
                    close()
                    throw LedgerFailureException(LedgerFailure.Timeout(LedgerPhase.Apdu))
                } catch (e: IOException) {
                    close()
                    throw LedgerFailureException(LedgerFailure.ConnectionLost)
                }
            }
        }

        override suspend fun awaitDisconnected() = withContext(Dispatchers.IO) {
            while (isActive && isConnected) {
                try {
                    socket.soTimeout = 200
                    if (input.read() == -1) {
                        closed = true
                    }
                } catch (e: SocketTimeoutException) {
                    continue
                } catch (e: IOException) {
                    closed = true
                }
            }
        }

        override fun close() {
            closed = true
            try {
                socket.close()
            } catch (e: IOException) {
                closed = true
            }
        }
    }
}
