package com.tonapps.bus.core

import android.content.Context
import android.os.Build
import android.os.Process
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.crashlytics.setCustomKeys
import com.tonapps.bus.core.contract.EventDelegate
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.core.tracer.AnalyticException
import com.tonapps.bus.generated.DefaultEvents
import com.tonapps.core.flags.FeatureKey
import java.util.concurrent.CancellationException
import kotlin.concurrent.atomics.AtomicReference

class AnalyticsHelper private constructor(
    private val executor: AptabaseEventExecutor = AptabaseEventExecutor(),
    private val delegate: DefaultEventDelegate = DefaultEventDelegate(executor),
    val events: DefaultEvents = DefaultEvents(executor)
) : EventDelegate by delegate {

    class Config(
        val aptabaseAppKey: String,
        val aptabaseEndpoint: String,
        val installId: String,
        val deviceId: String?,
        val storeCountryCode: String?,
        val deviceCountryCode: String?,
    )

    private val config = AtomicReference<Config?>(null)

    fun start(context: Context, persistentCacheFeature: FeatureKey) {
        executor.start(context, persistentCacheFeature)
    }

    fun setConfig(context: Context, initConfig: Config) {
        if (config.compareAndSet(null, initConfig)) {
            executor.setConfig(context, initConfig)
        }
    }

    fun eventsTaggedWith(utm: UtmParameters): DefaultEvents {
        if (utm.isEmpty) {
            return events
        }
        return DefaultEvents(UtmEventExecutor(executor, utm))
    }

    fun eventDelegateTaggedWith(utm: UtmParameters): EventDelegate {
        if (utm.isEmpty) {
            return delegate
        }
        return DefaultEventDelegate(UtmEventExecutor(executor, utm))
    }

    fun onAppForeground() {
        executor.onAppForeground()
    }

    fun onAppBackground() {
        executor.onAppBackground()
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

    fun applyInstallKeys(context: Context) {
        runCatching {
            val splitNames = context.applicationInfo.splitNames.orEmpty()
            val apkSplits = if (splitNames.isEmpty()) {
                "none"
            } else {
                splitNames.joinToString(",")
            }

            FirebaseCrashlytics.getInstance().setCustomKeys {
                key("installSource", getInitiatingPackageName(context) ?: "unknown")
                key("cpuAbi", Build.SUPPORTED_ABIS.joinToString(","))
                key("process64Bit", Process.is64Bit())
                key("apkSplits", apkSplits)
            }
        }
    }

    // TODO move apps/wallet/instance/app/src/main/java/com/tonapps/tonkeeper/os to the module
    private fun getInitiatingPackageName(context: Context): String? {
        return try {
            val packageManager = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                packageManager.getInstallSourceInfo(context.packageName).initiatingPackageName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstallerPackageName(context.packageName)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun captureException(error: AnalyticException) {
        if (isCancellation(error)) {
            return
        }

        runCatching {
            FirebaseCrashlytics.getInstance()
                .recordException(error)
        }
    }

    private fun isCancellation(error: Throwable): Boolean {
        return generateSequence(error.cause) { it.cause }
            .take(MAX_CAUSE_DEPTH)
            .any { it is CancellationException }
    }

    companion object {
        private const val MAX_CAUSE_DEPTH = 16

        val Default by lazy(LazyThreadSafetyMode.NONE) { AnalyticsHelper() }
    }
}
