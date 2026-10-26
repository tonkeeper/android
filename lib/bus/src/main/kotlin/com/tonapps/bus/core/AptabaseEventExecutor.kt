package com.tonapps.bus.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.aptabase.Aptabase
import com.aptabase.InitOptions
import com.tonapps.async.Async
import com.tonapps.async.AsyncPlatform
import com.tonapps.bus.core.aptabase.AnalyticsEventSequence
import com.tonapps.bus.core.aptabase.AnalyticsTransport
import com.tonapps.bus.core.aptabase.AnalyticsTransportProps
import com.tonapps.bus.core.aptabase.AptabaseDispatcher
import com.tonapps.bus.core.aptabase.AptabaseEndpoint
import com.tonapps.bus.core.aptabase.AptabaseEnvironment
import com.tonapps.bus.core.aptabase.AptabaseEventStore
import com.tonapps.bus.core.aptabase.AptabaseProps
import com.tonapps.bus.core.aptabase.AptabaseQueueClient
import com.tonapps.bus.core.aptabase.HttpUrlConnectionClient
import com.tonapps.bus.core.aptabase.PrefsSequenceStorage
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.core.flags.FeatureKey
import com.tonapps.core.flags.FeatureManager
import com.tonapps.core.flags.FeatureSource
import com.tonapps.extensions.currentTimeMillis
import com.tonapps.log.L
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

private const val PLATFORM = "android-native"
private const val STORE_DIRECTORY = "analytics"
private const val MAX_PENDING_EVENTS = 1000
private val FLUSH_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(60)
private val DEBUG_FLUSH_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(2)

