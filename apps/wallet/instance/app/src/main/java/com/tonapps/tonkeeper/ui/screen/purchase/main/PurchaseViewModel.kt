package com.tonapps.tonkeeper.ui.screen.purchase.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.tonkeeper.ui.screen.purchase.main.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.purchase.PurchaseRepository
import com.tonapps.wallet.data.purchase.entity.PurchaseMethodEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take

class PurchaseViewModel(
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val purchaseRepository: PurchaseRepository,
): ViewModel() {

    enum class Tab {
        BUY, SELL
    }

    val countryFlow = settingsRepository.countryFlow
    val walletFlow = accountRepository.selectedWalletFlow

    private val _tabFlow = MutableStateFlow(Tab.BUY)
    val tabFlow = _tabFlow.asStateFlow()

    private val dataFlow = combine(
        accountRepository.selectedWalletFlow,
        settingsRepository.countryFlow,
    ) { wallet, country ->
        purchaseRepository.get(wallet.testnet, country, settingsRepository.getLocale())
    }.flowOn(Dispatchers.IO)

    val uiItemsFlow = combine(dataFlow, tabFlow) { (buy, sell), tab ->
        val categories = if (tab == Tab.BUY) buy else sell
        val items = mutableListOf<Item>()
        for ((categoryIndex, category) in categories.withIndex()) {
            if (categoryIndex != 0) {
                items.add(Item.Space)
            }
            items.add(Item.Title(category.title))
            for ((methodIndex, method) in category.items.withIndex()) {
                val position = ListCell.getPosition(category.items.size, methodIndex)
                items.add(Item.Method(method, position))
            }
        }
        items.add(Item.Space)
        items
    }

    fun open(method: PurchaseMethodEntity) = walletFlow.take(1).map { wallet ->
        settingsRepository.isPurchaseOpenConfirm(wallet.id, method.id)
    }

    fun disableConfirmDialog(
        wallet: WalletEntity,
        method: PurchaseMethodEntity
    ) {
        settingsRepository.disablePurchaseOpenConfirm(wallet.id, method.id)
    }

    fun setTab(tab: Tab) {
        _tabFlow.value = tab
    }

}