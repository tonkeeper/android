package com.tonapps.wallet.data.battery.entity

import io.batteryapi.models.PurchaseBatteryEmulateResponse
import io.batteryapi.models.PurchaseBatteryEstimateResponse
import kotlinx.serialization.json.JsonObject
import java.math.BigInteger

/**
 * What a battery purchase paid on a chain other than TON adds, as `/purchase-battery/estimate` or
 * `/purchase-battery/emulate` answered it for [amount] smallest units of [assetId].
 */
data class BatteryPurchaseQuote(
    val chainId: String,
    val assetId: String,
    val senderAddress: String,
    val amount: BigInteger,
    /** What the purchase adds before the network fee. */
    val grossCharges: Long,
    /** What the balance grows by: [grossCharges] less the network fee. */
    val charges: Long,
    val fundsReceiver: String,
    val promoBonusPercent: String?,
    /** The operation to sign and send to `/gasless/send`; null for an estimate. */
    val operation: JsonObject? = null,
    /** Epoch millis on the battery's clock after which [operation] is not taken; null for an estimate. */
    val expiresAt: Long? = null,
) {

    /** The network fee the battery pays for the transfer, in charges taken off the purchase. */
    val feeCharges: Long
        get() = (grossCharges - charges).coerceAtLeast(0)

    companion object {

        fun of(response: PurchaseBatteryEstimateResponse) = BatteryPurchaseQuote(
            chainId = response.chainId,
            assetId = response.assetId,
            senderAddress = response.senderAddress,
            amount = BigInteger(response.amount),
            grossCharges = response.grossCharges,
            charges = response.charges,
            fundsReceiver = response.fundsReceiver,
            promoBonusPercent = response.promoBonusPercent,
        )

        fun of(response: PurchaseBatteryEmulateResponse) = BatteryPurchaseQuote(
            chainId = response.chainId,
            assetId = response.assetId,
            senderAddress = response.senderAddress,
            amount = BigInteger(response.amount),
            grossCharges = response.grossCharges,
            charges = response.charges,
            fundsReceiver = response.fundsReceiver,
            promoBonusPercent = response.promoBonusPercent,
            operation = response.operation,
            expiresAt = response.expiresAt.toInstant().toEpochMilli(),
        )
    }
}
