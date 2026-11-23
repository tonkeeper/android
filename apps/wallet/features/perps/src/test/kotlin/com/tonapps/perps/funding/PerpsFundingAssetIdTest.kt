package com.tonapps.perps.funding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerpsFundingAssetIdTest {

    @Test
    fun everyChainTheBackendQuotesDepositsFromIsASource() {
        listOf(
            "eth/mainnet/coin",
            "bsc/mainnet/coin",
            "base/mainnet/coin",
            "arb/mainnet/coin",
            "polygon/mainnet/coin",
            "avax/mainnet/coin",
            "robinhood/mainnet/coin",
            "ton/mainnet/coin",
            "tron/mainnet/coin",
            "btc/mainnet/coin",
        ).forEach { assetId ->
            val parsed = PerpsFundingAssetId.parse(assetId)
            assertNotNull(assetId, parsed)
            assertTrue(assetId, checkNotNull(parsed).isCoin)
        }
    }

    @Test
    fun tokensOfTheirOwnStandardAreSources() {
        val usdtTron = checkNotNull(PerpsFundingAssetId.parse("tron/mainnet/trc20/TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"))
        assertEquals("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t", usdtTron.tokenAddress)
        assertFalse(usdtTron.isEvm)

        val usdtBsc = checkNotNull(PerpsFundingAssetId.parse("bsc/mainnet/bep20/0x55d398326f99059ff775485246999027b3197955"))
        assertTrue(usdtBsc.isEvm)

        val usdcPolygon = checkNotNull(PerpsFundingAssetId.parse("polygon/mainnet/erc20/0x3C499c542cEF5E3811e1192ce70d8cC03d5c3359"))
        assertEquals("0x3c499c542cef5e3811e1192ce70d8cc03d5c3359", usdcPolygon.tokenAddress)
    }

    @Test
    fun usdtIsTheOneJettonOnTonThatIsASourceAndNoWithdrawalDestination() {
        val usdtTon = checkNotNull(PerpsFundingAssetId.parse(PerpsFundingAssetId.USDT_TON))
        assertFalse(usdtTon.isCoin)
        assertFalse(usdtTon.isEvm)
        assertEquals("ton/mainnet", usdtTon.chainId)
        assertEquals(PerpsFundingAssetId.USDT_TON_MASTER, usdtTon.tokenAddress)
        assertNotNull(PerpsFundingAssetId.parse("ton/mainnet/jetton/0:B113A994B5024A16719F69139328EB759596C38A25F59028B146FECDC3621DFE"))

        assertNull(PerpsFundingAssetId.parseDestination(PerpsFundingAssetId.USDT_TON))
        assertNull(PerpsFundingAssetId.parse("ton/mainnet/jetton/0:9f8e7d6c5b4a39281706f5e4d3c2b1a09f8e7d6c5b4a39281706f5e4d3c2b1a0"))
        assertNull(PerpsFundingAssetId.parse("ton/mainnet/jetton/EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs"))
        assertNull(PerpsFundingAssetId.parse("ton/mainnet/erc20/" + PerpsFundingAssetId.USDT_TON_MASTER))
        assertNull(PerpsFundingAssetId.parse("eth/mainnet/jetton/" + PerpsFundingAssetId.USDT_TON_MASTER))
    }

    @Test
    fun whatTheBackendDoesNotQuoteIsNoSource() {
        assertNull(PerpsFundingAssetId.parse("btc/mainnet/brc20/ordi"))
        assertNull(PerpsFundingAssetId.parse("sol/mainnet/coin"))
        assertNull(PerpsFundingAssetId.parse("bsc/mainnet/erc20/0x55d398326f99059ff775485246999027b3197955"))
        assertNull(PerpsFundingAssetId.parse("eth/testnet/coin"))
    }

    @Test
    fun theNewEvmChainsAreNoWithdrawalDestinationYet() {
        assertNull(PerpsFundingAssetId.parseDestination("polygon/mainnet/coin"))
        assertNotNull(PerpsFundingAssetId.parseDestination("btc/mainnet/coin"))
        assertNotNull(PerpsFundingAssetId.parseDestination("tron/mainnet/trc20/TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"))
    }
}
