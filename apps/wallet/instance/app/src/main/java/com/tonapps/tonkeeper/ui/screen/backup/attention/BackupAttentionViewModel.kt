package com.tonapps.tonkeeper.ui.screen.backup.attention

import android.content.Context
import androidx.lifecycle.ViewModel
import com.tonapps.tonkeeper.password.PasscodeRepository
import com.tonapps.wallet.data.account.AccountRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.take

class BackupAttentionViewModel(
    private val accountRepository: AccountRepository,
    private val passcodeRepository: PasscodeRepository
): ViewModel() {

    fun getRecoveryPhrase(
        context: Context
    ) = accountRepository.selectedWalletFlow.combine(passcodeRepository.confirmationFlow(context)) { wallet, _ ->
        accountRepository.getMnemonic(wallet.id)
    }.take(1)

}