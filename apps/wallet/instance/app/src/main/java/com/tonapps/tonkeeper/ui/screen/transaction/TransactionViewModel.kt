package com.tonapps.tonkeeper.ui.screen.transaction

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.events.EventsRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.settings.SpamTransactionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TransactionViewModel(
    app: Application,
    private val settingsRepository: SettingsRepository,
    private val eventsRepository: EventsRepository,
): BaseWalletVM(app) {

    fun reportSpam(
        wallet: WalletEntity,
        txId: String,
        spam: Boolean,
        callback: (isApplied: Boolean) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val isApplied = if (spam) {
                markAsSpam(wallet, txId)
            } else {
                settingsRepository.setSpamStateTransaction(wallet.id, txId, SpamTransactionState.NOT_SPAM)
                eventsRepository.removeSpam(wallet.accountId, wallet.network, txId)
                true
            }
            withContext(Dispatchers.Main) {
                callback(isApplied)
            }
        }
    }

    private suspend fun markAsSpam(wallet: WalletEntity, txId: String): Boolean {
        if (!eventsRepository.markAsSpam(wallet.accountId, wallet.network, txId)) {
            return false
        }
        settingsRepository.setSpamStateTransaction(wallet.id, txId, SpamTransactionState.SPAM)
        return true
    }

}