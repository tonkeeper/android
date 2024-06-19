package com.tonapps.tonkeeper.ui.screen.backup.attention

import android.content.Context
import androidx.lifecycle.ViewModel
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.take

class BackupAttentionViewModel(
    private val accountRepository: AccountRepository,
    private val passcodeManager: PasscodeManager
): ViewModel() {

    fun getRecoveryPhrase(
        context: Context
    ) = accountRepository.selectedWalletFlow.combine(passcodeManager.confirmationFlow(context, context.getString(Localization.app_name))) { wallet, _ ->
        accountRepository.getMnemonic(wallet.id)
    }.take(1)

}