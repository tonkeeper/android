package com.tonapps.deposit.multicoin.screens.confirm

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.async.Async
import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.core.common.getOrElse
import com.tonapps.chainkit.core.op.Intent
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.SignedOperation
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationErrorCause
import com.tonapps.deposit.multicoin.screens.confirm.base.PendingTransaction
import com.tonapps.deposit.multicoin.screens.confirm.base.SendingState
import com.tonapps.deposit.multicoin.screens.confirm.base.SignatureGate
import com.tonapps.deposit.multicoin.screens.confirm.base.Signing
import com.tonapps.deposit.multicoin.screens.confirm.base.isShortage
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.coinValue
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFee
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeBuilder
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeState
import com.tonapps.deposit.multicoin.screens.confirm.fee.relayedHash
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.BaseOperationConfirmFeature
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationConfirmAction
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationFeeMethods
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationIntent
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationQuote
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationSpending
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationTracker
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.SendData
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.SentPayload
import com.tonapps.log.L
import com.tonapps.time.TimeProvider
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.PendingSwap
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.exchange.SwapQuote
import com.tonapps.wallet.data.multichain.exchange.SwapRepository
import com.tonapps.wallet.data.multichain.exchange.SwapRouteRefusedException
import com.tonapps.wallet.data.multichain.exchange.SwapWalletScreenedException
import com.tonapps.wallet.data.multichain.exchange.SwapSlippage
import com.tonapps.wallet.data.multichain.exchange.status.SentSwap
import com.tonapps.wallet.data.multichain.exchange.status.SwapStatusTracker
import com.tonapps.wallet.data.multichain.tx.PostTransactionRefreshSchedule
import com.tonapps.wallet.data.passcode.WalletUnlocker
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

private const val SWAP_OPERATION_QUOTE_TICK_MS = 250L

// What a max sale keeps for a fee paid in the asset it sells, of the fee as priced: gas moves between
// the route prepared for the smaller amount and its signing.
private const val MAX_FEE_RESERVE_PERCENT = 120
private const val PERCENT = 100
private const val MAX_PICK_REFITS = 3

/** [this], a swap request, as the operation confirm takes it: with no operation until the route is prepared. */
internal fun ConfirmRequest.asSwapOperation(): ConfirmRequest {
    val swap = type as? ConfirmType.Swap ?: return this
    return retyped(ConfirmType.SwapOperation(operation = Operation.build("swap", emptyList()), swap = swap))
}

// This request as [type], expiring at [expiresAt].
private fun ConfirmRequest.retyped(type: ConfirmType, expiresAt: Long? = data.expiresAt) = ConfirmRequest(
    data = CommonTransactionData(
        assetId = data.assetId,
        walletId = data.walletId,
        nonce = data.nonce,
        expiresAt = expiresAt,
        preview = data.preview,
    ),
    type = type,
    action = action,
    context = context,
    initiator = initiator,
    utm = utm,
)

/**
 * Confirm feature for a swap quoted on `/v3/crosschain`: the route is prepared into a Generic Payload
 * operation, held by chain-kit to the swap the user asked for and signed as it is, payload by payload —
 * an EVM token's approve, then the swap. As on the transaction swap confirm the route is quoted and
 * prepared again once the quote ages or the slippage changes.
 */
