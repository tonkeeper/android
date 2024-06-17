package com.tonapps.tonkeeper.ui.screen.wallet.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.core.entities.TokenExtendedEntity
import com.tonapps.tonkeeper.ui.screen.wallet.manage.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import uikit.extensions.collectFlow

class TokensManageViewModel(
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val tokenRepository: TokenRepository,
): ViewModel() {

    private val tokensFlow = combine(
        accountRepository.selectedWalletFlow,
        settingsRepository.tokenPrefsChangedFlow,
    ) { wallet, _ ->
        tokenRepository.getLocal(settingsRepository.currency, wallet.accountId, wallet.testnet).map { token ->
            TokenExtendedEntity(
                raw = token,
                prefs = settingsRepository.getTokenPrefs(wallet.id, token.address),
            )
        }.filter { !it.raw.isTon }
    }

    private val _uiItemsFlow = MutableStateFlow<List<Item>>(emptyList())
    val uiItemsFlow = _uiItemsFlow.asStateFlow()

    init {
        tokensFlow.map { tokens ->
            val pinnedTokens = tokens.filter { it.pinned }.sortedBy {
                it.index
            }
            val otherTokens = tokens.filter { !it.pinned }

            val items = mutableListOf<Item>()
            if (pinnedTokens.isNotEmpty()) {
                items.add(Item.Title(Localization.pinned))
                for ((index, token) in pinnedTokens.withIndex()) {
                    items.add(Item.Token(ListCell.getPosition(pinnedTokens.size, index), token))
                }
            }

            if (otherTokens.isNotEmpty()) {
                items.add(Item.Space)
                items.add(Item.Title(Localization.all_assets, Localization.sorted_by_price))
                for ((index, token) in otherTokens.withIndex()) {
                    items.add(Item.Token(ListCell.getPosition(otherTokens.size, index), token))
                }
            }

            items
        }.flowOn(Dispatchers.IO).onEach { _uiItemsFlow.value = it }.launchIn(viewModelScope)
    }

    fun changeOrder(address: String, toIndex: Int) {
        val uiItems = _uiItemsFlow.value.toMutableList()
        val fromIndex = uiItems.indexOfFirst {
            it is Item.Token && it.address == address
        }
        if (fromIndex == -1) {
            return
        }
        val item = uiItems.removeAt(fromIndex)
        uiItems.add(toIndex, item)

        val pinnedItems = uiItems.filterIsInstance<Item.Token>().filter { it.pinned }
        val newPinnedUiItems = pinnedItems.mapIndexed { index, pinnedItem ->
            pinnedItem.copy(position = ListCell.getPosition(pinnedItems.size, index))
        }.toList()
        uiItems.removeAll(pinnedItems)
        uiItems.addAll(1, newPinnedUiItems)

        _uiItemsFlow.value = uiItems

        collectFlow(accountRepository.selectedWalletFlow.take(1)) { wallet ->
            settingsRepository.setTokensSort(wallet.id, newPinnedUiItems.map {
                it.address
            })
        }
    }

    fun onPinChange(tokenAddress: String, pin: Boolean) {
        collectFlow(accountRepository.selectedWalletFlow.take(1)) { wallet ->
            settingsRepository.setTokenPinned(wallet.id, tokenAddress, pin)
        }
    }

    fun onHiddenChange(tokenAddress: String, hidden: Boolean) {
        collectFlow(accountRepository.selectedWalletFlow.take(1)) { wallet ->
            settingsRepository.setTokenHidden(wallet.id, tokenAddress, hidden)
        }
    }
}