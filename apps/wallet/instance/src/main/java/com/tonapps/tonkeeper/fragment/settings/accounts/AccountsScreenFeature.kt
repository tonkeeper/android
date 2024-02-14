package com.tonapps.tonkeeper.fragment.settings.accounts

import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.event.ChangeWalletLabelEvent
import com.tonapps.tonkeeper.event.WalletRemovedEvent
import core.EventBus
import kotlinx.coroutines.launch
import uikit.mvi.UiFeature

class AccountsScreenFeature: UiFeature<AccountsScreenState, AccountsScreenEffect>(AccountsScreenState()) {

    private val updateWalletNameAction = fun (event: ChangeWalletLabelEvent) {
        requestWallets()
    }

    private val removeWalletAction = fun (event: WalletRemovedEvent) {
        requestWallets()
    }

    init {
        requestWallets()
        EventBus.subscribe(ChangeWalletLabelEvent::class.java, updateWalletNameAction)
        EventBus.subscribe(WalletRemovedEvent::class.java, removeWalletAction)
    }

    override fun onCleared() {
        super.onCleared()
        EventBus.unsubscribe(ChangeWalletLabelEvent::class.java, updateWalletNameAction)
        EventBus.unsubscribe(WalletRemovedEvent::class.java, removeWalletAction)
    }

    private fun requestWallets() {
        viewModelScope.launch {
            val wallets = App.walletManager.getWallets()
            updateUiState {
                it.copy(
                    emptyWallets = wallets.isEmpty(),
                    wallets = wallets
                )
            }
        }
    }

}