class SwapOperationConfirmFeature internal constructor(
    request: ConfirmRequest,
    provider: ChainKitProvider,
    accountRepo: McAccountRepository,
    walletUnlocker: WalletUnlocker,
    signatureGate: SignatureGate,
    postTransactionRefreshSchedule: PostTransactionRefreshSchedule,
    timeProvider: TimeProvider,
    confirmResults: ConfirmResultListener?,
    private val swapRepo: SwapRepository,
    private val statusTracker: SwapStatusTracker,
    private val swapQuote: SwapOperationQuote,
    swapFeeMethods: OperationFeeMethods?,
    private val swapTracker: SwapOperationTracker = SwapOperationTracker(),
    private val quoteIntervalMillis: Long = SWAP_QUOTE_INTERVAL_MS,
    // The fee the swap screen kept from an earlier attempt: the first prepare prices, and fits, by it.
    feeId: String? = null,
) : HeadlessSwapSource, BaseOperationConfirmFeature(
    request = request,
    provider = provider,
    accountRepo = accountRepo,
    walletUnlocker = walletUnlocker,
    signatureGate = signatureGate,
    postTransactionRefreshSchedule = postTransactionRefreshSchedule,
    intent = SwapIntent,
    timeProvider = timeProvider,
    tracker = swapTracker,
    relayedFeeMethods = swapFeeMethods,
    quote = swapQuote,
    spending = SwapSpending,
    confirmResults = confirmResults,
    pickedFee = feeId,
) {

    private val asked = (request.type as ConfirmType.SwapOperation).swap

    /** The swap as last prepared; the one the swap screen asked for until then. */
    val swap: StateFlow<ConfirmType.SwapOperation> = sendData
        .map { data -> data?.request?.type as? ConfirmType.SwapOperation ?: request.type as ConfirmType.SwapOperation }
        .cacheState(initialValue = request.type as ConfirmType.SwapOperation)

    /** The route the prepared operation makes; the swap screen's until one is prepared. */
    val quote: StateFlow<SwapQuote?> = swap
        .map { it.swap.snapshotQuote() }
        .cacheState(initialValue = asked.snapshotQuote())

    /** The account the swap buys into. */
    val destination: StateFlow<AccountWithDetails?> = flow { emit(findAccount(asked.destinationAssetId)) }
        .cacheState(initialValue = null)

    /** "1 TON ≈ 3.2 USDT", for the route on screen. */
    val rateLabel: StateFlow<String?> = combine(quote, account, destination) { quote, sell, buy ->
        if (quote == null || sell == null || buy == null) {
            null
        } else {
            swapRepo.formatRateLabel(quote, sell.asset.value, buy.asset.value)
        }
    }
        .cacheState(initialValue = null)

    /** What is left of the quote's life, 1 to 0: at 0 the route is quoted and prepared again. */
    val swapQuoteCountdown = MutableStateFlow(0f)

    /** Slippage options for the swap's chain; null while loading or for a chain with none. */
    val slippage: StateFlow<SwapSlippage?> = flow { emit(loadSlippage()) }
        .cacheState(initialValue = null)

    /** The slippage the route is quoted with: the swap screen's, until one is picked here. */
    val selectedSlippageBps: StateFlow<Int?> = swapQuote.slippageBps

    // The swap screen drives a headless swap: it quotes again on its own, and this screen's timer stays off.
    private val headless = MutableStateFlow(false)

    // The coin the chain charges its fee in, as the swap screen's fee rows read it.
    private val energy: StateFlow<AccountWithDetails?> = account
        .map { position -> position?.let { findAccount(it.data.chainAccount.asset.id) } }
        .cacheState(initialValue = null)

    override val pickedFeeId = MutableStateFlow<String?>(null)

    override val maxFit: StateFlow<SwapMaxFit?>
        get() = swapQuote.maxFit

    // How many times the fee last picked had the route prepared again to sell less; main thread only.
    private var pickRefits = 0

    override val prepared: StateFlow<PendingTransaction?> = combine(
        sendData,
        combine(feeMethods, energy, ::Pair),
        combine(destination, quote, rateLabel, ::Triple),
        combine(confirmationError, fee, coinShort, ::Triple),
    ) { data, (methods, energy), (destination, quote, rateLabel), (error, shortFee, coinShort) ->
        val short = coinShort?.takeIf { data == null && error.isShortage && shortFee != null }
        val shortPosition = short?.let { it.coin.takeIf { coin -> coin.asset.id == it.position.asset.id } ?: it.position }
        val position = data?.position ?: shortPosition ?: return@combine null
        val owner = data?.wallet ?: short?.wallet ?: return@combine null
        val type = data?.request?.type as? ConfirmType.SwapOperation
        val shortAttached = short?.sent?.takeIf { position.asset.value !is Asset.Coin }
        val messagesAmount = type?.operation?.payloads?.fold(BigInteger.ZERO) { sum, payload -> sum + payload.meta.coinValue() } ?: short?.sent
        // What the route sells as prepared: a max sale may have been fitted to its fee.
        val sourceAmount = type?.swap?.sourceAmount ?: asked.sourceAmount
        PendingTransaction(
            request = request.retyped(asked),
            // What the swap screen reads of the swap; the operation itself is what is signed.
            signing = Signing.Tx(
                Transaction.Swap(
                    account = position.value,
                    amount = sourceAmount,
                    destination = destination?.value ?: position.value,
                    to = position.value.address,
                    isMax = asked.isMax == true,
                ),
            ),
            wallet = owner,
            account = position,
            fee = methods?.takeIf { it.options.isNotEmpty() }?.let { options ->
                shortAttached?.takeUnless { options.hasRelayedOption }?.let { options.copy(attachedEnergy = it) } ?: options
            } ?: TxFeeState(
                energy = short?.coin ?: energy,
                estimated = data?.fee ?: shortFee,
                attachedEnergy = shortAttached ?: BigInteger.ZERO,
            ),
            nonce = data?.nonce,
            spentAmount = messagesAmount?.let {
                swapSpentAmount(
                    sellsCoin = position.asset.value is Asset.Coin,
                    messagesAmount = it,
                    sourceAmount = sourceAmount,
                )
            },
            destination = destination,
            quote = quote,
            rateLabel = rateLabel,
        )
    }
        .cacheState(initialValue = null)

    override val pendingLoader: StateFlow<Boolean>
        get() = preparing

    override val txCommitted: StateFlow<Boolean> = sendingState
        .map { it != SendingState.None }
        .cacheState(initialValue = false)

    override val hasSendData: Boolean
        get() = isSigned

    override val unsignedSend: PendingTransaction?
        get() = prepared.value.takeUnless { isSigned }

    override val shortageHasTx: Boolean
        get() = preparedCoinShort

    // Whether the sent swap's status is tracked: what the swap screen is told it may open.
    @Volatile
    private var tracked = false

    init {
        // The swap screen seeds the fee it kept before anything is priced: it is picked once the methods list it,
        // without being saved as the wallet's preference again (a pick made here is saved by [selectFee]).
        mainScope.launch {
            combine(pickedFeeId, feeMethods) { id, methods -> methods?.options?.firstOrNull { it.id == id } }
                .filterNotNull()
                .distinctUntilChangedBy { it.id }
                .collect(::keepFee)
        }

        // The quote ages only while it is on screen to be confirmed: not while it is sent, nor while an
        // error holds the screen, nor when the swap screen drives it.
        mainScope.launch {
            combine(sendingState, confirmationError, headless) { sending, error, isHeadless ->
                sending == SendingState.None && error == null && !isHeadless
            }
                .distinctUntilChanged()
                .collectLatest { active ->
                    if (active) {
                        runQuoteTimer()
                    }
                }
        }

        // A fee picked once the route is prepared may be one a max sale leaves no room for.
        mainScope.launch {
            combine(sendData, feeMethod, ::Pair).collect { (data, method) -> refit(data, method) }
        }

        bgScope.launch {
            sendingState.first { it == SendingState.Done }
            onSwapSent()
        }
    }

    constructor(
        request: ConfirmRequest,
        provider: ChainKitProvider,
        accountRepo: McAccountRepository,
        walletUnlocker: WalletUnlocker,
        signatureGate: SignatureGate,
        postTransactionRefreshSchedule: PostTransactionRefreshSchedule,
        timeProvider: TimeProvider,
        confirmResults: ConfirmResultListener?,
        swapRepo: SwapRepository,
        statusTracker: SwapStatusTracker,
        feeBuilder: TxFeeBuilder,
        settingsRepository: SettingsRepository,
        feeId: String? = null,
    ) : this(
        request = request.asSwapOperation(),
        provider = provider,
        accountRepo = accountRepo,
        walletUnlocker = walletUnlocker,
        signatureGate = signatureGate,
        postTransactionRefreshSchedule = postTransactionRefreshSchedule,
        timeProvider = timeProvider,
        confirmResults = confirmResults,
        swapRepo = swapRepo,
        statusTracker = statusTracker,
        swapQuote = SwapOperationQuote(request.asSwapOperation(), swapRepo, accountRepo, provider),
        swapFeeMethods = SwapOperationFeeMethods(request.asSwapOperation(), feeBuilder, settingsRepository),
        feeId = feeId,
    )

    override fun runHeadless() {
        headless.value = true
    }

    override fun retry(): Job? {
        val signed = isSigned
        sendAction(OperationConfirmAction.Retry)
        return if (signed) {
            sendAttempt()
        } else {
            null
        }
    }

    override fun sendTransaction(pendingTx: PendingTransaction): Job? {
        if (sendingState.value != SendingState.None || sendData.value == null) {
            return null
        }
        sendAction(OperationConfirmAction.Confirm)
        return sendAttempt()
    }

    override fun selectedFee(tx: PendingTransaction): TxFee? = feeMethod.value

    override fun selectFee(fee: TxFee) {
        pickedFeeId.value = fee.id
        // The cap is on what one pick sets off, not on how many fees the user tries.
        pickRefits = 0
        pickFee(fee)
    }

    // Done once the send just asked for is over, however it ended.
    private fun sendAttempt(): Job = mainScope.launch {
        sendingState.first { it != SendingState.None }
        sendingState.first { it != SendingState.Loading }
    }

    fun selectSlippage(bps: Int) {
        if (sendingState.value != SendingState.None || bps == swapQuote.slippageBps.value) {
            return
        }
        swapQuote.slippageBps.value = bps
        requote()
    }

    private fun requote() {
        swapQuote.requote()
        sendAction(OperationConfirmAction.RefreshFees)
    }

    // A max sale whose picked fee, paid in what it sells, no longer fits: the route is prepared again
    // to sell what that fee leaves, or the swap screen that quotes it is told to. What is fitted stays
    // fitted: a fee picked afterwards in another asset keeps the smaller amount, as an amount typed
    // in would be kept, and Max on the swap screen asks for the whole balance again.
    private fun refit(data: SendData?, method: TxFee?) {
        if (data == null || method == null || isSigned || sendingState.value != SendingState.None) {
            return
        }

        // A route just prepared may still be paired with the method of the one before it.
        if (data.feeMethods.options.none { it.id == method.id }) {
            return
        }
        // The balance this route's own prepare read: one still in flight may have read another.
        val payer = swapQuote.payerOf(data.request) ?: return
        val fit = swapQuote.fit(data.request, payer, method.fee)

        // The swap screen quotes its own route: it is told what to sell, that nothing is left to sell,
        // or that the fee picked now leaves the amount its room after all.
        if (asked.oneClick) {
            swapQuote.maxFit.value = fit
            return
        }
        val amount = (fit as? SwapMaxFit.Sell)?.amount ?: return

        // A route whose amounts do not follow what it sells would be fitted without end.
        if (pickRefits < MAX_PICK_REFITS && swapQuote.sell(amount)) {
            pickRefits++
            sendAction(OperationConfirmAction.RefreshFees)
        }
    }

    private suspend fun runQuoteTimer() {
        val totalTicks = (quoteIntervalMillis / SWAP_OPERATION_QUOTE_TICK_MS).toInt().coerceAtLeast(1)
        while (sendingState.value == SendingState.None) {
            for (i in 0..totalTicks) {
                swapQuoteCountdown.value = 1f - i.toFloat() / totalTicks
                if (i < totalTicks) {
                    delay(SWAP_OPERATION_QUOTE_TICK_MS)
                }
            }
            requote()
        }
    }

    // A one-click swap's status is tracked once it is out, whether or not the screen is still there.
    override fun onOperationSent() {
        val sent = trackedSwap() ?: return
        statusTracker.track(sent)
        tracked = true
    }

    // The swap screen underneath closes itself and opens History, or the swap's status, on this signal.
    private fun onSwapSent() {
        swapRepo.notifySwapCompleted(tracked = tracked)
    }

    private fun trackedSwap(): SentSwap? {
        if (!asked.oneClick || !request.action.withSend) {
            return null
        }
        val quote = quote.value ?: return null
        return SentSwap(
            walletId = request.data.walletId,
            txHash = swapTracker.swapHash,
            sell = account.value?.asset ?: return null,
            sendAmount = swap.value.swap.sourceAmount,
            buy = destination.value?.asset ?: return null,
            receiveAmount = quote.buyBaseAmount,
        )
    }

    private suspend fun loadSlippage(): SwapSlippage? = withContext(Async.Io) {
        val sell = findAccount(request.data.assetId) ?: return@withContext null
        swapRepo.getSlippage(request.data.walletId, sell.asset.value.chain)
    }

    private suspend fun findAccount(assetId: String): AccountWithDetails? {
        return try {
            accountRepo.findAccount(request.data.walletId, assetId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.e(e)
            null
        }
    }
}

/**
 * Quotes and prepares the swap [screen] asks for into the operation the screen signs: the swap screen's
 * own quote first, then a fresh one each time [requote] asks for it, or a prepare fails and is retried.
 */
internal class SwapOperationQuote(
    private val screen: ConfirmRequest,
    private val swapRepo: SwapRepository,
    private val accountRepo: McAccountRepository,
    private val provider: ChainKitProvider,
) : OperationQuote {

    private val walletId = screen.data.walletId
    private val asked = (screen.type as ConfirmType.SwapOperation).swap
    private val snapshot = AtomicReference(asked.snapshotQuote())

    // What the route sells: what was asked, until a max sale is fitted to its fee.
    private val sourceAmount = AtomicReference(asked.sourceAmount)

    val slippageBps = MutableStateFlow(asked.slippageBps)

    /**
     * What a max sale short of its fee is to do, for a swap screen that quotes the route itself: it
     * quotes the amount that fits, and what is prepared here stays short. Null while what was asked
     * can be paid.
     */
    val maxFit = MutableStateFlow<SwapMaxFit?>(null)

    // The account the sale is paid from, as a prepare read it (the coin refreshed, where the coin is
    // sold), with the request that prepare built. A fee picked later is fitted against the same balance.
    private val payer = AtomicReference<Pair<ConfirmRequest, AccountWithDetails>?>(null)

    /** The next build quotes the route anew. */
    fun requote() {
        snapshot.set(null)
    }

    /** The next build sells [amount], quoted anew; false when that is what it sells already. */
    fun sell(amount: BigInteger): Boolean {
        if (sourceAmount.getAndSet(amount) == amount) {
            return false
        }
        snapshot.set(null)
        return true
    }

    /** The account [built] is paid from, as the prepare that built it read it; null for another prepare's request. */
    fun payerOf(built: ConfirmRequest): AccountWithDetails? {
        return payer.get()?.takeIf { (request, _) -> request === built }?.second
    }

    /**
     * What [built], a max sale, is to do when [fee] is paid in the asset it sells and [payer]'s balance
     * does not hold both: sell what the balance leaves past the fee, with room for the fee to rise
     * before it is signed where there is any, or nothing, when the fee leaves nothing. Null when
     * [built] is paid as it is, a balance that covers both exactly included.
     */
    fun fit(built: ConfirmRequest, payer: AccountWithDetails, fee: Fee): SwapMaxFit? {
        val type = built.type as ConfirmType.SwapOperation
        val sold = type.swap.sourceAmount
        if (type.swap.isMax != true || !fee.asset.id.equals(payer.asset.id, ignoreCase = true)) {
            return null
        }

        // A coin's route may send more of it than it sells: what it charges on top goes out with the amount.
        val sent = if (payer.asset.value is Asset.Coin) {
            maxOf(sold, type.operation.payloads.fold(BigInteger.ZERO) { sum, payload -> sum + payload.meta.coinValue() })
        } else {
            sold
        }
        val balance = payer.unitBalance.value
        if (sent + fee.amount <= balance) {
            return null
        }

        val left = balance - (sent - sold)
        val reserve = fee.amount * BigInteger.fromInt(MAX_FEE_RESERVE_PERCENT) / BigInteger.fromInt(PERCENT)
        return listOf(left - reserve, left - fee.amount).firstOrNull { it.isPositive }?.let(SwapMaxFit::Sell)
            ?: SwapMaxFit.Short
    }

    override suspend fun refit(built: ConfirmRequest, payer: AccountWithDetails, fee: Fee): ConfirmRequest? {
        this.payer.set(built to payer)
        val fit = fit(built, payer, fee)

        // The swap screen quotes its own route: it is told what to sell, and this one stays as it is.
        // A route the fee leaves room in takes back a fit asked for before.
        if (asked.oneClick) {
            maxFit.value = fit
            return null
        }
        val amount = (fit as? SwapMaxFit.Sell)?.amount ?: return null

        sell(amount)
        return build(built).also { smaller -> this.payer.set(smaller to payer) }
    }

    override suspend fun build(request: ConfirmRequest): ConfirmRequest {
        return try {
            prepare(request)
        } catch (e: SwapWalletScreenedException) {
            throw SwapRouteError(ConfirmationError.WalletScreened, e)
        } catch (e: SwapRouteRefusedException) {
            throw SwapRouteError(ConfirmationError.UnsupportedTransaction, e)
        } catch (e: IOException) {
            throw SwapRouteError(ConfirmationError.NetworkError, e)
        }
    }

    private suspend fun prepare(request: ConfirmRequest): ConfirmRequest {
        val sell = position(screen.data.assetId)
        val buy = position(asked.destinationAssetId)
        val slippage = slippageBps.value
        val amount = sourceAmount.get()

        // The swap screen's quote is the first prepared, unless the slippage changed since.
        val quote = snapshot.getAndSet(null)?.takeIf { slippage == asked.slippageBps }
            ?: swapRepo.fetchConfirmQuote(sell, buy, amount, slippage, walletId, genericPayload = true)

        val prepared = swapRepo.prepareOperation(quote, walletId)
        val operation = prepared.operation.withDefaultFeesBehindApprovals(sell.data.chainAccount)
        val current = quote.preparedAs(prepared.providerRouteId, prepared.buyBaseAmount, prepared.minimumBuyBaseAmount)

        return request.retyped(
            type = ConfirmType.SwapOperation(
                operation = operation,
                swap = asked.copy(sourceAmount = amount, slippageBps = slippage, quote = current.toSnapshot()),
            ),
            expiresAt = prepared.expiresAtMillis,
        )
    }

    // A route is signed with the amounts it was prepared with: nothing is trimmed off what is built. A
    // max sale the fee leaves no room for is built anew instead, by [refit].
    override suspend fun fit(built: ConfirmRequest, fee: Fee, relayable: Boolean): ConfirmRequest = built

    // An SPL sell names its mint, and chain-kit signs it as the token's account; anything else signs as the chain's.
    override fun signer(position: AccountWithDetails): Account {
        return if (position.value.chain is Chain.Solana) {
            position.value
        } else {
            position.data.chainAccount
        }
    }

    /**
     * An EVM token swap is the approve, then the swap that spends the allowance it grants: simulated
     * before the approve is on chain, the swap reverts. One the route does not price is priced at the
     * chain's default limit for a call instead.
     */
    private suspend fun Operation.withDefaultFeesBehindApprovals(signer: Account): Operation {
        if (payloads.size < 2 || !signer.chain.isEvm || payloads.drop(1).all { it.fee != null }) {
            return this
        }

        val priced = payloads.mapIndexed { index, payload ->
            if (index == 0 || payload.fee != null) {
                return@mapIndexed payload
            }
            val transaction = payload.toTransaction(signer).getOrElse { throw it }
            val result = provider.defaultFee(transaction)
            val fee = result.value ?: throw IllegalStateException("No default fee for ${payload.id}: ${result.error}")
            payload.copy(fee = fee)
        }
        return Operation.build(id, priced)
    }

    private suspend fun position(assetId: String): AccountWithDetails {
        return accountRepo.findAccount(walletId, assetId, forceRefresh = assetId == screen.data.assetId)
            ?: throw IllegalStateException("Can't find account $assetId")
    }
}

/**
 * What a swap is held to: its sold amount of the account's asset. It names no recipient: the route's
 * deposit address and the contract it calls come from the same backend answer as the operation, so
 * holding the operation to them checks nothing, while a jetton sale, which goes through the account's
 * own jetton wallet, read as paying someone else.
 */
internal val SwapIntent = OperationIntent { request, account ->
    val type = request.type as ConfirmType.SwapOperation
    swapIntent(account, type.swap.sourceAmount)
}

internal fun swapIntent(account: Account, sourceAmount: BigInteger): Intent {
    return Intent(
        spends = listOf(Intent.Spend(account.asset, sourceAmount)),
        to = null,
        source = Intent.Source.Wallet,
    )
}

/** A route that could not be prepared, told as [confirmationError]. */
private class SwapRouteError(
    override val confirmationError: ConfirmationError,
    cause: Throwable,
) : Exception(cause.message, cause), ConfirmationErrorCause

/** A swap takes the amount it sells of the account's asset. */
internal val SwapSpending = OperationSpending { request, _ ->
    (request.type as ConfirmType.SwapOperation).swap.sourceAmount
}

/**
 * Reports the swap payload as the swap it is, the approve ahead of it as itself, and keeps the hash the
 * swap went out under for its status.
 */
internal class SwapOperationTracker : OperationTracker {

    @Volatile
    var swapHash: String? = null
        private set

    override suspend fun track(
        request: ConfirmRequest,
        account: Account,
        operation: SignedOperation,
        send: suspend () -> List<SentPayload>,
    ): List<SentPayload> {
        val sent = send()
        val swapId = (request.type as ConfirmType.SwapOperation).operation.payloads.lastOrNull()?.id
        sent.firstOrNull { it.payload.payloadId == swapId }?.let { swapHash = it.txHash }
        return sent
    }

    // The transaction the relayer names for the swap: the whole of it, or for a route sent in parts, the one
    // the relayer reports.
    override suspend fun trackRelayed(request: ConfirmRequest, account: Account, send: suspend () -> String?): String? {
        val txHash = try {
            send()
        } catch (e: Throwable) {
            // An outcome not known is tracked under the transaction the relayer named, where it named one.
            e.relayedHash?.let { swapHash = it }
            throw e
        }
        swapHash = txHash
        return txHash
    }

    override fun pendingSwap(request: ConfirmRequest, payload: Payload): PendingSwap? {
        val type = request.type as ConfirmType.SwapOperation
        if (payload.id != type.operation.payloads.lastOrNull()?.id) {
            return null
        }

        val quote = type.swap.quote
        return PendingSwap(
            destinationAssetId = type.swap.destinationAssetId,
            service = quote?.provider,
            routeId = quote?.routeId,
            serviceTxId = quote?.providerTxId,
            sellBaseAmount = type.swap.sourceAmount,
        )
    }
}
