package com.tonapps.tonkeeper.koin

import com.tonapps.network.NetworkMonitor
import com.tonapps.tonkeeper.billing.BillingManager
import com.tonapps.tonkeeper.manager.AssetsManager
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.ui.screen.main.MainViewModel
import com.tonapps.tonkeeper.ui.screen.root.RootViewModel
import com.tonapps.tonkeeper.fragment.tonconnect.auth.TCAuthViewModel
import com.tonapps.tonkeeper.sign.SignManager
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.action.ActionViewModel
import com.tonapps.tonkeeper.ui.screen.add.imprt.ImportWalletViewModel
import com.tonapps.tonkeeper.ui.screen.backup.main.BackupViewModel
import com.tonapps.tonkeeper.ui.screen.backup.check.BackupCheckViewModel
import com.tonapps.tonkeeper.ui.screen.battery.BatteryViewModel
import com.tonapps.tonkeeper.ui.screen.battery.recharge.BatteryRechargeViewModel
import com.tonapps.tonkeeper.ui.screen.battery.refill.BatteryRefillViewModel
import com.tonapps.tonkeeper.ui.screen.battery.settings.BatterySettingsViewModel
import com.tonapps.tonkeeper.ui.screen.browser.connected.BrowserConnectedViewModel
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppViewModel
import com.tonapps.tonkeeper.ui.screen.browser.explore.BrowserExploreViewModel
import com.tonapps.tonkeeper.ui.screen.browser.main.BrowserMainViewModel
import com.tonapps.tonkeeper.ui.screen.browser.search.BrowserSearchViewModel
import com.tonapps.tonkeeper.ui.screen.purchase.main.PurchaseViewModel
import com.tonapps.tonkeeper.ui.screen.collectibles.CollectiblesViewModel
import com.tonapps.tonkeeper.ui.screen.country.CountryPickerViewModel
import com.tonapps.tonkeeper.ui.screen.dev.DevViewModel
import com.tonapps.tonkeeper.ui.screen.events.EventsViewModel
import com.tonapps.tonkeeper.ui.screen.init.InitArgs
import com.tonapps.tonkeeper.ui.screen.settings.currency.CurrencyViewModel
import com.tonapps.tonkeeper.ui.screen.init.InitViewModel
import com.tonapps.tonkeeper.ui.screen.ledger.steps.LedgerConnectionViewModel
import com.tonapps.tonkeeper.ui.screen.settings.language.LanguageViewModel
import com.tonapps.tonkeeper.ui.screen.name.base.NameViewModel
import com.tonapps.tonkeeper.ui.screen.name.edit.EditNameViewModel
import com.tonapps.tonkeeper.ui.screen.nft.NftViewModel
import com.tonapps.tonkeeper.ui.screen.notifications.enable.NotificationsEnableViewModel
import com.tonapps.tonkeeper.ui.screen.notifications.manage.NotificationsManageViewModel
import com.tonapps.tonkeeper.ui.screen.purchase.web.PurchaseWebViewModel
import com.tonapps.tonkeeper.ui.screen.send.contacts.SendContactsViewModel
import com.tonapps.tonkeeper.ui.screen.send.main.SendViewModel
import com.tonapps.tonkeeper.ui.screen.wallet.picker.PickerViewModel
import com.tonapps.tonkeeper.ui.screen.settings.main.SettingsViewModel
import com.tonapps.tonkeeper.ui.screen.settings.passcode.ChangePasscodeViewModel
import com.tonapps.tonkeeper.ui.screen.settings.security.SecurityViewModel
import com.tonapps.tonkeeper.ui.screen.settings.theme.ThemeViewModel
import com.tonapps.tonkeeper.ui.screen.staking.stake.StakingViewModel
import com.tonapps.tonkeeper.ui.screen.staking.unstake.UnStakeViewModel
import com.tonapps.tonkeeper.ui.screen.staking.viewer.StakeViewerViewModel
import com.tonapps.tonkeeper.ui.screen.token.picker.TokenPickerViewModel
import com.tonapps.tonkeeper.ui.screen.token.viewer.TokenViewModel
import com.tonapps.tonkeeper.ui.screen.w5.stories.W5StoriesViewModel
import com.tonapps.tonkeeper.ui.screen.wallet.main.WalletViewModel
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.WalletAdapter
import com.tonapps.tonkeeper.ui.screen.wallet.manage.TokensManageViewModel
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val koinModel = module {
    factory { Dispatchers.Default }
    single(createdAtStart = true) { CoroutineScope(Dispatchers.IO + SupervisorJob()) }
    single { SettingsRepository(get(), get(), get()) }
    single { NetworkMonitor(get(), get()) }
    single { SignManager(get(), get(), get(), get(), get(), get()) }
    single { HistoryHelper(get(), get(), get(), get(), get(), get(), get()) }
    single { AssetsManager(get(), get(), get(), get()) }
    singleOf(::BillingManager)

    factory { (viewModel: BaseWalletVM) ->
        // TODO
    }

    uiAdapter { WalletAdapter(get()) }

    viewModel { DevViewModel(androidApplication()) }
    viewModel { ChangePasscodeViewModel(androidApplication(), get(), get()) }
    viewModel { CountryPickerViewModel(androidApplication(), get(), get()) }
    viewModel { CurrencyViewModel(androidApplication(), get()) }
    viewModel { ThemeViewModel(androidApplication(), get()) }
    viewModel { LanguageViewModel(androidApplication(), get()) }
    viewModel { SecurityViewModel(androidApplication(), get(), get(), get()) }
    viewModel { BrowserMainViewModel(androidApplication(), get()) }
    viewModel { BrowserSearchViewModel(androidApplication(), get(), get(), get()) }

    viewModel { parameters -> NameViewModel(androidApplication(), mode = parameters.get(), get(), get()) }
    viewModel { parameters -> InitViewModel(androidApplication(), args = parameters.get<InitArgs>(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { MainViewModel(androidApplication(), get(), get()) }
    viewModel { RootViewModel(androidApplication(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { PickerViewModel(androidApplication(), get(), get(), get()) }

    viewModel { parameters -> WalletViewModel(androidApplication(), wallet = parameters.get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { parameters -> SettingsViewModel(androidApplication(), wallet = parameters.get(), get(), get(), get(), get(), get(), get()) }
    viewModel { parameters -> EditNameViewModel(androidApplication(), wallet = parameters.get(), get()) }
    viewModel { parameters -> EventsViewModel(androidApplication(), wallet = parameters.get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { parameters -> TCAuthViewModel(androidApplication(), request = parameters.get(), get(), get(), get()) }
    viewModel { parameters -> CollectiblesViewModel(androidApplication(), wallet = parameters.get(), get(), get(), get()) }
    viewModel { parameters -> ActionViewModel(androidApplication(), args = parameters.get(), get(), get(), get(), get()) }
    viewModel { parameters -> BrowserExploreViewModel(androidApplication(), wallet = parameters.get(), get(), get(), get()) }
    viewModel { parameters -> BrowserConnectedViewModel(androidApplication(), wallet = parameters.get(), get(), get()) }
    viewModel { parameters -> DAppViewModel(androidApplication(), wallet = parameters.get(), url = parameters.get(), get(), get()) }
    viewModel { parameters -> NotificationsManageViewModel(androidApplication(), wallet = parameters.get(), get(), get()) }
    viewModel { parameters -> TokenViewModel(androidApplication(), wallet = parameters.get(), tokenAddress = parameters.get(), get(), get(), get(), get(), get(), get()) }
    viewModel { parameters -> BackupViewModel(androidApplication(), wallet = parameters.get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { parameters -> BackupCheckViewModel(androidApplication(), wallet = parameters.get(), get()) }
    viewModel { parameters -> TokensManageViewModel(androidApplication(), wallet = parameters.get(), get(), get()) }
    viewModel { parameters -> SendViewModel(androidApplication(), wallet = parameters.get(), nftAddress = parameters.get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { parameters -> TokenPickerViewModel(androidApplication(), wallet = parameters.get(), selectedToken = parameters.get(), allowedTokens = parameters.get(), get(), get()) }
    viewModel { parameters -> BatterySettingsViewModel(androidApplication(), wallet = parameters.get(), get(), get(), get(), get()) }
    viewModel { parameters -> BatteryRefillViewModel(androidApplication(), wallet = parameters.get(), get(), get(), get(), get(), get(), get()) }
    viewModel { parameters -> BatteryRechargeViewModel(androidApplication(), wallet = parameters.get(), args = parameters.get(), get(), get(), get(), get(), get(), get()) }
    viewModel { parameters -> PurchaseWebViewModel(androidApplication(), wallet = parameters.get(), get(), get()) }
    viewModel { parameters -> SendContactsViewModel(androidApplication(), wallet = parameters.get(), get(), get(), get()) }
    viewModel { parameters -> PurchaseViewModel(androidApplication(), wallet = parameters.get(), get(), get()) }
    viewModel { parameters -> NftViewModel(androidApplication(), wallet = parameters.get(), nft = parameters.get(), get(), get()) }
    viewModel { parameters -> StakeViewerViewModel(androidApplication(), wallet = parameters.get(), poolAddress = parameters.get(), get(), get(), get(), get()) }
    viewModel { parameters -> UnStakeViewModel(androidApplication(), wallet = parameters.get(), poolAddress = parameters.get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { parameters -> StakingViewModel(androidApplication(), wallet = parameters.get(), poolAddress = parameters.get(), get(), get(), get(), get(), get(), get(), get()) }



    viewModel { LedgerConnectionViewModel(androidApplication(), get(), get(), get(), get(), get()) }
    viewModel { W5StoriesViewModel(androidApplication(), get(), get(), get(), get()) }
    viewModel { NotificationsEnableViewModel(get(), get()) }
    viewModel { ImportWalletViewModel(androidApplication(), get()) }
    viewModel { BatteryViewModel(androidApplication()) }
    viewModel { BaseWalletVM.EmptyViewViewModel(androidApplication()) }
}