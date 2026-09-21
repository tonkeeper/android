package com.tonapps.deposit.multicoin.screens.confirm

import com.tonapps.blockchain.model.ConfirmContext
import com.tonapps.blockchain.model.ConfirmInitiator
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.RedOperations.RedOperationsFlow
import com.tonapps.bus.generated.Events.RedOperations.RedOperationsOperation
import com.tonapps.bus.generated.Events.RedOperations.RedOperationsOutcome
import com.tonapps.bus.generated.Events.SwapsNative.SwapsNativeFeeAsset
import com.tonapps.bus.generated.Events.SwapsNative.SwapsNativeType
import com.tonapps.bus.generated.Events.SwapsNative.SwapsNativeWalletMode
import com.tonapps.bus.generated.Events.TransactionSent.TransactionSentCategory
import com.tonapps.bus.generated.Events.TransactionSent.TransactionSentCategoryDetail
import com.tonapps.bus.generated.Events.TransactionSent.TransactionSentFeeAsset
import com.tonapps.bus.generated.Events.TransactionSent.TransactionSentInitiatedBy
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
import com.tonapps.core.helper.WalletRedMetadata
import com.tonapps.deposit.multicoin.screens.confirm.engine.FeeAccount
import com.tonapps.deposit.multicoin.screens.confirm.engine.TxFee
import com.tonapps.extensions.currentTimeMillis
import com.tonapps.extensions.currentTimeSecondsInt
import com.tonapps.extensions.generateUuid

/**
 * A single RED operation: owns its id and start time, emits `op_attempt` on creation and exactly one
 * `op_terminal` afterwards. Created through [ConfirmAnalytics.redOperation].
 */
