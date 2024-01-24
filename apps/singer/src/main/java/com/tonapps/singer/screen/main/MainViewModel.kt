package com.tonapps.singer.screen.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.singer.core.KeyEntity
import com.tonapps.singer.core.account.AccountRepository
import com.tonapps.singer.screen.main.list.MainItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import uikit.list.ListCell

class MainViewModel(
    private val accountRepository: AccountRepository
): ViewModel() {

    private val accounts = accountRepository.keysEntityFlow.map(::mapKeys)

    private val _uiItems = MutableStateFlow(uiItemsPrefix)
    val uiItems = _uiItems.asStateFlow()

    init {
        accounts.map(::wrapAccounts).onEach(::setUiItems).launchIn(viewModelScope)
    }

    private fun setUiItems(items: List<MainItem>) {
        _uiItems.value = items
    }

    private companion object {
        private val uiItemsPrefix: List<MainItem> = listOf(MainItem.Actions)

        private fun wrapAccounts(accounts: List<MainItem.Account>): List<MainItem> {
            val uiItems = mutableListOf<MainItem>()
            uiItems.addAll(uiItemsPrefix)
            uiItems.addAll(accounts)
            return uiItems
        }

        private fun mapKeys(keys: List<KeyEntity>): List<MainItem.Account> {
            val items = mutableListOf<MainItem.Account>()
            for ((index, key) in keys.withIndex()) {
                val position = ListCell.getPosition(keys.size, index)
                items.add(MainItem.Account(key, position))
            }
            return items.toList()
        }
    }
}