class AptabaseEventExecutor(
    private val clock: () -> Long = ::currentTimeMillis,
) : EventExecutor {

    private class PendingEvent(
        val eventName: String,
        val props: Map<String, Any>,
        val timestampMillis: Long,
    ) {
        var sequence: Long? = null
    }

    private val dispatcher = AsyncPlatform.createDispatcherPool("tk-analytic-queue")
    private val scope = Async.globalScope(dispatcher)

    private val pendingEvents = ArrayDeque<PendingEvent>()
    private var isStarted = false
    private var cohortSource = FeatureSource.Default
    private lateinit var sequence: AnalyticsEventSequence
    private var queueClient: AptabaseQueueClient? = null
    private var config: AnalyticsHelper.Config? = null
    private var isSdkReady = false

    private val isReady: Boolean
        get() = queueClient != null || isSdkReady

    fun start(context: Context, persistentCacheFeature: FeatureKey) {
        scope.launch {
            guarded("start") {
                startNow(
                    context = context,
                    persistentCacheEnabled = FeatureManager.isEnabled(persistentCacheFeature),
                    cohortSource = FeatureManager.source(persistentCacheFeature),
                )
            }
        }
    }

    fun setConfig(context: Context, config: AnalyticsHelper.Config) {
        scope.launch {
            if (this@AptabaseEventExecutor.config != null) {
                return@launch
            }
            this@AptabaseEventExecutor.config = config
            guarded("setConfig") {
                if (!isStarted) {
                    startNow(context, persistentCacheEnabled = false, cohortSource = FeatureSource.Default)
                }
                val client = queueClient
                if (client != null) {
                    connect(client, config)
                } else {
                    Aptabase.instance.initialize(context, config.aptabaseAppKey, InitOptions(host = config.aptabaseEndpoint))
                    isSdkReady = true
                    L.d("Aptabase: sdk transport ready, ${pendingEvents.size} early events")
                    drainPendingEvents()
                }
            }
        }
    }

    override fun trackEvent(eventName: String, props: Map<String, Any>) {
        val pending = PendingEvent(eventName, props, clock())
        scope.launch {
            guarded("trackEvent") {
                if (isStarted) {
                    pending.sequence = sequence.next()
                }
                if (isReady) {
                    dispatch(pending)
                } else {
                    if (pendingEvents.size >= MAX_PENDING_EVENTS) {
                        pendingEvents.removeFirst()
                    }
                    pendingEvents.addLast(pending)
                }
            }
        }
    }

    fun onAppForeground() {
        scope.launch {
            queueClient?.resumed()
        }
    }

    fun onAppBackground() {
        scope.launch {
            queueClient?.suspended()
        }
    }

    private fun startNow(context: Context, persistentCacheEnabled: Boolean, cohortSource: FeatureSource) {
        if (isStarted) {
            return
        }
        sequence = AnalyticsEventSequence(PrefsSequenceStorage(context))
        this.cohortSource = cohortSource
        isStarted = true
        val storeDirectory = File(context.noBackupFilesDir, STORE_DIRECTORY)
        if (!persistentCacheEnabled) {
            AptabaseEventStore.purge(storeDirectory)
            L.d("Aptabase: persistent cache disabled, cohort $cohortSource")
            return
        }
        val environment = AptabaseEnvironment.current(context)
        val client = AptabaseQueueClient(
            store = AptabaseEventStore(directory = storeDirectory, clock = clock),
            environment = environment,
            scope = scope,
            networkDispatcher = Async.Io,
            flushIntervalMillis = if (environment.isDebug) {
                DEBUG_FLUSH_INTERVAL_MILLIS
            } else {
                FLUSH_INTERVAL_MILLIS
            },
            clock = clock,
        )
        client.start()
        queueClient = client
        observeNetwork(context, client)
        L.d("Aptabase: persistent cache enabled, cohort $cohortSource, ${pendingEvents.size} early events")
        drainPendingEvents()
    }

    private fun connect(client: AptabaseQueueClient, config: AnalyticsHelper.Config) {
        val baseUrl = AptabaseEndpoint.resolve(config.aptabaseAppKey, config.aptabaseEndpoint)
        if (baseUrl == null) {
            L.w("Aptabase: invalid app key or endpoint, events stay queued")
            IssueHelper.recordException(
                IllegalStateException("Aptabase endpoint is unusable: key=${config.aptabaseAppKey} host=${config.aptabaseEndpoint}")
            )
            return
        }
        client.connect(
            dispatcher = AptabaseDispatcher(baseUrl, config.aptabaseAppKey, HttpUrlConnectionClient()),
            sharedProps = AptabaseProps.toJson(configProps(config)),
        )
        L.d("Aptabase: connected to $baseUrl")
    }

    private fun observeNetwork(context: Context, client: AptabaseQueueClient) {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java) ?: return
        try {
            connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    client.networkAvailable()
                }
            })
        } catch (e: RuntimeException) {
            L.w(e, "Aptabase: network callback is unavailable")
        }
    }

    private fun drainPendingEvents() {
        while (pendingEvents.isNotEmpty()) {
            dispatch(pendingEvents.removeFirst())
        }
    }

    private fun dispatch(pending: PendingEvent) {
        val client = queueClient
        if (client != null) {
            client.track(
                AptabaseQueueClient.PendingEvent(
                    name = pending.eventName,
                    props = AptabaseProps.toJson(baseProps(pending, AnalyticsTransport.Cache)),
                    timestampMillis = pending.timestampMillis,
                )
            )
            return
        }
        val config = config ?: return
        Aptabase.instance.trackEvent(pending.eventName, baseProps(pending, AnalyticsTransport.Sdk) + configProps(config))
    }

    private fun baseProps(pending: PendingEvent, transport: AnalyticsTransport): Map<String, Any> {
        val props = HashMap<String, Any>(AptabaseProps.normalize(pending.props))
        props["schema_version"] = Events.VERSION
        props["platform"] = PLATFORM
        AnalyticsTransportProps.decorate(
            props = props,
            transport = transport,
            cohortSource = cohortSource,
            sequence = pending.sequence ?: sequence.next(),
        )
        return props
    }

    private fun configProps(config: AnalyticsHelper.Config): Map<String, Any> {
        val props = HashMap<String, Any>()
        props["firebase_user_id"] = config.installId
        props[AnalyticsTransportProps.INSTALL_ID] = config.installId
        config.deviceId?.let { props["device_id"] = it }
        config.storeCountryCode?.let { props["store_country_code"] = it }
        config.deviceCountryCode?.let { props["device_country_code"] = it }
        return props
    }

    private inline fun guarded(stage: String, block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.e(e, "Aptabase: $stage failed")
            IssueHelper.recordException(e)
        }
    }
}
