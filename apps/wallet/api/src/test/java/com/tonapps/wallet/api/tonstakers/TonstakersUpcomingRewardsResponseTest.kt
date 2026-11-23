package com.tonapps.wallet.api.tonstakers

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class TonstakersUpcomingRewardsResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesTheFieldsTheForecastReads() {
        val response = json.decodeFromString<TonstakersUpcomingRewardsResponse>(
            """
            {
              "address": "0:abc",
              "balance_tston": 2000000000000,
              "wallet_tston": 1500000000000,
              "defi_tston": 500000000000,
              "rate": 1.0612,
              "items": [
                {"round_id": 9, "cycle_end": 1762000000, "roi_source": "round", "base_round_id": 7, "roi": 0.000223, "apy": null, "reward_ton": 473000000},
                {"round_id": 8, "cycle_end": 1761934464, "roi_source": "projected", "roi": 0.00022, "reward_ton": 466000000}
              ]
            }
            """.trimIndent()
        )

        assertEquals(1_500_000_000_000L, response.walletTstonNano)
        assertEquals(2_000_000_000_000L, response.balanceTstonNano)
        assertEquals(1.0612, response.tonPerTston, 0.0)
        assertEquals(1_762_000_000L, response.items[0].cycleEndSeconds)
        assertEquals(473_000_000L, response.items[0].rewardNano)
        assertEquals(0.00022, response.items[1].roi, 0.0)
    }
}
