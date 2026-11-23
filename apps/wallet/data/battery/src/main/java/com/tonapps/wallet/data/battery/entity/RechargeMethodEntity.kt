package com.tonapps.wallet.data.battery.entity

import android.os.Parcelable
import com.tonapps.icu.Coins
import com.tonapps.wallet.data.battery.BatteryRechargeMath
import io.batteryapi.models.RechargeMethodsMethodsInner
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal
import java.math.RoundingMode

@Parcelize
data class RechargeMethodEntity(
    val type: RechargeMethodType,
    val rate: String,
    val symbol: String,
    val decimals: Int,
    val supportGasless: Boolean,
    val supportRecharge: Boolean,
    val image: String? = null,
    val jettonMaster: String? = null,
    val minBootstrapValue: String? = null,
    /** The chain as the battery names it, `base/mainnet`. */
    val chain: String = "",
    /** The asset as the multichain API names it, `base/mainnet/erc20/0x…`. */
    val assetId: String = "",
    /** The charges one whole unit buys, after the markup; null from a battery that does not send it. */
    val chargesRate: String? = null,
) : Parcelable {

    fun fromTon(amount: BigDecimal): Coins {
        return Coins.of(amount.divide(rate.toBigDecimal(), decimals, RoundingMode.HALF_UP), decimals)
    }

    fun fromTon(amount: String) = fromTon(amount.toBigDecimal())

    /** The charges one whole unit buys at [chargeCost] TON a charge; null when the method gives no positive rate. */
    fun chargesPerUnit(chargeCost: String): BigDecimal? {
        return BatteryRechargeMath.chargesRate(chargesRate, rate, chargeCost)
    }

    /** The whole units that buy [charges], rounded up to the asset's decimals. */
    fun amountForCharges(charges: Int, chargeCost: String): BigDecimal {
        return BatteryRechargeMath.amount(charges, chargesPerUnit(chargeCost), decimals)
    }

    companion object {

        /**
         * [method] as the wallet buys with it, or null when it cannot: a type this build does not know, or one
         * on a chain other than TON for a wallet that is not [multichain].
         */
        fun of(method: RechargeMethodsMethodsInner, multichain: Boolean = false): RechargeMethodEntity? {
            val type = when (method.type) {
                RechargeMethodsMethodsInner.Type.jetton -> RechargeMethodType.JETTON
                RechargeMethodsMethodsInner.Type.ton -> RechargeMethodType.TON
                RechargeMethodsMethodsInner.Type.trc20 -> RechargeMethodType.TRC20
                RechargeMethodsMethodsInner.Type.erc20 -> RechargeMethodType.ERC20
                RechargeMethodsMethodsInner.Type.spl -> RechargeMethodType.SPL
                RechargeMethodsMethodsInner.Type.coin -> RechargeMethodType.COIN
                RechargeMethodsMethodsInner.Type.unknown -> return null
            }

            if (!type.isTon && !multichain) {
                return null
            }

            return RechargeMethodEntity(
                type = type,
                rate = method.rate,
                symbol = method.symbol,
                decimals = method.decimals,
                supportGasless = method.supportGasless,
                supportRecharge = method.supportRecharge,
                image = method.image,
                jettonMaster = method.jettonMaster,
                minBootstrapValue = method.minBootstrapValue,
                chain = method.chain,
                assetId = method.assetId,
                chargesRate = method.chargesRate,
            )
        }
    }
}
