package com.tonapps.network

import android.content.Context
import android.util.Log
import com.google.android.gms.net.CronetProviderInstaller
import com.google.net.cronet.okhttptransport.CronetInterceptor
import com.tonapps.network.interceptor.AuthorizationInterceptor
import okhttp3.OkHttpClient
import okhttp3.internal.notifyAll
import okhttp3.internal.wait
import org.chromium.net.CronetEngine
import org.chromium.net.CronetProvider

class OkHttpClientHolder {

    private var client: OkHttpClient? = null
    private val lock = Any()

    fun init(context: Context) {
        CronetProviderInstaller.installProvider(context).addOnCompleteListener {
            val newClient = if (it.isSuccessful) {
                clientWithCronet(context)
            } else {
                okHttpBuilder().build()
            }
            synchronized(lock) {
                client = newClient
                lock.notifyAll()
            }
        }
    }

    fun get(): OkHttpClient {
        synchronized(lock) {
            if (client == null) {
                try {
                    lock.wait() // Wait for the client to be initialized
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            return client!!
        }
    }

    private fun clientWithCronet(context: Context): OkHttpClient {
        val providers = CronetProvider.getAllProviders(context)
        for (provider in providers) {
            if (!provider.isEnabled || provider.name == CronetProvider.PROVIDER_NAME_FALLBACK) {
                continue
            }
            val engine = createCronetEngine(provider.createBuilder())
            val okHttpBuilder = okHttpBuilder()
            okHttpBuilder.addInterceptor(CronetInterceptor.newBuilder(engine).build())
            return okHttpBuilder.build()
        }
        return okHttpBuilder().build()
    }

    private fun createCronetEngine(builder: CronetEngine.Builder): CronetEngine {
        return builder
            .enableHttp2(true)
            .enableBrotli(true)
            .enableQuic(true)
            .build()
    }

    private fun okHttpBuilder(): OkHttpClient.Builder {
        return OkHttpClient().newBuilder()
            .retryOnConnectionFailure(true)
            .addInterceptor(AuthorizationInterceptor.bearer("AF77F5JND26OLHQAAAAKQMSCYW3UVPFRA7CF2XHX6QG4M5WAMF5QRS24R7J4TF2UTSXOZEY"))
    }
}
