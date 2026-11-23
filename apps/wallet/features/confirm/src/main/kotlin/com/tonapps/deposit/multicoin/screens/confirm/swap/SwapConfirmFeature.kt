package com.tonapps.deposit.multicoin.screens.confirm

import android.os.SystemClock
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.async.Async
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.api.models.ChainError
import com.tonapps.chainkit.core.chain.api.models.ChainRes
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.transaction.GasReservePolicy
import com.tonapps.deposit.multicoin.screens.confirm.base.BaseTransactionConfirmFeature
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.deposit.multicoin.screens.confirm.base.PendingTransaction
import com.tonapps.deposit.multicoin.screens.confirm.base.Prerequisite
import com.tonapps.deposit.multicoin.screens.confirm.base.QuoteExpiredException
import com.tonapps.deposit.multicoin.screens.confirm.base.SendingState
import com.tonapps.deposit.multicoin.screens.confirm.base.SignatureGate
import com.tonapps.deposit.multicoin.screens.confirm.base.Signing
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.FindingSeverity
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.severity
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeBuilder
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeLogic
import com.tonapps.deposit.multicoin.screens.confirm.fee.GaslessSender
import com.tonapps.core.flags.BootFlags
import com.tonapps.log.L
import com.tonapps.mvi.flow.mapLatestCatching
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.SigningWallet
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.ledger.signing.LedgerSigningInteractor
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.legacy.LegacySwapAccounts
import com.tonapps.wallet.data.multichain.legacy.LegacySwapWallet
import com.tonapps.wallet.data.multichain.tx.PostTransactionRefreshSchedule
import com.tonapps.wallet.data.multichain.exchange.PreparedSwapOperation
import com.tonapps.wallet.data.multichain.exchange.SwapQuote
import com.tonapps.wallet.data.multichain.exchange.SwapRouteRefusedException
import com.tonapps.wallet.data.multichain.exchange.toSwapRoute
import com.tonapps.wallet.data.multichain.exchange.SwapRepository
import com.tonapps.wallet.data.multichain.exchange.SwapRoute
import com.tonapps.wallet.data.multichain.exchange.SwapSlippage
import com.tonapps.wallet.data.multichain.exchange.SwapWalletScreenedException
import com.tonapps.wallet.data.multichain.exchange.status.SentSwap
import com.tonapps.wallet.data.multichain.exchange.status.SwapStatusTracker
import com.tonapps.wallet.data.passcode.PasscodeReason
import com.tonapps.wallet.data.passcode.WalletUnlocker
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal const val SWAP_QUOTE_INTERVAL_MS = 45_000L
private const val SWAP_QUOTE_TICK_MS = 250L

// What drives a swap re-preparation: the inactivity tick, a manual retry, and the chosen slippage.
private data class SwapContext(
    val tick: Int,
    val refresh: String?,
    val slippageBps: Int?,
)

private data class PreparedRoute(
    val quote: SwapQuote,
    val route: SwapRoute,
)

/**
 * Confirm feature for swaps. Adds the quote refresh timer and slippage selection on top of
 * [com.tonapps.deposit.multicoin.screens.confirm.base.BaseTransactionConfirmFeature]; an EVM approve is sent as its own transaction ahead of the swap.
 */
