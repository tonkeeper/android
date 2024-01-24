package com.tonapps.singer.screen.change

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.singer.core.password.Password
import com.tonapps.singer.core.SimpleState
import com.tonapps.singer.core.account.AccountRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class ChangeViewModel(
    private val accountRepository: AccountRepository,
    private val savedStateHandle: SavedStateHandle
): ViewModel() {

    companion object {
        const val CURRENT_INDEX = 0
        const val NEW_INDEX = 1
        const val CONFIRM_INDEX = 2

        const val PAGE_COUNT = 3

        private const val CURRENT_PASSWORD_KEY = "current_password"
        private const val NEW_PASSWORD_KEY = "new_password"
        private const val CONFIRM_PASSWORD_KEY = "confirm_password"
    }

    private val _onReady = Channel<Unit>(Channel.BUFFERED)
    val onReady: Flow<Unit> = _onReady.receiveAsFlow()

    private val _uiPageIndex = MutableStateFlow<Int?>(null)
    val uiPageIndex = _uiPageIndex.asSharedFlow().filterNotNull()

    private val _uiState = MutableStateFlow<UiState?>(null)
    val uiState = _uiState.asSharedFlow().filterNotNull()

    private val currentPassword: String
        get() = savedStateHandle[CURRENT_PASSWORD_KEY] ?: ""

    private val newPassword: String
        get() = savedStateHandle[NEW_PASSWORD_KEY] ?: ""

    private val confirmPassword: String
        get() = savedStateHandle[CONFIRM_PASSWORD_KEY] ?: ""

    private var currentPageIndex: Int = -1
        set(value) {
            if (field != value) {
                field = value
                _uiPageIndex.value = value
            }
        }

    init {
        setCurrentPage(CURRENT_INDEX)
    }

    fun setCurrentPage(page: Int) {
        currentPageIndex = page

        checkInputValid(page)
    }

    fun setPassword(page: Int, password: String) {
        when(page) {
            CURRENT_INDEX -> savedStateHandle[CURRENT_PASSWORD_KEY] = password
            NEW_INDEX -> savedStateHandle[NEW_PASSWORD_KEY] = password
            CONFIRM_INDEX -> savedStateHandle[CONFIRM_PASSWORD_KEY] = password
        }

        checkInputValid(page)
    }

    fun continuePassword() {
        when (currentPageIndex) {
            CURRENT_INDEX -> checkCurrentPassword()
            NEW_INDEX -> setCurrentPage(CONFIRM_INDEX)
            CONFIRM_INDEX -> checkNewPassword()
        }
    }

    private fun checkCurrentPassword() {
        val password = currentPassword

        accountRepository.checkPasswordFlow(password).map {
            UiState.Task(it, CURRENT_INDEX)
        }.onEach {
            _uiState.value = it

            if (it.state == SimpleState.Success) {
                setCurrentPage(NEW_INDEX)
            }
        }.launchIn(viewModelScope)
    }

    private fun checkNewPassword() {
        viewModelScope.launch {
            _uiState.value = UiState.Task(SimpleState.Loading, CONFIRM_INDEX)

            if (newPassword != confirmPassword) {
                _uiState.value = UiState.Task(SimpleState.Error, CONFIRM_INDEX)
                delay(500)
                setCurrentPage(NEW_INDEX)
                return@launch
            }

            accountRepository.setPassword(newPassword)
            _onReady.trySend(Unit)
        }
    }

    private fun checkInputValid(page: Int) {
        val password = when (page) {
            CURRENT_INDEX -> currentPassword
            NEW_INDEX -> newPassword
            else -> confirmPassword
        }

        val isValid = Password.isValid(password)

        _uiState.value = UiState.InputValid(isValid, page)
    }

}