package com.tonapps.deposit.multicoin.screens.confirm

import android.content.Context
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.async.Async
import com.tonapps.blockchain.model.ConfirmContext
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.bus.generated.Events.RedOperations.RedOperationsOperation
import com.tonapps.chainkit.core.chain.api.models.ChainError
import com.tonapps.chainkit.core.chain.api.models.NodeError
import com.tonapps.chainkit.core.chain.api.models.NodeRes
import com.tonapps.chainkit.core.chain.api.models.SignError
import com.tonapps.chainkit.core.chain.model.MessageType
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.CryptoWallet
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.transaction.GasReservePolicy
import com.tonapps.chainkit.transaction.GasReserveResult
import com.tonapps.core.flags.InAppReviewManager
import com.tonapps.deposit.multicoin.screens.confirm.engine.TxFee
import com.tonapps.deposit.multicoin.screens.confirm.engine.TxFeeState
import com.tonapps.deposit.multicoin.screens.confirm.engine.TxFeeLogic
import com.tonapps.deposit.multicoin.screens.confirm.engine.TxFeeBuilder
import com.tonapps.deposit.multicoin.screens.confirm.engine.GaslessSender
import com.tonapps.deposit.multicoin.screens.confirm.engine.FeeAccount
import com.tonapps.deposit.multicoin.screens.confirm.engine.RelayerOutcomeUnknownException
import com.tonapps.extensions.toHex
import com.tonapps.ledger.ble.extension.hexAsUtf8
import com.tonapps.log.L
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.mvi.MviRelay
import com.tonapps.wallet.ApprovalTransaction
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.data.dapps.wc.WcRepository
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.exchange.SwapQuote
import com.tonapps.wallet.data.multichain.tx.PostTransactionRefreshSchedule
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.settings.entities.PreferredFeeMethod
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ConfirmationError {
    data object Unknown : ConfirmationError

    data object OutcomeUnknown : ConfirmationError
    data object InsufficientBalance : ConfirmationError

    data object InvalidAddress : ConfirmationError
    data object UnsupportedTransaction : ConfirmationError
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
}

data class Message(
    val data: String,
    val type: MessageType,
    val account: Account,
)

sealed interface Signing {
    class Tx(val value: Transaction) : Signing
    class Msg(val value: Message) : Signing
}

class PendingTransaction(
    val request: ConfirmRequest,
    val signing: Signing,

    val wallet: McWalletEntity,
    val account: AccountWithDetails,

    val fee: TxFeeState = TxFeeState.Empty,

    val nonce: BigInteger?,

    val destination: AccountWithDetails? = null,
    val approval: ApprovalTransaction? = null,
    val quote: SwapQuote? = null,
    val rateLabel: String? = null,
)

enum class SendingState {
    None, Loading, Done
}

class SignedTx(
    val tx: Transaction,
    val data: ByteArray,
    val service: String? = null,
    val routeId: String? = null,
    val serviceTxId: String? = null,
    val sellBaseAmount: BigInteger? = null,
)

class SendData(
    val tx: PendingTransaction,
    val signed: List<SignedTx>,
    val hash: String? = null,
)

sealed interface ConfirmEvent {
    object Done : ConfirmEvent
}

/**
 * Shared state, transaction preparation helpers and signing/sending logic for the multichain confirm
 * screens. [TxConfirmFeature] handles transfers, calls and messages; [SwapConfirmFeature] adds the
 * swap-specific quote/slippage flow on top.
 */
