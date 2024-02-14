package com.tonapps.network

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retry
import com.tonapps.network.interceptor.AuthorizationInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

object Network {

    data class SSEvent(
        val id: String?,
        val type: String?,
        val data: String
    )

    private val holder = OkHttpClientHolder()

    val okHttpClient: OkHttpClient
        get() = holder.get()

    fun init(context: Context) {
        holder.init(context)
    }

    private val sseFactory: EventSource.Factory by lazy { EventSources.createFactory(holder.get()) }

    fun subscribe(url: String): Flow<SSEvent> = callbackFlow {
        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                this@callbackFlow.trySendBlocking(SSEvent(id, type, data))
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                this@callbackFlow.close(t)
            }

            override fun onClosed(eventSource: EventSource) {
                this@callbackFlow.close()
            }
        }
        val events = newEventSource(url, listener)
        awaitClose { events.cancel() }
    }.retry { _ ->
        delay(1000)
        true
    }

    fun newEventSource(url: String, listener: EventSourceListener): EventSource{
        val request = newRequest(url).build()
        return sseFactory.newEventSource(request, listener)
    }

    fun newRequest(url: String) = Request.Builder().url(url)

    fun newRequest(uri: Uri) = newRequest(uri.toString())

    fun newCall(request: Request) = holder.get().newCall(request)

    fun request(request: Request) = newCall(request).execute()

    fun get(url: String): String {
        val request = newRequest(url).build()
        val response = newCall(request).execute()
        return response.body?.string()!!
    }

}