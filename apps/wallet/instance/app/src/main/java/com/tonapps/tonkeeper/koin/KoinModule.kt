package com.tonapps.tonkeeper.koin

import com.tonapps.async.Async
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.network.NetworkMonitor
import com.tonapps.tonkeeper.Environment
import com.tonapps.core.flags.AddMcWalletTooltipInteractor
import com.tonapps.core.flags.RemoteConfig
import com.tonapps.tonkeeper.billing.BillingManager
import com.tonapps.tonkeeper.client.safemode.SafeModeClient
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.core.helper.EnvironmentHelper
import com.tonapps.extensions.AppLifecycleProvider
import com.tonapps.legacy.assets.AssetsManager
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.helper.CacheHelper
import com.tonapps.tonkeeper.helper.ReferrerClientHelper
import com.tonapps.dapp.screens.sessions.TonConnectDisconnectInteractor
import com.tonapps.deposit.multicoin.screens.confirm.ConfirmResultListener
import com.tonapps.tonkeeper.manager.ConfirmResultDispatcher
import com.tonapps.tonkeeper.manager.earn.EarnUrlResolver
import com.tonapps.tonkeeper.manager.apk.APKManager
import com.tonapps.tonkeeper.manager.push.PushManager
import com.tonapps.tonkeeper.manager.shortcut.AppShortcutRepository
import com.tonapps.tonkeeper.ui.screen.browser.dapp.evm.EvmProviderProtocol
import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.bridge.RawInAppScript
import com.tonapps.tonkeeper.ui.screen.main.MainViewModel
import com.tonapps.tonkeeper.ui.screen.root.RootViewModel
import com.tonapps.tonkeeper.manager.tonconnect.TonConnectManager
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.add.AddWalletViewModel
import com.tonapps.tonkeeper.ui.screen.battery.BatteryViewModel
import com.tonapps.tonkeeper.ui.screen.browser.main.BrowserMainViewModel
import com.tonapps.tonkeeper.ui.screen.browser.search.BrowserSearchViewModel
import com.tonapps.tonkeeper.ui.screen.country.CountryPickerViewModel
import com.tonapps.tonkeeper.ui.screen.dev.DevViewModel
import com.tonapps.tonkeeper.ui.screen.events.compose.McEncryptedCommentDecryptor
import com.tonapps.tonkeeper.ui.screen.start.StartFeature
import com.tonapps.tonkeeper.ui.screen.init.InitViewModel
import com.tonapps.tonkeeper.ui.screen.init.TonWalletVersionInteractor
import com.tonapps.tonkeeper.ui.screen.init.WalletImportAnalytics
import com.tonapps.tonkeeper.ui.screen.settings.currency.CurrencyViewModel
import com.tonapps.tonkeeper.ui.screen.ledger.steps.LedgerConnectionViewModel
import com.tonapps.tonkeeper.ui.screen.migration.MigrationViewModel
import com.tonapps.tonkeeper.ui.screen.settings.language.LanguageViewModel
import com.tonapps.tonkeeper.ui.screen.name.base.NameViewModel
import com.tonapps.tonkeeper.ui.screen.wallet.picker.PickerViewModel
import com.tonapps.tonkeeper.ui.screen.settings.passcode.ChangePasscodeViewModel
import com.tonapps.tonkeeper.ui.screen.settings.security.SecurityViewModel
import com.tonapps.tonkeeper.ui.screen.settings.theme.ThemeViewModel
import com.tonapps.tonkeeper.ui.screen.stories.w5.W5StoriesViewModel
import com.tonapps.tonkeeper.ui.screen.tonconnect.TonConnectViewModel
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeBuilder
import com.tonapps.deposit.multicoin.screens.confirm.fee.GaslessSender
import com.tonapps.deposit.multicoin.screens.confirm.fee.EvmGasless
import com.tonapps.deposit.usecase.emulation.EmulationUseCase
import com.tonapps.deposit.usecase.sign.SignTransaction
import com.tonapps.deposit.usecase.sign.SignUseCase
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.worker.WidgetUpdaterWorker
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.tx.TransactionManager
import com.tonapps.wallet.features.events.data.EncryptedCommentDecryptor
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val koinModel = module {
    factory { Dispatchers.Default }

    single(createdAtStart = true) { Async.ioScope() }
    singleOf(::ConfirmResultDispatcher) { bind<ConfirmResultListener>() }
    singleOf(::EarnUrlResolver)
    factory { EvmProviderProtocol(RawInAppScript(androidContext(), R.raw.evm_provider)) }

    singleOf(::Environment) // TODO move to data module
    single<EnvironmentHelper.Delegate> { get<Environment>() }
    singleOf(::EnvironmentHelper) // TODO remove
    singleOf(::RemoteConfig)

    singleOf(::SettingsRepository)
    singleOf(::NetworkMonitor)
    singleOf(::AppLifecycleProvider)
    singleOf(::HistoryHelper)
    singleOf(::AssetsManager)
    single<EmulationUseCase.Delegate> { // TODO remove
        val assetsManager = get<AssetsManager>()
        object : EmulationUseCase.Delegate {
            override suspend fun getTotalBalance(
                wallet: WalletEntity,
                currency: WalletCurrency,
            ) = assetsManager.getTotalBalance(wallet, currency, sorted = false)
        }
    }
    singleOf(::BillingManager)
    singleOf(::TransactionManager)
    single<TransactionManager.Delegate> { // TODO remove
        object : TransactionManager.Delegate {
            override fun onUpdateWidget() {
                WidgetUpdaterWorker.update(App.instance)
            }
        }
    }
    singleOf(::TonConnectManager) { bind<TonConnectDisconnectInteractor>() }
    singleOf(::PushManager)
    singleOf(::SafeModeClient)
    singleOf(::APKManager)
    singleOf(::AppShortcutRepository)
    singleOf(::CacheHelper)
    singleOf(::ReferrerClientHelper)
    singleOf(AnalyticsHelper::Default)
    singleOf(::WalletImportAnalytics)

    factoryOf(::SignUseCase)
    factoryOf(::EmulationUseCase)
    singleOf(::SignTransaction)
    single<EncryptedCommentDecryptor> { McEncryptedCommentDecryptor(get(), get(), get(), get(), get()) }
    singleOf(::TxFeeBuilder)
    singleOf(::GaslessSender)
    singleOf(::EvmGasless)
    factoryOf(::TonWalletVersionInteractor)
    factoryOf(::AddMcWalletTooltipInteractor)

    viewModelOf(::DevViewModel)
    viewModelOf(::StartFeature)
    viewModelOf(::ChangePasscodeViewModel)
    viewModelOf(::CountryPickerViewModel)
    viewModelOf(::CurrencyViewModel)
    viewModelOf(::ThemeViewModel)
    viewModelOf(::LanguageViewModel)
    viewModelOf(::SecurityViewModel)
    viewModelOf(::BrowserMainViewModel)
    viewModelOf(::BrowserSearchViewModel)
    viewModelOf(::MigrationViewModel)

    viewModelOf(::NameViewModel)
    viewModelOf(::InitViewModel)
    viewModelOf(::MainViewModel)
    viewModelOf(::RootViewModel)
    viewModelOf(::PickerViewModel)
    viewModelOf(::TonConnectViewModel)

    viewModelOf(::LedgerConnectionViewModel)
    viewModelOf(::W5StoriesViewModel)
    viewModelOf(::AddWalletViewModel)
    viewModelOf(::BatteryViewModel)
    viewModelOf(BaseWalletVM::EmptyViewViewModel)
}
