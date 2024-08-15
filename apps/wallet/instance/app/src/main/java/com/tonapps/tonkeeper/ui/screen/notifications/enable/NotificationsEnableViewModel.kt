package com.tonapps.tonkeeper.ui.screen.notifications.enable

import androidx.lifecycle.ViewModel
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take

class NotificationsEnableViewModel(
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository
): ViewModel() {

    fun enablePush() = accountRepository.selectedWalletFlow.take(1).map { wallet ->
        settingsRepository.setPushWallet(wallet.id, true)
    }
}