package com.tonapps.bus.core

import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowBalanceBucket

private const val ONE_DOLLAR = 1.0
private const val TEN_DOLLARS = 10.0
private const val HUNDRED_DOLLARS = 100.0
private const val THOUSAND_DOLLARS = 1_000.0
private const val TEN_THOUSAND_DOLLARS = 10_000.0
private const val HUNDRED_THOUSAND_DOLLARS = 100_000.0

fun balanceBucketOf(usd: Double): WalletFlowBalanceBucket {
    return when {
        usd <= 0.0 -> WalletFlowBalanceBucket._0
        usd < ONE_DOLLAR -> WalletFlowBalanceBucket._01
        usd < TEN_DOLLARS -> WalletFlowBalanceBucket._110
        usd < HUNDRED_DOLLARS -> WalletFlowBalanceBucket._10100
        usd < THOUSAND_DOLLARS -> WalletFlowBalanceBucket._1001k
        usd < TEN_THOUSAND_DOLLARS -> WalletFlowBalanceBucket._1k10k
        usd < HUNDRED_THOUSAND_DOLLARS -> WalletFlowBalanceBucket._10k100k
        else -> WalletFlowBalanceBucket._100k
    }
}
