package com.tonkeeper.core.tonconnect

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tonkeeper.core.tonconnect.models.TCEvent
import okhttp3.Request
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import core.network.Network
import okhttp3.Response

internal class Realtime(
    context: Context,
    private val onEvent: (TCEvent) -> Unit
): EventSourceListener() {

    private companion object {
        private const val EVENT_ID = "event_id"
    }

    private val prefs = context.getSharedPreferences("tc_realtime", Context.MODE_PRIVATE)
    private var eventSource: EventSource? = null

    private var lastEventId: String
        get() = prefs.getString(EVENT_ID, "") ?: ""
        set(value) {
            prefs.edit().putString(EVENT_ID, value).apply()
        }

    fun start(clientIds: List<String>) {
        release()

        val value = clientIds.joinToString(",")
        var uri = Uri.parse("${Bridge.DEFAULT_URL}/events?client_id=$value")
        if (lastEventId != "") {
            uri = uri.buildUpon().appendQueryParameter("last_event_id", lastEventId).build()
        }

        eventSource = Network.newEventSource(uri.toString(), this)
    }

    override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
        super.onEvent(eventSource, id, type, data)
        id?.let { lastEventId = it }

        try {
            onEvent(TCEvent(data))
        } catch (e: Throwable) {
            Log.e("TonConnectLog", "onEvent error", e)
        }
    }

    override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
        super.onFailure(eventSource, t, response)
        Log.e("TonConnectLog", "onFailure", t)
    }

    override fun onOpen(eventSource: EventSource, response: Response) {
        super.onOpen(eventSource, response)
        Log.d("TonConnectLog", "onOpen")
    }

    override fun onClosed(eventSource: EventSource) {
        super.onClosed(eventSource)
        Log.d("TonConnectLog", "onClosed")
    }

    fun release() {
        eventSource?.cancel()
        eventSource = null
    }
}