class SwapConfirmFeature(
    request: ConfirmRequest,
    provider: ChainKitProvider,
    accountRepo: McAccountRepository,
    postTransactionRefreshSchedule: PostTransactionRefreshSchedule,
    private val swapRepo: SwapRepository,
    walletUnlocker: WalletUnlocker,
    signatureGate: SignatureGate,
    confirmResults: ConfirmResultListener,
    feeBuilder: TxFeeBuilder,
    gaslessSender: GaslessSender,
    settingsRepository: SettingsRepository,
    ledgerSigner: LedgerSigningInteractor,
    private val legacyAccounts: LegacySwapAccounts,
    private val statusTracker: SwapStatusTracker,
) : HeadlessSwapSource, BaseTransactionConfirmFeature(
    request = request,
    provider = provider,
    accountRepo = accountRepo,
    postTransactionRefreshSchedule = postTransactionRefreshSchedule,
    walletUnlocker = walletUnlocker,
    signatureGate = signatureGate,
    confirmResults = confirmResults,
    feeBuilder = feeBuilder,
    gaslessSender = gaslessSender,
    settingsRepository = settingsRepository,
    ledgerSigner = ledgerSigner,
) {

    // A legacy wallet the backend never registered: it swaps on its own TON and TRON addresses, signs
    // with their keys and asks the swap API without a wallet id. Null for a multichain wallet.
    private val legacyWallet = bgScope.async(start = CoroutineStart.LAZY) {
        legacyAccounts.find(request.data.walletId)
    }

    private val swapQuoteTick = MutableStateFlow(0)
    private val headless = MutableStateFlow(false)
    val swapQuoteCountdown = MutableStateFlow(0f)

    // Slippage options for the swap's source chain; null while loading or for chains without config.
    val slippage: StateFlow<SwapSlippage?> = flow {
        emit(loadSlippage())
    }.cacheState(initialValue = null)

    // Selected slippage in bps, seeded from the swap screen and mutable here; changing it re-quotes.
    val selectedSlippageBps = MutableStateFlow((request.type as? ConfirmType.Swap)?.slippageBps)

    // A `/v2` max sale is trimmed to its fee as it is priced where the route lets it be: nothing is quoted again.
    override val maxFit: StateFlow<SwapMaxFit?> = MutableStateFlow(null)

    override val hasSendData: Boolean
        get() = sendData.value?.takeUnless { it.staleBeforeSend } != null

    override val unsignedSend: PendingTransaction?
        get() = sendData.value?.takeIf { it.signed.isEmpty() && it.hash == null }?.tx

    override val prepared: Flow<PendingTransaction?>
        get() = pendingTx

    override val pendingTx = combine(refreshToken, swapQuoteTick, selectedSlippageBps)
    { refresh, tick, slippageBps -> SwapContext(tick = tick, refresh = refresh, slippageBps = slippageBps) }
        .mapLatestCatching(
            onError = { onPrepareFailed(it) },
            onFinally = { pendingLoader.tryEmit(false) },
        ) { ctx ->
            pendingLoader.tryEmit(true)
            val swap = request.type as ConfirmType.Swap
            if (headless.value) {
                prepareSwap(swap, ctx)
            } else {
                prepareTracked { prepareSwap(swap, ctx) }
            }
        }
        .keepLastPrepared()
        .quoteSelectedFee()

    init {
        mainScope.launch {
            combine(sendingState, confirmationError, sendData, headless) { sending, error, data, isHeadless ->
                sending == SendingState.None && error == null && data == null && !isHeadless
            }
                .distinctUntilChanged()
                .collectLatest { active ->
                    if (active) {
                        runSwapQuoteTimer()
                    }
                }
        }
    }

    override fun runHeadless() {
        headless.value = true
    }

    fun selectSlippage(bps: Int) {
        if (txLocked) {
            return
        }
        selectedSlippageBps.tryEmit(bps)
    }

    // The swap screen underneath closes itself and opens History on this signal.
    override fun onSendSucceeded() {
        if (trackedSwap() == null) {
            swapRepo.notifySwapCompleted(tracked = false)
        }
    }

    override fun onSent() {
        val swap = trackedSwap() ?: return
        statusTracker.track(swap)
        swapRepo.notifySwapCompleted(tracked = true)
    }

    private fun trackedSwap(): SentSwap? {
        val data = sendData.value ?: return null
        val walletId = reportedWalletId(data.tx) ?: return null
        if (!(request.type as ConfirmType.Swap).oneClick || !request.action.withSend) {
            return null
        }
        return SentSwap(
            walletId = walletId,
            txHash = data.hash,
            sell = data.tx.account.asset,
            sendAmount = data.tx.quote?.sourceBaseAmount ?: (request.type as ConfirmType.Swap).sourceAmount,
            buy = data.tx.destination?.asset ?: return null,
            receiveAmount = data.tx.quote?.buyBaseAmount ?: return null,
        )
    }

    override suspend fun unlockSigner(pendingTx: PendingTransaction): SigningWallet {
        val legacy = legacyWallet.await()
        val signer = if (legacy == null) {
            super.unlockSigner(pendingTx)
        } else {
            val confirmed = walletUnlocker.confirm(PasscodeReason.CONFIRM_SWAP)
            if (!confirmed) {
                throw CancellationException("Passcode dismissed")
            }
            legacyAccounts.signingKeys(legacy)
        }
        if (headless.value && pendingTx.isRouteStale(quotedAt = SystemClock.elapsedRealtime())) {
            throw QuoteExpiredException()
        }
        return signer
    }

    override fun deviceSigningWindow(pendingTx: PendingTransaction): Duration? {
        val quote = pendingTx.quote ?: return null
        val receivedAt = quote.receivedAt ?: return null
        val age = SystemClock.elapsedRealtime() - receivedAt
        return (routeUsableMillis(quote.lifetimeMillis) - age).milliseconds
    }

    // Prepared only after the wallet resolved, so the answer is in by the time anything is sent.
    override fun reportedWalletId(pendingTx: PendingTransaction): String? {
        return when (resolvedLegacyWallet()) {
            null -> super.reportedWalletId(pendingTx)
            else -> null
        }
    }

    override fun notifyTransactionSent(pendingTx: PendingTransaction) {
        val legacy = resolvedLegacyWallet() ?: return super.notifyTransactionSent(pendingTx)
        legacyAccounts.onTransactionSent(legacy)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun resolvedLegacyWallet(): LegacySwapWallet? {
        return legacyWallet.takeIf { it.isCompleted }?.getCompleted()
    }

    private suspend fun registeredWalletId(): String? {
        return request.data.walletId.takeIf { legacyWallet.await() == null }
    }

    private suspend fun findAccount(assetId: String, forceRefresh: Boolean = false): AccountWithDetails? {
        val legacy = legacyWallet.await()
            ?: return accountRepo.findAccount(request.data.walletId, assetId, forceRefresh = forceRefresh)
        return legacyAccounts.findAccount(legacy, assetId, refresh = forceRefresh)
    }

    private suspend fun runSwapQuoteTimer() {
        val totalTicks = (SWAP_QUOTE_INTERVAL_MS / SWAP_QUOTE_TICK_MS).toInt()
        while (!txLocked) {
            for (i in 0..totalTicks) {
                swapQuoteCountdown.tryEmit(1f - i.toFloat() / totalTicks)

                if (i < totalTicks) {
                    delay(SWAP_QUOTE_TICK_MS)
                }
            }

            if (txLocked) {
                return
            }

            swapQuoteTick.value += 1
        }
    }

    // The slippage config is served per registered wallet; a legacy swap quotes with the server default.
    private suspend fun loadSlippage(): SwapSlippage? = withContext(Async.Io) {
        val walletId = registeredWalletId() ?: return@withContext null
        val account = accountRepo.findAccount(walletId, request.data.assetId)
        account?.let { swapRepo.getSlippage(walletId, it.asset.value.chain) }
    }

    private suspend fun prepareSwap(trade: ConfirmType.Swap, ctx: SwapContext): PendingTransaction? {
        val walletId = request.data.walletId
        val wallet = legacyWallet.await()?.toMcWallet()
            ?: accountRepo.getWallet(walletId)
            ?: throw IllegalStateException("Can't find wallet by id")
        val account = findAccount(request.data.assetId, forceRefresh = true) ?: throw IllegalStateException("Can't find account")
        val destination = findAccount(trade.destinationAssetId) ?: throw IllegalStateException("Can't find destination account")

        val asset = account.asset.value
        val energy = when (asset) {
            is Asset.Coin -> asset
            is Asset.Token, is Asset.Battery -> asset.chain.toAsset()
        }

        val energyAccount = findAccount(energy.id) ?: throw IllegalStateException("Can't find energy account by id")

        val prepared = loadRoute(trade, ctx, account, destination, asset.chain) ?: return null
        val route = prepared.route
        val buyBaseAmount = route.buyBaseAmount ?: prepared.quote.buyBaseAmount
        val quote = prepared.quote.copy(
            providerTxId = route.providerRouteId,
            buyBaseAmount = buyBaseAmount,
            minimumBuyBaseAmount = minOf(route.minimumBuyBaseAmount ?: prepared.quote.minimumBuyBaseAmountAt(buyBaseAmount), buyBaseAmount),
            priceImpactBps = prepared.quote.priceImpactBpsAt(buyBaseAmount),
        )
        val messages = route.mainMessages
        val batched = messages.size > 1

        val operation = route.toOperation(asset.chain)
        val swap = swapTransactions(
            operation = operation,
            messages = messages,
            account = account.value,
            destination = destination.value,
            isMax = trade.isMax == true && !batched,
        )
        val approvals = swap.approvals
        val transactions = swap.messages
        val transaction = transactions.first()
        val messagesAmount = transactions.fold(BigInteger.ZERO) { total, tx -> total + tx.amount }
        val attachedEnergy = transactions.fold(BigInteger.ZERO) { total, tx -> total + tx.attachedEnergy() }

        val payloadFee = operation.payloads.last().fee
        val (feeResult, nonceResult) = withContext(Async.Io) {
            val feeTask = when {
                payloadFee != null -> null
                approvals.isEmpty() -> async { provider.estimateBatchFee(transactions.map { it.asEnergyMessage(energy) }) }
                // The swap spends the allowance its approve has yet to grant: a simulation of it alone reverts.
                else -> async { provider.defaultFee(transaction) }
            }
            val nonceTask = async { provider.getNonce(account.value) }
            feeTask?.await() to nonceTask.await()
        }

        val prerequisitesResult = prerequisites(operation, approvals)
        val prerequisites = prerequisitesResult.value.orEmpty()

        // Use provider Fee or calculate on our own
        val feeError = (feeResult?.error ?: prerequisitesResult.error)
            ?.also { L.e(it) }
            ?.let(::parseChainError)
        val mainFee = payloadFee ?: feeResult?.value
        val fee = mainFee?.withPrerequisites(prerequisites)

        // Calculate Nonce
        val nonceError = nonceResult.error
            ?.also { L.e(it) }
            ?.let(::parseNodeError)

        // On `exact` the sell amount is baked into the provider calldata, so trimming it
        // here desyncs `value` from the calldata and the router reverts. Check the balance,
        // but never shrink: report InsufficientBalance instead.
        val exactCalldata = messages.first().isExactCalldata
        val fixedAmount = exactCalldata || batched

        val reservationResult = reserveNativeGas(
            account = transaction.account,
            energy = energyAccount.value,
            amount = messagesAmount,
            isMax = transaction.isMax && !fixedAmount,
            fee = fee?.withAttachedEnergy(attachedEnergy),
            policy = if (fixedAmount) {
                GasReservePolicy.DrainOrError
            } else {
                GasReservePolicy.ShrinkToFit
            },
        )

        val reservationError = reservationResult.error
            ?.also { L.e(it) }
            ?.let(::parseNodeError)
            ?: reservationResult.value?.error
                ?.let { parseReservationError(it) }

        // A token source spends its typed amount; the signed amount is the attached coin. A coin source
        // pays its messages out of the same balance, and a TON route attaches forward gas to every part
        // on top of the amount typed: no fee method covers that.
        val spentAmount = swapSpentAmount(
            sellsCoin = asset is Asset.Coin,
            messagesAmount = messagesAmount,
            sourceAmount = trade.sourceAmount,
        )
        val balanceError = ConfirmationError.InsufficientBalance
            .takeIf { (spentAmount ?: trade.sourceAmount) > account.unitBalance.value }

        val feeState = feeBuilder.buildSwap(
            account = account,
            energy = energyAccount,
            walletId = walletId,
            transactions = transactions,
            approvals = approvals,
            estimated = fee,
            attachedEnergy = attachedEnergy,
            shrinkToFit = transaction.isMax && !fixedAmount,
        )

        val batteryPayable = feeState.hasBatteryOption

        val shrunkTransaction = if (fixedAmount) {
            transaction
        } else {
            reservationResult.getOrNull()
                ?.let {
                    val isMax = transaction.isMax || it.isAmountAdjusted
                    transaction.copy(amount = it.amount, isMax = isMax)
                }
                ?: transaction
        }
        val adjustedTransaction = if (batteryPayable) {
            transaction
        } else {
            shrunkTransaction
        }

        val chainKitError = feeError ?: nonceError ?: reservationError.takeUnless { batteryPayable }

        confirmationError.tryEmit(balanceError ?: feeConfirmationError(feeState, chainKitError, nonceError))
        L.d("Nonce: ${nonceResult.value}")

        val signing = if (batched) {
            Signing.Tx(value = transaction.copy(isMax = trade.isMax == true), batchToSign = transactions)
        } else {
            Signing.Tx(adjustedTransaction, walletPaid = shrunkTransaction.takeIf { batteryPayable })
        }

        return PendingTransaction(
            request = request,
            wallet = wallet,
            account = account,
            fee = feeState.copy(error = chainKitError),
            nonce = nonceResult.value,
            signing = signing,
            spentAmount = spentAmount,
            destination = destination,
            prerequisites = prerequisites,
            signingFee = mainFee.takeIf { prerequisites.isNotEmpty() },
            quote = quote,
            rateLabel = swapRepo.formatRateLabel(quote, asset, destination.asset.value),
        )
    }

    // Each its own transaction ahead of the swap: the route's fee where it quotes one.
    private suspend fun prerequisites(operation: Operation, approvals: List<Transaction>): ChainRes<List<Prerequisite>> {
        val items = approvals.mapIndexed { index, tx ->
            val fee = operation.payloads[index].fee ?: run {
                val estimated = withContext(Async.Io) { provider.estimateFee(tx) }
                estimated.value ?: return ChainRes.err(estimated.error ?: ChainError.InternalError("No fee for $tx"))
            }
            Prerequisite(tx, fee)
        }
        return ChainRes.ok(items)
    }

    private suspend fun loadRoute(
        trade: ConfirmType.Swap,
        ctx: SwapContext,
        account: AccountWithDetails,
        destination: AccountWithDetails,
        chain: Chain,
    ): PreparedRoute? {
        return try {
            val quote = snapshotQuote(trade, ctx) ?: fetchQuote(trade, ctx, account, destination)
            PreparedRoute(quote = quote, route = prepareRoute(quote, account, chain))
        } catch (e: CancellationException) {
            throw e
        } catch (e: SwapWalletScreenedException) {
            L.e(e)
            confirmationError.tryEmit(routeError(e))
            null
        } catch (e: Throwable) {
            L.e(e)
            retryRoute(trade, ctx, account, destination, chain)
        }
    }

    private suspend fun retryRoute(
        trade: ConfirmType.Swap,
        ctx: SwapContext,
        account: AccountWithDetails,
        destination: AccountWithDetails,
        chain: Chain,
    ): PreparedRoute? {
        return try {
            val quote = fetchQuote(trade, ctx, account, destination)
            PreparedRoute(quote = quote, route = prepareRoute(quote, account, chain))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            L.e(e)
            confirmationError.tryEmit(routeError(e))
            null
        }
    }

    private fun routeError(e: Throwable): ConfirmationError {
        return when (e) {
            is IOException -> ConfirmationError.NetworkError
            is SwapWalletScreenedException -> ConfirmationError.WalletScreened
            is SwapRouteRefusedException -> ConfirmationError.UnsupportedTransaction
            else -> ConfirmationError.Unknown
        }
    }

    private fun snapshotQuote(trade: ConfirmType.Swap, ctx: SwapContext): SwapQuote? {
        return trade.takeIf { ctx.tick == 0 && ctx.refresh == null && ctx.slippageBps == trade.slippageBps }?.snapshotQuote()
    }

    private suspend fun fetchQuote(
        trade: ConfirmType.Swap,
        ctx: SwapContext,
        account: AccountWithDetails,
        destination: AccountWithDetails,
    ): SwapQuote {
        return swapRepo.fetchConfirmQuote(
            sell = account,
            buy = destination,
            amount = trade.sourceAmount,
            slippageBps = ctx.slippageBps,
            walletId = registeredWalletId(),
            genericPayload = trade.quote?.genericPayload == true,
        )
    }

    private suspend fun prepareRoute(quote: SwapQuote, account: AccountWithDetails, chain: Chain): SwapRoute {
        val walletId = registeredWalletId()
        if (!quote.genericPayload) {
            return swapRepo.prepareRoute(quote, chain, walletId)
        }

        val prepared = swapRepo.prepareOperation(quote, requireNotNull(walletId) { "Only a registered wallet swaps on /v3" })
        requireSafe(prepared, account, quote.sourceBaseAmount)
        val priced = prepared.copy(operation = prepared.operation.pricedOnNetwork(account.value))
        return priced.toSwapRoute(account.value, quote.sourceBaseAmount)
    }

    private suspend fun Operation.pricedOnNetwork(account: Account): Operation {
        if (!account.chain.isEvm) {
            return this
        }
        val priced = coroutineScope {
            payloads.map { payload -> async { payload.pricedOnNetwork(account) } }.awaitAll()
        }
        return copy(payloads = priced)
    }

    private suspend fun Payload.pricedOnNetwork(account: Account): Payload {
        val quotedLimit = when (val quoted = fee) {
            is Fee.Gas -> quoted.limit
            is Fee.Eip1559 -> quoted.limit
            else -> return this
        }
        val call = meta as? PayloadMeta.Evm.Main ?: return this
        val unpriced = copy(fee = null, meta = call.copy(gas = call.gas ?: quotedLimit))
        val networkFee = withContext(Async.Io) { provider.estimateFee(unpriced, account) }.getOrNull() ?: return this
        return copy(fee = networkFee)
    }

    private suspend fun requireSafe(prepared: PreparedSwapOperation, account: AccountWithDetails, sourceAmount: BigInteger) {
        if (!BootFlags.isTxValidationEnabled) {
            return
        }

        val intent = swapIntent(account.value, sourceAmount)
        val refused = provider.validateOperation(prepared.operation, account.value, intent)
            .findings
            .firstOrNull { it.severity == FindingSeverity.Danger }
        if (refused != null) {
            throw SwapRouteRefusedException("The route does not hold to the swap: ${refused.message}")
        }
    }
}

private val Transaction.Swap.attachesEnergy: Boolean
    get() = account.chain.isTvm && account.asset is Asset.Token && !data.isNullOrBlank()

private fun Transaction.Swap.attachedEnergy(): BigInteger {
    return if (attachesEnergy) {
        amount
    } else {
        BigInteger.ZERO
    }
}

private fun Transaction.Swap.asEnergyMessage(energy: Asset): Transaction.Swap {
    if (!attachesEnergy) {
        return this
    }
    return copy(account = account.copy(asset = energy), isMax = false)
}

private fun Fee.withAttachedEnergy(attachedEnergy: BigInteger): Fee {
    if (!attachedEnergy.isPositive) {
        return this
    }
    return Fee.Value(asset = asset, amount = TxFeeLogic.requiredEnergy(this, attachedEnergy))
}

private fun Fee.withPrerequisites(prerequisites: List<Prerequisite>): Fee {
    if (prerequisites.isEmpty()) {
        return this
    }
    return Fee.Value(asset = asset, amount = prerequisites.fold(amount) { total, item -> total + item.fee.amount })
}
