package com.tonapps.wallet.data.ledger.lab

import android.content.Context
import android.net.ConnectivityManager
import com.tonapps.chainkit.CryptoKitClient
import com.tonapps.chainkit.core.net.module.LoggerLevel
import com.tonapps.chainkit.core.net.module.NetConfig
import com.tonapps.chainkit.core.net.module.NetLogger
import com.tonapps.chainkit.core.net.module.NetModule
import com.tonapps.chainkit.core.net.module.SessionTokenProvider
import com.tonapps.wallet.ChainKitProvider
import io.mockk.every
import io.mockk.mockk

object LabChainKit {

    val provider: ChainKitProvider by lazy {
        Class.forName("com.tonapps.chainkit.core.log.Android").getDeclaredField("appContext").apply {
            isAccessible = true
            set(
                null,
                mockk<Context>(relaxed = true) {
                    every { getSystemService(any<String>()) } returns mockk<ConnectivityManager>(relaxed = true)
                },
            )
        }
        ChainKitProvider(
            client = CryptoKitClient(NetModule(NetConfig(false, NoLogger, "Keeper Ledger lab", NoSession))),
            txReporter = mockk(relaxed = true),
        )
    }

    private object NoLogger : NetLogger {
        override fun log(message: String, details: Map<String, String>, level: LoggerLevel) = Unit
    }

    private object NoSession : SessionTokenProvider {
        override suspend fun getAccessToken(): String = ""
        override suspend fun refreshToken(token: String): String = ""
    }
}
