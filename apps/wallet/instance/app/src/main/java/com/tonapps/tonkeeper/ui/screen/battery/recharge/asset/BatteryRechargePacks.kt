package com.tonapps.tonkeeper.ui.screen.battery.recharge.asset

import com.tonapps.wallet.data.battery.BatteryRechargeMath
import com.tonapps.wallet.data.battery.entity.BatteryPackageEntity
import java.math.BigDecimal

/** A pack of the asset recharge screen: [charges] for [amount] whole units of the asset. */
internal class BatteryRechargePack(
    val type: String,
    val charges: Int,
    /** Whole units of the asset. */
    val amount: BigDecimal,
    val isEnabled: Boolean,
)

/**
 * The packs the screen offers for [packages], priced at [chargesRate] in an asset of [decimals].
 * A pack that costs less than the method's least purchase, [minBootstrapValue], cannot be bought and is left
 * out; a pack the [balance] does not cover stays, disabled.
 */
internal fun batteryRechargePacks(
    packages: List<BatteryPackageEntity>,
    chargesRate: BigDecimal?,
    decimals: Int,
    minBootstrapValue: String?,
    balance: BigDecimal,
): List<BatteryRechargePack> {
    val min = minBootstrapValue?.toBigDecimalOrNull()

    return packages.filter { it.charges > 0 }.mapNotNull { pack ->
        val amount = BatteryRechargeMath.amount(pack.charges, chargesRate, decimals)
        if (min != null && amount < min) {
            return@mapNotNull null
        }

        BatteryRechargePack(
            type = pack.name,
            charges = pack.charges,
            amount = amount,
            isEnabled = BatteryRechargeMath.isEnough(amount, minBootstrapValue) && amount <= balance,
        )
    }
}
