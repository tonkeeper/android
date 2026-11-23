package com.tonapps.deposit.multicoin.screens.confirm.base

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.async.Async
import com.tonapps.blockchain.evm.EvmTypedData
import com.tonapps.blockchain.model.ConfirmContext
import com.tonapps.blockchain.model.ConfirmOperations
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.bus.generated.Events.RedOperations.RedOperationsOperation
import com.tonapps.bus.generated.Events.TransactionSent.TransactionSentLedgerModel
import com.tonapps.chainkit.core.chain.api.models.NodeError
import com.tonapps.chainkit.core.chain.api.models.NodeRes
import com.tonapps.chainkit.core.chain.api.models.SignError
import com.tonapps.chainkit.core.chain.api.models.SignRes
import com.tonapps.chainkit.core.chain.model.MessageType
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.MnemonicWallet
import com.tonapps.chainkit.core.chain.model.account.PubKey
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.op.PayloadMeta
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
import com.tonapps.deposit.multicoin.screens.confirm.fee.RelayerSigner
import com.tonapps.wallet.data.ledger.analytics.ledgerModel
import com.tonapps.wallet.data.ledger.analytics.toTransactionSentModel
import com.tonapps.wallet.data.ledger.signing.LedgerDismissedException
import com.tonapps.wallet.data.ledger.signing.ledgerCall
import com.tonapps.deposit.multicoin.screens.confirm.fee.RelayerRejectedException
import com.tonapps.ledger.ble.extension.hexAsUtf8
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.log.L
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.SigningWallet
import com.tonapps.wallet.data.dapps.wc.WcSolanaAnswers
import com.tonapps.wallet.data.ledger.signing.LedgerSigningInteractor
import com.tonapps.wallet.data.ledger.signing.ledgerSignsMessage
import com.tonapps.wallet.data.ledger.signing.LedgerTransactionRequest
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.exchange.SwapQuote
import com.tonapps.wallet.data.multichain.tx.PostTransactionRefreshSchedule
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.data.passcode.WalletUnlocker
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
import kotlin.time.Duration
import kotlin.time.TimeSource

private const val NONCE_WAIT_ATTEMPTS = 60
private const val NONCE_WAIT_INTERVAL_MS = 2_000L

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

    // What leaves [account] besides the fee, when [signing] doesn't say it: a sold token's amount, or
    // every message of a split swap route, forward gas included.
    val spentAmount: BigInteger? = null,

    val destination: AccountWithDetails? = null,
    val quote: SwapQuote? = null,
    val rateLabel: String? = null,
    // Transactions of their own sent ahead of the main one, a nonce each (an EVM approve before its
    // swap). [fee] then shows their sum with the main fee, and the main one is signed with [signingFee].
    val prerequisites: List<Prerequisite> = emptyList(),
    val signingFee: Fee? = null,
) {

    val isFeeUnavailable: Boolean
        get() = signing is Signing.Tx && signingFee == null && fee.estimated == null && fee.options.isEmpty()

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
    val waitForNonce: BigInteger? = null,
)

class SendData(
    val tx: PendingTransaction,
    val signed: List<SignedTx>,
    val hash: String? = null,
    val headMaybeSent: Boolean = false,
    val staleBeforeSend: Boolean = false,
)

