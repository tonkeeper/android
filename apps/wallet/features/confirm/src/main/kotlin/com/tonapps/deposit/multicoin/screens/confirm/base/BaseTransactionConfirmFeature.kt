package com.tonapps.deposit.multicoin.screens.confirm.base

import android.content.Context
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.async.Async
import com.tonapps.blockchain.model.ConfirmContext
import com.tonapps.blockchain.model.ConfirmOperations
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.bus.generated.Events.RedOperations.RedOperationsOperation
import com.tonapps.chainkit.core.chain.api.models.NodeRes
import com.tonapps.chainkit.core.chain.api.models.SignError
import com.tonapps.chainkit.core.chain.api.models.SignRes
import com.tonapps.chainkit.core.chain.model.MessageType
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.CryptoWallet
import com.tonapps.chainkit.core.chain.model.account.PubKey
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.common.getOrElse
import com.tonapps.chainkit.transaction.GasReservePolicy
import com.tonapps.chainkit.transaction.GasReserveResult
import com.tonapps.core.flags.InAppReviewManager
import com.tonapps.deposit.multicoin.screens.confirm.ConfirmAnalytics
import com.tonapps.deposit.multicoin.screens.confirm.ConfirmResultListener
import com.tonapps.deposit.multicoin.screens.confirm.RedOperation
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFee
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeState
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeLogic
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeBuilder
import com.tonapps.deposit.multicoin.screens.confirm.fee.GaslessSender
import com.tonapps.deposit.multicoin.screens.confirm.fee.RelayerOutcomeUnknownException
import com.tonapps.deposit.multicoin.screens.confirm.fee.RelayerQuoteExpiredException
import com.tonapps.deposit.multicoin.screens.confirm.fee.RelayerRejectedException
import com.tonapps.extensions.toHex
import com.tonapps.ledger.ble.extension.hexAsUtf8
import com.tonapps.log.L
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.SigningWallet
import com.tonapps.wallet.data.dapps.wc.WcSolanaAnswers
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.exchange.SwapQuote
import com.tonapps.wallet.data.multichain.tx.PostTransactionRefreshSchedule
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.settings.entities.PreferredFeeMethod
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

data class Message(
    val data: String,
    val type: MessageType,
    val account: Account,
)

sealed interface Signing {
    class Tx(
        val value: Transaction,
        val batchToSign: List<Transaction>? = null,
        // Signed instead of [value] when the wallet pays the fee: [value] is left whole for a relayer.
        val walletPaid: Transaction? = null,
    ) : Signing {

        fun sentWith(fee: TxFee?): Transaction = walletPaid?.takeUnless { fee?.viaRelayer == true } ?: value
    }
    class Msg(val value: Message) : Signing
}

class PendingTransaction(
    val request: ConfirmRequest,
    val signing: Signing,

    val wallet: McWalletEntity,
    val account: AccountWithDetails,

    val fee: TxFeeState = TxFeeState.Empty,

    val nonce: BigInteger?,

    // What leaves [account] besides the fee, when it is more than [signing] says: every message of
    // a split swap route, forward gas included.
    val spentAmount: BigInteger? = null,

    val destination: AccountWithDetails? = null,
    val quote: SwapQuote? = null,
    val rateLabel: String? = null,
    // Transactions of their own sent ahead of the main one, a nonce each (an EVM approve before its
    // swap). [fee] then shows their sum with the main fee, and the main one is signed with [signingFee].
    val prerequisites: List<Prerequisite> = emptyList(),
    val signingFee: Fee? = null,
) {

    fun withFee(fee: TxFeeState) = PendingTransaction(
        request = request,
        signing = signing,
        wallet = wallet,
        account = account,
        fee = fee,
        nonce = nonce,
        spentAmount = spentAmount,
        destination = destination,
        quote = quote,
        rateLabel = rateLabel,
        prerequisites = prerequisites,
        signingFee = signingFee,
    )
}

class Prerequisite(
    val tx: Transaction,
    val fee: Fee,
)

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

/**
 * Shared state, transaction preparation helpers and signing/sending logic for the confirm screens
 * that bind their request to a transaction. [com.tonapps.deposit.multicoin.screens.confirm.TxConfirmFeature] handles transfers, calls and
 * messages; [com.tonapps.deposit.multicoin.screens.confirm.SwapConfirmFeature] adds the swap-specific quote/slippage flow on top.
 */
