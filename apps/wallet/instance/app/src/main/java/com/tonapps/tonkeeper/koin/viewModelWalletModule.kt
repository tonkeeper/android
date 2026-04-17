package com.tonapps.tonkeeper.koin

import com.tonapps.dapp.warning.DAppConfirmFeature
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
import com.tonapps.tonkeeper.ui.screen.backup.check.BackupCheckViewModel
import com.tonapps.tonkeeper.ui.screen.backup.main.BackupViewModel
import com.tonapps.tonkeeper.ui.screen.battery.recharge.BatteryRechargeViewModel
import com.tonapps.tonkeeper.ui.screen.battery.refill.BatteryRefillViewModel
import com.tonapps.tonkeeper.ui.screen.battery.settings.BatterySettingsViewModel
import com.tonapps.tonkeeper.ui.screen.browser.base.BrowserBaseViewModel
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppViewModel
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
import com.tonapps.tonkeeper.ui.screen.onramp.main.OnRampViewModel
import com.tonapps.tonkeeper.ui.screen.onramp.picker.currency.OnRampPickerViewModel
import com.tonapps.tonkeeper.ui.screen.onramp.picker.provider.OnRampProviderPickerViewModel
import com.tonapps.tonkeeper.ui.screen.purchase.PurchaseViewModel
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
import com.tonapps.trading.screens.shelves.ShelvesFeature
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelWalletModule = module {
    viewModelOf(::WalletViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::EditNameViewModel)
    viewModelOf(::CollectiblesViewModel)
    viewModelOf(::DAppViewModel)
    viewModelOf(::NotificationsManageViewModel)
    viewModelOf(::TokenViewModel)
    viewModelOf(::BackupViewModel)
    viewModelOf(::BackupCheckViewModel)
    viewModelOf(::TokensManageViewModel)
    viewModelOf(::BatterySettingsViewModel)
    viewModelOf(::BatteryRefillViewModel)
    viewModelOf(::BatteryRechargeViewModel)
    viewModelOf(::NftViewModel)
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
    viewModelOf(::PurchaseViewModel)
    viewModelOf(::SendViewModel)
    viewModelOf(::OnRampViewModel)
    viewModelOf(::OnRampProviderPickerViewModel)
    viewModelOf(::OnRampPickerViewModel)

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
}
