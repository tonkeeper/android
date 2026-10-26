package com.tonapps.deposit.multicoin.screens.confirm.perpetual

import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.model.account.CryptoWallet
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.BaseOperationConfirmFeature
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationAuthorizer
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationTracker
import com.tonapps.log.L
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.tx.PostTransactionRefreshSchedule
import com.tonapps.wallet.data.passcode.PasscodeManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/** The perps side of a deposit's send: the funding journal and the deposit's registration. */
interface PerpetualDepositTracker : OperationTracker

/** The perps side of a deposit [com.tonapps.blockchain.model.CommonTransactionData.preview]: the deposit, once the wallet is unlocked. */
interface PerpetualDepositAuthorizer {

    /** Signs the preview [request] carries and returns the deposit quoted with those signatures. */
    suspend fun authorize(request: ConfirmRequest, wallet: CryptoWallet): ConfirmRequest
}

/**
 * Confirm feature for [ConfirmType.PerpetualDeposit]: signs the operation the deposit was quoted
 * with, as it is, until the quote expires. A preview is priced as its own operation — signatures,
 * nothing to broadcast — and on the swipe they make the deposit that is signed and sent instead.
 */
class PerpetualDepositConfirmFeature(
    request: ConfirmRequest,
    provider: ChainKitProvider,
    accountRepo: McAccountRepository,
    passcodeManager: PasscodeManager,
    postTransactionRefreshSchedule: PostTransactionRefreshSchedule,
    tracker: PerpetualDepositTracker?,
    authorizer: PerpetualDepositAuthorizer,
) : BaseOperationConfirmFeature(
    request = request,
    provider = provider,
    accountRepo = accountRepo,
    passcodeManager = passcodeManager,
    postTransactionRefreshSchedule = postTransactionRefreshSchedule,
    tracker = tracker,
    authorizer = OperationAuthorizer { quoted, wallet ->
        authorizer.authorize(quoted, wallet)
    },
) {

    // A getter: the base prepares the send while it is constructed, before a field here would be set.
    val deposit: ConfirmType.PerpetualDeposit
        get() = request.type as ConfirmType.PerpetualDeposit

    // A preview: [deposit] is the owner's signatures that make the deposit, quoted again once signed.
    val preview: Boolean
        get() = request.data.preview

    val sourceAccount: StateFlow<AccountWithDetails?> = account
        .map { priced -> priced ?: findSourceAccount() }
        .cacheState(initialValue = null)

    private suspend fun findSourceAccount(): AccountWithDetails? {
        return try {
            accountRepo.findAccount(request.data.walletId, request.data.assetId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.e(e)
            null
        }
    }
}
