package com.tonapps.wallet

import android.content.Context
import android.os.Build
import com.tonapps.chainkit.CryptoKitClient
import com.tonapps.chainkit.core.net.module.NetConfig
import com.tonapps.chainkit.core.net.module.NetModule
import com.tonapps.chainkit.core.net.module.SessionTokenProvider
import com.tonapps.extensions.Os
import com.tonapps.extensions.appVersionName

fun createChainKitClient(
    context: Context,
    isLogging: Boolean,
    sessionToken: SessionTokenProvider,
): CryptoKitClient {
    return CryptoKitClient(
        netModule = NetModule(
            NetConfig(
                isLogging = isLogging,
                logger = ChainKitLogger(),
                userAgent = userAgent(context),
                sessionProvider = sessionToken,
            )
        )
    )
}

// TODO get from the one place in app
private fun userAgent(context: Context): String {
    return "Tonkeeper/${context.appVersionName} (Android; ${Build.VERSION.RELEASE}; ${Os.deviceNameAndModel()})"
}
