package com.tonapps.wallet.api.tonstakers

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TonstakersRewardsResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun nullableFieldsMayBeNullOrAbsent() {
        val response = json.decodeFromString<TonstakersRewardsResponse>(
            """
            {
              "syncing": false,
              "total_reward_ton": null,
              "pagination": {"limit": null, "offset": 0, "total": 2},
              "items": [
                {"kind": "reward", "ts": 1761000000, "round_id": 7, "reward_ton": 1250000000, "apy": 0.1272},
                {"kind": "reward", "ts": 1760900000, "reward_ton": null}
              ]
            }
            """.trimIndent()
        )

        assertNull(response.totalRewardNano)
        assertEquals(2, response.pagination.totalCount)
        assertEquals(1_250_000_000L, response.items[0].rewardNano)
        assertNull(response.items[1].roundId)
        assertNull(response.items[1].rewardNano)
        assertNull(response.items[1].apyFraction)
    }
}
