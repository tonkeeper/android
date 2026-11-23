package com.tonapps.deposit

import com.tonapps.core.flags.WalletFeature
import com.tonapps.deposit.data.ExchangeRepository
import com.tonapps.deposit.multicoin.data.RampRepository
import com.tonapps.deposit.multicoin.domain.MerchantPayments
import com.tonapps.deposit.multicoin.domain.MerchantRampSource
import com.tonapps.deposit.multicoin.domain.MoonPayAccess
import com.tonapps.deposit.multicoin.domain.MoonPayPayments
import com.tonapps.deposit.multicoin.domain.MoonPayRemovedCards
import com.tonapps.deposit.multicoin.domain.MoonPayRamp
import com.tonapps.deposit.multicoin.domain.MoonPaySignIn
import com.tonapps.deposit.multicoin.domain.RampPayments
import com.tonapps.deposit.multicoin.domain.RampSource
import com.tonapps.deposit.multicoin.domain.RampWalletAddress
import com.tonapps.deposit.multicoin.domain.ReceiveAccountsInteractor
import com.tonapps.deposit.multicoin.moonpay.MoonPayAssetRepository
import com.tonapps.deposit.multicoin.moonpay.MoonPayCheckout
import com.tonapps.deposit.multicoin.moonpay.MoonPayConnectionException
import com.tonapps.deposit.multicoin.moonpay.MoonPayConnectionRepository
import com.tonapps.deposit.multicoin.moonpay.MoonPayMethodSupport
import com.tonapps.deposit.multicoin.moonpay.MoonPayTransactionRepository
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayPlatformApi
import com.tonapps.deposit.multicoin.moonpay.domain.MoonPayButtonPayments
import com.tonapps.deposit.multicoin.moonpay.domain.MoonPayPaymentInteractor
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrames
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayTheme
import com.tonapps.extensions.AppLifecycleProvider
import com.tonapps.log.L
import com.tonapps.wallet.api.API
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

val depositModule = module {
    singleOf(::ExchangeRepository)
    singleOf(::RampRepository)
    factoryOf(::ReceiveAccountsInteractor)
    factoryOf(::RampWalletAddress)
    factory<RampSource> { (walletId: String) ->
        MerchantRampSource(
            walletId = walletId,
            rampRepository = get(),
            exchangeRepository = get(),
            accountRepository = get(),
            mcAccountRepository = get(),
            settingsRepository = get(),
            walletAddress = get(),
        )
    }
    factory<RampPayments> { (walletId: String) ->
        MerchantPayments(walletId = walletId, rampRepository = get(), exchangeRepository = get(), walletAddress = get())
    }

    singleOf(::MoonPayFrames)
    single { MoonPayConnectionRepository(api = get(), environmentProvider = get(), frames = get()) }
    single { MoonPayCheckout(get(), get(), get<AppLifecycleProvider>().observeWithoutGrace) }
    single {
        val client = get<API>().defaultHttpClient
        MoonPayTransactionRepository(get()) { environment -> MoonPayPlatformApi(client, environment) }
    }
    single {
        val client = get<API>().defaultHttpClient
        MoonPayAssetRepository(platformApi = { environment -> MoonPayPlatformApi(client, environment) })
    }
    single { MoonPayRemovedCards() }
    single {
        val context = androidContext()
        // The WebView and Play services checks are IPC; their answer holds for the process.
        val device by lazy { MoonPayMethodSupport.deviceSupportsGooglePay(context) }
        MoonPayMethodSupport(
            googlePayEnabled = { WalletFeature.MoonPayGooglePay.isEnabled },
            deviceSupportsGooglePay = { device },
        )
    }
    factory { (walletId: String, theme: MoonPayTheme?) ->
        val client = get<API>().defaultHttpClient
        MoonPayPayments(
            walletId = walletId,
            connections = get(),
            assets = get(),
            frames = get(),
            rampRepository = get(),
            exchangeRepository = get(),
            walletAddress = get(),
            platformApi = { environment -> MoonPayPlatformApi(client, environment) },
            theme = theme,
            string = { androidContext().getString(it) },
            removedCards = get(),
            methodSupport = get(),
        )
    }
    factory { (walletId: String, theme: MoonPayTheme?) ->
        val connections = get<MoonPayConnectionRepository>()
        MoonPayRamp(
            payments = get<MoonPayPayments> { parametersOf(walletId, theme) },
            check = {
                MoonPayAccess.of(
                    connections.ensureConnected(theme, interactive = false)
                        .onFailure { L.i("MoonPay is not connected: ${it.message}") },
                )
            },
            connect = {
                MoonPaySignIn.of(
                    connections.ensureConnected(theme, interactive = true)
                        .onFailure {
                            if (it !is MoonPayConnectionException.Cancelled) {
                                L.w(it, "MoonPay sign-in did not connect")
                            }
                        },
                )
            },
            methodSupport = get(),
            buttons = get(),
        )
    }
    single { MoonPayButtonPayments(get()) }
    factory {
        MoonPayPaymentInteractor(
            connections = get(),
            checkout = get(),
            transactions = get(),
            foreground = get<AppLifecycleProvider>().observeWithoutGrace,
        )
    }
}
