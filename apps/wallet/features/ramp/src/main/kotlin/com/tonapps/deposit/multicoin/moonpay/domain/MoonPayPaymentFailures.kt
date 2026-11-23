package com.tonapps.deposit.multicoin.moonpay.domain

import com.tonapps.deposit.multicoin.moonpay.MoonPayCheckoutError
import com.tonapps.deposit.multicoin.moonpay.MoonPayConnectionException
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayConnectionStatus
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrameCompletion

// MoonPay's errors and failure codes, by what the customer can do next.
internal object MoonPayPaymentFailures {

    private const val CUSTOMER_STATUS_PENDING = "customer_status_pending"
    private const val CUSTOMER_STATUS_UNAVAILABLE = "customer_status_unavailable"
    private const val INVALID_PREFIX = "invalid_"
    private const val QUOTE_EXPIRED = "quoteExpired"

    private val QUOTE_ERRORS = setOf(
        "invalidQuote",
        "unsupportedPaymentMethod",
        "configurationError",
        "missing_quote",
    )

    fun connection(error: Throwable): MoonPayPaymentFailure {
        return when (error) {
            is MoonPayConnectionException.Rejected -> when (error.status) {
                MoonPayConnectionStatus.Pending -> MoonPayPaymentFailure.VerificationPending
                MoonPayConnectionStatus.Unavailable,
                MoonPayConnectionStatus.Failed,
                -> MoonPayPaymentFailure.VerificationUnavailable
                MoonPayConnectionStatus.ConnectionRequired,
                MoonPayConnectionStatus.TermsAcceptanceRequired,
                -> MoonPayPaymentFailure.SignInRequired
                MoonPayConnectionStatus.Active -> MoonPayPaymentFailure.Unknown
            }
            is MoonPayConnectionException.Cancelled -> MoonPayPaymentFailure.Cancelled
            else -> MoonPayPaymentFailure.Unavailable
        }
    }

    fun checkout(error: MoonPayCheckoutError): MoonPayPaymentFailure {
        return when (error) {
            is MoonPayCheckoutError.Cancelled,
            is MoonPayCheckoutError.ChallengeClosed,
            -> MoonPayPaymentFailure.Cancelled
            is MoonPayCheckoutError.NoResponse -> MoonPayPaymentFailure.NoResponse
            is MoonPayCheckoutError.NoOutcome -> MoonPayPaymentFailure.Unknown
            is MoonPayCheckoutError.ChallengeCompleted -> challenge(error.completion)
            is MoonPayCheckoutError.FrameFailed -> MoonPayPaymentFailure.Unavailable
            is MoonPayCheckoutError.Remote -> when (val code = error.error.code) {
                QUOTE_EXPIRED -> MoonPayPaymentFailure.QuoteExpired
                in QUOTE_ERRORS -> MoonPayPaymentFailure.QuoteRejected
                CUSTOMER_STATUS_PENDING -> MoonPayPaymentFailure.VerificationPending
                CUSTOMER_STATUS_UNAVAILABLE -> MoonPayPaymentFailure.VerificationUnavailable
                else -> if (code.startsWith(INVALID_PREFIX)) {
                    MoonPayPaymentFailure.QuoteRejected
                } else {
                    MoonPayPaymentFailure.Unknown
                }
            }
        }
    }

    // A challenge that settled something else instead of the purchase.
    private fun challenge(completion: MoonPayFrameCompletion): MoonPayPaymentFailure {
        return when (completion) {
            is MoonPayFrameCompletion.Identity -> MoonPayPaymentFailure.QuoteRejected
            is MoonPayFrameCompletion.Status -> when (completion.status) {
                "pending" -> MoonPayPaymentFailure.VerificationPending
                "unavailable" -> MoonPayPaymentFailure.VerificationUnavailable
                "upgraded" -> MoonPayPaymentFailure.QuoteRejected
                "rejected" -> MoonPayPaymentFailure.LimitReached
                else -> MoonPayPaymentFailure.Unknown
            }
            is MoonPayFrameCompletion.Connection,
            is MoonPayFrameCompletion.Transaction,
            is MoonPayFrameCompletion.Card,
            is MoonPayFrameCompletion.Raw,
            -> MoonPayPaymentFailure.Unknown
        }
    }

    // `failureCode` of a transaction that a frame completed as failed.
    fun transaction(failureCode: String?): MoonPayPaymentFailure {
        return when (failureCode) {
            "authorizationDeclined", "transactionDeclined" -> MoonPayPaymentFailure.Declined
            "validationError" -> MoonPayPaymentFailure.QuoteRejected
            "serviceUnavailable", "applePayMerchantUnavailable" -> MoonPayPaymentFailure.Unavailable
            else -> MoonPayPaymentFailure.Unknown
        }
    }

    // `failureReason` of the stage a polled transaction failed at.
    fun stage(failureReason: String?): MoonPayPaymentFailure {
        return when (failureReason) {
            "payment_authorization_declined", "card_not_supported", "rejected_card" -> MoonPayPaymentFailure.Declined
            "daily_purchase_limit_exceeded" -> MoonPayPaymentFailure.LimitReached
            "timeout_3d_secure",
            "timeout_card_verification",
            "timeout_bank_transfer",
            "timeout_kyc_verification",
            -> MoonPayPaymentFailure.NotConfirmed
            "rejected_kyc" -> MoonPayPaymentFailure.VerificationUnavailable
            "cancelled" -> MoonPayPaymentFailure.TransactionCancelled
            "error" -> MoonPayPaymentFailure.Unavailable
            else -> MoonPayPaymentFailure.Unknown
        }
    }
}
