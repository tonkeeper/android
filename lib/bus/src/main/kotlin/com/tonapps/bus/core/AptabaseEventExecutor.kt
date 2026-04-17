package com.tonapps.bus.core

import android.content.Context
import android.net.Uri
import com.aptabase.Aptabase
import com.aptabase.InitOptions
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.async.Async
import com.tonapps.async.AsyncPlatform
import com.tonapps.bus.core.contract.EventExecutor
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class AptabaseEventExecutor : EventExecutor {

    private data class QueuedEvent(
        val eventName: String,
        val props: Map<String, Any>
    )

    private var installId: String? = null
    private var storeCountryCode: String? = null
    private var deviceCountryCode: String? = null
    private val version: String = "2.7.1"
    private val platform: String = "android-native"

    private val dispatcher = AsyncPlatform.createDispatcherPool("tk-analytic-queue")
    private val scope = Async.globalScope(dispatcher)

    private val eventQueue = ConcurrentLinkedQueue<QueuedEvent>()

    val isInitialized = AtomicBoolean(false)

    fun init(
        context: Context,
        appKey: String,
        host: String,
        installId: String,
        storeCountryCode: String?,
        deviceCountryCode: String?,
    ) {
        scope.launch {
            this@AptabaseEventExecutor.installId = installId
            this@AptabaseEventExecutor.storeCountryCode = storeCountryCode
            this@AptabaseEventExecutor.deviceCountryCode = deviceCountryCode
            val options = InitOptions(host = host)

            try {
                Aptabase.instance.initialize(context, appKey, options)
                if (isInitialized.compareAndSet(false, true)) {
                    processEventQueue()
                }
            } catch (e: Throwable) {
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
    }

    private fun processEventQueue() {
        scope.launch {
            if (!isInitialized.get()) {
                return@launch
            }

            while (eventQueue.isNotEmpty()) {
                val queuedEvent = eventQueue.poll()
                if (queuedEvent != null) {
                    send(queuedEvent.eventName, queuedEvent.props)
                }
            }
        }
    }

    override fun trackEvent(eventName: String, props: Map<String, Any>) {
        scope.launch {
            if (isInitialized.get()) {
                send(eventName, props)
            } else {
                eventQueue.offer(QueuedEvent(eventName, props))
            }
        }
    }

    private fun send(eventName: String, props: Map<String, Any> = hashMapOf()) {
        val fixedProps = props
            .mapValues {
                if (it is Uri) {
                    it.value.toString()
                } else {
                    it.value
                }
            }
            .toMutableMap()

        installId?.let { fixedProps["firebase_user_id"] = it }
        fixedProps["schema_version"] = version
        fixedProps["platform"] = platform

        storeCountryCode?.let { fixedProps["store_country_code"] = it }
        deviceCountryCode?.let { fixedProps["device_country_code"] = it }

        Aptabase.instance.trackEvent(eventName, fixedProps)
    }
}
