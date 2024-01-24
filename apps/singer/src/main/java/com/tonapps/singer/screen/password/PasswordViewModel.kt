package com.tonapps.singer.screen.password

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.singer.core.SimpleState
import com.tonapps.singer.core.account.AccountRepository
import com.tonapps.singer.core.password.Password
import com.tonapps.singer.screen.root.action.RootAction
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class PasswordViewModel(
    private val accountRepository: AccountRepository
): ViewModel() {

    private val _uiState = MutableSharedFlow<SimpleState>()
    val uiState = _uiState.asSharedFlow()

    fun checkPassword(password: String) {
        viewModelScope.launch {
            _uiState.emit(SimpleState.Loading)

            val result = accountRepository.checkPassword(password)

            if (result == Password.Result.Success) {
                _uiState.emit(SimpleState.Success)
            } else {
                _uiState.emit(SimpleState.Error)
            }
        }
    }
}