abstract class BaseConfirmFeature(
    protected val request: ConfirmRequest,
    protected val provider: ChainKitProvider,
    protected val accountRepo: McAccountRepository,
    private val postTransactionRefreshSchedule: PostTransactionRefreshSchedule,
    protected val passcodeManager: PasscodeManager,
    protected val wcRepository: WcRepository,
    protected val feeBuilder: TxFeeBuilder,
    protected val gaslessSender: GaslessSender,
    protected val settingsRepository: SettingsRepository,
) : AsyncViewModel() {

    private val relay = MviRelay<ConfirmEvent>()
    val events = relay.events

    private val analytics = ConfirmAnalytics(request)

    protected val refreshToken = MutableStateFlow<String?>(null)

    // Selection deliberately outside PendingTransaction: pendingTx is rebuilt on every refresh
    // (refill round-trip, retry, swap tick), which would otherwise wipe the user's pick.
    val pickedFeeId = MutableStateFlow<String?>(null)

    val confirmationError = MutableStateFlow<ConfirmationError?>(null)

    protected val sendData = MutableStateFlow<SendData?>(null)

    val pendingLoader = MutableStateFlow(false)
    val sendingState = MutableStateFlow(SendingState.None) // TODO rewrite

    protected val txLocked: Boolean
        get() = sendData.value != null || sendingState.value != SendingState.None

    val txCommitted: StateFlow<Boolean> = combine(sendData, sendingState) { data, sending ->
        data != null || sending != SendingState.None
    }.cacheState(initialValue = false)

    abstract val pendingTx: Flow<PendingTransaction?>

    // Whether future-swap approvals should be requested for an unlimited allowance; only swaps expose
    // a toggle for this, so the base default keeps approvals bounded.
    protected open val unlimitedApproveEnabled: Boolean get() = false

    fun selectFee(fee: TxFee) {
        if (txLocked) {
            return
        }
        pickedFeeId.value = fee.id
        val method = when {
            fee.account is FeeAccount.Keeper -> PreferredFeeMethod.BATTERY
            fee.isNative -> PreferredFeeMethod.TON
            fee.viaRelayer -> PreferredFeeMethod.GASLESS
            else -> PreferredFeeMethod.TON
        }
        settingsRepository.setPreferredFeeMethod(request.data.walletId, method)
    }

    fun selectedFee(tx: PendingTransaction): TxFee? {
        return tx.fee.resolve(pickedFeeId.value)
    }

    fun refreshFees() {
        if (txLocked) {
            return
        }
        refreshToken.tryEmit(System.nanoTime().toString())
    }

    // Reports preparation as a RED operation; the failure path belongs to [onPrepareFailed], which the
    // features hand to `mapLatestCatching`.
    protected suspend fun prepareTracked(block: suspend () -> PendingTransaction?): PendingTransaction? {
        return analytics.redOperation(RedOperationsOperation.Emulate) {
            val pending = block()
            val error = confirmationError.value
            if (error == null) {
                success()
            } else {
                failure(error)
            }
            pending
        }
    }

    protected suspend fun onPrepareFailed(e: Throwable) {
        verifyError(e)
        L.e(e)
        confirmationError.tryEmit(ConfirmationError.Unknown)
    }

    protected suspend fun prepareCall(msg: ConfirmType.Call): PendingTransaction {
        val walletId = request.data.walletId

        val wallet = accountRepo.getWallet(walletId)
            ?: throw IllegalStateException("Can't find wallet by id")

        val account = accountRepo.findAccount(wallet.id, request.data.assetId)
            ?: throw IllegalStateException("Can't find account")

        val energy = when (val asset = account.asset.value) {
            is Asset.Coin -> asset
            is Asset.Token -> asset.chain.toAsset()
        }

        val energyAsset = runCatching { accountRepo.findAccount(walletId, energy.id) }
            .getOrNull()

        val transaction = Transaction.Call(
            account = account.value,
            contract = Address.force(msg.contract, account.asset.value.chain),
            amount = msg.amount,
            data = msg.data,
            energy = energy,
        )


        val (feeResult, nonceResult) = withContext(Async.Io) {
            val feeTask = async { provider.estimateFee(transaction) }
            val nonceTask = async { provider.getNonce(account.value) }
            feeTask.await() to nonceTask.await()
        }

        val feeError = feeResult.error?.let(::parseChainError)
        feeResult.error?.let { L.e(it) }
        val nonceError = nonceResult.error?.let(::parseNodeError)
        nonceResult.error?.let { L.e(it) }

        val error = feeError ?: nonceError
        confirmationError.tryEmit(error)
        L.d("Fee: ${feeResult.value}")
        L.d("Nonce: ${nonceResult.value}")

        return PendingTransaction(
            request = request,
            wallet = wallet,
            account = account,
            fee = TxFeeState(
                energy = energyAsset,
                estimated = if (feeError == null) {
                    feeResult.unwrap()
                } else {
                    null
                },
                error = error,
            ),
            nonce = if (nonceError == null) {
                nonceResult.unwrap()
            } else {
                null
            },
            signing = Signing.Tx(transaction),
        )
    }

    protected suspend fun prepareMessage(msg: ConfirmType.Message): PendingTransaction {
        val walletId = request.data.walletId

        val wallet = accountRepo.getWallet(walletId)
            ?: throw IllegalStateException("Can't find wallet by id")

        val account = accountRepo.findAccount(wallet.id, request.data.assetId)
            ?: throw IllegalStateException("Can't find account")

        val message = Message(
            account = account.value,
            type = when (msg.type) {
                ConfirmType.Message.Type.Default -> MessageType.Default
                ConfirmType.Message.Type.Legacy -> MessageType.Legacy
                ConfirmType.Message.Type.Personal -> MessageType.Legacy
                ConfirmType.Message.Type.Typed -> MessageType.TypedMessage
                ConfirmType.Message.Type.Transaction -> MessageType.Transaction
            },
            data = msg.data.hexAsUtf8() ?: msg.data
        )

        return PendingTransaction(
            request = request,
            wallet = wallet,
            account = account,
            nonce = null,
            signing = Signing.Msg(message),
        )
    }

    protected suspend fun prepareTransaction(transfer: ConfirmType.Transfer): PendingTransaction {
        val walletId = request.data.walletId
        val wallet = accountRepo.getWallet(walletId) ?: throw IllegalStateException("Can't find wallet by id")
        val account = accountRepo.findAccount(walletId, request.data.assetId) ?: throw IllegalStateException("Can't find account")

        val energy = when (val asset = account.asset.value) {
            is Asset.Coin -> asset
            is Asset.Token -> asset.chain.toAsset()
        }

        val energyAccount = accountRepo.findAccount(walletId, energy.id) ?: throw IllegalStateException("Can't find energy account by id")

        val transaction = Transaction.Transfer(
            account = account.value,
            amount = transfer.amount,
            to = Address.force(transfer.to, account.value.asset.chain),
            isMax = transfer.isMax ?: false,
            meta = transfer.meta,
            energy = energy,
        )

        val feeResult = provider.estimateFee(transaction)
        val feeError = feeResult.error
            ?.also { L.e(it) }
            ?.let(::parseChainError)

        val nonceResult = provider.getNonce(account.value)
        val nonceError = nonceResult.error
            ?.also { L.e(it) }
            ?.let(::parseNodeError)

        val reservationResult = reserveNativeGas(
            account = transaction.account,
            energy = energyAccount.value,
            amount = transaction.amount,
            isMax = transaction.isMax,
            fee = feeResult.value,
            policy = GasReservePolicy.DrainOrError,
        )

        val reservationError = reservationResult.error
            ?.also { L.e(it) }
            ?.let(::parseNodeError)
            ?: reservationResult.value?.error
                ?.let { parseReservationError(it) }

        val adjustedTransaction = reservationResult.getOrNull()
            ?.let {
                val isMax = transaction.isMax || it.isAmountAdjusted
                transaction.copy(amount = it.amount, isMax = isMax)
            }
            ?: transaction

        val chainKitError = feeError ?: nonceError ?: reservationError

        // Dapp requests answer a WalletConnect call and must be broadcast through chainkit so the
        // response can be returned; relayed fee methods are out of scope for them.
        val fee = if (request.context is ConfirmContext.Dapp) {
            TxFeeState(energy = energyAccount, estimated = feeResult.value)
        } else {
            feeBuilder.build(
                account = account,
                energy = energyAccount,
                walletId = walletId,
                amount = adjustedTransaction.amount,
                to = adjustedTransaction.to.display,
                comment = adjustedTransaction.meta,
                estimated = feeResult.value,
            )
        }.copy(error = chainKitError)

        // A relayer pays the chain fee, so the wallet not being able to cover it is not an error:
        // reporting it would block a send the battery (or the TON instant fee) can complete.
        val relayed = fee.resolve(pickedFeeId.value)?.viaRelayer == true
        confirmationError.tryEmit(
            if (relayed) {
                nonceError
            } else {
                chainKitError
            }
        )
        L.d("Fee: ${feeResult.value}")
        L.d("Nonce: ${nonceResult.value}")

        return PendingTransaction(
            request = request,
            wallet = wallet,
            account = account,
            fee = fee,
            nonce = nonceResult.value,
            signing = Signing.Tx(adjustedTransaction),
        )
    }

    protected suspend fun reserveNativeGas(
        account: Account,
        energy: Account,
        amount: BigInteger,
        isMax: Boolean,
        fee: Fee?,
        policy: GasReservePolicy,
    ): NodeRes<GasReserveResult> {
        return provider.calculateGasReserve(account, energy, amount, isMax, fee, policy)
    }

    open fun retry(context: Context) {
        if (confirmationError.value == ConfirmationError.OutcomeUnknown) {
            return
        }

        confirmationError.tryEmit(null)

        val data = sendData.value
        if (data == null) {
            refreshToken.tryEmit(System.nanoTime().toString())
        } else {
            sendTransaction(context, data.tx)
        }
    }

    fun sendTransaction(context: Context, pendingTx: PendingTransaction) {
        if (sendingState.value != SendingState.None) {
            return
        }

        val committed = sendData.value?.tx ?: pendingTx
        when (val signing = committed.signing) {
            is Signing.Msg -> confirmMessage(context, msg = signing, committed)
            is Signing.Tx -> confirmTransaction(context, tx = signing, committed)
        }
    }

    protected fun Flow<PendingTransaction?>.keepLastPrepared(): Flow<PendingTransaction?> {
        return scan(null as PendingTransaction?) { previous, next -> next ?: previous }
    }

    fun reject() {
        val dapp = request.context as? ConfirmContext.Dapp ?: return

        bgScope.launch {
            wcRepository.rejectRequest(dapp.requestId, dapp.topic)
        }

        relay.emit(ConfirmEvent.Done)
    }

    private suspend fun approveDappRequest(pendingTx: PendingTransaction, signedResult: String) {
        val dapp = pendingTx.request.context as? ConfirmContext.Dapp ?: return
        withContext(Async.Io) {
            wcRepository.approveRequest(dapp.requestId, signedResult)
        }
    }

    private fun confirmMessage(context: Context, msg: Signing.Msg, pendingTx: PendingTransaction) {
        sendingState.tryEmit(SendingState.Loading)

        mainScope.launch {
            try {
                analytics.redOperation(analytics.sendOperation) {
                    val cryptoWallet = unlockCryptoWallet(context, pendingTx.wallet.id)
                    val signing = withContext(Async.Io) {
                        provider.signMessage(msg.value.data, msg.value.type, msg.value.account, cryptoWallet)
                    }
                    val signedMessage = signing?.getOrNull()
                    if (signedMessage == null) {
                        signing?.error?.let { L.e(it) }
                        sendingState.tryEmit(SendingState.None)
                        val error = signing?.error?.let(::parseSignError) ?: ConfirmationError.Unknown
                        confirmationError.tryEmit(error)
                        failure(error)
                        return@launch
                    }

                    approveDappRequest(pendingTx, signedMessage)
                    success()
                }
            } catch (e: Throwable) {
                sendingState.tryEmit(SendingState.None)
                verifyError(e)
                L.e(e)
                confirmationError.tryEmit(ConfirmationError.Unknown)
                return@launch
            }

            sendingState.tryEmit(SendingState.Done)
            delay(500L)
            relay.emit(ConfirmEvent.Done)
        }
    }

    private suspend fun RedOperation.signCommitted(
        context: Context,
        tx: Signing.Tx,
        pendingTx: PendingTransaction,
        fee: Fee,
        nonce: BigInteger,
    ): List<SignedTx>? {
        val cryptoWallet = unlockCryptoWallet(context, pendingTx.wallet.id)

        val approval = pendingTx.approval
        val unlimitedToggle = unlimitedApproveEnabled
        val needsApproval = approval != null && !(unlimitedToggle && approval.isUnlimited)

        val approvalSigned = if (needsApproval) {
            val approvalTx = approval.signableTx(unlimitedToggle)
            val signing = withContext(Async.Io) {
                provider.signTransaction(approvalTx, approval.fee, nonce, cryptoWallet)
            }
            val error = signing.error
            if (error != null) {
                reportSignFailure(error, pendingTx)
                return null
            }
            SignedTx(tx = approvalTx, data = signing.unwrap().firstOutput())
        } else {
            null
        }

        val mainNonce = if (approvalSigned == null) {
            nonce
        } else {
            nonce + BigInteger.ONE
        }

        val signing = withContext(Async.Io) {
            provider.signTransaction(tx.value, fee, mainNonce, cryptoWallet)
        }
        val error = signing.error
        if (error != null) {
            reportSignFailure(error, pendingTx)
            return null
        }

        val main = SignedTx(
            tx = tx.value,
            data = signing.unwrap().firstOutput(),
            service = pendingTx.quote?.provider?.id,
            routeId = pendingTx.quote?.routeId,
            serviceTxId = pendingTx.quote?.providerTxId,
            sellBaseAmount = pendingTx.quote?.sourceBaseAmount,
        )

        return listOfNotNull(approvalSigned, main)
    }

    private fun RedOperation.reportSignFailure(signError: SignError, pendingTx: PendingTransaction) {
        L.e(signError)
        sendingState.tryEmit(SendingState.None)
        val error = parseSignError(signError)
        confirmationError.tryEmit(error)
        failure(error)
        analytics.swapFailed(pendingTx, error)
    }

    private suspend fun RedOperation.broadcast(pendingTx: PendingTransaction, committed: SendData): String? {
        var state = committed

        while (state.signed.isNotEmpty()) {
            val item = state.signed.first()
            val result = withContext(Async.Io) {
                provider.sendTransaction(
                    walletId = pendingTx.wallet.id,
                    tx = item.tx,
                    data = item.data,
                    service = item.service,
                    routeId = item.routeId,
                    serviceTxId = item.serviceTxId,
                    sellBaseAmount = item.sellBaseAmount,
                )
            }
            val nodeError = result.error
            if (nodeError != null) {
                L.e(nodeError)
                sendingState.tryEmit(SendingState.None)
                val error = parseNodeError(nodeError)
                confirmationError.tryEmit(error)
                failure(error)
                analytics.swapFailed(pendingTx, error)
                return null
            }
            val hash = result.unwrap()
            L.d("Tx hash $hash")
            state = SendData(pendingTx, state.signed.drop(1), hash)
            sendData.value = state
            notifyTransactionSent(pendingTx)
        }

        return state.hash
    }

    private fun confirmTransaction(context: Context, tx: Signing.Tx, pendingTx: PendingTransaction) {
        val selected = selectedFee(pendingTx)
        if (selected != null && !TxFeeLogic.isSufficient(selected)) {
            confirmationError.tryEmit(ConfirmationError.InsufficientBalance)
            return
        }
        if (selected?.viaRelayer == true) {
            confirmWithRelayer(context, tx, pendingTx, selected)
            return
        }

        val fee = pendingTx.fee.estimated ?: run {
            confirmationError.tryEmit(pendingTx.fee.error ?: ConfirmationError.Unknown)
            return
        }
        val nonce = pendingTx.nonce ?: run {
            confirmationError.tryEmit(pendingTx.fee.error ?: ConfirmationError.Unknown)
            return
        }

        analytics.swapConfirm(pendingTx)

        sendingState.tryEmit(SendingState.Loading)

        mainScope.launch {
            try {
                analytics.redOperation(analytics.sendOperation) {
                    val committed = sendData.value ?: run {
                        val signed = signCommitted(context, tx, pendingTx, fee, nonce) ?: return@launch
                        SendData(pendingTx, signed).also { sendData.value = it }
                    }

                    val signedResult = if (pendingTx.request.action.withSend) {
                        val hash = broadcast(pendingTx, committed) ?: return@launch
                        analytics.transactionSent(tx.value, selected)
                        analytics.swapSuccess(pendingTx)
                        trackSuccessfulSend(pendingTx)
                        hash
                    } else {
                        committed.signed.last().data.toHex()
                    }

                    approveDappRequest(pendingTx, signedResult)
                    success()
                }
            } catch (e: Throwable) {
                sendingState.tryEmit(SendingState.None)
                verifyError(e)
                L.e(e)
                confirmationError.tryEmit(ConfirmationError.Unknown)
                analytics.swapFailed(pendingTx, ConfirmationError.Unknown, e)
                return@launch
            }

            sendingState.tryEmit(SendingState.Done)
            delay(500L)
            relay.emit(ConfirmEvent.Done)
        }
    }

    // A relayer send that fails surfaces the error as-is: never fall back to a chainkit broadcast,
    // the relayer may already have accepted the transaction.
    private fun confirmWithRelayer(
        context: Context,
        tx: Signing.Tx,
        pendingTx: PendingTransaction,
        option: TxFee,
    ) {
        val relayer = pendingTx.fee.relayer ?: run {
            confirmationError.tryEmit(ConfirmationError.Unknown)
            return
        }

        sendingState.tryEmit(SendingState.Loading)

        mainScope.launch {
            try {
                analytics.swapConfirm(pendingTx)
                val cryptoWallet = unlockCryptoWallet(context, pendingTx.wallet.id)
                sendData.value = SendData(pendingTx, emptyList())
                withContext(Async.Io) {
                    relayer.send(cryptoWallet, option)
                }

                analytics.transactionSent(tx.value, option)
                analytics.swapSuccess(pendingTx)
                notifyTransactionSent(pendingTx)
                trackSuccessfulSend(pendingTx)
                sendingState.tryEmit(SendingState.Done)
                delay(500L)
                relay.emit(ConfirmEvent.Done)
            } catch (e: Throwable) {
                sendingState.tryEmit(SendingState.None)
                verifyError(e)
                L.e(e)
                if (e is RelayerOutcomeUnknownException) {
                    notifyTransactionSent(pendingTx)
                    confirmationError.tryEmit(ConfirmationError.OutcomeUnknown)
                } else {
                    confirmationError.tryEmit(ConfirmationError.Unknown)
                }
            }
        }
    }

    private fun trackSuccessfulSend(pendingTx: PendingTransaction) {
        if (pendingTx.request.type !is ConfirmType.Transfer) {
            return
        }
        if (pendingTx.request.context is ConfirmContext.Dapp) {
            return
        }
        InAppReviewManager.onTransactionSent()
    }

    private fun notifyTransactionSent(pendingTx: PendingTransaction) {
        val walletId = pendingTx.wallet.id
        val sourceChain = pendingTx.account.data.chain
        postTransactionRefreshSchedule.notifyTransactionSent(walletId, sourceChain)
        val destinationChain = pendingTx.destination?.data?.chain ?: return
        if (destinationChain != sourceChain) {
            postTransactionRefreshSchedule.notifyTransactionSent(walletId, destinationChain)
        }
    }

    private suspend fun unlockCryptoWallet(context: Context, walletId: String): CryptoWallet = withContext(Async.Io) {
        passcodeManager.unlockMultichainVault(context) { coder ->
            accountRepo.getCryptoWallet(walletId, coder)
                ?: throw IllegalStateException("CryptoWallet not found for $walletId")
        }
    }

    protected fun parseReservationError(error: GasReserveResult.Error): ConfirmationError {
        return when (error) {
            GasReserveResult.Error.InsufficientBalance -> ConfirmationError.InsufficientBalance
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
        is NodeError.NetworkWrap -> ConfirmationError.NetworkError
        is NodeError.NoAvailableNodes -> ConfirmationError.NoAvailableNodes
        is NodeError.Unauthorized -> ConfirmationError.Unauthorized
        is NodeError.Unknown -> ConfirmationError.Unknown
    }

    protected fun parseChainError(error: ChainError): ConfirmationError = when (error) {
        is ChainError.BadResponse -> ConfirmationError.BadResponse
        is ChainError.BitcoinDustError -> ConfirmationError.DustAmount
        is ChainError.BitcoinError -> ConfirmationError.BitcoinError
        is ChainError.BitcoinInsufficientInputs -> ConfirmationError.InsufficientBalance
        is ChainError.BitcoinMemPoolConflict -> ConfirmationError.MemPoolConflict
        is ChainError.InternalError -> ConfirmationError.InternalError
        is ChainError.Unknown -> ConfirmationError.Unknown
        is ChainError.UtxoError -> ConfirmationError.UtxoError
    }
}
