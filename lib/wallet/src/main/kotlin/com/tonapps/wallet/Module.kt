package com.tonapps.wallet

import android.content.Context
import com.tonapps.chainkit.CryptoKitClient
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

fun chainKitModule(context: Context, isLogging: Boolean) = module {
    single<CryptoKitClient> {
        createChainKitClient(
            context = context,
            isLogging = isLogging,
            sessionToken = get(),
        )
    }

    singleOf(::ChainKitProvider)
    singleOf(::SecureProofProvider)
}
