package com.tonapps.deposit.multicoin.screens.confirm.base

import com.tonapps.async.Async
import com.tonapps.blockchain.model.ConfirmContext
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.api.models.ChainError
import com.tonapps.chainkit.core.chain.api.models.NodeError
import com.tonapps.chainkit.core.chain.api.models.SignError
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.MnemonicWallet
import com.tonapps.deposit.multicoin.screens.confirm.ConfirmResultListener
import com.tonapps.mvi.MviRelay
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.graph.GraphViewModel
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.passcode.PasscodeReason
import com.tonapps.wallet.data.passcode.WalletUnlocker
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicBoolean

sealed interface ConfirmationError {
    data object Unknown : ConfirmationError

    data object OutcomeUnknown : ConfirmationError

    data object PartiallySent : ConfirmationError
    data object InsufficientBalance : ConfirmationError
    data class InsufficientNetworkFee(val symbol: String, val required: BigDecimal? = null) : ConfirmationError

    data object Expired : ConfirmationError

    data object InvalidAddress : ConfirmationError
    data object UnsupportedTransaction : ConfirmationError
    data object LedgerUnsupported : ConfirmationError
    data object UnsupportedAsset : ConfirmationError
    data object MissingField : ConfirmationError
    data object DustAmount : ConfirmationError
    data object VerificationFailed : ConfirmationError
    data object SignInternalError : ConfirmationError

    data object Unauthorized : ConfirmationError
    data object NoAvailableNodes : ConfirmationError
    data object NetworkError : ConfirmationError

    data object BadResponse : ConfirmationError
    data object BitcoinError : ConfirmationError
    data object MemPoolConflict : ConfirmationError
    data object UtxoError : ConfirmationError
    data object InternalError : ConfirmationError

    data class NodeRejected(val message: String) : ConfirmationError

    // The node says the nonce this one was signed with is already used, and the lookup did not find it:
    // most likely another transaction of the account, but maybe this one behind a lagging node. It is
    // not sent again, nor signed again here: the user checks the history before sending again.
    data object NonceTaken : ConfirmationError

    data class RelayerRejected(val message: String) : ConfirmationError

    data object DepositInFlight : ConfirmationError

    data class DepositBelowMinimum(val minUsd: BigDecimal?) : ConfirmationError

    data object WalletScreened : ConfirmationError

    data object SignatureDisabled : ConfirmationError
}

// What was signed is not sent again: it may already be out.
internal val ConfirmationError?.endsSend: Boolean
    get() = this == ConfirmationError.OutcomeUnknown || this == ConfirmationError.NonceTaken

internal val ConfirmationError?.isShortage: Boolean
    get() = this == ConfirmationError.InsufficientBalance || this is ConfirmationError.InsufficientNetworkFee

val ConfirmationError.isRetryable: Boolean
    get() = this != ConfirmationError.OutcomeUnknown &&
        this != ConfirmationError.NonceTaken &&
        this != ConfirmationError.PartiallySent &&
        this != ConfirmationError.WalletScreened &&
        this != ConfirmationError.LedgerUnsupported

/**
 * A user-state failure the user can fix (balance, fee, dust, screening), reported as `stage=validation`
 * whichever operation found it: before the node, or as the node's answer to a broadcast.
 */
val ConfirmationError.isValidation: Boolean
    get() = when (this) {
        ConfirmationError.InsufficientBalance,
        is ConfirmationError.InsufficientNetworkFee,
        ConfirmationError.DustAmount,
        is ConfirmationError.DepositBelowMinimum,
        ConfirmationError.WalletScreened -> true

        else -> false
    }

val ConfirmationError.analyticsKey: String
    get() = when (this) {
        ConfirmationError.Unknown -> "unknown"
        ConfirmationError.OutcomeUnknown -> "outcome_unknown"
        ConfirmationError.InsufficientBalance -> "insufficient_balance"
        is ConfirmationError.InsufficientNetworkFee -> "insufficient_network_fee"
        ConfirmationError.Expired -> "expired"
        ConfirmationError.InvalidAddress -> "invalid_address"
        ConfirmationError.UnsupportedTransaction,
        ConfirmationError.LedgerUnsupported -> "unsupported_transaction"
        ConfirmationError.UnsupportedAsset -> "unsupported_asset"
        ConfirmationError.MissingField -> "missing_field"
        ConfirmationError.DustAmount -> "dust_amount"
        ConfirmationError.VerificationFailed -> "verification_failed"
        ConfirmationError.SignInternalError -> "sign_internal_error"
        ConfirmationError.Unauthorized -> "unauthorized"
        ConfirmationError.NoAvailableNodes -> "no_available_nodes"
        ConfirmationError.NetworkError -> "network_error"
        ConfirmationError.BadResponse -> "bad_response"
        ConfirmationError.BitcoinError -> "bitcoin_error"
        ConfirmationError.MemPoolConflict -> "mempool_conflict"
        ConfirmationError.UtxoError -> "utxo_error"
        ConfirmationError.InternalError -> "internal_error"
        is ConfirmationError.NodeRejected -> "node_rejected"
        ConfirmationError.NonceTaken -> "nonce_taken"
        is ConfirmationError.RelayerRejected -> "relayer_rejected"
        ConfirmationError.DepositInFlight -> "deposit_in_flight"
        is ConfirmationError.DepositBelowMinimum -> "below_minimum"
        ConfirmationError.WalletScreened -> "wallet_screened"
        ConfirmationError.PartiallySent -> "partially_sent"
        ConfirmationError.SignatureDisabled -> "signature_disabled"
    }

