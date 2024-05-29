package com.tonapps.tonkeeper.ui.screen.backup.attention

import android.content.Context
import androidx.lifecycle.ViewModel
import com.tonapps.tonkeeper.password.PasscodeRepository
import com.tonapps.wallet.data.account.WalletRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.take

class BackupAttentionViewModel(
    private val walletRepository: WalletRepository,
    private val passcodeRepository: PasscodeRepository
): ViewModel() {

    fun getRecoveryPhrase(
        context: Context
    ) = walletRepository.activeWalletFlow.combine(passcodeRepository.confirmationFlow(context)) { wallet, _ ->
        walletRepository.getMnemonic(wallet.id)
    }.take(1)

}