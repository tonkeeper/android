package com.tonapps.tonkeeper.ui.screen.browser.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.ui.screen.browser.explore.list.Item
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.browser.BrowserRepository
import com.tonapps.wallet.data.browser.entities.BrowserDataEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BrowserExploreViewModel(
    private val browserRepository: BrowserRepository,
    private val api: API,
): ViewModel() {

    private val _uiItemsFlow = MutableStateFlow<List<Item>>(emptyList())
    val uiItemsFlow = _uiItemsFlow.asStateFlow()

    init {
        viewModelScope.launch {
            val data = browserRepository.load("RU") ?: return@launch
            setData(data)
        }
    }

    private fun setData(data: BrowserDataEntity) {
        val items = mutableListOf<Item>()
        if (data.apps.isNotEmpty()) {
            items.add(Item.Banners(data.apps, api.config.featuredPlayInterval))
        }
        for (category in data.categories) {
            items.add(Item.Title(category.title))
            for (app in category.apps) {
                items.add(Item.App(app))
            }
        }

        _uiItemsFlow.value = items.toList()
    }
}