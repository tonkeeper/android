package com.tonapps.network.ws

import com.tonapps.async.Async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

open class WSConnection(
    private val client: OkHttpClient,
    private val url: String,
    private val headers: Map<String, String> = emptyMap(),
    private val reconnectDelay: Long = 3000L
){

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var isManuallyDisconnected = false

    val scope = Async.ioScope()

    private val _events = MutableSharedFlow<WSEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<WSEvent> = _events.asSharedFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    init {
        connect()
    }

    fun connect() {
        if (_connectionState.value != ConnectionState.Disconnected) return

        isManuallyDisconnected = false
        doConnect()
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        _connectionState.value = ConnectionState.Reconnecting

        reconnectJob = scope.launch {
            delay(reconnectDelay)
            if (!isManuallyDisconnected) {
                doConnect()
            }
        }
    }

    private fun shouldReconnect(closeCode: Int): Boolean {
        return closeCode != 1000 && closeCode != 1001
    }

    private fun doConnect() {
        if (isManuallyDisconnected) return

        _connectionState.value = ConnectionState.Connecting

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                this@WSConnection.webSocket = webSocket
                _connectionState.value = ConnectionState.Connected
                _events.tryEmit(WSEvent.Opened)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                _events.tryEmit(WSEvent.Text(text))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                this@WSConnection.webSocket = null
                _connectionState.value = ConnectionState.Disconnected
                _events.tryEmit(WSEvent.Closed(code, reason))

                if (!isManuallyDisconnected && shouldReconnect(code)) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                this@WSConnection.webSocket = null
                _connectionState.value = ConnectionState.Disconnected
                _events.tryEmit(WSEvent.Failure(t))

                if (!isManuallyDisconnected) {
                    scheduleReconnect()
                }
            }
        }

        val request = Request.Builder()
            .url(url)
            .apply {
                headers.forEach { (key, value) ->
                    addHeader(key, value)
                }
            }
            .build()

        webSocket = client.newWebSocket(request, listener)
    }

    fun send(text: String): Boolean = webSocket?.send(text) ?: false

    fun send(bytes: ByteString): Boolean = webSocket?.send(bytes) ?: false

    fun disconnect() {
        isManuallyDisconnected = true
        reconnectJob?.cancel()
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
    }

    open fun release() {
        disconnect()
        scope.cancel()
    }
}