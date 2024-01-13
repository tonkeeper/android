package com.tonapps.singer.screen.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.singer.core.account.AccountRepository
import com.tonapps.singer.core.flow.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class RootViewModel(
    private val accountRepository: AccountRepository
): ViewModel() {

    val initialized: Boolean
        get() = accountRepository.keysEntityFlow.value is Resource.Success

    private val _hasKeys = MutableStateFlow<Boolean?>(null)
    val hasKeys = _hasKeys.asStateFlow()

    init {
        accountRepository.keysEntityFlow.onEach {
            if (it is Resource.Success) {
                _hasKeys.value = it.value.isNotEmpty()
            }
        }.launchIn(viewModelScope)
    }
}