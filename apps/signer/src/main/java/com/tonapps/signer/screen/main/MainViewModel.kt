package com.tonapps.signer.screen.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.signer.core.entities.KeyEntity
import com.tonapps.signer.core.repository.KeyRepository
import com.tonapps.signer.screen.main.list.MainItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uikit.list.ListCell

class MainViewModel(
    private val repository: KeyRepository
): ViewModel() {

    val uiItems = repository.stream.map(::mapKeys).map(::wrapAccounts).stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList(),
    )

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