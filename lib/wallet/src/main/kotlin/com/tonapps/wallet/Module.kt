package com.tonapps.wallet

import com.tonapps.chainkit.CryptoKitClient
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

fun chainKitModule(versionName: String, isLogging: Boolean) = module {
    single<CryptoKitClient> {
        createChainKitClient(
            versionName = versionName,
            isLogging = isLogging,
            sessionToken = get(),
            nodeManifestHost = get(),
        )
    }

    singleOf(::ChainKitProvider)
    singleOf(::SecureProofProvider)
}
