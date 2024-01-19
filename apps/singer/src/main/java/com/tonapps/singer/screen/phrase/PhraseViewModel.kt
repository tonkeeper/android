package com.tonapps.singer.screen.phrase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.singer.core.account.AccountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class PhraseViewModel(
    private val id: Long,
    private val accountRepository: AccountRepository
): ViewModel() {

    private val _mnemonic = MutableStateFlow<List<String>?>(null)
    val mnemonic = _mnemonic.asStateFlow().filterNotNull()

    init {
        viewModelScope.launch {
            val mnemonic = accountRepository.getMnemonic(id)
            _mnemonic.value = mnemonic
        }
    }
}