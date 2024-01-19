package com.tonkeeper.fragment.settings.accounts

import androidx.lifecycle.viewModelScope
import com.tonkeeper.App
import com.tonkeeper.event.ChangeWalletNameEvent
import com.tonkeeper.event.WalletRemovedEvent
import core.EventBus
import kotlinx.coroutines.launch
import uikit.mvi.UiFeature

class AccountsScreenFeature: UiFeature<AccountsScreenState, AccountsScreenEffect>(AccountsScreenState()) {

    private val updateWalletNameAction = fun (event: ChangeWalletNameEvent) {
        updateUiState { state ->
            val wallets = state.wallets.map {
                if (it.address == event.address) {
                    it.copy(name = event.name)
                } else {
                    it
                }
            }
            state.copy(
                emptyWallets = wallets.isEmpty(),
                wallets = wallets
            )
        }
    }

    private val removeWalletAction = fun (event: WalletRemovedEvent) {
        updateUiState { state ->
            val wallets = state.wallets.filter { it.address != event.address }
            state.copy(
                emptyWallets = wallets.isEmpty(),
                wallets = wallets
            )
        }
    }

    init {
        viewModelScope.launch {
            val wallets = App.walletManager.getWallets()
            updateUiState {
                it.copy(
                    emptyWallets = wallets.isEmpty(),
                    wallets = wallets
                )
            }
        }

        EventBus.subscribe(ChangeWalletNameEvent::class.java, updateWalletNameAction)
        EventBus.subscribe(WalletRemovedEvent::class.java, removeWalletAction)
    }

    override fun onCleared() {
        super.onCleared()
        EventBus.unsubscribe(ChangeWalletNameEvent::class.java, updateWalletNameAction)
        EventBus.unsubscribe(WalletRemovedEvent::class.java, removeWalletAction)
    }

}