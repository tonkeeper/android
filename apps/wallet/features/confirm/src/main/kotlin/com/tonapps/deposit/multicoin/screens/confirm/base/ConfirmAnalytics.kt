package com.tonapps.deposit.multicoin.screens.confirm

import com.tonapps.blockchain.model.ConfirmContext
import com.tonapps.blockchain.model.ConfirmInitiator
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.core.contract.RedAttemptSource
import com.tonapps.bus.core.contract.RedMetadata
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.RedOperations.RedOperationsFlow
import com.tonapps.bus.generated.Events.RedOperations.RedOperationsOperation
import com.tonapps.bus.generated.Events.RedOperations.RedOperationsOutcome
import com.tonapps.bus.generated.Events.SendNative.SendNativeFrom
import com.tonapps.bus.generated.Events.SwapsNative.SwapsNativeFeeAsset
import com.tonapps.bus.generated.Events.SwapsNative.SwapsNativeType
import com.tonapps.bus.generated.Events.SwapsNative.SwapsNativeWalletMode
import com.tonapps.bus.generated.Events.TransactionSent.TransactionSentCategory
import com.tonapps.bus.generated.Events.TransactionSent.TransactionSentCategoryDetail
import com.tonapps.bus.generated.Events.TransactionSent.TransactionSentFeeAsset
import com.tonapps.bus.generated.Events.TransactionSent.TransactionSentInitiatedBy
import com.tonapps.bus.generated.Events.TransactionSent.TransactionSentLedgerModel
import com.tonapps.bus.generated.Events.TransactionSent.TransactionSentWalletInterface
import com.tonapps.bus.generated.Events.TransactionSent.TransactionSentWalletMode
import com.tonapps.bus.generated.Events.TransactionSent.TransactionSentWalletSource
import com.tonapps.bus.generated.opTerminal
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.TokenType
import com.tonapps.chainkit.core.chain.model.num.toDisplayUnit
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.core.helper.SendRedMetadata
import com.tonapps.core.helper.TransactionSentAnalytics
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.deposit.multicoin.screens.confirm.base.PendingTransaction
import com.tonapps.deposit.multicoin.screens.confirm.base.analyticsKey
import com.tonapps.deposit.multicoin.screens.confirm.base.isValidation
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFee
import com.tonapps.extensions.currentTimeMillis
import com.tonapps.extensions.generateUuid
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.wallet.data.ledger.analytics.ledgerFailure
import com.tonapps.wallet.data.ledger.analytics.statusWord
import com.tonapps.wallet.data.ledger.analytics.toFailureType
import com.tonapps.wallet.data.multichain.exchange.SwapQuote
import kotlin.coroutines.cancellation.CancellationException

private const val STAGE_VALIDATION = "validation"
private const val STAGE_EMULATE = "emulate"
private const val STAGE_SEND = "send"
private const val STAGE_QUOTE = "quote"

@PublishedApi
internal const val ERROR_TYPE_REQUEST_GONE = "request_gone"

/**
 * A single RED operation: owns its id and start time, emits `op_attempt` on creation and exactly one
 * `op_terminal` afterwards, both with [otherMetadata] unless the terminal brings its own. Created
 * through [ConfirmAnalytics.redOperation].
 */
