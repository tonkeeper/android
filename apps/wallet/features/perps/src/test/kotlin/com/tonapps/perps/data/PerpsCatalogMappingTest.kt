package com.tonapps.perps.data

import com.tonapps.wallet.data.multichain.asset.AssetEntity
import io.Serializer
import io.tradingapi.models.AssetRefSummary
import io.tradingapi.models.MarketItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PerpsCatalogMappingTest {

    @Test
    fun unknownVerificationIsUnverifiedWithoutLosingTheCatalogRow() {
        for (verification in listOf("future_status", "unknown")) {
            val item = marketItem(verification)
            assertEquals(AssetRefSummary.Verification.unknown, item.asset.verification)

            val row = item.toCatalogSearchRow("usd") as CatalogSearchRow.Spot

            assertEquals("ton/mainnet/coin", row.id)
            assertEquals(AssetEntity.Verification.none, row.item.asset.verification)
            assertFalse(row.item.asset.verification.isVerified)
        }
    }

    @Test
    fun knownVerificationKeepsItsMeaning() {
        for (verification in AssetEntity.Verification.entries) {
            val row = marketItem(verification.name).toCatalogSearchRow("usd") as CatalogSearchRow.Spot

            assertEquals(verification, row.item.asset.verification)
        }
    }

    private fun marketItem(verification: String): MarketItem = Serializer.fromJSON(
        """
        {
          "asset": {
            "asset_type": "asset",
            "id": "ton/mainnet/coin",
            "name": "Toncoin",
            "symbol": "TON",
            "decimals": 9,
            "trust_score": 100,
            "image_url": "",
            "is_scam": false,
            "verification": "$verification"
          },
          "metrics": {
            "price": "1",
            "change_24h_percent": "2",
            "provider": "test",
            "as_of": "2026-10-08T00:00:00Z",
            "volume": "10"
          }
        }
        """.trimIndent(),
    )
}
