package com.tonapps.wallet.features.events.data

import io.JsonAny
import io.walletapi.models.Activity
import io.walletapi.models.ActivityDirection
import io.walletapi.models.ActivityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.OffsetDateTime

class PerpsLoyaltyRewardMappingTest {

    @Test
    fun `a credited reward is kept as the backend sent it`() {
        val event = map(perpsActivity(reward(amount = "11")), showsLoyaltyReward = true)

        assertEquals("11", event.perps?.loyaltyReward)
    }

    @Test
    fun `a fractional reward is not rounded`() {
        val event = map(perpsActivity(reward(amount = "2.2")), showsLoyaltyReward = true)

        assertEquals("2.2", event.perps?.loyaltyReward)
    }

    @Test
    fun `a reward with a padded scale drops the trailing zeros`() {
        val event = map(perpsActivity(reward(amount = "12.000000")), showsLoyaltyReward = true)

        assertEquals("12", event.perps?.loyaltyReward)
    }

    @Test
    fun `a boosted reward shows the amount with the boost already inside`() {
        val event = map(perpsActivity(reward(amount = "15", boost = "5")), showsLoyaltyReward = true)

        assertEquals("15", event.perps?.loyaltyReward)
    }

    @Test
    fun `a zero reward hides the row`() {
        val event = map(perpsActivity(reward(amount = "0")), showsLoyaltyReward = true)

        assertNull(event.perps?.loyaltyReward)
    }

    @Test
    fun `a missing reward hides the row`() {
        val event = map(perpsActivity(reward = null), showsLoyaltyReward = true)

        assertNull(event.perps?.loyaltyReward)
    }

    @Test
    fun `a reward in another asset hides the row`() {
        val event = map(perpsActivity(reward(amount = "11", asset = "USDC")), showsLoyaltyReward = true)

        assertNull(event.perps?.loyaltyReward)
    }

    @Test
    fun `a reward is dropped while the loyalty section is off`() {
        val event = map(perpsActivity(reward(amount = "11")), showsLoyaltyReward = false)

        assertNull(event.perps?.loyaltyReward)
    }

    private fun map(activity: Activity, showsLoyaltyReward: Boolean) =
        requireNotNull(mapActivityToEntity(activity, showsLoyaltyReward) { null })

    private fun reward(amount: String, asset: String = "OHM", boost: String? = null): JsonAny =
        JsonAny.Object(
            buildMap {
                put("asset", JsonAny.Primitive(asset))
                put("amount", JsonAny.Primitive(amount))
                boost?.let { put("boost", JsonAny.Primitive(it)) }
            }
        )

    private fun perpsActivity(reward: JsonAny?): Activity {
        val perps = buildMap {
            put("symbol", JsonAny.Primitive("BTC"))
            put("side", JsonAny.Primitive("long"))
            reward?.let { put("reward", it) }
        }
        return Activity(
            activityType = "perps.position_opened",
            status = ActivityStatus.confirmed,
            blockTime = OffsetDateTime.parse("2026-11-13T12:00:00Z"),
            fromChain = "lighter",
            toChain = "lighter",
            direction = ActivityDirection.self,
            txIds = listOf("lighter:a818e3159db2bf80c2676e6e9fd2bd46"),
            meta = mapOf("perps" to JsonAny.Object(perps)),
        )
    }
}