class RedOperation internal constructor(
    private val flow: RedOperationsFlow,
    private val operation: RedOperationsOperation,
    private val otherMetadata: String?,
) {

    private val operationId = generateUuid()
    private val startedAtMs = currentTimeMillis()
    private var terminated = false

    private val events: Events.RedOperations
        get() = AnalyticsHelper.Default.events.redOperations

    private val RedOperationsOperation.stage: String
        get() = when (this) {
            RedOperationsOperation.Emulate -> STAGE_EMULATE
            RedOperationsOperation.Quote -> STAGE_QUOTE
            else -> STAGE_SEND
        }

    private val ConfirmationError.message: String?
        get() = when (this) {
            is ConfirmationError.NodeRejected -> message
            is ConfirmationError.RelayerRejected -> message
            else -> null
        }

    /**
     * An operation given up before it could fail or succeed, told apart from a user cancel by
     * [errorType]: a quote that changed under a send, say, which the screen offers anew.
     */
    fun cancel(errorType: String? = null, errorMessage: String? = null) {
        if (!terminate()) {
            return
        }
        events.opTerminal(
            operationId = operationId,
            flow = flow,
            operation = operation,
            outcome = RedOperationsOutcome.Cancel,
            durationMs = durationMs(),
            finishedAtMs = currentTimeMillis(),
            errorCode = null,
            errorMessage = errorMessage,
            errorType = errorType,
            stage = operation.stage,
            otherMetadata = otherMetadata,
        )
    }

    internal fun attempt(attemptSource: String?) {
        events.opAttempt(
            operationId = operationId,
            flow = flow,
            operation = operation,
            attemptSource = attemptSource,
            startedAtMs = startedAtMs,
            otherMetadata = otherMetadata,
        )
    }

    fun success(otherMetadata: String? = this.otherMetadata) {
        if (!terminate()) {
            return
        }
        events.opTerminal(
            operationId = operationId,
            flow = flow,
            operation = operation,
            durationMs = durationMs(),
            finishedAtMs = currentTimeMillis(),
            error = null,
            otherMetadata = otherMetadata,
        )
    }

    fun failure(error: Throwable) {
        if (!terminate()) {
            return
        }
        val ledgerFailure = error.ledgerFailure
        if (ledgerFailure != null) {
            events.opTerminal(
                operationId = operationId,
                flow = flow,
                operation = operation,
                outcome = if (ledgerFailure == LedgerFailure.Cancelled || ledgerFailure is LedgerFailure.UserRefused) {
                    RedOperationsOutcome.Cancel
                } else {
                    RedOperationsOutcome.Fail
                },
                durationMs = durationMs(),
                finishedAtMs = currentTimeMillis(),
                errorCode = ledgerFailure.statusWord,
                errorMessage = null,
                errorType = ledgerFailure.toFailureType()?.let { LEDGER_ERROR_PREFIX + it.key },
                stage = null,
                otherMetadata = null,
            )
            return
        }
        events.opTerminal(
            operationId = operationId,
            flow = flow,
            operation = operation,
            durationMs = durationMs(),
            finishedAtMs = currentTimeMillis(),
            error = error,
            otherMetadata = otherMetadata,
        )
    }

    /**
     * A failure the screen shows: a user-state error is a `validation` stage failure, any other one
     * failed at the operation's own stage. [cause] is what threw it, for the message of an error
     * that carries none of its own.
     */
    fun failure(error: ConfirmationError, otherMetadata: String? = this.otherMetadata, cause: Throwable? = null) {
        if (!terminate()) {
            return
        }
        events.opTerminal(
            operationId = operationId,
            flow = flow,
            operation = operation,
            outcome = RedOperationsOutcome.Fail,
            durationMs = durationMs(),
            finishedAtMs = currentTimeMillis(),
            errorCode = null,
            errorMessage = error.message?.takeIf { it.isNotBlank() } ?: cause?.message,
            errorType = error.analyticsKey,
            stage = if (error.isValidation) {
                STAGE_VALIDATION
            } else {
                operation.stage
            },
            otherMetadata = otherMetadata,
        )
    }

    private fun durationMs(): Double = (currentTimeMillis() - startedAtMs).toDouble()

    private fun terminate(): Boolean {
        if (terminated) {
            return false
        }
        terminated = true
        return true
    }

    private companion object {
        const val LEDGER_ERROR_PREFIX = "ledger_"
    }
}

/**
 * Every analytics event the multichain confirm screens emit. Keeps [com.tonapps.deposit.multicoin.screens.confirm.base.BaseTransactionConfirmFeature]
 * free of event payload mapping and of the RED operation bookkeeping.
 */
class ConfirmAnalytics(private val request: ConfirmRequest) {

    private val events = AnalyticsHelper.Default.eventsTaggedWith(request.utm)

    private val flow: RedOperationsFlow
        get() = when {
            request.type is ConfirmType.Swap || request.type is ConfirmType.SwapOperation -> RedOperationsFlow.Swap
            request.context is ConfirmContext.Dapp -> RedOperationsFlow.TonConnect
            else -> RedOperationsFlow.Transfer
        }

    private val swapRequest: ConfirmType.Swap?
        get() = request.swapType

    private val ConfirmRequest.swapType: ConfirmType.Swap?
        get() = type as? ConfirmType.Swap ?: (type as? ConfirmType.SwapOperation)?.swap

    val sendOperation: RedOperationsOperation
        get() = when (request.context) {
            is ConfirmContext.Dapp -> RedOperationsOperation.ConfirmTransaction
            else -> RedOperationsOperation.Send
        }

    /** `attempt_source` of a send: the user starts it in the app unless a dapp asked for it. */
    val sendAttemptSource: String?
        get() = when (request.context) {
            is ConfirmContext.Dapp -> null
            else -> RedAttemptSource.NATIVE_UI
        }

    private val PendingTransaction.providerName: String
        get() = quote?.provider?.id.orEmpty()

    @PublishedApi
    @Volatile
    internal var requestGone = false
        private set

    fun markRequestGone() {
        requestGone = true
    }

    @PublishedApi
    internal fun startRedOperation(
        operation: RedOperationsOperation,
        attemptSource: String?,
        otherMetadata: String?,
    ): RedOperation {
        return RedOperation(flow, operation, otherMetadata).also { it.attempt(attemptSource) }
    }

