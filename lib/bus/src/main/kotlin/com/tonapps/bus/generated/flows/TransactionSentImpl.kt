package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.TransactionSent.TransactionSentCategory
import com.tonapps.bus.generated.Events.TransactionSent.TransactionSentCategoryDetail
import com.tonapps.bus.generated.Events.TransactionSent.TransactionSentFeeAsset
import com.tonapps.bus.generated.Events.TransactionSent.TransactionSentInitiatedBy
import com.tonapps.bus.generated.Events.TransactionSent.TransactionSentWalletInterface
import com.tonapps.bus.generated.Events.TransactionSent.TransactionSentWalletMode
import com.tonapps.bus.generated.Events.TransactionSent.TransactionSentWalletSource

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class TransactionSentImpl(
    private val eventExecutor: EventExecutor,
) : Events.TransactionSent {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * transaction_sent
     *
     * Sent once when any blockchain transaction is submitted, on any supported chain (TON, TRON, BTC, EVM: Ethereum / Base / Arbitrum / Polygon / BNB Chain, Solana). This is the single cross-cutting "transaction submitted" signal and is emitted in addition to any flow-specific event — e.g. send_success remains a separate send-flow event and is NOT replaced by transaction_sent.
Chain and network are NOT separate fields: they are carried inside `asset` (and `to_asset`) in chain/network/type[/addr] format. The structure mirrors the client Transaction model: `category` is the top-level transaction kind and `category_detail` is the variant within it.
`initiated_by` says who asked for the transaction — the user inside the wallet, or an external dapp over TON Connect, WalletConnect, or an injected EVM provider. It makes transaction_sent self-sufficient: dapp-driven volume no longer has to be inferred by correlating with tc_send_success / send_success on user + timestamp.

     */
    @AnyThread
    override fun transactionSent(
        category: TransactionSentCategory,
        categoryDetail: TransactionSentCategoryDetail,
        asset: String,
        amount: Double,
        feeAsset: TransactionSentFeeAsset,
        walletInterface: TransactionSentWalletInterface,
        walletSource: TransactionSentWalletSource,
        walletMode: TransactionSentWalletMode,
        initiatedBy: TransactionSentInitiatedBy,
        appId: String?,
        dappUrl: String?,
        isMax: Boolean?,
        toAsset: String?,
        stakingProvider: String?,
        isLiquid: Boolean?
    ) {
        val props = hashMapOf<String, Any>(
            "category" to category.key,
            "category_detail" to categoryDetail.key,
            "asset" to asset,
            "amount" to amount,
            "fee_asset" to feeAsset.key,
            "wallet_interface" to walletInterface.key,
            "wallet_source" to walletSource.key,
            "wallet_mode" to walletMode.key,
            "initiated_by" to initiatedBy.key
        )
        appId?.let { props["app_id"] = it }
        dappUrl?.let { props["dapp_url"] = it }
        isMax?.let { props["is_max"] = it }
        toAsset?.let { props["to_asset"] = it }
        stakingProvider?.let { props["staking_provider"] = it }
        isLiquid?.let { props["is_liquid"] = it }
        trackEvent("transaction_sent", props)
    }
}