class QuoteExpiredException : Exception()

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
    walletUnlocker: WalletUnlocker,
    signatureGate: SignatureGate,
    confirmResults: ConfirmResultListener,
    protected val feeBuilder: TxFeeBuilder,
    protected val gaslessSender: GaslessSender,
    protected val settingsRepository: SettingsRepository,
    private val ledgerSigner: LedgerSigningInteractor,
) : BaseConfirmFeature<MviViewState, MviAction>(request, accountRepo, walletUnlocker, signatureGate, confirmResults) {

    final override val confirmationError = MutableStateFlow<ConfirmationError?>(null)

    final override val sendingState = MutableStateFlow(SendingState.None) // TODO rewrite

    private val analytics = ConfirmAnalytics(request)

    protected val refreshToken = MutableStateFlow<String?>(null)

    // Selection deliberately outside PendingTransaction: pendingTx is rebuilt on every refresh
    // (refill round-trip, retry, swap tick), which would otherwise wipe the user's pick.
    val pickedFeeId = MutableStateFlow<String?>(null)

    protected val sendData = MutableStateFlow<SendData?>(null)

    @Volatile
    private var messageSignedToSend = false

    val pendingLoader = MutableStateFlow(false)

    val feeQuoting = MutableStateFlow(false)

    protected val txLocked: Boolean
        get() = sendData.value != null || sendingState.value != SendingState.None

    val txCommitted: StateFlow<Boolean> = combine(sendData, sendingState) { data, sending ->
        data != null || sending != SendingState.None
    }.cacheState(initialValue = false)

    abstract val pendingTx: Flow<PendingTransaction?>

    final override val isCommitted: Boolean
        get() = sendData.value?.takeUnless { it.staleBeforeSend } != null || messageSignedToSend

    init {
        watchRequestGone()
    }

    // Runs after the success hold, right before the screen is told to close, so anything it opens
    // lands once the "sent" state has been seen.
    protected open fun onSendSucceeded() = Unit

    protected open fun onSent() = Unit

    override fun onStoppedAsGone() {
        analytics.markRequestGone()
    }

    // The keys a transaction is signed with: the wallet's HD wallet, behind the passcode.
    protected open suspend fun unlockSigner(pendingTx: PendingTransaction): SigningWallet {
        return SigningWallet.Hd(unlockCryptoWallet(pendingTx.wallet.id))
    }

    // The registered wallet a broadcast is reported to the backend for; null sends without a report.
    protected open fun reportedWalletId(pendingTx: PendingTransaction): String? = pendingTx.wallet.id

    protected open fun deviceSigningWindow(pendingTx: PendingTransaction): Duration? = null

    fun selectFee(fee: TxFee) {
        if (txLocked) {
            return
        }
        pickedFeeId.value = fee.id
        settingsRepository.setPreferredFeeMethod(request.data.walletId, fee.preferredMethod)
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
        return analytics.redOperation(RedOperationsOperation.Emulate, otherMetadata = analytics.emulateMetadata()) {
            val pending = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // What [onPrepareFailed] shows for a throw.
                failure(ConfirmationError.Unknown, analytics.emulateMetadata(), cause = e)
                throw e
            }
            val error = confirmationError.value
            // The terminal names the provider the route was quoted with, not the one the screen opened with.
            val metadata = analytics.emulateMetadata(quote = pending?.quote)
            if (error == null) {
                success(metadata)
            } else {
                failure(error, metadata)
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

        // As for a transfer: a coin that can't pay the fee (and the call's value) is a shortage to
        // show before the passcode, not a broadcast the node rejects after it.
        val reservationError = energyAsset?.let { energyAccount ->
            val reservation = reserveNativeGas(
                account = transaction.account,
                energy = energyAccount.value,
                amount = transaction.amount,
                isMax = false,
                fee = feeResult.value,
                policy = GasReservePolicy.DrainOrError,
            )
            reservation.error
                ?.also { L.e(it) }
                ?.let(::parseNodeError)
                ?: reservation.value?.error?.let(::parseReservationError)
        }

        val error = feeError ?: nonceError ?: reservationError
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
            data = when (msg.type) {
                ConfirmType.Message.Type.Typed -> typedDataToSign(msg.data, account.value)
                else -> msg.data.hexAsUtf8() ?: msg.data
            }
        )

        val ledgerRefuses = wallet.isHardware && !account.value.chain.ledgerSignsMessage(message.type)
        confirmationError.tryEmit(ConfirmationError.LedgerUnsupported.takeIf { ledgerRefuses })
        return PendingTransaction(
            request = request,
            wallet = wallet,
            account = account,
            nonce = null,
            signing = Signing.Msg(message),
        )
    }

    private fun typedDataToSign(data: String, account: Account): String {
        val typedData = EvmTypedData.decode(data)
            ?: throw IllegalArgumentException("Typed data cannot be read")
        check(typedData.isAddressedTo(account.chain)) { "Typed data is addressed to another chain" }
        return typedData.json
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

        // A dapp's token transfer is the token contract's call: the transfer is what that call carries,
        // signed as the token the call goes to.
        val bound = transfer.tokenTransfer?.let { asked ->
            val called = (transfer.operation.payloads.singleOrNull()?.meta as? PayloadMeta.Evm.Main)?.to?.display
            val contract = (account.asset.value as? Asset.Token)?.contract?.display
            check(contract != null && contract.equals(called, ignoreCase = true)) { "The token transfer calls another contract" }

            Transaction.Transfer(
                account = account.value,
                amount = asked.amount,
                isMax = false,
                to = Address.force(asked.to, account.value.chain),
            )
        }
            ?: bind(transfer.operation, account.value) as? Transaction.Transfer
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

    open fun retry(): Job? {
        if (confirmationError.value?.isRetryable == false) {
            return null
        }

        confirmationError.tryEmit(null)

        val data = sendData.value
        return if (data == null || data.staleBeforeSend) {
            sendData.value = null
            refreshToken.tryEmit(System.nanoTime().toString())
            null
        } else {
            sendTransaction(data.tx)
        }
    }

    fun sendTransaction(pendingTx: PendingTransaction): Job? {
        if (sendingState.value != SendingState.None) {
            return null
        }

        val committed = sendData.value?.takeUnless { it.staleBeforeSend }?.tx ?: pendingTx
        return when (val signing = committed.signing) {
            is Signing.Msg -> confirmMessage(msg = signing, committed)
            is Signing.Tx -> confirmTransaction(signing = signing, committed)
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

    // [unlockSigner] behind the same mark: whichever keys a subclass signs with, getting them took the
    // passcode.
    private suspend fun unlockConfirmedSigner(pendingTx: PendingTransaction): SigningWallet {
        return unlockSigner(pendingTx).also { markUserConfirmed() }
    }

    private fun confirmMessage(msg: Signing.Msg, pendingTx: PendingTransaction): Job {
        sendingState.tryEmit(SendingState.Loading)

        return mainScope.launch {
            try {
                analytics.redOperation(analytics.sendOperation) {
                    val batch = (pendingTx.request.type as? ConfirmType.Message)
                        ?.takeIf { it.type == ConfirmType.Message.Type.Transactions }
                    val signing = if (pendingTx.wallet.isHardware) {
                        ledgerCall {
                            SignRes.ok(signMessageWithLedger(pendingTx, msg.value, batch).also { markUserConfirmed() })
                        }
                    } else {
                        val cryptoWallet = unlockCryptoWallet(pendingTx.wallet.id)
                        withContext(Async.Io) {
                            if (batch != null) {
                                signTransactions(batch.data, msg.value.account, cryptoWallet)
                            } else {
                                provider.signMessage(msg.value.data, msg.value.type, msg.value.account, cryptoWallet)
                            }
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

                    stopIfGone()
                    val result = if (pendingTx.request.action.withSend) {
                        messageSignedToSend = true
                        sendSignedTransaction(pendingTx, msg.value.account, signedMessage) ?: return@launch
                    } else {
                        signedMessage
                    }

                    approveDappRequest(result)
                    success()
                }
            } catch (e: Throwable) {
                sendingState.tryEmit(SendingState.None)
                verifyError(e)
                L.e(e)
                confirmationError.tryEmit(e.toConfirmationError())
                return@launch
            }

            sendingState.tryEmit(SendingState.Done)
            delay(SUCCESS_HOLD_MS)
            onSendSucceeded()
            finish()
        }
    }

    private suspend fun signMessageWithLedger(pendingTx: PendingTransaction, message: Message, batch: ConfirmType.Message?): String {
        if (batch == null) {
            return ledgerSigner.signMessage(pendingTx.wallet.id, message.account, message.type, message.data)
        }
        val transactions = WcSolanaAnswers.unpackTransactions(batch.data)
        val signed = ledgerSigner.signSolanaTransactions(pendingTx.wallet.id, message.account, transactions)
        return WcSolanaAnswers.signedAll(signed)
    }

    // solana_signAllTransactions: each transaction is signed on its own; one failure fails the batch.
    private suspend fun signTransactions(
        data: String,
        account: Account,
        cryptoWallet: MnemonicWallet,
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
            if (nodeError is NodeError.Rejected) {
                messageSignedToSend = false
            }
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
        tx: Signing.Tx,
        pendingTx: PendingTransaction,
        fee: Fee,
        nonce: BigInteger,
    ): List<SignedTx>? {
        requireSignatureEnabled(tx.value.account)
        if (pendingTx.wallet.isHardware) {
            return signCommittedWithLedger(tx, pendingTx, fee, nonce)?.also { markUserConfirmed() }
        }
        val signer = unlockConfirmedSigner(pendingTx)

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
            val signed = withContext(Async.Io) {
                provider.signTransaction(prerequisite.tx, prerequisite.fee, mainNonce, signer)
            }
            val signError = signed.error
            if (signError != null) {
                reportSignFailure(signError, pendingTx)
                return null
            }
            mainNonce += BigInteger.ONE
            SignedTx(tx = prerequisite.tx, data = signed.unwrap().firstOutput())
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

    private suspend fun RedOperation.signCommittedWithLedger(
        tx: Signing.Tx,
        pendingTx: PendingTransaction,
        fee: Fee,
        nonce: BigInteger,
    ): List<SignedTx>? {
        val transfer = pendingTx.request.type as? ConfirmType.Transfer
        if (transfer?.commentEncryptionKey != null && transfer.operation?.let(ConfirmOperations::comment) != null) {
            reportSignFailure(SignError.UnsupportedTransaction("Encrypted comments cannot be signed on Ledger"), pendingTx)
            return null
        }
        val signingWindow = deviceSigningWindow(pendingTx)
        if (signingWindow != null && !signingWindow.isPositive()) {
            throw QuoteExpiredException()
        }
        val signableUntil = signingWindow?.let { TimeSource.Monotonic.markNow() + it }
        val batch = tx.batchToSign.orEmpty()
        val sequential = batch.size > 1
        if (sequential && tx.value.account.chain !is Chain.Ton) {
            reportSignFailure(SignError.UnsupportedTransaction("Batched messages cannot be signed on Ledger"), pendingTx)
            return null
        }

        val mainTx = tx.value
        val mainTxs = if (sequential) {
            batch
        } else {
            listOf(mainTx)
        }
        val prerequisiteTxs = pendingTx.prerequisites.map { signableApproval(it.tx, mainTx) to it.fee }
        val requests = prerequisiteTxs.mapIndexed { index, (prerequisiteTx, prerequisiteFee) ->
            LedgerTransactionRequest(prerequisiteTx, prerequisiteFee, nonce + BigInteger.fromInt(index), signableUntil)
        } + mainTxs.mapIndexed { index, main ->
            LedgerTransactionRequest(main, fee, nonce + BigInteger.fromInt(prerequisiteTxs.size + index), signableUntil)
        }
        val signed = ledgerCall { ledgerSigner.signTransactions(pendingTx.wallet.id, requests) }
        check(signed.size == requests.size) { "Ledger returned ${signed.size} signatures for ${requests.size} transactions" }
        if (signableUntil?.hasPassedNow() == true) {
            throw QuoteExpiredException()
        }

        val prerequisites = prerequisiteTxs.mapIndexed { index, (prerequisiteTx, _) ->
            SignedTx(tx = prerequisiteTx, data = signed[index])
        }
        val mains = mainTxs.mapIndexed { index, main ->
            val position = prerequisiteTxs.size + index
            val quote = pendingTx.quote.takeIf { index == 0 }
            SignedTx(
                tx = main,
                data = signed[position],
                service = quote?.provider?.id,
                routeId = quote?.routeId,
                serviceTxId = quote?.providerTxId,
                sellBaseAmount = quote?.sourceBaseAmount,
                waitForNonce = (nonce + BigInteger.fromInt(position)).takeIf { index > 0 },
            )
        }
        return prerequisites + mains
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
            item.waitForNonce?.let { awaitNonce(item.tx.account, it) }
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
                val error = when {
                    item.waitForNonce != null -> {
                        sendData.value = SendData(pendingTx, emptyList(), state.hash)
                        ConfirmationError.PartiallySent
                    }
                    nodeError.isRejection && state.headMaybeSent -> {
                        sendData.value = SendData(pendingTx, emptyList(), state.hash)
                        notifyTransactionSent(pendingTx)
                        ConfirmationError.OutcomeUnknown
                    }
                    // The nonce may be this transaction's own, sent by a resend the node answered late:
                    // it is neither prepared nor signed again, the user checks the history. A dapp is
                    // answered that it failed, as for an outcome unknown: it gets no hash it could be
                    // sure of, and the history, not the dapp, tells the user whether it went out.
                    nodeError is NodeError.NonceTaken -> {
                        sendData.value = SendData(pendingTx, emptyList(), state.hash)
                        // The history the user is told to check picks it up if it is out.
                        notifyTransactionSent(pendingTx)
                        ConfirmationError.NonceTaken
                    }
                    nodeError.isRejection && state.hash == null -> {
                        sendData.value = SendData(pendingTx, emptyList(), staleBeforeSend = true)
                        parseNodeError(nodeError)
                    }
                    nodeError is NodeError.NetworkWrap || nodeError is NodeError.Unknown -> {
                        sendData.value = SendData(pendingTx, state.signed, state.hash, headMaybeSent = true)
                        parseNodeError(nodeError)
                    }
                    else -> parseNodeError(nodeError)
                }
                sendingState.tryEmit(SendingState.None)
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

    private suspend fun awaitNonce(account: Account, nonce: BigInteger) {
        repeat(NONCE_WAIT_ATTEMPTS) {
            val current = withContext(Async.Io) { provider.getNonce(account) }.value
            if (current != null && current >= nonce) {
                return
            }
            delay(NONCE_WAIT_INTERVAL_MS)
        }
    }

    private fun confirmTransaction(signing: Signing.Tx, pendingTx: PendingTransaction): Job? {
        val selected = selectedFee(pendingTx)
        if (selected != null && !TxFeeLogic.isSufficient(selected)) {
            confirmationError.tryEmit(ConfirmationError.InsufficientBalance)
            return null
        }
        if (selected?.viaRelayer == true) {
            return confirmWithRelayer(signing, pendingTx, selected)
        }
        val tx = Signing.Tx(value = signing.sentWith(selected), batchToSign = signing.batchToSign)

        val fee = pendingTx.signingFee ?: pendingTx.fee.estimated ?: run {
            confirmationError.tryEmit(pendingTx.fee.error ?: ConfirmationError.Unknown)
            return null
        }
        val nonce = pendingTx.nonce ?: run {
            confirmationError.tryEmit(pendingTx.fee.error ?: ConfirmationError.Unknown)
            return null
        }

        analytics.swapConfirm(pendingTx)

        sendingState.tryEmit(SendingState.Loading)

        return mainScope.launch {
            try {
                analytics.redOperation(
                    operation = analytics.sendOperation,
                    attemptSource = analytics.sendAttemptSource,
                    otherMetadata = analytics.sendMetadata(tx.value, selected, pendingTx.quote),
                ) {
                    val committed = sendData.value?.takeUnless { it.staleBeforeSend } ?: run {
                        val signed = signCommitted(tx, pendingTx, fee, nonce) ?: return@launch
                        stopIfGone()
                        SendData(pendingTx, signed).also { sendData.value = it }
                    }

                    val signedResult = if (pendingTx.request.action.withSend) {
                        val hash = broadcast(pendingTx, committed) ?: return@launch
                        analytics.transactionSent(tx.value, selected, pendingTx.quote, pendingTx.wallet.isHardware, ledgerModelOf(pendingTx))
                        analytics.swapSuccess(pendingTx)
                        trackSuccessfulSend(pendingTx)
                        hash
                    } else {
                        val signed = committed.signed.last()
                        provider.signedTransactionHex(signed.tx.account.chain, signed.data)
                    }

                    approveDappRequest(signedResult)
                    success()
                }
            } catch (e: Throwable) {
                sendingState.tryEmit(SendingState.None)
                verifyError(e)
                L.e(e)
                val error = when (e) {
                    is QuoteExpiredException -> {
                        if (sendData.value == null) {
                            sendData.value = SendData(pendingTx, emptyList(), staleBeforeSend = true)
                        }
                        ConfirmationError.Expired
                    }
                    else -> e.toConfirmationError()
                }
                confirmationError.tryEmit(error)
                analytics.swapFailed(pendingTx, error, e)
                return@launch
            }

            onSent()
            sendingState.tryEmit(SendingState.Done)
            delay(SUCCESS_HOLD_MS)
            onSendSucceeded()
            finish()
        }
    }

    // A relayer send that fails surfaces the error as-is: never fall back to a chainkit broadcast,
    // the relayer may already have accepted the transaction.
    private fun confirmWithRelayer(
        tx: Signing.Tx,
        pendingTx: PendingTransaction,
        option: TxFee,
    ): Job? {
        val relayer = pendingTx.fee.relayer ?: run {
            confirmationError.tryEmit(ConfirmationError.Unknown)
            return null
        }

        sendingState.tryEmit(SendingState.Loading)

        return mainScope.launch {
            try {
                analytics.swapConfirm(pendingTx)
                analytics.redOperation(
                    operation = analytics.sendOperation,
                    attemptSource = analytics.sendAttemptSource,
                    otherMetadata = analytics.sendMetadata(tx.value, option, pendingTx.quote),
                ) {
                    requireSignatureEnabled(tx.value.account)
                    val signer = if (pendingTx.wallet.isHardware) {
                        RelayerSigner.Ledger(pendingTx.wallet.id)
                    } else {
                        RelayerSigner.Software(unlockConfirmedSigner(pendingTx))
                    }
                    sendData.value = SendData(pendingTx, emptyList())
                    ledgerCall {
                        withContext(Async.Io) {
                            relayer.send(signer, option)
                        }
                    }

                    analytics.transactionSent(tx.value, option, pendingTx.quote, pendingTx.wallet.isHardware, ledgerModelOf(pendingTx))
                    analytics.swapSuccess(pendingTx)
                    notifyTransactionSent(pendingTx)
                    trackSuccessfulSend(pendingTx)
                    success()
                }
                onSent()
                sendingState.tryEmit(SendingState.Done)
                delay(SUCCESS_HOLD_MS)
                onSendSucceeded()
                finish()
            } catch (e: Throwable) {
                val nothingSent = when (e) {
                    is LedgerDismissedException,
                    is LedgerFailureException,
                    is RelayerQuoteExpiredException,
                    is QuoteExpiredException -> true

                    is RelayerRejectedException -> e.nothingSent
                    else -> false
                }
                if (nothingSent) {
                    sendData.value = null
                }
                sendingState.tryEmit(SendingState.None)
                verifyError(e)
                L.e(e)
                when (e) {
                    is RelayerOutcomeUnknownException -> {
                        notifyTransactionSent(pendingTx)
                        confirmationError.tryEmit(ConfirmationError.OutcomeUnknown)
                    }

                    // Nothing was sent and what was quoted is gone: unlock, so a retry prepares again.
                    is RelayerQuoteExpiredException, is QuoteExpiredException -> {
                        confirmationError.tryEmit(ConfirmationError.Expired)
                    }

                    is RelayerRejectedException -> {
                        confirmationError.tryEmit(ConfirmationError.RelayerRejected(e.reason.orEmpty()))
                    }

                    else -> confirmationError.tryEmit(e.toConfirmationError())
                }
            }
        }
    }

    private fun Throwable.toConfirmationError(): ConfirmationError {
        if (this is ConfirmationErrorCause) {
            return confirmationError
        }
        val failure = (this as? LedgerFailureException)?.failure
        return if (failure is LedgerFailure.Unsupported) {
            ConfirmationError.LedgerUnsupported
        } else {
            ConfirmationError.Unknown
        }
    }

    private suspend fun ledgerModelOf(pendingTx: PendingTransaction): TransactionSentLedgerModel? {
        if (!pendingTx.wallet.isHardware) {
            return null
        }
        return accountRepo.ledgerModel(pendingTx.wallet.id)?.toTransactionSentModel()
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

// The node refused the transaction itself, a taken nonce included.
private val NodeError.isRejection: Boolean
    get() = this is NodeError.Rejected || this is NodeError.NonceTaken
