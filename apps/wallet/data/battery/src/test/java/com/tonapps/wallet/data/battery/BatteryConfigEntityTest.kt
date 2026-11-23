package com.tonapps.wallet.data.battery

import com.tonapps.icu.Coins
import com.tonapps.wallet.data.battery.entity.BatteryConfigEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryConfigEntityTest {

    private val usdtRaw = "0:b113a994b5024a16719f69139328eb759596c38a25f59028b146fecdc3621dfe"
    private val usdtFriendly = "EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs"
    private val otherJetton = "0:da6b1b6663a0e4d18cc8574ccd9db5296e367dd9324706f3bbd9eb1cd2caf0bf"

    @Test
    fun jettonOverrideMatchesRawAndFriendlyAddress() {
        val config = config(default = "0.05", jettons = mapOf(usdtRaw to "0.002"))
        assertNano("0.002", config.transferCost(usdtRaw))
        assertNano("0.002", config.transferCost(usdtFriendly))
    }

    @Test
    fun unknownJettonTakesDefault() {
        val config = config(default = "0.04", jettons = mapOf(usdtRaw to "0.002"))
        assertNano("0.04", config.transferCost(otherJetton))
    }

    @Test
    fun missingJettonMasterTakesDefault() {
        val config = config(default = "0.04", jettons = mapOf(usdtRaw to "0.002"))
        assertNano("0.04", config.transferCost(null))
        assertNano("0.04", config.transferCost("TON"))
    }

    @Test
    fun unusableValueFallsBackToBaseForwardAmount() {
        assertNano("0.05", config(default = "", jettons = emptyMap()).transferCost(null))
        assertNano("0.05", config(default = "0", jettons = emptyMap()).transferCost(null))
        assertNano("0.05", config(default = "0.04", jettons = mapOf(usdtRaw to "abc")).transferCost(usdtRaw))
    }

    @Test
    fun emptyConfigUsesBaseForwardAmount() {
        assertNano("0.05", BatteryConfigEntity.Empty.transferCost(usdtRaw))
    }

    private fun config(default: String, jettons: Map<String, String>) = BatteryConfigEntity.Empty.copy(
        transferCostDefault = default,
        transferCostJettons = jettons,
    )

    // Coins equality is scale-sensitive; compare nanotons.
    private fun assertNano(expected: String, actual: Coins) {
        assertEquals(Coins.of(expected).toLong(), actual.toLong())
    }
}
