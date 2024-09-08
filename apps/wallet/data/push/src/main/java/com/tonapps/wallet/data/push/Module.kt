package com.tonapps.wallet.data.push

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val pushModule = module {
    singleOf(::PushManager)
}