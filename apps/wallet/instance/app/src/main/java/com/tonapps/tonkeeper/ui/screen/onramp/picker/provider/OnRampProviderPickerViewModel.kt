package com.tonapps.tonkeeper.ui.screen.onramp.picker.provider

import android.app.Application
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.onramp.picker.provider.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.purchase.PurchaseRepository
import com.tonapps.wallet.data.purchase.entity.PurchaseMethodEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class OnRampProviderPickerViewModel(
    app: Application,
    private val wallet: WalletEntity,
    provider: PurchaseMethodEntity,
    supportedProviders: List<PurchaseMethodEntity>,
    private val settingsRepository: SettingsRepository,
    private val purchaseRepository: PurchaseRepository,
): BaseWalletVM(app) {

    private val _selectedProviderFlow = MutableStateFlow(provider)
    val selectedProviderFlow = _selectedProviderFlow.asStateFlow()

    val uiItemsFlow = selectedProviderFlow.map { selectedProvider ->
        val list = mutableListOf<Item>()
        for ((index, itemProvider) in supportedProviders.withIndex()) {
            val position = ListCell.getPosition(supportedProviders.size, index)
            list.add(Item(
                position = position,
                provider = itemProvider,
                selected = itemProvider.id == selectedProvider.id,
                best = index == 0 && supportedProviders.size > 1
            ))
        }
        list.toList()
    }

    fun setSelectedProvider(provider: PurchaseMethodEntity) {
        _selectedProviderFlow.value = provider
    }
}