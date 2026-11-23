package com.tonapps.wallet.api

import android.content.Context
import com.tonapps.extensions.Os
import com.tonapps.extensions.cacheFolder
import com.tonapps.extensions.locale
import com.tonapps.network.interceptor.AcceptLanguageInterceptor
import com.tonapps.network.interceptor.AuthorizationInterceptor
import com.tonapps.network.interceptor.LoggingInterceptor
import com.tonapps.network.interceptor.RateLimitInterceptor
import com.tonapps.network.interceptor.ServerTimeInterceptor
import com.tonapps.network.interceptor.Tron429RetryInterceptor
import com.tonapps.network.safeProxySelector
import com.tonapps.time.ServerTimeProvider
import com.tonapps.wallet.api.entity.ConfigEntity
import com.tonapps.wallet.api.internal.DeviceAuthInterceptor
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

//import org.chromium.net.CronetEngine
//import com.google.android.gms.net.CronetProviderInstaller
//import com.google.net.cronet.okhttptransport.CronetInterceptor

abstract class CoreAPI(
    private val context: Context,
    private val delegate: LoggingInterceptor.Delegate,
    val appVersionName: String,
    serverTime: ServerTimeProvider,
) {

    private val serverTimeInterceptor = ServerTimeInterceptor(serverTime, SERVER_TIME_DOMAINS)

    // e.g. Tonkeeper/1.2.3 (Android; 14; Pixel Tablet)
    val userAgent = Os.tonkeeperUserAgent(appVersionName)

    @Volatile
    protected var deviceAuthProvider: DeviceAuthProvider? = null
        private set

//    private var cronetEngine: CronetEngine? = null

    val defaultHttpClient = baseOkHttpClientBuilder(
//        cronetEngine = { cronetEngine },
        timeoutSeconds = 30,
        rateLimit = 15,
        context = context,
        interceptors = listOf(
            UserAgentInterceptor(userAgent),
        ),
        serverTimeInterceptor = serverTimeInterceptor,
        delegate = delegate,
    ).build()


    val tronHttpClient = baseOkHttpClientBuilder(
//        cronetEngine = { cronetEngine },
        timeoutSeconds = 30,
        // No proactive client throttle — TronGrid rate limits vary; retry on 429 instead.
        rateLimit = 0,
        context = context,
        interceptors = listOf(
            UserAgentInterceptor(userAgent),
            Tron429RetryInterceptor(),
        ),
        serverTimeInterceptor = serverTimeInterceptor,
        delegate = delegate,
    ).build()

    fun setDeviceAuthProvider(provider: DeviceAuthProvider) {
        deviceAuthProvider = provider
    }

    protected fun walletAuthToken(walletId: String, accessToken: String): String? {
        return deviceAuthProvider?.walletAuthToken(walletId, accessToken)
    }

    fun sseHttpClient(config: () -> ConfigEntity): OkHttpClient {
        return createTonAPIHttpClient(
//        cronetEngine = { null },
            timeoutSeconds = 60,
            context = context,
            userAgent = userAgent,
            tonApiV2Key = { config().tonApiV2Key },
            allowDomains = { config().domains },
            deviceAuthHosts = { deviceAuthHosts(config()) },
            deviceAuthProvider = { deviceAuthProvider },
            serverTimeInterceptor = serverTimeInterceptor,
            delegate = delegate,
        )
    }

//    init {
//        requestCronet(context, userAgent) {
//            cronetEngine = it
//        }
//    }

    fun tonAPIHttpClient(config: () -> ConfigEntity): OkHttpClient {
        return createTonAPIHttpClient(
            context = context,
            userAgent = userAgent,
            tonApiV2Key = { config().tonApiV2Key },
            allowDomains = { config().domains },
            deviceAuthHosts = { deviceAuthHosts(config()) },
            deviceAuthProvider = { deviceAuthProvider },
            serverTimeInterceptor = serverTimeInterceptor,
            delegate = delegate,
//            cronetEngine = { cronetEngine }
        )
    }

    protected fun <T> withExtraHeaders(headers: Map<String, String>, request: () -> T): T {
        if (headers.isEmpty()) {
            return request()
        }

        val previous = ExtraHeadersInterceptor.current.get()
        ExtraHeadersInterceptor.current.set(headers)
        try {
            return request()
        } finally {
            if (previous == null) {
                ExtraHeadersInterceptor.current.remove()
            } else {
                ExtraHeadersInterceptor.current.set(previous)
            }
        }
    }

    private companion object {

        const val MAX_CACHE_SIZE = 500L * 1024 * 1024

        // Our servers only: the clients also fetch dApp manifests and pages, whose date must not set the app's time.
        val SERVER_TIME_DOMAINS = listOf("tonkeeper.com", "tonapi.io")

        object ExtraHeadersInterceptor : Interceptor {

            val current = ThreadLocal<Map<String, String>>()

            override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
                val headers = current.get() ?: return chain.proceed(chain.request())
                val request = chain.request().newBuilder()
                for ((name, value) in headers) {
                    request.header(name, value)
                }
                return chain.proceed(request.build())
            }
        }

        class UserAgentInterceptor(private val userAgent: String) : Interceptor {
            override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
                val request = chain.request().newBuilder()
                    .addHeader("User-Agent", userAgent)
                    .build()
                return chain.proceed(request)
            }
        }

        private val ownHosts: Set<String> by lazy {
            setOfNotNull(
                Constants.SWAP_API.toHttpUrlOrNull()?.host,
                Constants.TRADING_API.toHttpUrlOrNull()?.host,
                Constants.MULTICHAIN_API.toHttpUrlOrNull()?.host,
            )
        }

        private fun baseOkHttpClientBuilder(
            timeoutSeconds: Long = 30,
            callTimeoutSeconds: Long = timeoutSeconds,
            rateLimit: Int = 10,
            interceptors: List<Interceptor> = emptyList(),
            context: Context,
            serverTimeInterceptor: ServerTimeInterceptor,
            delegate: LoggingInterceptor.Delegate,
//            cronetEngine: () -> CronetEngine?,
        ): OkHttpClient.Builder {
            val builder = OkHttpClient().newBuilder()
                .retryOnConnectionFailure(true)
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .callTimeout(callTimeoutSeconds, TimeUnit.SECONDS)
                .pingInterval(timeoutSeconds, TimeUnit.SECONDS)
                .cache(
                    Cache(
                        context.cacheFolder("cronet"),
                        MAX_CACHE_SIZE
                    )
                )
                // TODO additional setup like connectionPool etc
//                .connectionPool(
//                    ConnectionPool(
//                        maxIdleConnections = 16,
//                        keepAliveDuration = 30,
//                        timeUnit = TimeUnit.SECONDS,
//                    )
//                )
                .followSslRedirects(true)
                .followRedirects(true)
                .safeProxySelector()

            for (interceptor in interceptors) {
                builder.addInterceptor(interceptor)
            }

//                cronetEngine()?.let { engine ->
//                    builder.addInterceptor(
//                        CronetInterceptor.newBuilder(engine)
//                            .build()
//                    )
//                }

            if (rateLimit > 0) {
                builder.addInterceptor(
                    RateLimitInterceptor(requestsPerSecondLimit = rateLimit)
                )
            }

            builder.addInterceptor(
                LoggingInterceptor(delegate = delegate)
            )

            builder.addNetworkInterceptor(serverTimeInterceptor)

            return builder
        }

        private fun deviceAuthHosts(config: ConfigEntity): Set<String> {
            return ownHosts + listOfNotNull(
                config.tradingApiUrl.toHttpUrlOrNull()?.host,
                config.multichainApiUrl.toHttpUrlOrNull()?.host,
                config.keeperEarnApiUrl.toHttpUrlOrNull()?.host,
                config.batteryHost.toHttpUrlOrNull()?.host,
                config.batteryTestnetHost.toHttpUrlOrNull()?.host,
            )
        }

        private fun createTonAPIHttpClient(
            userAgent: String,
            context: Context,
            timeoutSeconds: Long = 30,
            tonApiV2Key: () -> String,
            allowDomains: () -> List<String>,
            deviceAuthHosts: () -> Collection<String>,
            deviceAuthProvider: () -> DeviceAuthProvider?,
            serverTimeInterceptor: ServerTimeInterceptor,
            delegate: LoggingInterceptor.Delegate,
//            cronetEngine: () -> CronetEngine?,
        ): OkHttpClient {
            val interceptors = mutableListOf(
                UserAgentInterceptor(userAgent),
                AcceptLanguageInterceptor(context.locale),
                AuthorizationInterceptor.bearer(
                    token = tonApiV2Key,
                    allowDomains = allowDomains
                ),
                DeviceAuthInterceptor(
                    hosts = deviceAuthHosts,
                    provider = deviceAuthProvider,
                ),
                ExtraHeadersInterceptor,
            )

            return baseOkHttpClientBuilder(
                timeoutSeconds = timeoutSeconds,
                callTimeoutSeconds = 0,
                // No proactive throttle: a cold launch sends ~25 calls at once, and the queue delayed the portfolio
                // by up to a second. Calls in withRetry retry a 429; iOS has no client throttle either.
                rateLimit = 0,
                context = context,
                interceptors = interceptors,
                serverTimeInterceptor = serverTimeInterceptor,
                delegate = delegate,
//                cronetEngine = cronetEngine,
            ).build()
        }

//        private fun requestCronet(context: Context, userAgent: String, callback: (CronetEngine) -> Unit) {
//            CronetProviderInstaller.installProvider(context)
//                .addOnSuccessListener {
//                    build(context, userAgent)?.let(callback)
//                }
//        }
//
//        private fun build(context: Context, userAgent: String): CronetEngine? {
//            if (!CronetProviderInstaller.isInstalled()) {
//                return null
//            }
//
//            return try {
//                CronetEngine.Builder(context)
//                    .setUserAgent(userAgent)
//                    .enableQuic(true)
//                    .enableHttp2(true)
//                    .enableBrotli(true)
//                    .setStoragePath(context.cacheFolder("cronet").absolutePath)
//                    .enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISK, 500 * 1024 * 1024)
//                    .build()
//            } catch (e: Throwable) {
//                null
//            }
//        }
    }
}