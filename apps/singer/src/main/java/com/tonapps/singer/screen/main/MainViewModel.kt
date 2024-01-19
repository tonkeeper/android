package com.tonapps.singer.screen.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.singer.core.account.AccountRepository
import com.tonapps.singer.screen.main.list.MainItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import uikit.list.ListCell

class MainViewModel(
    private val accountRepository: AccountRepository
): ViewModel() {

    private val _uiItems = MutableStateFlow<List<MainItem>>(createUiItems())
    val uiItems = _uiItems.asStateFlow()

    init {
        accountRepository.keysEntityFlow.filterNotNull().onEach { keys ->
            val uiItems = createUiItems()
            for ((index, key) in keys.withIndex()) {
                val item = MainItem.Account(
                    id = key.id,
                    label = key.name,
                    hex = key.hex,
                    position = ListCell.getPosition(keys.size, index)
                )
                uiItems.add(item)
            }
            _uiItems.value = uiItems.toList()
        }.launchIn(viewModelScope)
    }

    private fun createUiItems(): MutableList<MainItem> {
        val uiItems = mutableListOf<MainItem>()
        uiItems.add(MainItem.Actions)
        uiItems.add(MainItem.Space)

        return uiItems
    }
}