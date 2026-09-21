@file:UseSerializers(BigIntegerSerializer::class)
package com.tonapps.blockchain.model

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.utils.BigIntegerSerializer
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
class ConfirmRequest(
    val data: CommonTransactionData,
    val type: ConfirmType,
    val action: ConfirmAction = ConfirmAction.SignAndSend,
    val context: ConfirmContext? = null,
    val initiator: ConfirmInitiator = ConfirmInitiator.User,
)

@Serializable
enum class ConfirmInitiator {
    User,
    DeepLink,
    QrCode,
}

@Serializable
sealed interface ConfirmContext {
    data class Dapp(
        val requestId: String,
        val topic: String,
        val name: String,
        val url: String,
    ) : ConfirmContext
}

@Serializable
class CommonTransactionData(
    val assetId: String,
    val walletId: String,
    val nonce: Long? = null,
    val fee: Fee? = null,
)

@Serializable
enum class ConfirmAction {
    Sign,
    Send,
    SignAndSend;

    val isSignOnly: Boolean get() = this == Sign
    val isSendOnly: Boolean get() = this == Send
    val withSend: Boolean get() = this == Send || this == SignAndSend
}

@Serializable
sealed interface ConfirmType {

    @Serializable
    data class Transfer(
        val amount: BigInteger,
        val to: String,
        val domain: String? = null,
        val isMax: Boolean? = null,
        val meta: String? = null,
    ) : ConfirmType

    @Serializable
    data class Swap(
        val sourceAmount: BigInteger,
        val destinationAssetId: String,
        val isMax: Boolean? = null,
        val slippageBps: Int? = null,
        // Quote already fetched on the swap screen; reused for the first confirm render so we don't
        // re-request a quote until the countdown expires or the user changes slippage here.
        val quote: Quote? = null,
    ) : ConfirmType {
        @Serializable
        data class Quote(
            val routeId: String,
            val buyAmount: BigInteger,
            val minBuyAmount: BigInteger,
            val provider: String,
            val providerTxId: String? = null,
            val slippageBps: Int? = null,
            val priceImpactBps: Int? = null,
        )
    }

    @Serializable
    data class Call(
        val amount: BigInteger,
        val contract: String,
        val data: String,
    ) : ConfirmType

    @Serializable
    class Message(
        val data: String,
        val type: Type,
    ) : ConfirmType {
        enum class Type {
            Default, Legacy, Personal, Typed, Transaction,
        }
    }
}
