package com.tonapps.tonkeeper.ui.screen.backup.main

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.Wallet
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.backup.main.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.backup.BackupRepository
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackupViewModel(
    app: Application,
    private val accountRepository: AccountRepository,
    private val backupRepository: BackupRepository,
    private val passcodeManager: PasscodeManager,
    private val settingsRepository: SettingsRepository,
    private val mcAccountRepository: McAccountRepository,
) : BaseWalletVM(app) {

    val walletFlow: StateFlow<Wallet?> = combine(
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

    val uiItemsFlow = combine(
        walletFlow.filterNotNull(),
        backupRepository.stream,
    ) { kind, backups ->
        val filtered = backups.filter { it.walletId == kind.id }
        val backupsCount = filtered.size

        val items = mutableListOf<Item>()
        items.add(Item.Header)
        items.add(Item.Space)

        for ((index, backup) in filtered.withIndex()) {
            val position = ListCell.getPosition(backupsCount, index)
            items.add(Item.Backup(position, backup, settingsRepository.getLocale()))
        }
        if (backupsCount > 0) {
            items.add(Item.Space)
            items.add(Item.RecoveryPhrase)
        } else {
            items.add(Item.ManualBackup)
        }
        items.toList()
    }.flowOn(Dispatchers.IO)

    fun getRecoveryPhrase(
        context: Context,
        callback: (Array<String>, Throwable?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val words = when (val kind = walletFlow.value) {
                    is Wallet.Legacy -> {
                        if (!passcodeManager.confirmation(context, getString(Localization.app_name))) {
                            return@launch
                        }
                        val mnemonic = accountRepository.getMnemonic(kind.id) ?: emptyArray()
                        if (mnemonic.isEmpty()) {
                            val hasPrivateKey = accountRepository.getPrivateKey(kind.id) != null
                            if (hasPrivateKey) {
                                throw IllegalStateException("No mnemonic but has private key")
                            } else {
                                throw IllegalStateException("No mnemonic and no private key")
                            }
                        }
                        mnemonic
                    }
                    is Wallet.Multichain -> {
                        passcodeManager.unlockMultichainVault(context) { coder ->
                            mcAccountRepository.getMnemonic(kind.id, coder)
                                ?: throw IllegalStateException("No mnemonic for multichain wallet")
                        }
                    }
                    null -> {
                        throw IllegalStateException("Wallet not found")
                    }
                }
                withContext(Dispatchers.Main) {
                    callback(words, null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    callback(emptyArray(), e)
                }
            }
        }
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