    /**
     * Runs [block] as a RED operation: `op_attempt` is emitted up front and an uncaught throwable is
     * reported as the terminal outcome before it propagates. [block] is responsible for calling
     * [RedOperation.success] / [RedOperation.failure] on the paths it handles itself.
     */
    inline fun <T> redOperation(
        operation: RedOperationsOperation,
        attemptSource: String? = null,
        otherMetadata: String? = null,
        block: RedOperation.() -> T,
    ): T {
        val red = startRedOperation(operation, attemptSource, otherMetadata)
        try {
            return red.block()
        } catch (e: Throwable) {
            if (requestGone && e is CancellationException) {
                red.cancel(errorType = ERROR_TYPE_REQUEST_GONE)
            } else {
                red.failure(e)
            }
            throw e
        }
    }

    /**
     * `other_metadata` of a send, with the keys of the legacy send screen. A dapp request is a
     * ton_connect confirm_transaction rather than a send, so it carries none.
     */
    fun sendMetadata(asset: Asset, amount: Double?, paidIn: Asset?): String? {
        if (request.context is ConfirmContext.Dapp) {
            return null
        }
        return SendRedMetadata.transfer(
            source = redSource(request.initiator),
            asset = asset.id,
            amount = amount,
            feePaidIn = feeAsset(paidIn).key,
            tokenSymbol = asset.symbol,
        )
    }

    fun sendMetadata(transaction: Transaction, fee: TxFee?, quote: SwapQuote?): String? {
        val asset = transaction.account.asset
        return sendMetadata(asset, displayAmount(transaction, quote), fee?.fee?.asset)
    }

    /**
     * `other_metadata` of a swap's emulate: the provider of [quote], else the one [quoted] was
     * opened with, and the assets swapped. Nothing for a request that is no swap.
     */
    fun emulateMetadata(quoted: ConfirmRequest = request, quote: SwapQuote? = null): String? {
        val swap = quoted.swapType ?: return null
        return RedMetadata.json(
            RedMetadata.PROVIDER_NAME to (quote?.provider?.id ?: swap.quote?.provider),
            RedMetadata.ASSET_FROM to quoted.data.assetId,
            RedMetadata.ASSET_TO to swap.destinationAssetId,
        )
    }

    // Cross-cutting "transaction submitted" signal, emitted in addition to the flow-specific events.
    // Only broadcast transactions qualify, so sign-only requests and message signing never reach here.
    fun transactionSent(
        transaction: Transaction,
        fee: TxFee?,
        quote: SwapQuote?,
        isHardware: Boolean,
        ledgerModel: TransactionSentLedgerModel?,
    ) {
        val asset = transaction.account.asset
        val dapp = request.context as? ConfirmContext.Dapp
        events.transactionSent.transactionSent(
            category = transactionCategory(transaction),
            categoryDetail = transactionCategoryDetail(transaction),
            asset = asset.id,
            amount = displayAmount(transaction, quote),
            feeAsset = feeAsset(fee?.fee?.asset),
            walletInterface = walletInterface(transaction.account.address.type, asset.chain),
            walletSource = if (isHardware) {
                TransactionSentWalletSource.Ledger
            } else {
                TransactionSentWalletSource.Mnemonic
            },
            walletMode = TransactionSentWalletMode.Multi,
            initiatedBy = when {
                dapp?.source == ConfirmContext.Dapp.Source.Injected -> TransactionSentInitiatedBy.EvmInjected
                dapp != null -> TransactionSentInitiatedBy.Walletconnect
                else -> TransactionSentAnalytics.initiatedBy(request.initiator)
            },
            appId = dapp?.url?.takeIf { it.isNotEmpty() },
            dappUrl = dapp?.url?.takeIf { it.isNotEmpty() },
            isMax = transaction.isMax,
            toAsset = (transaction as? Transaction.Swap)?.destination?.asset?.id,
            stakingProvider = (transaction as? Transaction.Staking)?.validators?.firstOrNull()?.id,
            isLiquid = if (transaction is Transaction.Staking) {
                transaction is Transaction.Staking.Liquid
            } else {
                null
            },
            ledgerModel = ledgerModel,
        )
    }

    // Swap funnel counterpart of swap_open/swap_click on the swap screen. Only the send phase counts:
    // quote and emulation failures before the user confirms are covered by the RED operation events.
    fun swapConfirm(pendingTx: PendingTransaction) {
        val swap = swapRequest ?: return
        events.swapsNative.swapConfirm(
            type = SwapsNativeType.Native,
            walletMode = SwapsNativeWalletMode.Multi,
            assetFrom = request.data.assetId,
            assetTo = swap.destinationAssetId,
            feeAsset = SwapsNativeFeeAsset.Coin,
            providerName = pendingTx.providerName,
            isMax = swap.isMax,
        )
    }

