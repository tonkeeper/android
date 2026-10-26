@file:UseSerializers(BigIntegerSerializer::class)
package com.tonapps.blockchain.model

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.utils.BigIntegerSerializer
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.chainkit.core.op.Operation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
class ConfirmRequest(
    val data: CommonTransactionData,
    val type: ConfirmType,
    val action: ConfirmAction = ConfirmAction.SignAndSend,
    val context: ConfirmContext? = null,
    val initiator: ConfirmInitiator = ConfirmInitiator.User,
    val utm: UtmParameters = UtmParameters.Empty,
)

@Serializable
enum class ConfirmInitiator {
    User,
    DeepLink,
    QrCode,
}

@Serializable
sealed interface ConfirmContext {

    @Serializable
    data class Dapp(
        val requestId: String,
        val topic: String,
        val name: String,
        val url: String,
        val source: Source = Source.WalletConnect,
    ) : ConfirmContext {

        @Serializable
        enum class Source {
            WalletConnect,
            Injected,
        }
    }
}

@Serializable
class CommonTransactionData(
    val assetId: String,
    val walletId: String,
    val nonce: Long? = null,
    /** Epoch millis after which the request must not be signed; null when it does not expire. */
    val expiresAt: Long? = null,
    /**
     * The operation is not the one sent but what the wallet signs to get it: the request is quoted
     * again once that is signed.
     */
    val preview: Boolean = false,
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

    /** A type that carries all it signs as an [operation]; the operation confirm screen takes only these. */
    @Serializable
    sealed interface Operational : ConfirmType {
        val operation: Operation
    }

    /**
     * [operation] carries what is signed (recipient, amount, comment); the rest is what the confirm
     * screen shows and how it treats the transfer.
     */
    @Serializable
    data class Transfer(
        override val operation: Operation,
        val domain: String? = null,
        val isMax: Boolean? = null,
        val commentEncryptionKey: String? = null,
    ) : Operational

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
        override val operation: Operation,
    ) : Operational

    /**
     * A deposit of [amount] units of the request's asset into the perps account, quoted before the
     * confirm screen opens: [operation] is the quote's, and the request's data expires with it.
     *
     * A deposit that also registers the account's key opens as a [CommonTransactionData.preview]:
     * [operation] is then the owner's two signatures that make it, and the deposit sent is quoted
     * again once they are signed.
     */
    @Serializable
    data class PerpetualDeposit(
        override val operation: Operation,
        val amount: BigInteger,
        /** USDC units the account is credited with; set for routes that swap on the way. */
        val minReceive: BigInteger? = null,
        val quote: Quote,
        val feeShareBps: Int? = null,
        val estimatedDurationSeconds: Long? = null,
    ) : Operational {

        /** What the perps funding journal records the deposit under. */
        @Serializable
        data class Quote(
            val id: String,
            val executionId: String?,
            /** The Ethereum address whose perps account is credited. */
            val beneficiary: String,
            /** USDC units the account is credited with. */
            val depositAmount: BigInteger,
            /** The aggregator fills it, rather than the wallet paying the gateway itself. */
            val crossChain: Boolean,
        )
    }

    /**
     * A withdrawal of [amount] USDC units out of the perps account into the request's asset.
     * Lighter signs it, so it carries no operation; it is quoted on the confirm screen.
     */
    @Serializable
    data class PerpetualWithdraw(
        val amount: BigInteger,
    ) : ConfirmType

    @Serializable
    class Message(
        val data: String,
        // `type` is the polymorphic discriminator of ConfirmType itself; a property of the same
        // name makes the whole request unserializable.
        @SerialName("kind")
        val type: Type,
    ) : ConfirmType {
        enum class Type {
            Default, Legacy, Personal, Typed, Transaction,

            // Several transactions signed at once (solana_signAllTransactions): [data] is a JSON
            // array of them, each signed as a [Transaction].
            Transactions,
        }
    }
}
