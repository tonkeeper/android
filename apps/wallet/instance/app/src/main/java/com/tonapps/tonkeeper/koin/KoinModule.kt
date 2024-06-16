package com.tonapps.tonkeeper.koin

import com.tonapps.network.NetworkMonitor
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.api.internal.repositories.FiatMethodsRepository
import com.tonapps.tonkeeper.core.fiat.Fiat
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.ui.screen.main.MainViewModel
import com.tonapps.tonkeeper.ui.screen.root.RootViewModel
import com.tonapps.tonkeeper.fragment.tonconnect.auth.TCAuthViewModel
import com.tonapps.tonkeeper.password.PasscodeDataStore
import com.tonapps.tonkeeper.password.PasscodeRepository
import com.tonapps.wallet.data.push.PushManager
import com.tonapps.tonkeeper.sign.SignManager
import com.tonapps.tonkeeper.ui.screen.action.ActionViewModel
import com.tonapps.tonkeeper.ui.screen.backup.main.BackupViewModel
import com.tonapps.tonkeeper.ui.screen.backup.attention.BackupAttentionViewModel
import com.tonapps.tonkeeper.ui.screen.backup.check.BackupCheckViewModel
import com.tonapps.tonkeeper.ui.screen.browser.connected.BrowserConnectedViewModel
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppViewModel
import com.tonapps.tonkeeper.ui.screen.browser.explore.BrowserExploreViewModel
import com.tonapps.tonkeeper.ui.screen.browser.main.BrowserMainViewModel
import com.tonapps.tonkeeper.ui.screen.browser.search.BrowserSearchViewModel
import com.tonapps.tonkeeper.ui.screen.collectibles.CollectiblesViewModel
import com.tonapps.tonkeeper.ui.screen.country.CountryPickerViewModel
import com.tonapps.tonkeeper.ui.screen.dialog.encrypted.EncryptedCommentViewModel
import com.tonapps.tonkeeper.ui.screen.events.EventsViewModel
import com.tonapps.tonkeeper.ui.screen.settings.currency.CurrencyViewModel
import com.tonapps.tonkeeper.ui.screen.init.InitViewModel
import com.tonapps.tonkeeper.ui.screen.settings.language.LanguageViewModel
import com.tonapps.tonkeeper.ui.screen.name.base.NameViewModel
import com.tonapps.tonkeeper.ui.screen.name.edit.EditNameViewModel
import com.tonapps.tonkeeper.ui.screen.notifications.NotificationsViewModel
import com.tonapps.tonkeeper.ui.screen.send.SendViewModel
import com.tonapps.tonkeeper.ui.screen.wallet.picker.PickerViewModel
import com.tonapps.tonkeeper.ui.screen.wallet.picker.list.WalletPickerAdapter
import com.tonapps.tonkeeper.ui.screen.settings.main.SettingsViewModel
import com.tonapps.tonkeeper.ui.screen.settings.passcode.ChangePasscodeViewModel
import com.tonapps.tonkeeper.ui.screen.settings.security.SecurityViewModel
import com.tonapps.tonkeeper.ui.screen.settings.theme.ThemeViewModel
import com.tonapps.tonkeeper.ui.screen.token.picker.TokenPickerViewModel
import com.tonapps.tonkeeper.ui.screen.token.viewer.TokenViewModel
import com.tonapps.tonkeeper.ui.screen.wallet.main.WalletViewModel
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.WalletAdapter
import com.tonapps.tonkeeper.ui.screen.wallet.manage.TokensManageViewModel
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val koinModel = module {
    factory { Dispatchers.Default }
    single(createdAtStart = true) { CoroutineScope(Dispatchers.IO + SupervisorJob()) }
    single { SettingsRepository(get(), get(), get()) }
    single { PasscodeDataStore(get()) }
    single { PasscodeRepository(get(), get(), get()) }
    single { NetworkMonitor(get(), get()) }
    single { PushManager(get(), get(), get(), get(), get(), get(), get()) }
    single { SignManager(get(), get(), get(), get(), get()) }
    single { HistoryHelper(get(), get(), get(), get()) }
    single { FiatMethodsRepository(get(), get()) }
    single { Fiat(get(), get(), get()) }

    uiAdapter { WalletAdapter(get()) }
    uiAdapter { WalletPickerAdapter() }

    viewModel { parameters -> NameViewModel(mode = parameters.get(), get(), get(), get()) }
    viewModel { parameters -> InitViewModel(get(), parameters.get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { MainViewModel(get(), get()) }
    viewModel { RootViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { PickerViewModel(get(), get()) }
    viewModel { WalletViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { CurrencyViewModel(get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get()) }
    viewModel { EditNameViewModel(get()) }
    viewModel { LanguageViewModel(get()) }
    viewModel { SecurityViewModel(get()) }
    viewModel { ThemeViewModel(get()) }
    viewModel { EventsViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { parameters -> TCAuthViewModel(request = parameters.get(), get(), get(), get()) }
    viewModel { CollectiblesViewModel(get(), get(), get(), get()) }
    viewModel { parameters -> ActionViewModel(args = parameters.get(), get(), get()) }
    viewModel { BrowserExploreViewModel(get(), get(), get(), get()) }
    viewModel { BrowserConnectedViewModel(get(), get()) }
    viewModel { BrowserMainViewModel(get()) }
    viewModel { BrowserSearchViewModel(get(), get(), get(), get()) }
    viewModel { parameters -> DAppViewModel(url = parameters.get(), get(), get()) }
    viewModel { ChangePasscodeViewModel(get(), get()) }
    viewModel { EncryptedCommentViewModel(get(), get()) }
    viewModel { NotificationsViewModel(get(), get(), get()) }
    viewModel { parameters -> TokenViewModel(get(), tokenAddress = parameters.get(), get(), get(), get(), get(), get(), get()) }
    viewModel { BackupViewModel(get(), get(), get()) }
    viewModel { BackupCheckViewModel(get(), get()) }
    viewModel { BackupAttentionViewModel(get(), get()) }
    viewModel { TokensManageViewModel(get(), get(), get()) }
    viewModel { parameters -> SendViewModel(nftAddress = parameters.get(), accountRepository = get(), api = get(), settingsRepository = get(), tokenRepository = get(), ratesRepository = get(), passcodeRepository = get()) }
    viewModel { TokenPickerViewModel(get(), get(), get()) }
    viewModel { CountryPickerViewModel(get(), get(), get()) }
}