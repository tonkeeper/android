package com.tonapps.tonkeeper.ui.screen.browser.explore

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.browser.explore.list.Item
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.browser.BrowserRepository
import com.tonapps.wallet.data.browser.entities.BrowserDataEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch

class BrowserExploreViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val browserRepository: BrowserRepository,
    private val api: API,
    private val settingsRepository: SettingsRepository
): BaseWalletVM(app) {

    private val _uiItemsFlow = MutableStateFlow<List<Item>>(emptyList())
    val uiItemsFlow = _uiItemsFlow.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val code = settingsRepository.country
            val locale = settingsRepository.getLocale()
            _uiItemsFlow.value = emptyList()
            browserRepository.load(code, wallet.testnet, locale)?.let { setData(it) }
            browserRepository.loadRemote(code, wallet.testnet, locale)?.let { setData(it) }
        }
    }

    private fun setData(data: BrowserDataEntity) {
        val items = mutableListOf<Item>()
        if (data.apps.isNotEmpty()) {
            items.add(Item.Banners(data.apps, api.config.featuredPlayInterval, wallet))
        }
        for (category in data.categories) {
            if (category.id == "featured") {
                continue
            }
            items.add(Item.Title(category.title))
            for (app in category.apps) {
                items.add(Item.App(app, wallet))
            }
        }

        _uiItemsFlow.value = items.toList()
    }
}