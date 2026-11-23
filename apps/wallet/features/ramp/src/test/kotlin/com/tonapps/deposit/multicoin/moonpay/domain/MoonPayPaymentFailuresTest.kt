package com.tonapps.deposit.multicoin.moonpay.domain

import com.tonapps.deposit.multicoin.moonpay.MoonPayCheckoutError
import com.tonapps.deposit.multicoin.moonpay.MoonPayCheckoutError.Source
import com.tonapps.deposit.multicoin.moonpay.MoonPayConnectionException
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayConnectionStatus
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrameCompletion
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrameError
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrameException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class MoonPayPaymentFailuresTest {

    @Test
    fun `connection failures`() {
        val cases = mapOf(
            MoonPayConnectionException.Rejected(MoonPayConnectionStatus.Pending) to
                MoonPayPaymentFailure.VerificationPending,
            MoonPayConnectionException.Rejected(MoonPayConnectionStatus.Unavailable) to
                MoonPayPaymentFailure.VerificationUnavailable,
            MoonPayConnectionException.Rejected(MoonPayConnectionStatus.Failed) to
                MoonPayPaymentFailure.VerificationUnavailable,
            MoonPayConnectionException.Rejected(MoonPayConnectionStatus.ConnectionRequired) to
                MoonPayPaymentFailure.SignInRequired,
            MoonPayConnectionException.Rejected(MoonPayConnectionStatus.TermsAcceptanceRequired) to
                MoonPayPaymentFailure.SignInRequired,
            MoonPayConnectionException.Rejected(MoonPayConnectionStatus.Active) to
                MoonPayPaymentFailure.Unknown,
            MoonPayConnectionException.Cancelled() to MoonPayPaymentFailure.Cancelled,
            MoonPayConnectionException.TimedOut() to MoonPayPaymentFailure.Unavailable,
            MoonPayConnectionException.Session(IOException()) to MoonPayPaymentFailure.Unavailable,
            MoonPayFrameException.HostGone() to MoonPayPaymentFailure.Unavailable,
        )
        cases.forEach { (error, failure) -> assertEquals("$error", failure, MoonPayPaymentFailures.connection(error)) }
    }

    @Test
    fun `checkout failures`() {
        val cases = mapOf(
            MoonPayCheckoutError.Cancelled to MoonPayPaymentFailure.Cancelled,
            MoonPayCheckoutError.ChallengeClosed to MoonPayPaymentFailure.Cancelled,
            MoonPayCheckoutError.NoResponse to MoonPayPaymentFailure.NoResponse,
            MoonPayCheckoutError.FrameFailed(IOException()) to MoonPayPaymentFailure.Unavailable,
            remote(Source.Challenge, "missing_quote") to MoonPayPaymentFailure.QuoteRejected,
            remote(Source.Challenge, "invalid_signature") to MoonPayPaymentFailure.QuoteRejected,
            remote(Source.Payment, "invalidQuote") to MoonPayPaymentFailure.QuoteRejected,
            remote(Source.Payment, "unsupportedPaymentMethod") to MoonPayPaymentFailure.QuoteRejected,
            remote(Source.Payment, "configurationError") to MoonPayPaymentFailure.QuoteRejected,
            remote(Source.Challenge, "quoteExpired") to MoonPayPaymentFailure.QuoteExpired,
            remote(Source.Challenge, "customer_status_pending") to MoonPayPaymentFailure.VerificationPending,
            remote(Source.Challenge, "customer_status_unavailable") to MoonPayPaymentFailure.VerificationUnavailable,
            remote(Source.Payment, "generic") to MoonPayPaymentFailure.Unknown,
            challenge(MoonPayFrameCompletion.Status("pending")) to MoonPayPaymentFailure.VerificationPending,
            challenge(MoonPayFrameCompletion.Status("unavailable")) to MoonPayPaymentFailure.VerificationUnavailable,
            challenge(MoonPayFrameCompletion.Status("upgraded")) to MoonPayPaymentFailure.QuoteRejected,
            challenge(MoonPayFrameCompletion.Status("rejected")) to MoonPayPaymentFailure.LimitReached,
            challenge(MoonPayFrameCompletion.Identity("idn_1")) to MoonPayPaymentFailure.QuoteRejected,
            challenge(MoonPayFrameCompletion.Raw(null)) to MoonPayPaymentFailure.Unknown,
        )
        cases.forEach { (error, failure) -> assertEquals("$error", failure, MoonPayPaymentFailures.checkout(error)) }
    }

    @Test
    fun `transaction failure codes`() {
        val cases = mapOf(
            "authorizationDeclined" to MoonPayPaymentFailure.Declined,
            "transactionDeclined" to MoonPayPaymentFailure.Declined,
            "transactionNotAllowed" to MoonPayPaymentFailure.Unknown,
            "validationError" to MoonPayPaymentFailure.QuoteRejected,
            "serviceUnavailable" to MoonPayPaymentFailure.Unavailable,
            "applePayMerchantUnavailable" to MoonPayPaymentFailure.Unavailable,
            "unknown" to MoonPayPaymentFailure.Unknown,
            null to MoonPayPaymentFailure.Unknown,
        )
        cases.forEach { (code, failure) -> assertEquals("$code", failure, MoonPayPaymentFailures.transaction(code)) }
    }

    @Test
    fun `stage failure reasons`() {
        val cases = mapOf(
            "payment_authorization_declined" to MoonPayPaymentFailure.Declined,
            "card_not_supported" to MoonPayPaymentFailure.Declined,
            "rejected_card" to MoonPayPaymentFailure.Declined,
            "daily_purchase_limit_exceeded" to MoonPayPaymentFailure.LimitReached,
            "timeout_3d_secure" to MoonPayPaymentFailure.NotConfirmed,
            "timeout_card_verification" to MoonPayPaymentFailure.NotConfirmed,
            "timeout_bank_transfer" to MoonPayPaymentFailure.NotConfirmed,
            "timeout_kyc_verification" to MoonPayPaymentFailure.NotConfirmed,
            "rejected_kyc" to MoonPayPaymentFailure.VerificationUnavailable,
            "cancelled" to MoonPayPaymentFailure.TransactionCancelled,
            "error" to MoonPayPaymentFailure.Unavailable,
            "rejected_other" to MoonPayPaymentFailure.Unknown,
            null to MoonPayPaymentFailure.Unknown,
        )
        cases.forEach { (reason, failure) -> assertEquals("$reason", failure, MoonPayPaymentFailures.stage(reason)) }
    }

    private fun challenge(completion: MoonPayFrameCompletion): MoonPayCheckoutError {
        return MoonPayCheckoutError.ChallengeCompleted(completion)
    }

    private fun remote(source: Source, code: String): MoonPayCheckoutError {
        return MoonPayCheckoutError.Remote(source, MoonPayFrameError(code, "message"))
    }
}
