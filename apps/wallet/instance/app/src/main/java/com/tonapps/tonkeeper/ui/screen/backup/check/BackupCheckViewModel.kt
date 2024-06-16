package com.tonapps.tonkeeper.ui.screen.backup.check

import androidx.lifecycle.ViewModel
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.backup.BackupRepository
import com.tonapps.wallet.data.backup.entities.BackupEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take

class BackupCheckViewModel(
    private val accountRepository: AccountRepository,
    private val backupRepository: BackupRepository
): ViewModel() {

    fun saveBackup(backupId: Long) = accountRepository.selectedWalletFlow.onEach {
        if (backupId != 0L) {
            backupRepository.updateBackup(backupId)
        } else {
            backupRepository.addBackup(it.id, BackupEntity.Source.LOCAL)
        }
    }.flowOn(Dispatchers.IO).take(1)
}