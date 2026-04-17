package com.tonapps.wallet.api

import android.content.Context
import android.os.Build
import com.tonapps.apps.wallet.api.BuildConfig
import com.tonapps.core.flags.WalletFeature
import com.tonapps.extensions.Os
import com.tonapps.extensions.appVersionName
import com.tonapps.extensions.cacheFolder
import com.tonapps.extensions.locale
import com.tonapps.network.interceptor.AcceptLanguageInterceptor
import com.tonapps.network.interceptor.AuthorizationInterceptor
import com.tonapps.network.interceptor.LoggingInterceptor
import com.tonapps.network.interceptor.RateLimitInterceptor
import com.tonapps.wallet.api.entity.ConfigEntity
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

//import org.chromium.net.CronetEngine
//import com.google.android.gms.net.CronetProviderInstaller
//import com.google.net.cronet.okhttptransport.CronetInterceptor

abstract class CoreAPI(
    private val context: Context,
) {

    val appVersionName = context.appVersionName
    // e.g. Tonkeeper/1.2.3 (Android; 14; Pixel Tablet)
    private val userAgent = "Tonkeeper/${appVersionName} (Android; ${Build.VERSION.RELEASE}; ${Os.deviceNameAndModel()})"

//    private var cronetEngine: CronetEngine? = null

    val defaultHttpClient = baseOkHttpClientBuilder(
//        cronetEngine = { cronetEngine },
        timeoutSeconds = 30,
        rateLimit = 15,
        context = context,
        interceptors = listOf(
            UserAgentInterceptor(userAgent),
        )
    ).build()


    val tronHttpClient = baseOkHttpClientBuilder(
//        cronetEngine = { cronetEngine },
        timeoutSeconds = 30,
        rateLimit = 10,
        context = context,
        interceptors = listOf(
            UserAgentInterceptor(userAgent),
        )
    ).build()

    fun sseHttpClient(config: () -> ConfigEntity): OkHttpClient {
        return createTonAPIHttpClient(
//        cronetEngine = { null },
            timeoutSeconds = 60,
            callTimeoutSeconds = 0,
            context = context,
            userAgent = userAgent,
            tonApiV2Key = { config().tonApiV2Key },
            allowDomains = { config().domains },
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
//            cronetEngine = { cronetEngine }
        )
    }

    private companion object {

        const val MAX_CACHE_SIZE = 500L * 1024 * 1024

        private val loggingInterceptor by lazy { LoggingInterceptor() }

        class UserAgentInterceptor(private val userAgent: String) : Interceptor {
            override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
                val request = chain.request().newBuilder()
                    .addHeader("User-Agent", userAgent)
                    .build()
                return chain.proceed(request)
            }
        }

        class XCapabilityInterceptor : Interceptor {
            override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
                if (!WalletFeature.StreamingV2.isEnabled) {
                    return chain.proceed(chain.request())
                }

                val request = chain.request().newBuilder()
                    .addHeader("X-Capability", "sub-second")
                    .build()
                return chain.proceed(request)
            }
        }

        private fun baseOkHttpClientBuilder(
            timeoutSeconds: Long = 30,
            callTimeoutSeconds: Long = timeoutSeconds,
            rateLimit: Int = 10,
            interceptors: List<Interceptor> = emptyList(),
            context: Context,
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

            for (interceptor in interceptors) {
                builder.addInterceptor(interceptor)
            }

//                cronetEngine()?.let { engine ->
//                    builder.addInterceptor(
//                        CronetInterceptor.newBuilder(engine)
//                            .build()
//                    )
//                }

            builder.addInterceptor(
                RateLimitInterceptor(requestsPerSecondLimit = rateLimit)
            )

            if (BuildConfig.DEBUG) {
                builder.addInterceptor(loggingInterceptor)
            }

            return builder
        }

        private fun createTonAPIHttpClient(
            userAgent: String,
            context: Context,
            timeoutSeconds: Long = 30,
            callTimeoutSeconds: Long = timeoutSeconds,
            rateLimit: Int = 10,
            tonApiV2Key: () -> String,
            allowDomains: () -> List<String>,
//            cronetEngine: () -> CronetEngine?,
        ): OkHttpClient {
            val interceptors = mutableListOf(
                UserAgentInterceptor(userAgent),
                XCapabilityInterceptor(),
                AcceptLanguageInterceptor(context.locale),
                AuthorizationInterceptor.bearer(
                    token = tonApiV2Key,
                    allowDomains = allowDomains
                ),
            )

            return baseOkHttpClientBuilder(
                context = context,
                interceptors = interceptors,
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