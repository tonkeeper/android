package com.tonapps.tonkeeper.koin

import org.koin.dsl.module
import com.tonapps.tonkeeper.ui.screen.wallet.main.WalletViewModel
import com.tonapps.tonkeeper.ui.screen.settings.main.SettingsViewModel
import com.tonapps.tonkeeper.ui.screen.name.edit.EditNameViewModel
import com.tonapps.tonkeeper.ui.screen.events.main.EventsViewModel
import com.tonapps.tonkeeper.ui.screen.collectibles.main.CollectiblesViewModel
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppViewModel
import com.tonapps.tonkeeper.ui.screen.notifications.NotificationsManageViewModel
import com.tonapps.tonkeeper.ui.screen.token.viewer.TokenViewModel
import com.tonapps.tonkeeper.ui.screen.backup.main.BackupViewModel
import com.tonapps.tonkeeper.ui.screen.backup.check.BackupCheckViewModel
import com.tonapps.tonkeeper.ui.screen.wallet.manage.TokensManageViewModel
import com.tonapps.tonkeeper.ui.screen.send.main.SendViewModel
import com.tonapps.tonkeeper.ui.screen.token.picker.TokenPickerViewModel
import com.tonapps.tonkeeper.ui.screen.battery.settings.BatterySettingsViewModel
import com.tonapps.tonkeeper.ui.screen.battery.refill.BatteryRefillViewModel
import com.tonapps.tonkeeper.ui.screen.battery.recharge.BatteryRechargeViewModel
import com.tonapps.tonkeeper.ui.screen.browser.base.BrowserBaseViewModel
import com.tonapps.tonkeeper.ui.screen.browser.more.BrowserMoreViewModel
import com.tonapps.tonkeeper.ui.screen.card.CardViewModel
import com.tonapps.tonkeeper.ui.screen.collectibles.manage.CollectiblesManageViewModel
import com.tonapps.tonkeeper.ui.screen.events.spam.SpamEventsViewModel
import com.tonapps.tonkeeper.ui.screen.send.contacts.main.SendContactsViewModel
import com.tonapps.tonkeeper.ui.screen.purchase.PurchaseViewModel
import com.tonapps.tonkeeper.ui.screen.nft.NftViewModel
import com.tonapps.tonkeeper.ui.screen.qr.QRViewModel
import com.tonapps.tonkeeper.ui.screen.send.contacts.add.AddContactViewModel
import com.tonapps.tonkeeper.ui.screen.send.contacts.edit.EditContactViewModel
import com.tonapps.tonkeeper.ui.screen.staking.viewer.StakeViewerViewModel
import com.tonapps.tonkeeper.ui.screen.staking.unstake.UnStakeViewModel
import com.tonapps.tonkeeper.ui.screen.staking.stake.StakingViewModel
import com.tonapps.tonkeeper.ui.screen.send.transaction.SendTransactionViewModel
import com.tonapps.tonkeeper.ui.screen.settings.apps.AppsViewModel
import com.tonapps.tonkeeper.ui.screen.sign.SignDataViewModel
import com.tonapps.tonkeeper.ui.screen.staking.withdraw.StakeWithdrawViewModel
import com.tonapps.tonkeeper.ui.screen.transaction.TransactionViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf

val viewModelWalletModule = module {
    viewModelOf(::WalletViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::EditNameViewModel)
    viewModelOf(::EventsViewModel)
    viewModelOf(::CollectiblesViewModel)
    viewModelOf(::DAppViewModel)
    viewModelOf(::NotificationsManageViewModel)
    viewModelOf(::TokenViewModel)
    viewModelOf(::BackupViewModel)
    viewModelOf(::BackupCheckViewModel)
    viewModelOf(::TokensManageViewModel)
    viewModelOf(::SendViewModel)
    viewModelOf(::TokenPickerViewModel)
    viewModelOf(::BatterySettingsViewModel)
    viewModelOf(::BatteryRefillViewModel)
    viewModelOf(::BatteryRechargeViewModel)
    viewModelOf(::SendContactsViewModel)
    viewModelOf(::PurchaseViewModel)
    viewModelOf(::NftViewModel)
    viewModelOf(::StakeViewerViewModel)
    viewModelOf(::UnStakeViewModel)
    viewModelOf(::StakingViewModel)
    viewModelOf(::SendTransactionViewModel)
    viewModelOf(::StakeWithdrawViewModel)
    viewModelOf(::AddContactViewModel)
    viewModelOf(::EditContactViewModel)
    viewModelOf(::AppsViewModel)
    viewModelOf(::CollectiblesManageViewModel)
    viewModelOf(::CardViewModel)
    viewModelOf(::QRViewModel)
    viewModelOf(::TransactionViewModel)
    viewModelOf(::BrowserMoreViewModel)
    viewModelOf(::BrowserBaseViewModel)
    viewModelOf(::SpamEventsViewModel)
    viewModelOf(::SignDataViewModel)
}