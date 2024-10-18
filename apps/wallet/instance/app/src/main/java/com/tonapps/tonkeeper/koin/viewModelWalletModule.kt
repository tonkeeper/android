package com.tonapps.tonkeeper.koin

import org.koin.dsl.module
import com.tonapps.tonkeeper.ui.screen.wallet.main.WalletViewModel
import com.tonapps.tonkeeper.ui.screen.settings.main.SettingsViewModel
import com.tonapps.tonkeeper.ui.screen.name.edit.EditNameViewModel
import com.tonapps.tonkeeper.ui.screen.events.EventsViewModel
import com.tonapps.tonkeeper.ui.screen.collectibles.CollectiblesViewModel
import com.tonapps.tonkeeper.ui.screen.browser.explore.BrowserExploreViewModel
import com.tonapps.tonkeeper.ui.screen.browser.connected.BrowserConnectedViewModel
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
import com.tonapps.tonkeeper.ui.screen.send.contacts.main.SendContactsViewModel
import com.tonapps.tonkeeper.ui.screen.purchase.PurchaseViewModel
import com.tonapps.tonkeeper.ui.screen.nft.NftViewModel
import com.tonapps.tonkeeper.ui.screen.send.contacts.add.AddContactViewModel
import com.tonapps.tonkeeper.ui.screen.send.contacts.edit.EditContactViewModel
import com.tonapps.tonkeeper.ui.screen.staking.viewer.StakeViewerViewModel
import com.tonapps.tonkeeper.ui.screen.staking.unstake.UnStakeViewModel
import com.tonapps.tonkeeper.ui.screen.staking.stake.StakingViewModel
import com.tonapps.tonkeeper.ui.screen.send.transaction.SendTransactionViewModel
import com.tonapps.tonkeeper.ui.screen.staking.withdraw.StakeWithdrawViewModel
import org.koin.core.module.dsl.viewModelOf

val viewModelWalletModule = module {
    viewModelOf(::WalletViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::EditNameViewModel)
    viewModelOf(::EventsViewModel)
    viewModelOf(::CollectiblesViewModel)
    viewModelOf(::BrowserExploreViewModel)
    viewModelOf(::BrowserConnectedViewModel)
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
}