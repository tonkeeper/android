package com.tonapps.tonkeeper.koin

import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.bus.generated.Events.DappBrowser.DappAppClickFrom
import com.tonapps.dapp.screens.confirm.DAppConfirmFeature
import com.tonapps.dapp.screens.session.WcSessionFeature
import com.tonapps.dapp.screens.sessions.WcSessionsFeature
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualConfirmFeature
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualDepositConfirmFeature
import com.tonapps.deposit.multicoin.screens.confirm.SwapConfirmFeature
import com.tonapps.deposit.multicoin.screens.confirm.TxConfirmFeature
import com.tonapps.deposit.multicoin.screens.assets.AssetsExtendedFeature
import com.tonapps.deposit.multicoin.screens.method.PaymentMethodFeature as McPaymentMethodFeature
import com.tonapps.deposit.multicoin.screens.picker.AssetPickerFeature
import com.tonapps.deposit.multicoin.screens.qr.ReceiveQrFeature
import com.tonapps.deposit.multicoin.screens.ramp.RampFeature as McRampFeature
import com.tonapps.deposit.multicoin.screens.ramp.amount.RampAmountFeature as McRampAmountFeature
import com.tonapps.deposit.multicoin.screens.receive.ReceiveFeature
import com.tonapps.deposit.multicoin.screens.send.SendFeature as McSendFeature
import com.tonapps.deposit.screens.assets.AssetsCryptoExtendedFeature
import com.tonapps.deposit.screens.buy.crypto.BuyWithCryptoFeature
import com.tonapps.deposit.screens.confirm.ConfirmFeature
import com.tonapps.deposit.screens.currency.SelectCurrencyFeature
import com.tonapps.deposit.screens.method.PaymentMethodFeature
import com.tonapps.deposit.screens.network.SelectNetworkFeature
import com.tonapps.deposit.screens.picker.TokenPickerFeature
import com.tonapps.deposit.screens.qr.QrAssetFeature
import com.tonapps.deposit.screens.ramp.RampFeature
import com.tonapps.deposit.screens.ramp.amount.DepositAmountFeature
import com.tonapps.deposit.screens.send.SendFeature
import com.tonapps.onboading.screens.backup.BackupCheckFeature
import com.tonapps.swap.screens.picker.SwapAssetPickerFeature
import com.tonapps.swap.SwapRoutes
import com.tonapps.swap.screens.swap.SwapFeature
import com.tonapps.tonkeeper.ui.screen.backup.check.BackupCheckViewModel
import com.tonapps.tonkeeper.ui.screen.backup.main.BackupViewModel
import com.tonapps.tonkeeper.ui.screen.browser.dapp.earn.EarnDAppBridges
import com.tonapps.tonkeeper.ui.screen.phrase.PhraseViewModel
import com.tonapps.tonkeeper.ui.screen.battery.recharge.BatteryRechargeViewModel
import com.tonapps.tonkeeper.ui.screen.battery.refill.BatteryRefillViewModel
import com.tonapps.tonkeeper.ui.screen.battery.settings.BatterySettingsViewModel
import com.tonapps.tonkeeper.ui.screen.browser.base.BrowserBaseViewModel
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppViewModel
import com.tonapps.tonkeeper.ui.screen.browser.dapp.earn.EarnDAppViewModel
import com.tonapps.tonkeeper.ui.screen.browser.more.BrowserMoreViewModel
import com.tonapps.tonkeeper.ui.screen.card.CardViewModel
import com.tonapps.tonkeeper.ui.screen.collectibles.main.CollectiblesViewModel
import com.tonapps.tonkeeper.ui.screen.collectibles.manage.CollectiblesManageViewModel
import com.tonapps.tonkeeper.ui.screen.dns.renew.DNSRenewViewModel
import com.tonapps.tonkeeper.ui.screen.events.compose.details.TxDetailsViewModel
import com.tonapps.tonkeeper.ui.screen.events.compose.history.TxEventsViewModel
import com.tonapps.tonkeeper.ui.screen.events.spam.SpamEventsViewModel
import com.tonapps.tonkeeper.ui.screen.name.edit.EditNameViewModel
import com.tonapps.tonkeeper.ui.screen.nft.NftViewModel
import com.tonapps.tonkeeper.ui.screen.notifications.NotificationsManageViewModel
import com.tonapps.tonkeeper.ui.screen.send.boc.RemoveExtensionViewModel
import com.tonapps.tonkeeper.ui.screen.send.contacts.add.AddContactViewModel
import com.tonapps.tonkeeper.ui.screen.send.contacts.edit.EditContactViewModel
import com.tonapps.tonkeeper.ui.screen.send.contacts.main.SendContactsViewModel
import com.tonapps.tonkeeper.ui.screen.send.main.SendViewModel
import com.tonapps.tonkeeper.ui.screen.send.transaction.SendTransactionViewModel
import com.tonapps.tonkeeper.ui.screen.settings.apps.AppsViewModel
import com.tonapps.tonkeeper.ui.screen.settings.extensions.ExtensionsViewModel
import com.tonapps.tonkeeper.ui.screen.settings.main.SettingsViewModel
import com.tonapps.tonkeeper.ui.screen.sign.SignDataViewModel
import com.tonapps.tonkeeper.ui.screen.staking.stake.StakingViewModel
import com.tonapps.tonkeeper.ui.screen.staking.unstake.UnStakeViewModel
import com.tonapps.tonkeeper.ui.screen.staking.viewer.StakeViewerViewModel
import com.tonapps.tonkeeper.ui.screen.staking.withdraw.StakeWithdrawViewModel
import com.tonapps.tonkeeper.ui.screen.swap.omniston.OmnistonViewModel
import com.tonapps.tonkeeper.ui.screen.swap.picker.SwapPickerViewModel
import com.tonapps.tonkeeper.ui.screen.token.picker.TokenPickerViewModel
import com.tonapps.tonkeeper.ui.screen.token.viewer.TokenViewModel
import com.tonapps.tonkeeper.ui.screen.transaction.TransactionViewModel
import com.tonapps.tonkeeper.ui.screen.tronfees.TronFeesViewModel
import com.tonapps.tonkeeper.ui.screen.wallet.main.WalletViewModel
import com.tonapps.tonkeeper.ui.screen.wallet.manage.TokensManageViewModel
import com.tonapps.portfolio.screens.manage.AccountsManageFeature
import com.tonapps.portfolio.screens.raffle.RaffleFeature
import com.tonapps.migration.screens.confirm.MigrationConfirmFeature
import com.tonapps.migration.screens.prepare.MigrationPrepareFeature
import com.tonapps.onboading.screens.backup.BackupFeature
import com.tonapps.portfolio.screens.search.SearchFeature
import com.tonapps.portfolio.screens.search.SearchFeatureData
import com.tonapps.wallet.features.events.screens.EventsFeature
import com.tonapps.wallet.features.events.screens.EventsScreenMode
import com.tonapps.wallet.features.events.screens.comments.EncryptedCommentsFeature
import com.tonapps.wallet.features.events.screens.market.PerpsMarketHistoryFeature
import com.tonapps.portfolio.screens.wallet.BiometryStatusProvider
import com.tonapps.portfolio.screens.wallet.MigrationStatusProvider
import com.tonapps.portfolio.screens.wallet.PushStatusProvider
import com.tonapps.tonkeeper.Environment
import com.tonapps.portfolio.screens.wallet.WalletFeature
import com.tonapps.tonkeeper.manager.migration.WalletMigrationStatusProvider
import com.tonapps.tonkeeper.manager.passcode.DeviceBiometryStatusProvider
import com.tonapps.portfolio.screens.list.WalletsListFeature
import com.tonapps.trading.screens.assets.AssetsFeature
import com.tonapps.trading.screens.details.AssetDetailsFeature
import com.tonapps.trading.screens.shelves.ShelvesFeature
import com.tonapps.perps.data.PerpsMarketFilter
import com.tonapps.perps.data.PerpsOrderSide
import com.tonapps.perps.data.PerpsSizeDirection
import com.tonapps.perps.screens.deposit.PerpsDepositFeature
import com.tonapps.perps.screens.details.PerpsAssetDetailsFeature
import com.tonapps.perps.screens.details.PerpsPositionAutoCloseFeature
import com.tonapps.perps.screens.margin.PerpsMarginChangeFeature
import com.tonapps.perps.screens.markets.PerpsMarketsFeature
import com.tonapps.perps.screens.order.PerpsCreateOrderFeature
import com.tonapps.perps.screens.order.PerpsLimitPriceFeature
import com.tonapps.perps.screens.review.PerpsReviewArgs
import com.tonapps.perps.screens.review.PerpsReviewFeature
import com.tonapps.perps.screens.portfolio.PerpsPortfolioFeature
import com.tonapps.perps.screens.sizechange.PerpsSizeChangeFeature
import com.tonapps.perps.screens.withdraw.PerpsWithdrawFeature
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import java.math.BigDecimal

