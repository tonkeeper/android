package com.tonapps.wallet.data.battery.entity

import android.os.Parcelable
import io.batteryapi.models.ConfigMeanPrices
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import org.ton.block.AddrStd

@Parcelize
data class BatteryConfigEntity(
    val excessesAccount: String?,
    val fundReceiver: String?,
    val rechargeMethods: List<RechargeMethodEntity>,
    val gasProxy: List<String>,
    val meanPrices: MeanPrices,
    val chargeCost: String,
    val reservedAmount: String,
) : Parcelable {

    @Parcelize
    data class MeanPrices(
        val batteryMeanPriceSwap: Int,
        val batteryMeanPriceJetton: Int,
        val batteryMeanPriceNft: Int,
        val batteryMeanPriceTronUsdt: Int,
        val tonMeanPriceTronUsdt: Float
    ) : Parcelable

    @IgnoredOnParcel
    val excessesAddress: AddrStd? by lazy {
        excessesAccount?.let { AddrStd(it) }
    }

    companion object {
        val Empty = BatteryConfigEntity(
            excessesAccount = null,
            fundReceiver = null,
            rechargeMethods = emptyList(),
            gasProxy = emptyList(),
            meanPrices = MeanPrices(0, 0, 0, 0, 0f),
            chargeCost = "0",
            reservedAmount = "0",
        )
    }
}