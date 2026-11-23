package com.tonapps.wallet.data.ledger

import android.content.Context
import com.tonapps.ledgerkit.LedgerDiagnostics
import com.tonapps.ledgerkit.controller.LedgerController
import com.tonapps.ledgerkit.transport.LedgerRoutingTransport
import com.tonapps.ledgerkit.transport.LedgerTransport
import com.tonapps.ledgerkit.transport.ble.LedgerBleEnvironment
import com.tonapps.ledgerkit.transport.ble.LedgerBleScanner
import com.tonapps.ledgerkit.transport.ble.LedgerBleTransport
import com.tonapps.ledgerkit.transport.usb.LedgerUsbEnvironment
import com.tonapps.ledgerkit.transport.usb.LedgerUsbTransport
import com.tonapps.wallet.data.ledger.analytics.LedgerNonFatals
import com.tonapps.wallet.data.ledger.analytics.LedgerRequestAnalytics
import com.tonapps.wallet.data.ledger.connection.LedgerActivityLog
import com.tonapps.wallet.data.ledger.connection.LedgerActivityPrefsStore
import com.tonapps.wallet.data.ledger.connection.LedgerBleRadio
import com.tonapps.wallet.data.ledger.connection.LedgerConnectionInteractor
import com.tonapps.wallet.data.ledger.connection.LedgerConnectionInteractorImpl
import com.tonapps.wallet.data.ledger.discovery.LedgerDiscoveryInteractor
import com.tonapps.wallet.data.ledger.lab.LedgerLab
import com.tonapps.wallet.data.ledger.discovery.LedgerDiscoveryInteractorImpl
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.ledger.signing.EvmTokenDescriptors
import com.tonapps.wallet.data.ledger.signing.EvmTypedDataFilters
import com.tonapps.wallet.data.ledger.signing.LedgerSigningInteractor
import com.tonapps.wallet.data.ledger.signing.LedgerSigningInteractorImpl
import com.tonapps.wallet.data.ledger.solana.SolanaTrustedNames
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import org.koin.dsl.module
import kotlin.time.Duration.Companion.seconds

private val LAB_REFRESH = 2.seconds

val ledgerModule = module {
    single { LedgerBleEnvironment(get<Context>()) }
    single { LedgerLab(get<Context>()) }
    single { LedgerBleScanner(get(), extraDevices = get<LedgerLab>()::devices) }
    single {
        val lab = get<LedgerLab>()
        LedgerUsbEnvironment(get<Context>(), extraDevices = lab::usbDevices, refreshInterval = LAB_REFRESH.takeIf { lab.isDebuggable })
    }
    single {
        val usb = get<LedgerUsbEnvironment>()
        LedgerRoutingTransport(
            usb = LedgerUsbTransport(usb),
            bluetooth = LedgerBleTransport(get<Context>(), get(), get()),
            isUsbAttached = { usb.isAttached },
            awaitUsbAttached = { usb.observe().first { it.isNotEmpty() } },
        )
    }
    single<LedgerTransport> { get<LedgerLab>().route(get<LedgerRoutingTransport>()) }
    single { LedgerController(get(), CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
    single(createdAtStart = true) {
        LedgerActivityLog(get<LedgerController>(), CoroutineScope(SupervisorJob() + Dispatchers.Default), LedgerActivityPrefsStore(get<Context>()))
    }
    single(createdAtStart = true) {
        LedgerDiagnostics.sink = LedgerNonFatals::recordFallback
        LedgerRequestAnalytics(get<LedgerController>(), CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }
    single<LedgerConnectionInteractor> {
        LedgerConnectionInteractorImpl(get(), get(), get(), LedgerBleRadio(get(), get(), get(), get(), get()))
    }
    single { EvmTokenDescriptors(get<API>().defaultHttpClient) }
    single { SolanaTrustedNames(get<API>().defaultHttpClient) }
    single { EvmTypedDataFilters(get()) }
    single<LedgerSigningInteractor> { LedgerSigningInteractorImpl(get(), get(), get(), get(), get(), get(), get()) }
    single<LedgerDiscoveryInteractor> { LedgerDiscoveryInteractorImpl(get()) }
    single<LedgerWalletInteractor> { LedgerWalletInteractorImpl(get(), get(), get(), get()) }
}