    fun swapSuccess(pendingTx: PendingTransaction) {
        val swap = swapRequest ?: return
        events.swapsNative.swapSuccess(
            type = SwapsNativeType.Native,
            walletMode = SwapsNativeWalletMode.Multi,
            assetFrom = request.data.assetId,
            assetTo = swap.destinationAssetId,
            feeAsset = SwapsNativeFeeAsset.Coin,
            providerName = pendingTx.providerName,
            isMax = swap.isMax,
        )
    }

    fun swapFailed(
        pendingTx: PendingTransaction,
        error: ConfirmationError,
        cause: Throwable? = null,
    ) {
        val swap = swapRequest ?: return
        events.swapsNative.swapFailed(
            type = SwapsNativeType.Native,
            walletMode = SwapsNativeWalletMode.Multi,
            assetFrom = request.data.assetId,
            assetTo = swap.destinationAssetId,
            feeAsset = SwapsNativeFeeAsset.Coin,
            providerName = pendingTx.providerName,
            errorMessage = cause?.message
                ?: (error as? ConfirmationError.NodeRejected)?.message
                ?: error.analyticsKey,
            isMax = swap.isMax,
        )
    }

    private fun displayAmount(transaction: Transaction, quote: SwapQuote?): Double {
        val sentAmount = (transaction as? Transaction.Swap)?.let { quote?.sourceBaseAmount } ?: transaction.amount
        return transaction.account.asset.toDisplayUnit(sentAmount).value.doubleValue(exactRequired = false)
    }

    // The legacy send screen tells the wallet and the jetton screen apart; a confirm request only
    // knows that the user started it.
    private fun redSource(initiator: ConfirmInitiator): String? {
        return when (initiator) {
            ConfirmInitiator.User -> null
            ConfirmInitiator.DeepLink -> SendNativeFrom.DeepLink.key
            ConfirmInitiator.QrCode -> SendNativeFrom.QrCode.key
        }
    }

    private fun feeAsset(feeAsset: Asset?): TransactionSentFeeAsset {
        return when (feeAsset) {
            null, is Asset.Coin -> TransactionSentFeeAsset.Coin
            is Asset.Battery -> TransactionSentFeeAsset.BatteryCharges
            is Asset.Token -> TransactionSentFeeAsset.Gasless
        }
    }

    private fun transactionCategory(transaction: Transaction): TransactionSentCategory {
        return when (transaction) {
            is Transaction.Transfer -> TransactionSentCategory.Transfer
            is Transaction.Swap -> TransactionSentCategory.Swap
            is Transaction.Call -> TransactionSentCategory.Call
            is Transaction.Staking -> TransactionSentCategory.Staking
        }
    }

    private fun transactionCategoryDetail(transaction: Transaction): TransactionSentCategoryDetail {
        return when (transaction) {
            is Transaction.Transfer -> when (val asset = transaction.account.asset) {
                is Asset.Coin -> TransactionSentCategoryDetail.Coin
                is Asset.Token -> when (asset.type) {
                    TokenType.Erc721, TokenType.Erc1155 -> TransactionSentCategoryDetail.Nft
                    else -> TransactionSentCategoryDetail.Token
                }
                is Asset.Battery -> TransactionSentCategoryDetail.Unknown
            }

            is Transaction.Swap -> if (transaction.destination.asset.chain == transaction.account.asset.chain) {
                TransactionSentCategoryDetail.Onchain
            } else {
                TransactionSentCategoryDetail.CrossChain
            }

            // A dapp call carries no hint about what the contract does.
            is Transaction.Call -> TransactionSentCategoryDetail.Unknown

            is Transaction.Staking.Stake -> TransactionSentCategoryDetail.Stake
            is Transaction.Staking.Unstake -> TransactionSentCategoryDetail.Unstake
            is Transaction.Staking.Claim -> TransactionSentCategoryDetail.Claim
            is Transaction.Staking.Restake -> TransactionSentCategoryDetail.Restake
            is Transaction.Staking.Compound -> TransactionSentCategoryDetail.Compound
        }
    }

    // Wallets created before the multi-variant change carry Address.Type.Default, so fall back to the
    // variant multichain registers for the chain.
    private fun walletInterface(type: Address.Type, chain: Chain): TransactionSentWalletInterface {
        return when (type) {
            Address.Type.TonV4R2 -> TransactionSentWalletInterface.V4R2
            Address.Type.TonV5R1 -> TransactionSentWalletInterface.V5R1
            Address.Type.BtcSegwit -> TransactionSentWalletInterface.Segwit
            Address.Type.BtcSTaproot -> TransactionSentWalletInterface.Taproot
            Address.Type.Default -> when (chain) {
                is Chain.Ton -> TransactionSentWalletInterface.V5R1
                is Chain.Bitcoin -> TransactionSentWalletInterface.Segwit
                else -> TransactionSentWalletInterface.Eoa
            }
        }
    }
}
