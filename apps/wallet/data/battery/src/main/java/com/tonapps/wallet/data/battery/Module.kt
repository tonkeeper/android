package com.tonapps.wallet.data.battery

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val batteryModule = module {
    singleOf(::BatteryRepository)
}