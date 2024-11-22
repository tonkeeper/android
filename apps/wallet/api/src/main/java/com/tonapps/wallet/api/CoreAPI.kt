package com.tonapps.wallet.api

import android.content.Context
import android.os.Build
import com.google.android.gms.net.CronetProviderInstaller
import com.tonapps.extensions.appVersionName
import com.tonapps.extensions.cacheFolder
import com.tonapps.extensions.locale
import com.tonapps.network.interceptor.AcceptLanguageInterceptor
import com.tonapps.network.interceptor.AuthorizationInterceptor
import com.tonapps.wallet.api.cronet.CronetInterceptor
import com.tonapps.wallet.api.entity.ConfigEntity
import okhttp3.OkHttpClient
import org.chromium.net.CronetEngine
import java.util.concurrent.TimeUnit

abstract class CoreAPI(private val context: Context) {

    private val userAgent = "Tonkeeper/${context.appVersionName} (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL})"

    private var cronetEngine: CronetEngine? = null

    val defaultHttpClient = baseOkHttpClientBuilder(
        userAgent = userAgent,
        cronetEngine = { cronetEngine },
        timeoutSeconds = 15
    ).build()

    val seeHttpClient = baseOkHttpClientBuilder(
        userAgent = userAgent,
        cronetEngine = { null },
        timeoutSeconds = 120
    ).build()

    init {
        requestCronet(context, userAgent) {
            cronetEngine = it
        }
    }

    fun tonAPIHttpClient(config: () -> ConfigEntity): OkHttpClient {
        return createTonAPIHttpClient(
            context = context,
            userAgent = userAgent,
            tonApiV2Key = { config().tonApiV2Key },
            allowDomains = { config().domains },
            cronetEngine = { cronetEngine }
        )
    }

    private companion object {
        private fun baseOkHttpClientBuilder(
            userAgent: String,
            cronetEngine: () -> CronetEngine?,
            timeoutSeconds: Long = 5
        ): OkHttpClient.Builder {
            val builder = OkHttpClient().newBuilder()
                .retryOnConnectionFailure(true)
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .pingInterval(timeoutSeconds, TimeUnit.SECONDS)
                .followSslRedirects(true)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .addHeader("User-Agent", userAgent)
                        .build()
                    chain.proceed(request)
                }
                .followRedirects(true)
                .addInterceptor { chain ->
                    cronetEngine()?.let { engine ->
                        CronetInterceptor.newBuilder(engine).build()
                    }?.intercept(chain) ?: chain.proceed(chain.request())
                }

            return builder
        }

        private fun createTonAPIHttpClient(
            context: Context,
            userAgent: String,
            cronetEngine: () -> CronetEngine?,
            tonApiV2Key: () -> String,
            allowDomains: () -> List<String>
        ): OkHttpClient {
            return baseOkHttpClientBuilder(userAgent, cronetEngine)
                .addInterceptor(AcceptLanguageInterceptor(context.locale))
                .addInterceptor(
                    AuthorizationInterceptor.bearer(
                    token = tonApiV2Key,
                    allowDomains = allowDomains
                )).build()
        }

        private fun requestCronet(context: Context, userAgent: String, callback: (CronetEngine) -> Unit) {
            CronetProviderInstaller.installProvider(context).addOnSuccessListener {
                build(context, userAgent)?.let(callback)
            }
        }

        private fun build(context: Context, userAgent: String): CronetEngine? {
            if (!CronetProviderInstaller.isInstalled()) {
                return null
            }
            return try {
                CronetEngine.Builder(context)
                    .setUserAgent(userAgent)
                    .enableQuic(true)
                    .enableHttp2(true)
                    .enableBrotli(true)
                    .setStoragePath(context.cacheFolder("cronet").absolutePath)
                    .enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISK, 500 * 1024 * 1024)
                    .build()
            } catch (e: Throwable) {
                null
            }
        }
    }
}