class RedOperation internal constructor(
    private val flow: RedOperationsFlow,
    private val operation: RedOperationsOperation,
) {

    private val operationId = generateUuid()
    private val startedAtMs = currentTimeMillis()
    private var terminated = false

    private val events: Events.RedOperations
        get() = AnalyticsHelper.Default.events.redOperations

    internal fun attempt() {
        events.opAttempt(
            operationId = operationId,
            flow = flow,
            operation = operation,
            attemptSource = null,
            startedAtMs = currentTimeSecondsInt(),
            otherMetadata = WalletRedMetadata.walletKit(),
        )
    }

    fun success() {
        if (!terminate()) {
            return
        }
        events.opTerminal(
            operationId = operationId,
            flow = flow,
            operation = operation,
            durationMs = durationMs(),
            finishedAtMs = currentTimeSecondsInt(),
            error = null,
            otherMetadata = WalletRedMetadata.walletKit(),
        )
    }

    fun failure(error: Throwable) {
        if (!terminate()) {
            return
        }
        events.opTerminal(
            operationId = operationId,
            flow = flow,
            operation = operation,
            durationMs = durationMs(),
            finishedAtMs = currentTimeSecondsInt(),
            error = error,
            otherMetadata = WalletRedMetadata.walletKit(),
        )
    }

    fun failure(error: ConfirmationError) {
        if (!terminate()) {
            return
        }
        events.opTerminal(
            operationId = operationId,
            flow = flow,
            operation = operation,
            outcome = RedOperationsOutcome.Fail,
            durationMs = durationMs(),
            finishedAtMs = currentTimeSecondsInt(),
            errorCode = null,
            errorMessage = null,
            errorType = error::class.simpleName,
            stage = null,
            otherMetadata = WalletRedMetadata.walletKit(),
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
}

/**
 * Every analytics event the multichain confirm screens emit. Keeps [BaseConfirmFeature] free of event
 * payload mapping and of the RED operation bookkeeping.
 */
class ConfirmAnalytics(private val request: ConfirmRequest) {

    private val flow: RedOperationsFlow
        get() = when {
            request.type is ConfirmType.Swap -> RedOperationsFlow.Swap
            request.context is ConfirmContext.Dapp -> RedOperationsFlow.TonConnect
            else -> RedOperationsFlow.Transfer
        }

    private val swapRequest: ConfirmType.Swap?
        get() = request.type as? ConfirmType.Swap

    val sendOperation: RedOperationsOperation
        get() = when (request.context) {
            is ConfirmContext.Dapp -> RedOperationsOperation.ConfirmTransaction
            else -> RedOperationsOperation.Send
        }

    @PublishedApi
    internal fun startRedOperation(operation: RedOperationsOperation): RedOperation {
        return RedOperation(flow, operation).also { it.attempt() }
    }

    /**
     * Runs [block] as a RED operation: `op_attempt` is emitted up front and an uncaught throwable is
     * reported as the terminal outcome before it propagates. [block] is responsible for calling
     * [RedOperation.success] / [RedOperation.failure] on the paths it handles itself.
     */
    inline fun <T> redOperation(
        operation: RedOperationsOperation,
        block: RedOperation.() -> T,
    ): T {
        val red = startRedOperation(operation)
        try {
            return red.block()
        } catch (e: Throwable) {
            red.failure(e)
            throw e
        }
    }

    // Cross-cutting "transaction submitted" signal, emitted in addition to the flow-specific events.
    // Only broadcast transactions qualify, so sign-only requests and message signing never reach here.
    fun transactionSent(transaction: Transaction, fee: TxFee?) {
        val asset = transaction.account.asset
        val dapp = request.context as? ConfirmContext.Dapp
        AnalyticsHelper.Default.events.transactionSent.transactionSent(
            category = transactionCategory(transaction),
            categoryDetail = transactionCategoryDetail(transaction),
            asset = asset.id,
            amount = asset.toDisplayUnit(transaction.amount).value.doubleValue(exactRequired = false),
            feeAsset = feeAsset(fee),
            walletInterface = walletInterface(transaction.account.address.type, asset.chain),
            walletSource = TransactionSentWalletSource.Mnemonic,
            walletMode = TransactionSentWalletMode.Multi,
            initiatedBy = when {
                dapp != null -> TransactionSentInitiatedBy.Walletconnect
                else -> when (request.initiator) {
                    ConfirmInitiator.DeepLink -> TransactionSentInitiatedBy.DeepLink
                    ConfirmInitiator.QrCode -> TransactionSentInitiatedBy.QrCode
                    ConfirmInitiator.User -> TransactionSentInitiatedBy.User
                }
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
        )
    }

    // Swap funnel counterpart of swap_open/swap_click on the swap screen. Only the send phase counts:
    // quote and emulation failures before the user confirms are covered by the RED operation events.
    fun swapConfirm(pendingTx: PendingTransaction) {
        val swap = swapRequest ?: return
        AnalyticsHelper.Default.events.swapsNative.swapConfirm(
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
        AnalyticsHelper.Default.events.swapsNative.swapSuccess(
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
        AnalyticsHelper.Default.events.swapsNative.swapFailed(
            type = SwapsNativeType.Native,
            walletMode = SwapsNativeWalletMode.Multi,
            assetFrom = request.data.assetId,
            assetTo = swap.destinationAssetId,
            feeAsset = SwapsNativeFeeAsset.Coin,
            providerName = pendingTx.providerName,
            errorMessage = cause?.message ?: error::class.simpleName.orEmpty(),
            isMax = swap.isMax,
        )
    }

    private val PendingTransaction.providerName: String
        get() = quote?.provider?.id.orEmpty()

    private fun feeAsset(fee: TxFee?): TransactionSentFeeAsset {
        return when {
            fee == null -> TransactionSentFeeAsset.Coin
            fee.account is FeeAccount.Keeper -> TransactionSentFeeAsset.BatteryCharges
            fee.isNative -> TransactionSentFeeAsset.Coin
            else -> TransactionSentFeeAsset.Gasless
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
