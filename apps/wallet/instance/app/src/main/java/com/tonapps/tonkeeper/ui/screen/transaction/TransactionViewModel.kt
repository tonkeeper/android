package com.tonapps.tonkeeper.ui.screen.transaction

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.settings.SpamTransactionState
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TransactionViewModel(
    app: Application,
    private val api: API,
    private val settingsRepository: SettingsRepository,
): BaseWalletVM(app) {

    fun reportSpam(
        wallet: WalletEntity,
        txId: String,
        comment: String?,
        spam: Boolean,
        callback: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            loading(true)
            val state = if (spam) SpamTransactionState.SPAM else SpamTransactionState.NOT_SPAM
            settingsRepository.setSpamStateTransaction(wallet.id, txId, state)
            try {
                if (spam) {
                    api.reportTX(
                        txId = txId,
                        comment = comment,
                        recipient = wallet.accountId
                    )
                    toast(Localization.tx_marked_as_spam)
                }
            } catch (e: Throwable) {
                toast(Localization.unknown_error)
            }
            loading(false)
            withContext(Dispatchers.Main) {
                callback()
            }
            if (spam) {
                finish()
            }
        }
    }

}