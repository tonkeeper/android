package com.tonapps.tonkeeper.ui.screen.phrase

import android.app.Application
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.data.account.AccountRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhraseViewModel(
    app: Application,
    private val accountRepository: AccountRepository,
) : BaseWalletVM(app) {

    // Multichain wallets are absent from the legacy accounts table, so a hit here means legacy.
    suspend fun legacyWalletIdOrNull(): String? = withContext(Dispatchers.IO) {
        val id = accountRepository.getSelectedWalletId() ?: return@withContext null
        accountRepository.getWalletById(id)?.id
    }
}