abstract class BaseTransactionConfirmFeature(
    request: ConfirmRequest,
    protected val provider: ChainKitProvider,
    accountRepo: McAccountRepository,
    private val postTransactionRefreshSchedule: PostTransactionRefreshSchedule,
    passcodeManager: PasscodeManager,
    protected val confirmResults: ConfirmResultListener,
    protected val feeBuilder: TxFeeBuilder,
    protected val gaslessSender: GaslessSender,
    protected val settingsRepository: SettingsRepository,
) : BaseConfirmFeature<MviViewState, MviAction>(request, accountRepo, passcodeManager) {

    final override val confirmationError = MutableStateFlow<ConfirmationError?>(null)

    final override val sendingState = MutableStateFlow(SendingState.None) // TODO rewrite

    private val analytics = ConfirmAnalytics(request)

    private val answered = AtomicBoolean(false)

    // Set once the passcode is passed: from then on a request closed without an approval failed,
    // the user did not reject it. Not answered at the failure itself, so a retry can still approve.
    @Volatile
    private var userConfirmed = false

    protected val refreshToken = MutableStateFlow<String?>(null)

    // Selection deliberately outside PendingTransaction: pendingTx is rebuilt on every refresh
    // (refill round-trip, retry, swap tick), which would otherwise wipe the user's pick.
    val pickedFeeId = MutableStateFlow<String?>(null)

    protected val sendData = MutableStateFlow<SendData?>(null)

    val pendingLoader = MutableStateFlow(false)

    val feeQuoting = MutableStateFlow(false)

    protected val txLocked: Boolean
        get() = sendData.value != null || sendingState.value != SendingState.None

    val txCommitted: StateFlow<Boolean> = combine(sendData, sendingState) { data, sending ->
        data != null || sending != SendingState.None
    }.cacheState(initialValue = false)

    abstract val pendingTx: Flow<PendingTransaction?>

    // Runs after the success hold, right before the screen is told to close, so anything it opens
    // lands once the "sent" state has been seen.
    protected open fun onSendSucceeded() = Unit

    // The keys a transaction is signed with: the wallet's HD wallet, behind the passcode.
    protected open suspend fun unlockSigner(context: Context, pendingTx: PendingTransaction): SigningWallet {
        return SigningWallet.Hd(unlockCryptoWallet(context, pendingTx.wallet.id))
    }

    // The registered wallet a broadcast is reported to the backend for; null sends without a report.
    protected open fun reportedWalletId(pendingTx: PendingTransaction): String? = pendingTx.wallet.id

    fun selectFee(fee: TxFee) {
        if (txLocked) {
            return
        }
        pickedFeeId.value = fee.id
        val method = when (fee.fee.asset) {
            is Asset.Battery -> PreferredFeeMethod.BATTERY
            is Asset.Coin -> PreferredFeeMethod.TON
            is Asset.Token -> PreferredFeeMethod.GASLESS
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
            is Asset.Token, is Asset.Battery -> asset.chain.toAsset()
        }

        val energyAsset = runCatching { accountRepo.findAccount(walletId, energy.id) }
            .getOrNull()

        val transaction = bind(msg.operation, account.value)


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
                ConfirmType.Message.Type.Transaction,
                ConfirmType.Message.Type.Transactions -> MessageType.Transaction
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
            is Asset.Token, is Asset.Battery -> asset.chain.toAsset()
        }

        val energyAccount = accountRepo.findAccount(walletId, energy.id) ?: throw IllegalStateException("Can't find energy account by id")

        val bound = bind(transfer.operation, account.value) as? Transaction.Transfer
            ?: throw IllegalStateException("Transfer operation does not bind to a transfer")

        val recipientKey = transfer.commentEncryptionKey?.let { PubKey(defaultHex = it, segWit = "") }
        val memo = bound.memo
        val emulation = if (recipientKey != null && memo != null) {
            provider.tonEmulationPayload(memo.encodeToByteArray().size, account.value, recipientKey)
        } else {
            null
        }
        val payloadError = emulation?.error
            ?.also { L.e(it) }
            ?.let(::parseSignError)

        val transaction = bound.copy(
            isMax = transfer.isMax ?: false,
            memo = memo.takeIf { emulation == null },
            payload = emulation?.getOrNull() ?: bound.payload,
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

        val shrunkTransaction = reservationResult.getOrNull()
            ?.let {
                val isMax = transaction.isMax || it.isAmountAdjusted
                transaction.copy(amount = it.amount, isMax = isMax)
            }
            ?: transaction

        val chainKitError = payloadError ?: feeError ?: nonceError ?: reservationError

        // Dapp requests answer a WalletConnect call and must be broadcast through chainkit so the
        // response can be returned; relayed fee methods are out of scope for them.
        val fee = if (request.context is ConfirmContext.Dapp) {
            TxFeeState(energy = energyAccount, estimated = feeResult.value)
        } else {
            feeBuilder.build(
                account = account,
                energy = energyAccount,
                walletId = walletId,
                amount = transaction.amount,
                to = transaction.to.display,
                comment = memo,
                estimated = feeResult.value,
                shrinkToFit = transaction.isMax && reservationResult.value?.isAmountAdjusted == true,
            )
        }.copy(error = chainKitError)

        val relayable = fee.options.any { it.viaRelayer }
        val adjustedTransaction = if (relayable) {
            transaction
        } else {
            shrunkTransaction
        }

        confirmationError.tryEmit(feeConfirmationError(fee, chainKitError, nonceError))
        L.d("Fee: ${feeResult.value}")
        L.d("Nonce: ${nonceResult.value}")

        return PendingTransaction(
            request = request,
            wallet = wallet,
            account = account,
            fee = fee,
            nonce = nonceResult.value,
            signing = Signing.Tx(adjustedTransaction, walletPaid = shrunkTransaction.takeIf { relayable }),
        )
    }

    /**
     * A relayer pays the chain fee, so the wallet not covering it is no error while a relayed method
     * can pay: reporting it would block a send the battery (or the TON instant fee) completes. When
     * no method can pay, the shortage is reported whatever is selected, so the dialog offers its
     * top-up right away.
     */
    protected fun feeConfirmationError(
        fee: TxFeeState,
        chainKitError: ConfirmationError?,
        nonceError: ConfirmationError?,
    ): ConfirmationError? {
        val relayed = fee.resolve(pickedFeeId.value)?.viaRelayer == true
        return when {
            !relayed -> chainKitError
            fee.options.none(TxFeeLogic::isSufficient) -> nonceError ?: ConfirmationError.InsufficientBalance
            else -> nonceError
        }
    }

    protected fun bind(operation: Operation, account: Account): Transaction =
        operation.transaction(account).getOrElse { throw it }

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
            is Signing.Tx -> confirmTransaction(context, signing = signing, committed)
        }
    }

    protected fun Flow<PendingTransaction?>.keepLastPrepared(): Flow<PendingTransaction?> {
        return scan(null as PendingTransaction?) { previous, next -> next ?: previous }
    }

    // Always starts from the prepared transaction, so only the option selected now is ever quoted.
    protected fun Flow<PendingTransaction?>.quoteSelectedFee(): Flow<PendingTransaction?> {
        return combine(this, pickedFeeId) { tx, picked -> tx to picked }
            .mapLatest { (tx, picked) ->
                if (tx == null || tx.fee.quote == null || txLocked) {
                    return@mapLatest tx
                }

                feeQuoting.value = true
                try {
                    val fee = TxFeeLogic.quote(tx.fee, picked)
                    // The quote can rule out the last method that paid while chainkit reported
                    // nothing: the shortage is still what the user has to hear about, unless
                    // prepare already reported what blocks the send (a failed nonce, say).
                    if (fee.options.none { TxFeeLogic.isSufficient(it) }) {
                        val error = fee.error ?: confirmationError.value ?: ConfirmationError.InsufficientBalance
                        confirmationError.tryEmit(error)
                    }
                    tx.withFee(fee)
                } finally {
                    feeQuoting.value = false
                }
            }
    }

    override fun onCleared() {
        rejectDappRequest()
        super.onCleared()
    }

    fun reject() {
        if (request.context !is ConfirmContext.Dapp) {
            return
        }
        rejectDappRequest()
        finish()
    }

    private fun rejectDappRequest() {
        val dapp = request.context as? ConfirmContext.Dapp ?: return
        if (!answered.compareAndSet(false, true)) {
            return
        }
        val failed = userConfirmed
        // Undispatched: the provider is answered before the sheet's destruction reaches its handler,
        // which would otherwise answer the request as rejected first.
        Async.ioScope().launch(start = CoroutineStart.UNDISPATCHED) {
            if (failed) {
                confirmResults.failed(dapp)
            } else {
                confirmResults.rejected(dapp)
            }
        }
    }

    private suspend fun unlockConfirmed(context: Context, walletId: String): CryptoWallet {
        return unlockCryptoWallet(context, walletId).also { userConfirmed = true }
    }

    // [unlockSigner] behind the same mark: whichever keys a subclass signs with, getting them took the
    // passcode.
    private suspend fun unlockConfirmedSigner(context: Context, pendingTx: PendingTransaction): SigningWallet {
        return unlockSigner(context, pendingTx).also { userConfirmed = true }
    }

    private suspend fun approveDappRequest(pendingTx: PendingTransaction, signedResult: String) {
        val dapp = pendingTx.request.context as? ConfirmContext.Dapp ?: return
        if (!answered.compareAndSet(false, true)) {
            return
        }
        Async.ioScope().async {
            confirmResults.approved(dapp, signedResult)
        }.await()
    }

    private fun confirmMessage(context: Context, msg: Signing.Msg, pendingTx: PendingTransaction) {
        sendingState.tryEmit(SendingState.Loading)

        mainScope.launch {
            try {
                analytics.redOperation(analytics.sendOperation) {
                    val cryptoWallet = unlockConfirmed(context, pendingTx.wallet.id)
                    val batch = (pendingTx.request.type as? ConfirmType.Message)
                        ?.takeIf { it.type == ConfirmType.Message.Type.Transactions }
                    val signing = withContext(Async.Io) {
                        if (batch != null) {
                            signTransactions(batch.data, msg.value.account, cryptoWallet)
                        } else {
                            provider.signMessage(msg.value.data, msg.value.type, msg.value.account, cryptoWallet)
                        }
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

                    val result = if (pendingTx.request.action.withSend) {
                        sendSignedTransaction(pendingTx, msg.value.account, signedMessage) ?: return@launch
                    } else {
                        signedMessage
                    }

                    approveDappRequest(pendingTx, result)
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
            delay(SUCCESS_HOLD_MS)
            onSendSucceeded()
            finish()
        }
    }

    // solana_signAllTransactions: each transaction is signed on its own; one failure fails the batch.
    private suspend fun signTransactions(
        data: String,
        account: Account,
        cryptoWallet: CryptoWallet,
    ): SignRes<String>? {
        val signed = WcSolanaAnswers.unpackTransactions(data).map { transaction ->
            val signing = provider.signMessage(transaction, MessageType.Transaction, account, cryptoWallet)
                ?: return null
            signing.getOrNull() ?: return signing
        }
        return SignRes.ok(WcSolanaAnswers.signedAll(signed))
    }

    // solana_signAndSendTransaction: signed as solana_signTransaction is, then broadcast. Null when
    // the broadcast failed, after reporting it.
    private suspend fun RedOperation.sendSignedTransaction(
        pendingTx: PendingTransaction,
        account: Account,
        signed: String,
    ): String? {
        val transaction = WcSolanaAnswers.signedTransaction(signed)
        val result = withContext(Async.Io) { provider.sendRawTransaction(account, transaction) }
        val nodeError = result.error
        if (nodeError != null) {
            L.e(nodeError)
            sendingState.tryEmit(SendingState.None)
            val error = parseNodeError(nodeError)
            confirmationError.tryEmit(error)
            failure(error)
            return null
        }
        // As after any other send: the balance and history pick the transaction up right away.
        notifyTransactionSent(pendingTx)
        return WcSolanaAnswers.sent(signed)
    }

    private suspend fun RedOperation.signCommitted(
        context: Context,
        tx: Signing.Tx,
        pendingTx: PendingTransaction,
        fee: Fee,
        nonce: BigInteger,
    ): List<SignedTx>? {
        val signer = unlockConfirmedSigner(context, pendingTx)

        val transfer = pendingTx.request.type as? ConfirmType.Transfer
        val encryptionKey = transfer?.commentEncryptionKey
        val encryptedMemo = transfer?.operation?.let(ConfirmOperations::comment)
        val mainTx = if (tx.value is Transaction.Transfer && encryptionKey != null && encryptedMemo != null) {
            val encrypted = provider.encryptTonMemo(
                memo = encryptedMemo,
                account = tx.value.account,
                recipientPublicKey = PubKey(defaultHex = encryptionKey, segWit = ""),
                wallet = signer,
            )
            val encryptError = encrypted.error
            if (encryptError != null) {
                reportSignFailure(encryptError, pendingTx)
                return null
            }
            tx.value.copy(memo = null, payload = encrypted.unwrap())
        } else {
            tx.value
        }

        var mainNonce = nonce
        val prerequisites = pendingTx.prerequisites.map { prerequisite ->
            val prerequisiteTx = signableApproval(prerequisite.tx, mainTx)
            val signed = withContext(Async.Io) {
                provider.signTransaction(prerequisiteTx, prerequisite.fee, mainNonce, signer)
            }
            val signError = signed.error
            if (signError != null) {
                reportSignFailure(signError, pendingTx)
                return null
            }
            mainNonce += BigInteger.ONE
            SignedTx(tx = prerequisiteTx, data = signed.unwrap().firstOutput())
        }

        val messages = tx.batchToSign ?: listOf(mainTx)
        val signing = withContext(Async.Io) {
            if (messages.size == 1) {
                provider.signTransaction(mainTx, fee, mainNonce, signer)
            } else {
                provider.signBatch(messages, fee, mainNonce, signer)
            }
        }
        val error = signing.error
        if (error != null) {
            reportSignFailure(error, pendingTx)
            return null
        }

        val main = SignedTx(
            tx = mainTx,
            data = signing.unwrap().firstOutput(),
            service = pendingTx.quote?.provider?.id,
            routeId = pendingTx.quote?.routeId,
            serviceTxId = pendingTx.quote?.providerTxId,
            sellBaseAmount = pendingTx.quote?.sourceBaseAmount,
        )

        return prerequisites + main
    }

    /** What [tx], sent ahead of [main], is signed as. */
    protected open fun signableApproval(tx: Transaction, main: Transaction): Transaction = tx

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
                    walletId = reportedWalletId(pendingTx),
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

    private fun confirmTransaction(context: Context, signing: Signing.Tx, pendingTx: PendingTransaction) {
        val selected = selectedFee(pendingTx)
        if (selected != null && !TxFeeLogic.isSufficient(selected)) {
            confirmationError.tryEmit(ConfirmationError.InsufficientBalance)
            return
        }
        if (selected?.viaRelayer == true) {
            confirmWithRelayer(context, signing, pendingTx, selected)
            return
        }
        val tx = Signing.Tx(value = signing.sentWith(selected), batchToSign = signing.batchToSign)

        val fee = pendingTx.signingFee ?: pendingTx.fee.estimated ?: run {
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
                        analytics.transactionSent(tx.value, selected, pendingTx.quote)
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
            delay(SUCCESS_HOLD_MS)
            onSendSucceeded()
            finish()
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
                val signer = unlockConfirmedSigner(context, pendingTx)
                sendData.value = SendData(pendingTx, emptyList())
                withContext(Async.Io) {
                    relayer.send(signer, option)
                }

                analytics.transactionSent(tx.value, option, pendingTx.quote)
                analytics.swapSuccess(pendingTx)
                notifyTransactionSent(pendingTx)
                trackSuccessfulSend(pendingTx)
                sendingState.tryEmit(SendingState.Done)
                delay(SUCCESS_HOLD_MS)
                onSendSucceeded()
                finish()
            } catch (e: Throwable) {
                sendingState.tryEmit(SendingState.None)
                verifyError(e)
                L.e(e)
                when (e) {
                    is RelayerOutcomeUnknownException -> {
                        notifyTransactionSent(pendingTx)
                        confirmationError.tryEmit(ConfirmationError.OutcomeUnknown)
                    }

                    // Nothing was sent and what was quoted is gone: unlock, so a retry prepares again.
                    is RelayerQuoteExpiredException -> {
                        sendData.value = null
                        confirmationError.tryEmit(ConfirmationError.Expired)
                    }

                    is RelayerRejectedException -> {
                        if (e.nothingSent) {
                            sendData.value = null
                        }
                        confirmationError.tryEmit(ConfirmationError.RelayerRejected(e.reason.orEmpty()))
                    }

                    else -> confirmationError.tryEmit(ConfirmationError.Unknown)
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

    // Refreshes what the sent transaction touched: the source and destination chains of the wallet.
    protected open fun notifyTransactionSent(pendingTx: PendingTransaction) {
        val walletId = pendingTx.wallet.id
        val sourceChain = pendingTx.account.data.chain
        postTransactionRefreshSchedule.notifyTransactionSent(walletId, sourceChain)
        val destinationChain = pendingTx.destination?.data?.chain ?: return
        if (destinationChain != sourceChain) {
            postTransactionRefreshSchedule.notifyTransactionSent(walletId, destinationChain)
        }
    }

    protected fun parseReservationError(error: GasReserveResult.Error): ConfirmationError {
        return when (error) {
            GasReserveResult.Error.InsufficientBalance -> ConfirmationError.InsufficientBalance
        }
    }
}
