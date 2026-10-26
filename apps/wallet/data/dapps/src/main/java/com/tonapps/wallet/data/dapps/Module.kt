package com.tonapps.wallet.data.dapps

import com.tonapps.wallet.data.dapps.confirm.ConfirmSheetQueue
import com.tonapps.chainkit.core.net.provider.NetworkProvider
import com.tonapps.chainkit.core.net.provider.PlatformNetworkProvider
import com.tonapps.wallet.data.dapps.evm.EvmProviderRepository
import com.tonapps.wallet.data.dapps.source.DappsDatabase
import com.tonapps.wallet.data.dapps.source.TonConnectPrefs
import com.tonapps.wallet.data.dapps.wc.WcRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val dAppsModule = module {
    single { DappsDatabase.instance(get()) }
    single { get<DappsDatabase>().appDao() }
    single { get<DappsDatabase>().connectDao() }
    single { get<DappsDatabase>().notificationDao() }
    single { TonConnectPrefs(get()) }
    single<NetworkProvider> { PlatformNetworkProvider(get()) }
    singleOf(::DAppsRepository)
    singleOf(::WcRepository)
    singleOf(::ConfirmSheetQueue)
    singleOf(::EvmProviderRepository)
}