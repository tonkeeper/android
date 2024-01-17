package com.tonkeeper.fragment.wallet.init.pager.child.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonkeeper.api.Tonapi
import core.QueueScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import uikit.mvi.AsyncState

class WatchViewModel: ViewModel() {

    private val queueScope = QueueScope(viewModelScope.coroutineContext)

    private val _inputState = MutableStateFlow(AsyncState.Default)
    val inputState = _inputState.asStateFlow()

    fun checkAddress(address: String) {
        queueScope.submit {
            if (address.isEmpty()) {
                _inputState.value = AsyncState.Default
                return@submit
            }

            _inputState.value = AsyncState.Loading

            val account = Tonapi.resolveAccount(address, false)
            if (account == null) {
                _inputState.value = AsyncState.Error
                return@submit
            }

            _inputState.value = AsyncState.Success
        }
    }

    override fun onCleared() {
        super.onCleared()
        queueScope.cancel()
    }
}