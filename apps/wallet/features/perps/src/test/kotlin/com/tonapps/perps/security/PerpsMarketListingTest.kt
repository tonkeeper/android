package com.tonapps.perps.security

import com.tonapps.perps.data.PerpsPosition
import com.tonapps.perps.data.PerpsPositionSide
import com.tonapps.perps.data.isListedIn
import com.tonapps.perps.trade.PerpsSignedMarkets
import io.Serializer
import io.tradingapi.models.MarketItem
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

import org.junit.Test

class PerpsMarketListingTest {

    private val reports = mutableListOf<Pair<Throwable, Map<String, String>>>()

    private val listing = PerpsMarketListing(
        securityConfig = PerpsSignedMarkets.repository(
            listOf(
                PerpsSecurityBlocks.market(marketId = SOL, symbol = "SOL"),
                PerpsSecurityBlocks.market(marketId = SKHYNIX, symbol = "SKHYNIXUSD"),
            ),
        ),
        report = { error, keys -> reports += error to keys },
    )

    @Test
    fun `every market the catalog lists today is listed by today's signed config except the three korean stocks`() = runTest {
        val block = checkNotNull(PerpsMarketListingTest::class.java.getResource(PRODUCTION_BLOCK_WITH_MARKETS)).readText()
        val production = PerpsMarketListing(
            securityConfig = PerpsSecurityConfigRepository(servedBlock = { block }, store = NothingKept),
            report = { _, _ -> },
        )
        val signedSymbols = production.signedSymbols()
        val catalog = checkNotNull(PerpsMarketListingTest::class.java.getResource(CATALOG_SYMBOLS)).readText()
            .lines()
            .filter { it.isNotBlank() }
            .map { line -> line.substringBefore(' ').toInt() to line.substringAfter(' ') }

        val unlisted = catalog.filterNot { (marketIndex, symbol) -> catalogItem(marketIndex, symbol).isListedIn(signedSymbols) }

        assertEquals(204, catalog.size)
        assertEquals(listOf(160 to "HYUNDAI", 161 to "SKHYNIX", 162 to "SAMSUNG"), unlisted)
    }

    @Test
    fun `a failing crash reporter does not fail the positions read`() = runTest {
        val failing = PerpsMarketListing(
            securityConfig = PerpsSignedMarkets.repository(listOf(PerpsSecurityBlocks.market(marketId = SOL, symbol = "SOL"))),
            report = { _, _ -> error("crashlytics is not initialized") },
        )

        failing.reportUnlisted(listOf(position(UNLISTED, "MKR")))

        val retried = mutableListOf<Int>()
        val flaky = PerpsMarketListing(
            securityConfig = PerpsSignedMarkets.repository(listOf(PerpsSecurityBlocks.market(marketId = SOL, symbol = "SOL"))),
            report = { _, keys ->
                retried += keys.getValue("perps_market_index").toInt()
                if (retried.size == 1) {
                    error("crashlytics is not initialized")
                }
            },
        )
        flaky.reportUnlisted(listOf(position(UNLISTED, "MKR")))
        flaky.reportUnlisted(listOf(position(UNLISTED, "MKR")))
        flaky.reportUnlisted(listOf(position(UNLISTED, "MKR")))

        assertEquals(listOf(UNLISTED), retried)
    }

    @Test
    fun `the signed symbols are the listed markets by number`() = runTest {
        assertEquals(mapOf(SOL to "SOL", SKHYNIX to "SKHYNIXUSD"), listing.signedSymbols())
    }

    @Test
    fun `only the signed symbol lists a market`() = runTest {
        assertTrue(listing.isListed(SOL, "SOL"))
        assertFalse(listing.isListed(SKHYNIX, "SKHYNIX"))
        assertFalse(listing.isListed(SOL, "sol"))
        assertFalse(listing.isListed(UNLISTED, "SOL"))
    }

    @Test
    fun `a position on an unlisted market is reported once per market`() = runTest {
        listing.reportUnlisted(listOf(position(SOL, "SOL"), position(UNLISTED, "MKR")))
        listing.reportUnlisted(listOf(position(UNLISTED, "MKR")))

        assertEquals(1, reports.size)
        val (error, keys) = reports.single()
        assertTrue(error is PerpsUnlistedPositionException)
        assertEquals(UNLISTED.toString(), keys["perps_market_index"])
        assertEquals("MKR", keys["perps_symbol"])
        assertEquals("1", keys["perps_security_version"])
    }

    @Test
    fun `positions on listed markets are not reported`() = runTest {
        listing.reportUnlisted(listOf(position(SOL, "SOL"), position(SKHYNIX, "SKHYNIXUSD")))

        assertTrue(reports.isEmpty())
    }

    @Test
    fun `without a verified config nothing is reported and the config download is not awaited`() = runTest {
        var downloads = 0
        val unverified = PerpsSecurityConfigRepository(
            servedBlock = { null },
            store = NothingKept,
            awaitDownloadedConfig = {
                downloads++
                awaitCancellation()
            },
        )
        val silent = PerpsMarketListing(unverified, report = { error, keys -> reports += error to keys })

        silent.reportUnlisted(listOf(position(UNLISTED, "MKR")))

        assertTrue(reports.isEmpty())
        assertEquals(null, silent.signedSymbols())
        assertTrue(silent.isListed(SKHYNIX, "SKHYNIX"))
        assertEquals(0, downloads)
    }

    @Test
    fun `a verified config without markets lists everything as served and reports nothing`() = runTest {
        val withoutMarkets = PerpsMarketListing(
            securityConfig = PerpsSignedMarkets.repository(null),
            report = { error, keys -> reports += error to keys },
        )

        withoutMarkets.reportUnlisted(listOf(position(SOL, "SOL"), position(UNLISTED, "MKR")))

        assertTrue(reports.isEmpty())
        assertEquals(null, withoutMarkets.signedSymbols())
        assertTrue(withoutMarkets.isListed(SKHYNIX, "SKHYNIX"))
        assertTrue(catalogItem(SOL, "SOL").isListedIn(withoutMarkets.signedSymbols()))
    }

    private fun position(marketIndex: Int, symbol: String) = PerpsPosition(
        marketIndex = marketIndex,
        symbol = symbol,
        iconUrl = null,
        side = PerpsPositionSide.LONG,
        margin = null,
        unrealizedPnl = null,
        equity = null,
        leverage = null,
        liquidationDistancePct = null,
    )

    private fun catalogItem(marketIndex: Int, symbol: String): MarketItem = Serializer.fromJSON(
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
            "verification": "trusted"
          },
          "metrics": {
            "price": "1",
            "change_24h_percent": "0",
            "provider": "test",
            "as_of": "2026-11-12T00:00:00Z",
            "volume": "1"
          }
        }
        """.trimIndent(),
    )

    private object NothingKept : PerpsSecurityConfigStore {

        override fun keptBlock(): String? = null

        override fun keep(block: String) = Unit
    }

    private companion object {
        const val PRODUCTION_BLOCK_WITH_MARKETS = "production_block_markets.json"
        const val CATALOG_SYMBOLS = "catalog_symbols_2026_11_12.txt"
        const val SOL = 2
        const val SKHYNIX = 161
        const val UNLISTED = 28
    }
}
