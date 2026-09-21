package com.tonapps.tonkeeper.ui.screen.backup.check

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowSource
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowWalletMode
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.tonkeeper.Wallet
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.backup.BackupRepository
import com.tonapps.wallet.data.backup.entities.BackupEntity
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackupCheckViewModel(
    app: Application,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val mcAccountRepository: McAccountRepository,
    private val backupRepository: BackupRepository,
) : BaseWalletVM(app) {

    private val walletFlow: StateFlow<Wallet?> = combine(
        accountRepository.selectedWalletFlow,
        settingsRepository.walletPrefsChangedFlow,
        mcAccountRepository.refreshTrigger,
    ) { selected, _, _ ->
        withContext(Dispatchers.IO) {
            resolveWallet(selected)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    fun saveBackup(backupId: Long, source: WalletFlowSource, callback: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val kind = walletFlow.value
            val walletId = when (kind) {
                is Wallet.Legacy -> kind.entity.id
                is Wallet.Multichain -> kind.entity.id
                null -> throw IllegalStateException("Wallet not found")
            }
            if (backupId != 0L) {
                backupRepository.updateBackup(backupId)
            } else {
                backupRepository.addBackup(walletId, BackupEntity.Source.LOCAL)
            }
            AnalyticsHelper.Default.events.walletFlow.walletBackupSuccess(
                walletMode = walletMode(kind),
                source = source,
            )
            withContext(Dispatchers.Main) {
                callback()
            }
        }
    }

    fun trackBackupError(source: WalletFlowSource) {
        AnalyticsHelper.Default.events.walletFlow.walletBackupError(
            walletMode = walletMode(walletFlow.value ?: return),
            source = source,
            errorType = "backup_check_mismatch",
            errorCode = null,
            errorMessage = null,
        )
    }

    private fun walletMode(wallet: Wallet): WalletFlowWalletMode = when (wallet) {
        is Wallet.Legacy -> WalletFlowWalletMode.Single
        is Wallet.Multichain -> WalletFlowWalletMode.Multi
    }

    private suspend fun resolveWallet(selected: WalletEntity): Wallet? {
        val id = selected.id
        if (id.isBlank()) {
            return null
        }
        accountRepository.getWalletById(id)?.let { return Wallet.Legacy(it) }
        mcAccountRepository.getWallet(id)?.let { return Wallet.Multichain(it) }
        return null
    }
}
