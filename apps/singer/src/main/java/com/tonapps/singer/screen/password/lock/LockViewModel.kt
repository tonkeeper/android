package com.tonapps.singer.screen.password.lock

import androidx.lifecycle.ViewModel
import com.tonapps.singer.core.account.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class LockViewModel(
    private val accountRepository: AccountRepository
): ViewModel() {

    fun checkPassword(password: String): Flow<PasswordState> = flow {
        emit(PasswordState.Checking)

        val valid = accountRepository.checkPassword(password)
        if (valid) {
            emit(PasswordState.Success)
        } else {
            emit(PasswordState.Error)
        }
    }
}