package com.tonapps.perps.data

import com.tonapps.wallet.data.multichain.asset.AssetEntity
import io.Serializer
import io.tradingapi.models.AssetRef
import io.tradingapi.models.AssetRefSummary
import io.tradingapi.models.MarketItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerpsCatalogMappingTest {

    @Test
    fun aPerpRowIsListedOnlyUnderTheSymbolTheSignedConfigGivesItsMarket() {
        assertTrue(perpItem(marketIndex = 2, symbol = "SOL").isListedIn(SIGNED))
        assertTrue(perpItem(marketIndex = 4, symbol = "1000PEPE").isListedIn(SIGNED))
        assertFalse(perpItem(marketIndex = 161, symbol = "SKHYNIX").isListedIn(SIGNED))
        assertFalse(perpItem(marketIndex = 2, symbol = "BTC").isListedIn(SIGNED))
        assertFalse(perpItem(marketIndex = 2, symbol = "sol").isListedIn(SIGNED))
        assertFalse(perpItem(marketIndex = 9, symbol = "SOL").isListedIn(SIGNED))
        assertFalse(perpItem(marketIndex = 2, symbol = "SOL").isListedIn(emptyMap()))
    }

    @Test
    fun withoutAVerifiedConfigTheCatalogIsShownAsServed() {
        assertTrue(perpItem(marketIndex = 161, symbol = "SKHYNIX").isListedIn(null))
        assertTrue(skhynixDetails().isListedIn(null, SKHYNIX))
        assertFalse(skhynixDetails().isListedIn(null, 2))
    }

    @Test
    fun aSpotRowIsNotHeldToThePerpsListing() {
        assertTrue(marketItem("trusted").isListedIn(emptyMap()))
    }

    @Test
    fun theMarketDetailsAreListedUnderTheirPerpsSymbolForTheRequestedMarketOnly() {
        val skhynix = skhynixDetails()
        val perp = checkNotNull(skhynix.perps)

        assertTrue(skhynix.isListedIn(SIGNED, SKHYNIX))
        assertTrue(skhynix.copy(perps = perp.copy(symbol = "")).isListedIn(mapOf(SKHYNIX to "SKHYNIX"), SKHYNIX))
        assertFalse(skhynix.copy(perps = perp.copy(symbol = "SKHYNIX")).isListedIn(SIGNED, SKHYNIX))
        assertFalse(skhynix.copy(perps = perp.copy(marketIndex = 2)).isListedIn(SIGNED, SKHYNIX))
        assertFalse(skhynix.isListedIn(SIGNED, 2))
        assertFalse(skhynix.copy(perps = null).isListedIn(SIGNED, SKHYNIX))
        assertFalse(skhynix.isListedIn(emptyMap(), SKHYNIX))
    }

    private fun skhynixDetails(): AssetRef {
        val json = checkNotNull(PerpsCatalogMappingTest::class.java.getResource(SKHYNIX_DETAILS)).readText()
        return Serializer.fromJSON(json)
    }

    private fun perpItem(marketIndex: Int, symbol: String): MarketItem = Serializer.fromJSON(
        """
        {
          "asset": {
            "asset_type": "perpetuals",
            "id": "lighter/mainnet/market/$marketIndex",
            "name": "$symbol",
            "symbol": "$symbol",
            "decimals": 0,
            "trust_score": 100,
            "image_url": "",
            "is_scam": false,
            "verification": "trusted",
            "leverage": 10
          },
          "metrics": {
            "price": "1",
            "change_24h_percent": "2",
            "provider": "test",
            "as_of": "2026-11-12T00:00:00Z",
            "volume": "10"
          }
        }
        """.trimIndent(),
    )

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

    private companion object {
        const val SKHYNIX = 161
        const val SKHYNIX_DETAILS = "perps_asset_skhynix.json"
        val SIGNED = mapOf(2 to "SOL", 4 to "1000PEPE", SKHYNIX to "SKHYNIXUSD")
    }
}
