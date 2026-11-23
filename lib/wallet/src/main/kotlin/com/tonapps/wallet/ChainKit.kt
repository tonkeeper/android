package com.tonapps.wallet

import com.tonapps.chainkit.CryptoKitClient
import com.tonapps.chainkit.core.net.module.NetConfig
import com.tonapps.chainkit.core.net.module.NetModule
import com.tonapps.chainkit.core.net.module.NodeManifestHostProvider
import com.tonapps.chainkit.core.net.module.SessionTokenProvider
import com.tonapps.extensions.Os

fun createChainKitClient(
    versionName: String,
    isLogging: Boolean,
    sessionToken: SessionTokenProvider,
    nodeManifestHost: NodeManifestHostProvider,
): CryptoKitClient {
    return CryptoKitClient(
        netModule = NetModule(
            NetConfig(
                isLogging = isLogging,
                logger = ChainKitLogger(),
                userAgent = Os.tonkeeperUserAgent(versionName),
                sessionProvider = sessionToken,
                nodeManifestHost = nodeManifestHost,
            )
        )
    )
}
