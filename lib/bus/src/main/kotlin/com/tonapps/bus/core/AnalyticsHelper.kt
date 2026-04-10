package com.tonapps.bus.core

import android.content.Context
import com.tonapps.bus.core.contract.EventDelegate
import com.tonapps.bus.generated.DefaultEvents
import kotlin.concurrent.atomics.AtomicReference

class AnalyticsHelper private constructor(
    private val executor: AptabaseEventExecutor = AptabaseEventExecutor(),
    private val delegate: DefaultEventDelegate = DefaultEventDelegate(executor),
    val events: DefaultEvents = DefaultEvents(executor)
) : EventDelegate by delegate {

    companion object {
        val Default by lazy(LazyThreadSafetyMode.NONE) { AnalyticsHelper() }
    }

    class Config(
        val aptabaseAppKey: String,
        val aptabaseEndpoint: String,
        val installId: String,
        val storeCountryCode: String?,
        val deviceCountryCode: String?,
    )

    private val config = AtomicReference<Config?>(null)

    fun setConfig(context: Context, initConfig: Config) {
        if (config.compareAndSet(null, initConfig)) {
            executor.init(
                context = context,
                appKey = initConfig.aptabaseAppKey,
                host = initConfig.aptabaseEndpoint,
                installId = initConfig.installId,
                storeCountryCode = initConfig.storeCountryCode,
                deviceCountryCode = initConfig.deviceCountryCode
            )
        }
    }

    @Deprecated("Use `delegate` instead")
    fun simpleTrackScreenEvent(eventName: String, from: String) {
        simpleTrackEvent(
            eventName, hashMapOf(
                "from" to from
            )
        )
    }

    @Deprecated("Use `delegate` instead")
    fun simpleTrackEvent(
        eventName: String,
        props: MutableMap<String, Any> = hashMapOf()
    ) {
        executor.trackEvent(eventName, props)
    }
}