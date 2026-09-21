package com.tonapps.perps.data

/**
 * Display model for a perps deposit payment method (the asset the user tops up with).
 *
 * Like [PerpMarket], values are placeholder until the real perps funding API lands.
 * [balance] and [usdPrice] are kept numeric so the amount field can derive the fiat
 * equivalent, MAX, and insufficient-balance checks; everything else is presentation.
 */
data class PerpsPaymentMethod(
    val id: String,
    val symbol: String,
    val iconRes: Int,
    val feeLabel: String,
    val zeroFee: Boolean,
    val balance: Double,
    val usdPrice: Double,
)
