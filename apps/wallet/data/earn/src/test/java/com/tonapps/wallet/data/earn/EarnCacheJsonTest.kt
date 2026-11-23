package com.tonapps.wallet.data.earn

import io.earnapi.models.Balance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.OffsetDateTime

class EarnCacheJsonTest {

    @Test
    fun aBalanceWithItsLoadTimeSurvivesTheDiskCache() {
        val balance = Balance(
            totalFiat = mapOf("USD" to "12.5"),
            updatedAt = OffsetDateTime.parse("2026-10-28T12:00:00.123Z"),
            positions = emptyList(),
        )

        assertEquals(balance, EarnCacheJson.decode<Balance>(EarnCacheJson.encode(balance)))
    }

    @Test
    fun aBalanceCachedBeforeTheLoadTimeIsAMiss() {
        assertNull(EarnCacheJson.decode<Balance>("""{"total_fiat":{"USD":"12.5"},"positions":[]}"""))
    }
}
