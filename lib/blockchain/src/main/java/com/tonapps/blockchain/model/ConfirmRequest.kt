@file:UseSerializers(BigIntegerSerializer::class)
package com.tonapps.blockchain.model

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.utils.BigDecimalSerializer
import com.tonapps.blockchain.utils.BigIntegerSerializer
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events.Perps.PerpsSourceScreen
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
        val domainStatus: DappConnectRequest.DomainStatus? = null,
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
    /**
     * Epoch millis, on the server's clock, after which the request must not be signed; null when it
     * does not expire. The device clock can be set to anything, so it is never compared with it.
     */
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
        /**
         * Set when [operation] is the token contract's own `transfer` call, as a dapp sends it: the
         * recipient and amount it carries encoded.
         */
        val tokenTransfer: TokenTransfer? = null,
    ) : Operational {

        @Serializable
        data class TokenTransfer(
            val to: String,
            val amount: BigInteger,
        )
    }

    @Serializable
    data class Swap(
        val sourceAmount: BigInteger,
        val destinationAssetId: String,
        val isMax: Boolean? = null,
        val slippageBps: Int? = null,
        // Quote already fetched on the swap screen; reused for the first confirm render so we don't
        // re-request a quote until the countdown expires or the user changes slippage here.
        val quote: Quote? = null,
        /**
         * The swap screen sends this swap itself (one-click swap) instead of opening the confirm screen.
         * Decided once by the screen, so the confirm never reads the boot flag again mid-send.
         */
        val oneClick: Boolean = false,
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
            val receivedAt: Long? = null,
            val lifetimeMillis: Long? = null,
            /** The route was quoted on `/v3/crosschain`, so it is prepared there. */
            val genericPayload: Boolean = false,
        )
    }

    /**
     * A swap quoted on `/v3/crosschain`: [operation] is the route as `/prepare` made it, signed as it
     * is. [swap] is what the user asked for, its [Swap.quote] the route [operation] was prepared from;
     * the confirm screen quotes and prepares it again as the quote ages or the slippage changes. Until
     * the first prepare [operation] has no payloads.
     */
    @Serializable
    data class SwapOperation(
        override val operation: Operation,
        val swap: Swap,
    ) : Operational

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
        val estimatedDurationSeconds: Long? = null,
        val activatesAccount: Boolean = false,
        val providerFees: ProviderFees? = null,
        /** The perps screen the deposit was started from. */
        val sourceScreen: PerpsSourceScreen? = null,
        val paymentOperation: Operation? = null,
        val payableByRelayer: Boolean = true,
        val verifiedPayment: VerifiedPayment? = null,
    ) : Operational {

        @Serializable
        data class VerifiedPayment(
            val provider: String,
            val amount: BigInteger,
        )

        @Serializable
        data class ProviderFees(
            @Serializable(with = BigDecimalSerializer::class)
            val destinationGasUsd: BigDecimal,
            @Serializable(with = BigDecimalSerializer::class)
            val providerUsd: BigDecimal,
        )

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

    /**
     * A battery recharge paid with [amount] smallest units of the request's asset, on a chain other than
     * TON. Quoted on the confirm screen (`/purchase-battery/emulate`), whose operation is signed and sent
     * to `/gasless/send`; [estimatedCharges] is what the recharge screen showed before that.
     */
    @Serializable
    data class BatteryPurchase(
        /** The chain as the battery names it, `base/mainnet`. */
        val chainId: String,
        /**
         * The asset as the battery names it, which the request's own asset id, the multichain API's,
         * may spell differently (`bsc/mainnet/erc20/…` against `bsc/mainnet/bep20/…`).
         */
        val batteryAssetId: String,
        val senderAddress: String,
        val amount: BigInteger,
        val symbol: String,
        val decimals: Int,
        val estimatedCharges: Long,
        val promo: String? = null,
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
