package com.tonapps.signer.screen.change

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.signer.password.Password
import com.tonapps.signer.SimpleState
import com.tonapps.signer.vault.SignerVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class ChangeViewModel(
    private val vault: SignerVault,
    private val savedStateHandle: SavedStateHandle
): ViewModel() {

    companion object {
        const val CURRENT_INDEX = 0
        const val NEW_INDEX = 1
        const val CONFIRM_INDEX = 2

        const val PAGE_COUNT = 3
    }

    private val args = ChangeArgs(savedStateHandle)

    private val _onReady = Channel<Unit>(Channel.BUFFERED)
    val onReady: Flow<Unit> = _onReady.receiveAsFlow()

    private val _uiPageIndex = MutableStateFlow<Int?>(null)
    val uiPageIndex = _uiPageIndex.asSharedFlow().filterNotNull()

    private val _uiState = MutableStateFlow<UiState?>(null)
    val uiState = _uiState.asSharedFlow().filterNotNull()

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

    fun setPassword(page: Int, value: CharArray) {
        when(page) {
            CURRENT_INDEX -> args.currentPassword = value
            NEW_INDEX -> args.newPassword = value
            CONFIRM_INDEX -> args.confirmPassword = value
        }

        checkInputValid(page)
    }

    fun continuePassword() {
        when (currentPageIndex) {
            CURRENT_INDEX -> setCurrentPage(NEW_INDEX)
            NEW_INDEX -> setCurrentPage(CONFIRM_INDEX)
            CONFIRM_INDEX -> checkNewPassword()
        }
    }

    private fun checkNewPassword() {
        val password = args.currentPassword ?: return
        val newPassword = args.newPassword ?: return
        val confirmPassword = args.confirmPassword ?: return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = UiState.Task(SimpleState.Loading, CONFIRM_INDEX)

            if (!newPassword.contentEquals(confirmPassword)) {
                _uiState.value = UiState.Task(SimpleState.Error, CURRENT_INDEX)
                delay(500)
                setCurrentPage(CURRENT_INDEX)
                return@launch
            }

            if (vault.changePassword(newPassword, password)) {
                _onReady.trySend(Unit)
            } else {
                _uiState.value = UiState.Task(SimpleState.Error, CURRENT_INDEX)
                delay(500)
                setCurrentPage(CURRENT_INDEX)
            }
        }
    }

    private fun checkInputValid(page: Int) {
        val password = when (page) {
            CURRENT_INDEX -> args.currentPassword
            NEW_INDEX -> args.newPassword
            else -> args.confirmPassword
        } ?: return

        val isValid = Password.isValid(password)

        _uiState.value = UiState.InputValid(isValid, page)
    }

}