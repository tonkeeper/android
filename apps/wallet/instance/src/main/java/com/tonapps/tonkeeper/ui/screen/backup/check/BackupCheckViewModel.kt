package com.tonapps.tonkeeper.ui.screen.backup.check

import androidx.lifecycle.ViewModel
import com.tonapps.wallet.data.account.WalletRepository
import com.tonapps.wallet.data.backup.BackupRepository
import com.tonapps.wallet.data.backup.entities.BackupEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take

class BackupCheckViewModel(
    private val walletRepository: WalletRepository,
    private val backupRepository: BackupRepository
): ViewModel() {

    fun saveBackup(backupId: Long) = walletRepository.activeWalletFlow.onEach {
        if (backupId != 0L) {
            backupRepository.updateBackup(backupId)
        } else {
            backupRepository.addBackup(it.id, BackupEntity.Source.LOCAL)
        }
    }.flowOn(Dispatchers.IO).take(1)
}