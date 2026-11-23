package com.tonapps.tonkeeper.manager.ledger

import android.content.Context
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.LedgerFlow.LedgerMigrationStartFrom
import com.tonapps.core.flags.WalletFeature
import com.tonapps.extensions.logError
import com.tonapps.tonkeeper.manager.push.PushManager
import com.tonapps.tonkeeper.worker.PushToggleWorker
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.ledger.LedgerLegacyTwins
import com.tonapps.wallet.data.ledger.LedgerWalletInteractor
import com.tonapps.wallet.data.ledger.analytics.LedgerIssueSite
import com.tonapps.wallet.data.ledger.analytics.LedgerNonFatals
import com.tonapps.wallet.data.ledger.analytics.ledgerModel
import com.tonapps.wallet.data.ledger.analytics.toLedgerFlowModel
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class LedgerLegacyTwinReplacer(
    private val context: Context,
    private val accountRepository: AccountRepository,
    private val unifiedAccountRepository: UnifiedAccountRepository,
    private val mcAccountRepository: McAccountRepository,
    private val settingsRepository: SettingsRepository,
    private val ledgerWalletInteractor: LedgerWalletInteractor,
    private val pushManager: PushManager,
) {

    suspend fun replaceMigrated() = withContext(Dispatchers.IO + NonCancellable) {
        if (!WalletFeature.LedgerMultichain.isEnabled) {
            return@withContext
        }
        for (legacy in accountRepository.getWallets().filter(LedgerLegacyTwins::isMigratable)) {
            try {
                val replacementId = ledgerWalletInteractor.multichainTwinId(legacy.id) ?: continue
                if (replace(legacy, replacementId, carryPush = !settingsRepository.getPushWallet(replacementId))) {
                    AnalyticsHelper.Default.events.ledgerFlow.ledgerMigrationSuccess(
                        from = LedgerMigrationStartFrom.Startup,
                        ledgerModel = mcAccountRepository.ledgerModel(replacementId)?.toLedgerFlowModel(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                context.logError(e)
                LedgerNonFatals.recordIssue(e, LedgerIssueSite.Migration)
            }
        }
    }

    suspend fun replace(legacy: WalletEntity, replacementId: String, carryPush: Boolean): Boolean = withContext(Dispatchers.IO + NonCancellable) {
        val hadPush = settingsRepository.getPushWallet(legacy.id)
        if (hadPush && !pushManager.wallet(legacy, PushManager.State.Delete)) {
            return@withContext false
        }
        if (accountRepository.getSelectedWalletId() == legacy.id) {
            accountRepository.setSelectedWallet(replacementId)
        }
        if (hadPush && carryPush) {
            settingsRepository.setPushWallet(replacementId, true)
            PushToggleWorker.runByIds(context, listOf(replacementId), PushManager.State.Enable)
        }
        unifiedAccountRepository.deleteWallet(legacy.id)
        true
    }
}
