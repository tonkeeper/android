package com.tonapps.tonkeeper.api

import com.tonapps.network.interceptor.LoggingInterceptor
import com.tonapps.tonkeeper.core.DevSettings
import com.tonapps.tonkeeperx.BuildConfig
import com.tonapps.wallet.api.API
import org.koin.dsl.module

val appApiModule = module {
    single<LoggingInterceptor.Delegate>(createdAtStart = true) {
        object : LoggingInterceptor.Delegate {
            override fun isEnabled(): Boolean {
                return DevSettings.isLogsEnabled || BuildConfig.DEBUG
            }
        }
    }

    single<API.BuildProvider> {
        API.BuildProvider { DevSettings.buildOverride }
    }
}
