package com.tonapps.onboading.screens.backup

import android.content.Context
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.backup.BackupRepository
import com.tonapps.wallet.data.backup.entities.BackupEntity
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.passcode.PasscodeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackupFeature(
    private val accountRepository: AccountRepository,
    private val mcAccountRepository: McAccountRepository,
    private val passcodeManager: PasscodeManager,
    private val backupRepository: BackupRepository,
) : AsyncViewModel() {

    val words: StateFlow<List<String>> field = MutableStateFlow(emptyList())
    val isLoading: StateFlow<Boolean> field = MutableStateFlow(false)

    fun loadWords(context: Context, onSuccess: () -> Unit) {
        if (isLoading.value) return
        bgScope.launch {
            isLoading.tryEmit(true)
            try {
                val walletId = accountRepository.getSelectedWalletId() ?: return@launch
                val mnemonic = passcodeManager.unlockMultichainVault(context) { coder ->
                    mcAccountRepository.getMnemonic(walletId, coder)
                        ?: throw IllegalStateException("No mnemonic for wallet")
                }
                words.tryEmit(mnemonic.toList())
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (_: Throwable) {
                // Passcode cancelled or unlock failed — stay on intro.
            } finally {
                isLoading.tryEmit(false)
            }
        }
    }

    fun saveBackup(onSuccess: () -> Unit) {
        bgScope.launch {
            val walletId = accountRepository.getSelectedWalletId() ?: return@launch
            backupRepository.addBackup(walletId, BackupEntity.Source.LOCAL)
            withContext(Dispatchers.Main) { onSuccess() }
        }
    }
}
