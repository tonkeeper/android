package com.tonapps.trading.screens.shelves

import com.tonapps.core.navigation.PortfolioSearchAssetType
import io.tradingapi.models.AssetsTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShelfSeeAllTest {

    @Test
    fun `a stocks route opens the search on stocks`() {
        assertEquals(PortfolioSearchAssetType.STOCKS, AssetsTab.stocks.toPortfolioSearchAssetType())
    }

    @Test
    fun `an etfs route opens the search on ETFs`() {
        assertEquals(PortfolioSearchAssetType.ETFS, AssetsTab.etfs.toPortfolioSearchAssetType())
    }

    @Test
    fun `a tokens route opens the search on tokens`() {
        assertEquals(PortfolioSearchAssetType.TOKENS, AssetsTab.tokens.toPortfolioSearchAssetType())
    }

    @Test
    fun `any other route opens the search on all assets`() {
        listOf(AssetsTab.all, AssetsTab.commodities, AssetsTab.perpetuals, AssetsTab.unknown).forEach { tab ->
            assertNull("$tab", tab.toPortfolioSearchAssetType())
        }
    }
}
