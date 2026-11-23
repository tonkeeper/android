package com.tonapps.deposit.multicoin.moonpay.domain

sealed interface MoonPayPaymentState {

    data object Starting : MoonPayPaymentState

    // MoonPay's buy button is drawn in the buying screen; nothing is paid before a tap.
    data object ButtonReady : MoonPayPaymentState

    // The customer tapped the buy button.
    data object ButtonPressed : MoonPayPaymentState

    data class Started(val transactionId: String?) : MoonPayPaymentState

    // The widget opened at `openedAt`, on MoonPay's clock.
    data class WidgetOpened(val openedAt: Long) : MoonPayPaymentState

    // The widget ended without naming a transaction; MoonPay is asked whether it made one.
    data object Checking : MoonPayPaymentState

    // MoonPay named the transaction, but its frames ended without its status; MoonPay is asked for it.
    data class Verifying(val transactionId: String) : MoonPayPaymentState

    data class Processing(val transactionId: String) : MoonPayPaymentState

    data class Completed(val transactionId: String?) : MoonPayPaymentState

    // MoonPay has not settled the payment within a minute; slower polls go on until `pollsUntil`, wall clock ms.
    data class StillProcessing(val transactionId: String, val pollsUntil: Long) : MoonPayPaymentState

    // The outcome will not be learned here: polling gave up, or there is no transaction id to follow.
    data class Unknown(val transactionId: String?) : MoonPayPaymentState

    data class Failed(val failure: MoonPayPaymentFailure, val transactionId: String?) : MoonPayPaymentState
}

enum class MoonPayPaymentFailure {
    Declined,
    LimitReached,
    QuoteRejected,

    // The quote ran out before MoonPay took it: a newer one is asked for.
    QuoteExpired,

    // The customer closed MoonPay's window, checkout, or 3DS page.
    Cancelled,

    // MoonPay reports the transaction as cancelled.
    TransactionCancelled,
    NotConfirmed,
    Unavailable,
    VerificationPending,
    VerificationUnavailable,
    NoResponse,

    // MoonPay wants the customer to sign in, or accept its terms, again.
    SignInRequired,
    Unknown,
}
