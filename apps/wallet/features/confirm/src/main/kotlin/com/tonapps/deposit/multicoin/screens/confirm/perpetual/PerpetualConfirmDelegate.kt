package com.tonapps.deposit.multicoin.screens.confirm.perpetual

import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.model.account.CryptoWallet
import com.tonapps.wallet.data.multichain.asset.AssetEntity

/**
 * The perps side of a [ConfirmType.PerpetualWithdraw] confirmation. Quoting, the funding journal and
 * the Lighter account live in the perps feature, which implements this; the confirm screen only
 * drives the review, the re-quote and the swipe.
 *
 * Both calls fail with [PerpetualConfirmException].
 */
interface PerpetualConfirmDelegate {

    suspend fun review(request: ConfirmRequest, withdraw: ConfirmType.PerpetualWithdraw): PerpetualReview

    /** Signs and sends [review]; a send whose outcome is unknown returns normally, recovery settles it. */
    suspend fun submit(review: PerpetualReview, wallet: CryptoWallet)
}

class PerpetualConfirmException(
    val error: PerpetualConfirmError,
    /** The quote behind the review can no longer be sent: the screen quotes again. */
    val requote: Boolean = false,
    cause: Throwable? = null,
) : Exception(error.toString(), cause)

sealed interface PerpetualConfirmError {
    data object QuoteExpired : PerpetualConfirmError

    /** [minAmount] is the formatted minimum, when the service names one. */
    data class BelowMinimum(val minAmount: String?) : PerpetualConfirmError
    data object InsufficientBalance : PerpetualConfirmError

    /** An earlier move of the same kind is not settled yet. */
    data object InFlight : PerpetualConfirmError
    data object AccountUnavailable : PerpetualConfirmError
    data object ServiceUnavailable : PerpetualConfirmError
    data object Refused : PerpetualConfirmError
    data object PrepareFailed : PerpetualConfirmError
    data object AmountTooLow : PerpetualConfirmError
    data object Failed : PerpetualConfirmError
}

/** Whether swiping again can succeed; any other error blocks the swipe. */
val PerpetualConfirmError.isRetryable: Boolean
    get() = this == PerpetualConfirmError.Failed ||
        this == PerpetualConfirmError.ServiceUnavailable ||
        this == PerpetualConfirmError.Refused ||
        // Re-quoted by then: the next swipe sends the fresh quote.
        this == PerpetualConfirmError.QuoteExpired

/**
 * A quoted perps move. [display] is what the screen shows, already formatted by the perps side;
 * [plan] is the delegate's own plan, handed back to [PerpetualConfirmDelegate.submit].
 */
class PerpetualReview(
    val walletId: String,
    /** When the quote stops being valid; null for moves that are not quoted. */
    val expiresAtMs: Long?,
    val display: Display,
    val plan: Any,
) {

    sealed interface Display {
        val symbol: String
        val network: String?

        /** The asset whose image heads the screen; null shows the generic dollar icon. */
        val image: AssetEntity?
    }

    class Withdraw(
        override val symbol: String,
        override val network: String?,
        override val image: AssetEntity?,
        val amount: String,
        val receive: String,
        /** Formatted USD cost; null when the withdrawal is free. */
        val fee: String?,
        val feeIsEstimate: Boolean,
        val arrivalSeconds: Long?,
        val feeShareBps: Int? = null,
    ) : Display
}