val viewModelWalletModule = module {
    viewModelOf(::WalletViewModel)
    viewModelOf(::SettingsViewModel)
    viewModel { params -> EditNameViewModel(get(), get(), get(), get(), params.getOrNull()) }
    viewModelOf(::CollectiblesViewModel)
    viewModelOf(::DAppViewModel)
    viewModel { (wallet: WalletEntity, from: DappAppClickFrom) ->
        EarnDAppViewModel(get { parametersOf(wallet, from) })
    }
    factoryOf(::EarnDAppBridges)
    viewModelOf(::NotificationsManageViewModel)
    viewModelOf(::TokenViewModel)
    viewModelOf(::BackupViewModel)
    viewModelOf(::PhraseViewModel)
    viewModelOf(::BackupCheckViewModel)
    viewModelOf(::BackupCheckFeature)
    viewModelOf(::TokensManageViewModel)
    viewModelOf(::BatterySettingsViewModel)
    viewModelOf(::BatteryRefillViewModel)
    viewModelOf(::BatteryRechargeViewModel)
    viewModel { (nftAddress: String, prefetchedNft: NftEntity?) ->
        NftViewModel(
            app = get(),
            nftAddress = nftAddress,
            prefetchedNft = prefetchedNft,
            unifiedAccountRepository = get(),
            mcAccountRepository = get(),
            settingsRepository = get(),
            api = get(),
            collectiblesRepository = get(),
        )
    }
    viewModelOf(::StakeViewerViewModel)
    viewModelOf(::UnStakeViewModel)
    viewModelOf(::StakingViewModel)
    viewModelOf(::SendTransactionViewModel)
    viewModelOf(::RemoveExtensionViewModel)
    viewModelOf(::StakeWithdrawViewModel)
    viewModelOf(::AddContactViewModel)
    viewModelOf(::EditContactViewModel)
    viewModelOf(::AppsViewModel)
    viewModelOf(::ExtensionsViewModel)
    viewModelOf(::CollectiblesManageViewModel)
    viewModelOf(::CardViewModel)
    viewModelOf(::TransactionViewModel)
    viewModelOf(::BrowserMoreViewModel)
    viewModelOf(::BrowserBaseViewModel)
    viewModelOf(::SpamEventsViewModel)
    viewModelOf(::SignDataViewModel)
    viewModelOf(::OmnistonViewModel)
    viewModelOf(::SwapPickerViewModel)
    viewModelOf(::DNSRenewViewModel)
    viewModelOf(::TronFeesViewModel)

    viewModelOf(::TokenPickerViewModel)
    viewModelOf(::SendContactsViewModel)
    viewModelOf(::SendViewModel)

    // Compose
    viewModelOf(::TxEventsViewModel)
    viewModelOf(::TxDetailsViewModel)

    viewModelOf(::RampFeature)
    viewModelOf(::AssetsCryptoExtendedFeature)
    viewModelOf(::PaymentMethodFeature)
    viewModelOf(::DepositAmountFeature)
    viewModelOf(::QrAssetFeature)
    viewModelOf(::BuyWithCryptoFeature)
    viewModelOf(::SendFeature)
    viewModelOf(::ConfirmFeature)
    viewModelOf(::DAppConfirmFeature)
    viewModelOf(::SelectCurrencyFeature)
    viewModelOf(::SelectNetworkFeature)
    viewModelOf(::TokenPickerFeature)
    viewModelOf(::ShelvesFeature)
    single<PushStatusProvider> { get<Environment>() }
    single<MigrationStatusProvider> { WalletMigrationStatusProvider(get()) }
    single<BiometryStatusProvider> { DeviceBiometryStatusProvider(get()) }
    viewModelOf(::WalletFeature)
    viewModel { (walletId: String, raffleId: String?) ->
        RaffleFeature(
            walletId = walletId,
            raffleId = raffleId,
            raffleRepository = get(),
            accountRepository = get(),
            transactionManager = get(),
        )
    }
    viewModelOf(::WalletsListFeature)
    viewModelOf(::AccountsManageFeature)
    viewModel { (data: SearchFeatureData) ->
        SearchFeature(
            data = data,
            catalogSearchRepository = get(),
            accountRepoLegacy = get(),
            settingsRepository = get(),
            perpsRepository = get(),
            unifiedAccountRepository = get(),
        )
    }
    viewModel { params ->
        EventsFeature(
            application = get(),
            eventsRepository = get(),
            nftResolver = get(),
            perpsMarketResolver = get(),
            accountRepoLegacy = get(),
            accountRepo = get(),
            settingsRepository = get(),
            networkMonitor = get(),
            realtimeProvider = get(),
            unifiedAccountRepository = get(),
            assetId = params.getOrNull(),
            mode = params.getOrNull() ?: EventsScreenMode.General,
            awaitsFundingActivity = params.getOrNull() ?: false,
        )
    }
    viewModel { (walletId: String, marketIndex: Int) ->
        PerpsMarketHistoryFeature(
            walletId = walletId,
            marketIndex = marketIndex,
            eventsRepository = get(),
            settingsRepository = get(),
        )
    }

    // Mc
    viewModelOf(::ReceiveFeature)
    viewModelOf(::ReceiveQrFeature)
    viewModelOf(::McSendFeature)
    viewModelOf(::EncryptedCommentsFeature)
    viewModelOf(::TxConfirmFeature)
    viewModelOf(::SwapConfirmFeature)
    viewModelOf(::PerpetualConfirmFeature)
    viewModel { (request: ConfirmRequest) ->
        PerpetualDepositConfirmFeature(
            request = request,
            provider = get(),
            accountRepo = get(),
            passcodeManager = get(),
            postTransactionRefreshSchedule = get(),
            tracker = getOrNull(),
            authorizer = get(),
        )
    }
    viewModelOf(::AssetPickerFeature)
    viewModelOf(::AssetsExtendedFeature)
    viewModelOf(::McPaymentMethodFeature)
    viewModelOf(::McRampFeature)
    viewModelOf(::McRampAmountFeature)
    viewModelOf(::AssetsFeature)
    viewModelOf(::AssetDetailsFeature)
    viewModelOf(::WcSessionFeature)
    viewModelOf(::WcSessionsFeature)

    viewModelOf(::SwapAssetPickerFeature)
    viewModel { (route: SwapRoutes.Swap) ->
        SwapFeature(
            initialRoute = route,
            context = get(),
            oldAccount = get(),
            accountRepo = get(),
            swapRepo = get(),
            raffleRepository = get(),
            appLifecycle = get(),
            settingsRepository = get(),
            networkMonitor = get(),
            legacyAccounts = get(),
        )
    }

    // Perps
    viewModel { (filter: PerpsMarketFilter) ->
        PerpsMarketsFeature(
            repository = get(),
            livePriceStore = get(),
            initialFilter = filter,
        )
    }
    viewModelOf(::PerpsPortfolioFeature)
    viewModelOf(::PerpsDepositFeature)
    viewModelOf(::PerpsWithdrawFeature)
    viewModel { (marketIndex: Int, symbol: String?) ->
        PerpsAssetDetailsFeature(
            marketIndex = marketIndex,
            initialSymbol = symbol,
            repository = get(),
            livePrices = get(),
            accountRefresh = get(),
            executor = get(),
            adapter = get(),
            accountService = get(),
            unifiedAccountRepository = get(),
            passcodeManager = get(),
            settingsRepository = get(),
        )
    }
    viewModel { (marketIndex: Int) ->
        PerpsPositionAutoCloseFeature(
            marketIndex = marketIndex,
            adapter = get(),
            executor = get(),
            accountService = get(),
            unifiedAccountRepository = get(),
        )
    }
    viewModel { (marketIndex: Int, symbol: String, side: PerpsOrderSide) ->
        PerpsCreateOrderFeature(
            marketIndex = marketIndex,
            symbol = symbol,
            side = side,
            adapter = get(),
            perpetualRepository = get(),
            livePrices = get(),
            unifiedAccountRepository = get(),
            settingsRepository = get(),
        )
    }
    viewModel { (marketIndex: Int, symbol: String, side: PerpsOrderSide, initialPrice: BigDecimal?) ->
        PerpsLimitPriceFeature(
            marketIndex = marketIndex,
            symbol = symbol,
            side = side,
            initialPrice = initialPrice,
            adapter = get(),
            livePrices = get(),
            unifiedAccountRepository = get(),
        )
    }
    viewModel { (marketIndex: Int, symbol: String, side: PerpsOrderSide, direction: PerpsSizeDirection) ->
        PerpsSizeChangeFeature(
            marketIndex = marketIndex,
            symbol = symbol,
            side = side,
            direction = direction,
            repository = get(),
            adapter = get(),
            livePrices = get(),
            unifiedAccountRepository = get(),
            settingsRepository = get(),
        )
    }
    viewModel { (marketIndex: Int, symbol: String, side: PerpsOrderSide, add: Boolean) ->
        PerpsMarginChangeFeature(
            marketIndex = marketIndex,
            symbol = symbol,
            side = side,
            add = add,
            repository = get(),
            adapter = get(),
            livePrices = get(),
            unifiedAccountRepository = get(),
            settingsRepository = get(),
        )
    }
    viewModel { (args: PerpsReviewArgs) ->
        PerpsReviewFeature(
            args = args,
            livePrices = get(),
            adapter = get(),
            executor = get(),
            accountService = get(),
            unifiedAccountRepository = get(),
        )
    }

    viewModelOf(::BackupFeature)

    viewModelOf(::MigrationPrepareFeature)
    viewModelOf(::MigrationConfirmFeature)
}
