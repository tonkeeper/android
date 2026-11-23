package com.tonapps.tonkeeper.manager.push

import com.tonapps.core.deeplink.DeepLinkRoute
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushDisplayFilterTest {

    private fun isPerpsOrLoyalty(data: Map<String, String>, route: DeepLinkRoute? = null): Boolean {
        return PushDisplayFilter.isPerpsOrLoyalty(route, data::get)
    }

    @Test
    fun perpsRoutePasses() {
        assertTrue(isPerpsOrLoyalty(emptyMap(), DeepLinkRoute.Perps.Home()))
    }

    @Test
    fun perpsTypePasses() {
        assertTrue(isPerpsOrLoyalty(mapOf("type" to "perps_liquidation")))
    }

    @Test
    fun loyaltyKindPasses() {
        val data = mapOf(
            "kind" to "loyalty_raffle_won",
            "mode" to "always",
            "wallet_id" to "mc-wallet",
            "deeplink" to "tonkeeper://dapp/https://loyalty.keeperwallet.com/",
        )
        assertTrue(isPerpsOrLoyalty(data))
    }

    @Test
    fun everyCurrentLoyaltyKindPasses() {
        listOf("loyalty_raffle_won", "loyalty_raffle_lost", "loyalty_lottery_won", "loyalty_lottery_lost")
            .forEach { kind -> assertTrue(kind, isPerpsOrLoyalty(mapOf("kind" to kind))) }
    }

    @Test
    fun loyaltyPrefixInTypeDoesNotPass() {
        assertFalse(isPerpsOrLoyalty(mapOf("type" to "loyalty_raffle_won")))
    }

    @Test
    fun otherKindDoesNotPass() {
        assertFalse(isPerpsOrLoyalty(mapOf("kind" to "battery_charged")))
    }

    @Test
    fun transferPushDoesNotPass() {
        assertFalse(isPerpsOrLoyalty(mapOf("wallet_id" to "mc-wallet", "deeplink" to "tonkeeper://history")))
    }
}
