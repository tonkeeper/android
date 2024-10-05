package com.tonapps.tonkeeper.ui.screen.backup.check

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.backup.BackupRepository
import com.tonapps.wallet.data.backup.entities.BackupEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackupCheckViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val backupRepository: BackupRepository
): BaseWalletVM(app) {

    fun saveBackup(backupId: Long, callback: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            if (backupId != 0L) {
                backupRepository.updateBackup(backupId)
            } else {
                backupRepository.addBackup(wallet.id, BackupEntity.Source.LOCAL)
            }
            withContext(Dispatchers.Main) {
                callback()
            }
        }

    }
}