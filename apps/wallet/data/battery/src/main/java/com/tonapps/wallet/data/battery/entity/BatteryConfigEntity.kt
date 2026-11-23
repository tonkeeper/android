package com.tonapps.wallet.data.battery.entity

import android.os.Parcelable
import com.tonapps.blockchain.model.legacy.TransferEntity
import com.tonapps.blockchain.ton.extensions.equalsRawAddress
import com.tonapps.icu.Coins
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
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
    val packages: List<BatteryPackageEntity>,
    val transferCostDefault: String,
    val transferCostJettons: Map<String, String>,
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

    // TON forwarded with a jetton or NFT transfer: the per-jetton override, else the default,
    // else 0.05 TON when the config has no usable value (e.g. testnet, whose config omits it).
    fun transferCost(jettonMasterAddress: String?): Coins {
        val raw = jettonMasterAddress?.let { master ->
            transferCostJettons.entries.firstOrNull { it.key.equalsRawAddress(master) }?.value
        } ?: transferCostDefault
        return Coins.of(raw).takeIf { it.isPositive } ?: TransferEntity.BASE_FORWARD_AMOUNT
    }

    // NFTs have no jetton master and take the default cost.
    fun transferCost(transfer: TransferEntity): Coins {
        val jettonMaster = transfer.token.token.address.takeUnless { transfer.isNft || transfer.isTon }
        return transferCost(jettonMaster)
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
            packages = emptyList(),
            transferCostDefault = "0.05",
            transferCostJettons = emptyMap(),
        )
    }
}