package com.tonapps.deposit.multicoin.screens.moonpay

import com.tonapps.deposit.multicoin.moonpay.api.MoonPayBuyQuote
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayTheme
import kotlinx.serialization.Serializable
import com.tonapps.deposit.multicoin.analytics.BuyCryptoReport

const val KEY_MOONPAY_PAYMENT_RESULT = "moonpay_payment"

@Serializable
data class MoonPayPaymentData(
    val quote: MoonPayBuyQuote,
    val paymentTitle: String,
    val externalTransactionId: String,
    val theme: MoonPayTheme?,
    // «USDT (TON)» as the buying screen names it, not MoonPay's «USDT_TON».
    val assetLabel: String? = null,
    // Set by «Buy crypto»; without it the sheet reports no outcome.
    val report: BuyCryptoReport? = null,
)
