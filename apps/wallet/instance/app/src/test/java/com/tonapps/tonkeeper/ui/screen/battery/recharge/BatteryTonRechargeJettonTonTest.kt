package com.tonapps.tonkeeper.ui.screen.battery.recharge

import com.tonapps.icu.Coins
import com.tonapps.wallet.data.battery.entity.BatteryConfigEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryTonRechargeJettonTonTest {

    private val config = BatteryConfigEntity.Empty.copy(
        transferCostDefault = "0.05",
        transferCostJettons = mapOf(USDT to "0.002"),
    )

    @Test
    fun `a relayed recharge attaches the battery's transfer cost of the jetton`() {
        assertNano("0.002", BatteryTonRecharge.jettonTransferTon(config, USDT, relayed = true))
    }

    @Test
    fun `a relayed recharge with a jetton the battery has no cost for attaches the default one`() {
        assertNano("0.05", BatteryTonRecharge.jettonTransferTon(config, OTHER_JETTON, relayed = true))
    }

    @Test
    fun `a recharge the wallet pays for attaches 0,1 TON`() {
        assertNano("0.1", BatteryTonRecharge.jettonTransferTon(config, USDT, relayed = false))
    }

    // The attached TON is sent in nanotons.
    private fun assertNano(expected: String, actual: Coins) {
        assertEquals(Coins.of(expected).toBigInteger(), actual.toBigInteger())
    }

    private companion object {
        const val USDT = "0:b113a994b5024a16719f69139328eb759596c38a25f59028b146fecdc3621dfe"
        const val OTHER_JETTON = "0:da6b1b6663a0e4d18cc8574ccd9db5296e367dd9324706f3bbd9eb1cd2caf0bf"
    }
}
