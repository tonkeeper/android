package com.tonapps.migration.screens.prepare

import com.tonapps.log.L
import com.tonapps.migration.analytics.MigrationAnalytics
import com.tonapps.migration.data.MigratableWallet
import com.tonapps.migration.data.MigrationRepository
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.mvi.MviRelay
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.backup.BackupRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface MigrationPrepareEvent {
    data object NeedBackup : MigrationPrepareEvent
    data class Ready(val walletId: String) : MigrationPrepareEvent
}

class MigrationPrepareFeature(
    private val migrationRepository: MigrationRepository,
    private val settingsRepository: SettingsRepository,
    private val accountRepository: AccountRepository,
    private val backupRepository: BackupRepository,
) : AsyncViewModel() {

    val wallets: StateFlow<List<MigratableWallet>> field = MutableStateFlow(emptyList())
    val selectedWalletId: StateFlow<String?> field = MutableStateFlow(null)
    val isLoading: StateFlow<Boolean> field = MutableStateFlow(true)
    val isFailed: StateFlow<Boolean> field = MutableStateFlow(false)
    val isPreparing: StateFlow<Boolean> field = MutableStateFlow(false)

    private val relay = MviRelay<MigrationPrepareEvent>()
    val events = relay.events

    val currencyCode: StateFlow<String> = settingsRepository.currencyFlow
        .map { it.code }
        .cacheState(initialValue = settingsRepository.currency.code)

    init {
        load()
    }

    fun retry() {
        if (isLoading.value) {
            return
        }
        load()
    }

    private fun load() {
        mainScope.launch {
            isLoading.tryEmit(true)
            isFailed.tryEmit(false)
            try {
                val loaded = try {
                    withContext(bgDispatcher) {
                        migrationRepository.loadWallets()
                    }
                } catch (e: Throwable) {
                    verifyError(e)
                    L.e(e, "Failed to load migratable wallets")
                    null
                }
                if (loaded != null) {
                    wallets.tryEmit(loaded)
                    if (selectedWalletId.value == null) {
                        selectedWalletId.tryEmit(loaded.firstOrNull()?.wallet?.id)
                    }
                    if (loaded.isNotEmpty()) {
                        MigrationAnalytics.walletSelectionView()
                    }
                } else {
                    isFailed.tryEmit(true)
                    selectedWalletId.tryEmit(null)
                }
            } finally {
                isLoading.tryEmit(false)
            }
        }
    }

    fun selectWallet(walletId: String) {
        selectedWalletId.tryEmit(walletId)
    }

    fun continueClicked() {
        MigrationAnalytics.walletSelectionClick()
        continueToConfirm()
    }

    fun continueToConfirm() {
        val walletId = selectedWalletId.value ?: return
        val wallet = wallets.value.find { it.wallet.id == walletId } ?: return
        if (!isPreparing.compareAndSet(expect = false, update = true)) return
        mainScope.launch {
            try {
                val outcome = withContext(bgDispatcher) {
                    val destinationId = accountRepository.getSelectedWalletId()
                        ?: return@withContext ContinueOutcome.Failed
                    val hasBackup = backupRepository.stream.first().any { it.walletId == destinationId }
                    if (!hasBackup) {
                        return@withContext ContinueOutcome.NeedBackup
                    }
                    val prepared = migrationRepository.prepare(wallet)
                        ?: return@withContext ContinueOutcome.Failed
                    ContinueOutcome.Ready(prepared.wallet.wallet.id)
                }
                when (outcome) {
                    ContinueOutcome.NeedBackup -> relay.emit(MigrationPrepareEvent.NeedBackup)
                    is ContinueOutcome.Ready -> relay.emit(MigrationPrepareEvent.Ready(outcome.walletId))
                    ContinueOutcome.Failed -> Unit
                }
            } finally {
                isPreparing.tryEmit(false)
            }
        }
    }

    private sealed interface ContinueOutcome {
        data object NeedBackup : ContinueOutcome
        data object Failed : ContinueOutcome
        data class Ready(val walletId: String) : ContinueOutcome
    }
}
