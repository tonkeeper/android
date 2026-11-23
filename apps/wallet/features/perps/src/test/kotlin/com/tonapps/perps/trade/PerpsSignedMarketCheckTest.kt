package com.tonapps.perps.trade

import com.tonapps.chainkit.perpetual.PerpsTradeError
import com.tonapps.chainkit.perpetual.PerpsTradeException
import com.tonapps.perps.security.PerpsSecurityConfig
import com.tonapps.perps.security.PerpsSignedMarket
import io.perpsapi.models.Market
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

class PerpsSignedMarketCheckTest {

    @Test
    fun `a listed market within the signable range is the signed one`() {
        val signed = PerpsSignedMarketCheck.signedMarket(MARKET_INDEX, served(MARKET_INDEX), config(MARKET_INDEX))

        assertEquals("LIT", signed.symbol)
    }

    @Test
    fun `a market number beyond a short is refused even when a config lists it`() {
        val outOfRange = Short.MAX_VALUE + 1

        val refusal = assertThrows(PerpsTradeException::class.java) {
            PerpsSignedMarketCheck.signedMarket(outOfRange, served(outOfRange), config(outOfRange))
        }

        assertEquals(PerpsTradeError.UnsupportedMarket, refusal.kind)
        assertEquals(SIGNED_MARKET_FIELD, refusal.field)
    }

    @Test
    fun `a negative market number is refused`() {
        val refusal = assertThrows(PerpsTradeException::class.java) {
            PerpsSignedMarketCheck.signedMarket(-1, served(-1), config(-1))
        }

        assertEquals(PerpsTradeError.UnsupportedMarket, refusal.kind)
    }

    @Test
    fun `a served symbol other than the signed one is refused`() {
        val refusal = assertThrows(PerpsTradeException::class.java) {
            PerpsSignedMarketCheck.signedMarket(MARKET_INDEX, served(MARKET_INDEX).copy(symbol = "SKHYNIX"), config(MARKET_INDEX))
        }

        assertEquals(PerpsTradeError.InvalidRules, refusal.kind)
        assertEquals(SIGNED_MARKET_FIELD, refusal.field)
    }

    private fun served(marketIndex: Int) = Market(
        marketIndex = marketIndex,
        symbol = "LIT",
        priceDecimals = 5,
        sizeDecimals = 1,
        minSizeBase = "0.1",
        minSizeQuote = "1",
        maxLeverage = 10,
    )

    private fun config(marketIndex: Int) = PerpsSecurityConfig(
        version = 1,
        hermesDelegate = "",
        hermesDelegateChains = emptySet(),
        changePubKeyContract = "",
        lighterRelayAccounts = emptySet(),
        tonRelayDepositories = emptySet(),
        lighterGateway = null,
        evmRelayDepositories = emptyMap(),
        evmRelayRouters = emptyMap(),
        tronRelayDepositories = emptySet(),
        btcRelayDepositories = emptySet(),
        markets = mapOf(
            marketIndex to PerpsSignedMarket(
                marketId = marketIndex,
                symbol = "LIT",
                priceDecimals = 5,
                sizeDecimals = 1,
                quoteDecimals = 6,
                minBaseAmount = BigDecimal("0.1"),
                minQuoteAmount = BigDecimal("1"),
                maxLeverage = 10,
            ),
        ),
    )

    private companion object {
        const val MARKET_INDEX = 120
    }
}
