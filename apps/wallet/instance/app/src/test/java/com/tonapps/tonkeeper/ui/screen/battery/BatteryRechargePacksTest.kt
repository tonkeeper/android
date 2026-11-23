package com.tonapps.tonkeeper.ui.screen.battery

import com.tonapps.tonkeeper.ui.screen.battery.recharge.asset.BatteryRechargePack
import com.tonapps.tonkeeper.ui.screen.battery.recharge.asset.batteryRechargePacks
import com.tonapps.wallet.data.battery.entity.BatteryPackageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class BatteryRechargePacksTest {

    private val packages = listOf(
        BatteryPackageEntity(name = "large", image = "", charges = 3000),
        BatteryPackageEntity(name = "medium", image = "", charges = 1500),
        BatteryPackageEntity(name = "small", image = "", charges = 750),
    )

    // 750 charges cost 2.245509 USDT, 1500 about 4.49 and 3000 about 8.98.
    private val chargesRate = BigDecimal("334")

    private fun packs(minBootstrapValue: String?, balance: String = "100"): List<BatteryRechargePack> {
        return batteryRechargePacks(packages, chargesRate, decimals = 6, minBootstrapValue, BigDecimal(balance))
    }

    @Test
    fun aPackBelowTheLeastPurchaseIsLeftOut() {
        assertEquals(listOf("large", "medium"), packs(minBootstrapValue = "2.25").map { it.type })
        assertEquals(listOf("large"), packs(minBootstrapValue = "5").map { it.type })
    }

    @Test
    fun aPackAtTheLeastPurchaseIsOffered() {
        val small = packs(minBootstrapValue = "2.245509").single { it.type == "small" }

        assertEquals(BigDecimal("2.245509"), small.amount)
        assertTrue(small.isEnabled)
    }

    @Test
    fun aPackTheBalanceDoesNotCoverStaysDisabled() {
        val packs = packs(minBootstrapValue = "2.25", balance = "5")

        assertEquals(listOf("large", "medium"), packs.map { it.type })
        assertFalse(packs.single { it.type == "large" }.isEnabled)
        assertTrue(packs.single { it.type == "medium" }.isEnabled)
    }

    @Test
    fun withoutALeastPurchaseEveryPackIsOffered() {
        val packs = packs(minBootstrapValue = null)

        assertEquals(listOf("large", "medium", "small"), packs.map { it.type })
        assertTrue(packs.all { it.isEnabled })
    }

    @Test
    fun aPackWithoutChargesIsLeftOut() {
        val packs = batteryRechargePacks(
            packages = packages + BatteryPackageEntity(name = "empty", image = "", charges = 0),
            chargesRate = chargesRate,
            decimals = 6,
            minBootstrapValue = null,
            balance = BigDecimal("100"),
        )

        assertEquals(listOf("large", "medium", "small"), packs.map { it.type })
    }
}
