package com.tonapps.wallet.data.multichain.fees

import java.math.BigInteger

/**
 * Battery's `/network-fees`: per asset, the gas a swap from it and a perps deposit of it cost when
 * the wallet pays that gas itself, in the smallest units of the coin the gas is paid in.
 */
class NetworkFees(entries: List<Entry>) {

    data class Entry(
        val assetId: String,
        val feeAssetId: String,
        val swapSafe: BigInteger,
        val perpsSafe: BigInteger?,
    )

    private val byAssetId = entries.associateBy { it.assetId }

    /** What MAX in a swap from [assetId] leaves for gas, or null when the response says nothing for it. */
    fun swapReserve(assetId: String): BigInteger? {
        return sameAssetEntry(assetId)?.swapSafe
    }

    /** What MAX in a perps deposit of [assetId] leaves for gas, or null when the response says nothing for it. */
    fun perpsReserve(assetId: String): BigInteger? {
        return sameAssetEntry(assetId)?.perpsSafe
    }

    // A fee is taken off the amount only when it is paid in the asset being sent: a token's gas is paid
    // in the chain's coin, and coin units cannot be subtracted from token units.
    private fun sameAssetEntry(assetId: String): Entry? {
        return byAssetId[assetId]?.takeIf { it.feeAssetId == assetId }
    }
}
