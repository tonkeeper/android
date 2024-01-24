package com.tonapps.singer.screen.name

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.singer.core.account.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class NameViewModel(
    private val id: Long,
    private val accountRepository: AccountRepository
): ViewModel() {

    val nameFlow = accountRepository.getKey(id).map {
        it.name
    }

    fun save(name: String) {
        viewModelScope.launch {
            accountRepository.setName(id, name)
        }
    }
}