interface ConfirmationErrorCause {
    val confirmationError: ConfirmationError
}

enum class SendingState {
    None, Loading, Done
}

sealed interface ConfirmEvent {
    object Done : ConfirmEvent
}

internal const val SUCCESS_HOLD_MS = 1_500L

/**
 * What every multichain confirm feature shares: the request it confirms, the send state, the error
 * its action slot shows, the event that closes the screen and the answer to the dapp that asked for
 * it. [BaseTransactionConfirmFeature] binds the request to a transaction;
 * [com.tonapps.deposit.multicoin.screens.confirm.base.operation.BaseOperationConfirmFeature] signs the request's operation as it is.
 */
abstract class BaseConfirmFeature<State : MviViewState, Action : MviAction>(
    protected val request: ConfirmRequest,
    protected val accountRepo: McAccountRepository,
    protected val walletUnlocker: WalletUnlocker,
    private val signatureGate: SignatureGate,
    private val confirmResults: ConfirmResultListener? = null,
) : GraphViewModel<State, Action>() {

    init {
        require(request.context !is ConfirmContext.Dapp || confirmResults != null) { "A dapp request needs a listener to answer it" }
    }

    private val relay = MviRelay<ConfirmEvent>()
    val events = relay.events

    abstract val confirmationError: StateFlow<ConfirmationError?>

    abstract val sendingState: StateFlow<SendingState>

    protected abstract val isCommitted: Boolean

    private val answered = AtomicBoolean(false)

    @Volatile
    private var closedAsGone = false

    // Set once the passcode is passed, or the wallet's 2FA refused the send: from then on a request closed
    // without an approval failed, the user did not reject it. Not answered at the failure itself, so a retry
    // can still approve.
    @Volatile
    private var userConfirmed = false

    // One screen confirms sends, swaps, perps and dapp requests: the PIN screen names the one at hand.
    private val passcodeReason: PasscodeReason
        get() = if (request.context is ConfirmContext.Dapp) {
            PasscodeReason.DAPP_REQUEST
        } else {
            when (request.type) {
                is ConfirmType.Transfer -> PasscodeReason.CONFIRM_TRANSFER
                is ConfirmType.Swap, is ConfirmType.SwapOperation -> PasscodeReason.CONFIRM_SWAP
                is ConfirmType.PerpetualDeposit, is ConfirmType.PerpetualWithdraw -> PasscodeReason.PERPS
                is ConfirmType.Call -> PasscodeReason.CONFIRM_ACTION
                is ConfirmType.Message -> PasscodeReason.DAPP_REQUEST
                is ConfirmType.BatteryPurchase -> PasscodeReason.TOPUP_BATTERY
            }
        }

    protected fun finish() {
        relay.emit(ConfirmEvent.Done)
    }

    protected fun watchRequestGone() {
        val dapp = request.context as? ConfirmContext.Dapp ?: return
        val listener = confirmResults ?: return
        mainScope.launch {
            listener.gone(dapp).firstOrNull() ?: return@launch
            sendingState.first { it != SendingState.Loading && !isCommitted }
            closeGone()
        }
    }

    private fun closeGone() {
        if (!answered.compareAndSet(false, true)) {
            return
        }
        closedAsGone = true
        finish()
    }

    protected open fun onStoppedAsGone() = Unit

    // Before the passcode: the network would only reject what is signed for a TON wallet whose 2FA turned
    // signing by key off.
    protected suspend fun requireSignatureEnabled(account: Account) {
        if (signatureGate.isSignatureDisabled(account)) {
            // The user did not turn the request down: a dapp hears it failed.
            markUserConfirmed()
            throw SignatureDisabledCause()
        }
    }

    protected suspend fun unlockCryptoWallet(walletId: String): MnemonicWallet {
        return walletUnlocker.unlockWallet(walletId, passcodeReason).also { markUserConfirmed() }
    }

    // For keys a subclass gets behind the passcode without [unlockCryptoWallet].
    protected suspend fun markUserConfirmed() {
        userConfirmed = true
        stopIfGone()
    }

    protected suspend fun stopIfGone() {
        val dapp = request.context as? ConfirmContext.Dapp ?: return
        val listener = confirmResults ?: return
        if (isCommitted) {
            return
        }
        if (listener.isGone(dapp)) {
            closeGone()
            if (closedAsGone) {
                onStoppedAsGone()
            }
            awaitCancellation()
        }
    }

    override fun onCleared() {
        answerUnapproved()
        val dapp = request.context as? ConfirmContext.Dapp
        val listener = confirmResults
        if (closedAsGone && dapp != null && listener != null) {
            Async.ioScope().launch(Async.Io) {
                listener.closedAsGone(dapp)
            }
        }
        super.onCleared()
    }

    /** Closes a dapp request without signing it: rejected, or failed once the user confirmed it. */
    fun reject() {
        if (request.context !is ConfirmContext.Dapp) {
            return
        }
        answerUnapproved()
        finish()
    }

    // [result] is what the dapp asked for: the signature, or the hash the transaction was sent under.
    protected suspend fun approveDappRequest(result: String) {
        val dapp = request.context as? ConfirmContext.Dapp ?: return
        val listener = confirmResults ?: return
        if (!answered.compareAndSet(false, true)) {
            return
        }
        // Not cancelled with the screen: what was signed or sent is answered whatever happens to the sheet.
        withContext(Async.Io + NonCancellable) {
            listener.approved(dapp, result)
        }
    }

    private fun answerUnapproved() {
        val dapp = request.context as? ConfirmContext.Dapp ?: return
        val listener = confirmResults ?: return
        if (!answered.compareAndSet(false, true)) {
            return
        }
        val failed = userConfirmed
        // Undispatched: the provider is answered before the sheet's destruction reaches its handler,
        // which would otherwise answer the request as rejected first.
        Async.ioScope().launch(start = CoroutineStart.UNDISPATCHED) {
            if (failed) {
                listener.failed(dapp)
            } else {
                listener.rejected(dapp)
            }
        }
    }

    protected fun parseSignError(error: SignError): ConfirmationError = when (error) {
        is SignError.DustAmount -> ConfirmationError.DustAmount
        is SignError.InsufficientInputs -> ConfirmationError.InsufficientBalance
        is SignError.InternalError -> ConfirmationError.SignInternalError
        is SignError.InvalidAddress -> ConfirmationError.InvalidAddress
        is SignError.MissingField -> ConfirmationError.MissingField
        is SignError.Unknown -> ConfirmationError.Unknown
        is SignError.UnsupportedAsset -> ConfirmationError.UnsupportedAsset
        is SignError.UnsupportedTransaction -> ConfirmationError.UnsupportedTransaction
        is SignError.VerificationFailed -> ConfirmationError.VerificationFailed
    }

    protected fun parseNodeError(error: NodeError): ConfirmationError = when (error) {
        is NodeError.InsufficientFunds -> ConfirmationError.InsufficientBalance
        is NodeError.NetworkWrap -> ConfirmationError.NetworkError
        is NodeError.NoAvailableNodes -> ConfirmationError.NoAvailableNodes
        is NodeError.Unauthorized -> ConfirmationError.Unauthorized
        is NodeError.Rejected -> ConfirmationError.NodeRejected(error.message)
        is NodeError.NonceTaken -> ConfirmationError.NonceTaken
        is NodeError.Unknown -> ConfirmationError.Unknown
    }

    protected fun parseChainError(error: ChainError): ConfirmationError = when (error) {
        is ChainError.BadResponse -> ConfirmationError.BadResponse
        is ChainError.BitcoinDustError -> ConfirmationError.DustAmount
        is ChainError.BitcoinError -> ConfirmationError.BitcoinError
        is ChainError.BitcoinInsufficientInputs -> ConfirmationError.InsufficientBalance
        is ChainError.BitcoinMemPoolConflict -> ConfirmationError.MemPoolConflict
        is ChainError.InsufficientFunds -> ConfirmationError.InsufficientBalance
        is ChainError.InternalError -> ConfirmationError.InternalError
        is ChainError.Unknown -> ConfirmationError.Unknown
        is ChainError.UtxoError -> ConfirmationError.UtxoError
    }
}
