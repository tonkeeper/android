package com.tonapps.wallet.data.multichain.account

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.blockchain.ton.extensions.toRawAddress

private fun networkName(testnet: Boolean): String {
    return if (testnet) {
        "testnet"
    } else {
        "mainnet"
    }
}

fun tonCoinAssetId(testnet: Boolean): String {
    return "ton/${networkName(testnet)}/coin"
}

fun jettonAssetId(master: String, testnet: Boolean): String {
    val raw = runCatching { master.toRawAddress() }.getOrDefault(master)
    return "ton/${networkName(testnet)}/jetton/$raw"
}

fun nftAssetId(address: String, testnet: Boolean): String {
    val raw = runCatching { address.toRawAddress() }.getOrDefault(address)
    return "ton/${networkName(testnet)}/nft/$raw"
}

fun AccountWithDetails.matchesJettonMaster(master: String): Boolean {
    if (!asset.id.contains("/jetton/")) {
        return false
    }
    return master.equalsAddress(asset.id.substringAfterLast('/'))
}

fun jettonToTon(
    amount: BigDecimal,
    jettonPrice: BigDecimal,
    tonPrice: BigDecimal,
): BigDecimal {
    if (amount <= BigDecimal.ZERO || tonPrice <= BigDecimal.ZERO) {
        return BigDecimal.ZERO
    }
    val mode = DecimalMode(
        decimalPrecision = 36,
        roundingMode = RoundingMode.ROUND_HALF_AWAY_FROM_ZERO,
    )
    return amount.multiply(jettonPrice, mode).divide(tonPrice, mode)
}
