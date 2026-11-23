package com.tonapps.wallet.data.multichain.activity

import io.walletapi.models.Activity
import io.walletapi.models.ActivityFeeType
import java.math.BigDecimal

data class TronResourceEntity(
    val energy: Long,
    val bandwidth: Long,
)

fun Activity.tronResourceOrNull(): TronResourceEntity? {
    val resource = meta?.get("tron_resource")?.asObject() ?: return null
    val energy = resource["energy"]?.asLong() ?: 0L
    val bandwidth = resource["bandwidth"]?.asLong() ?: 0L
    if (energy <= 0L && bandwidth <= 0L) {
        return null
    }
    return TronResourceEntity(energy = energy, bandwidth = bandwidth)
}

fun Activity.isBatteryFee(): Boolean = (fee?.type ?: feeType) == ActivityFeeType.battery

fun Activity.batteryChargesOrNull(): Int? {
    if (!isBatteryFee()) {
        return null
    }
    val charges = (fee?.amount ?: feeAmount)?.toBigDecimalOrNull()?.stripTrailingZeros() ?: return null
    if (charges.signum() < 0 || charges.scale() > 0 || charges > MAX_BATTERY_CHARGES) {
        return null
    }
    return charges.toInt()
}

private val MAX_BATTERY_CHARGES = BigDecimal(Int.MAX_VALUE)
