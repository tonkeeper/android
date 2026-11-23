package com.tonapps.perps.trade

import com.tonapps.chainkit.perpetual.PerpsAutoCloseLeg
import com.tonapps.chainkit.perpetual.PerpsAutoCloseSpec
import com.tonapps.chainkit.perpetual.PerpsOrderSpec
import com.tonapps.chainkit.perpetual.PerpsScope
import com.tonapps.chainkit.perpetual.PerpsSide
import com.tonapps.chainkit.perpetual.PerpsTradeIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PerpsConfirmedAutoCloseStoreTest {

    @Test
    fun `only the wallet's legs are read`() {
        val entries = mapOf(
            "$WALLET_ID/101" to "90000/$NOW",
            "$WALLET_ID/102" to "78000/$NOW",
            "$WALLET_ID-other/103" to "1/$NOW",
            "other/104" to "2/$NOW",
        )

        assertEquals(mapOf(101L to 90_000L, 102L to 78_000L), confirmedAutoCloseTriggers(entries, WALLET_ID))
    }

    @Test
    fun `malformed entries are skipped`() {
        val entries = mapOf(
            "$WALLET_ID/101" to "90000/$NOW",
            "$WALLET_ID/index" to "90000/$NOW",
            "$WALLET_ID/102" to "90000",
            "$WALLET_ID/103" to "price/$NOW",
            "$WALLET_ID/104" to "90000/$NOW/1",
            "$WALLET_ID/105" to 90_000L,
            "$WALLET_ID/106" to null,
        )

        assertEquals(mapOf(101L to 90_000L), confirmedAutoCloseTriggers(entries, WALLET_ID))
    }

    @Test
    fun `entries older than the expiry plus the renewal lead are stale`() {
        val keepAfterMs = NOW - PerpsProductRules.AUTO_CLOSE_EXPIRY_MS - PerpsProductRules.AUTO_CLOSE_RENEWAL_LEAD_MS
        val entries = mapOf(
            "$WALLET_ID/101" to "90000/${keepAfterMs - 1}",
            "other/102" to "78000/${keepAfterMs - 1}",
            "$WALLET_ID/103" to "90000/$keepAfterMs",
            "$WALLET_ID/104" to "90000/$NOW",
            "$WALLET_ID/105" to "malformed",
        )

        assertEquals(listOf("$WALLET_ID/101", "other/102"), staleConfirmedAutoCloseKeys(entries, NOW))
    }

    @Test
    fun `every intent that places legs exposes them`() {
        assertEquals(SPEC, open().autoCloseSpec())
        assertEquals(SPEC, add().autoCloseSpec())
        assertEquals(SPEC, close().autoCloseSpec())
        assertEquals(SPEC, autoClose().autoCloseSpec())
    }

    @Test
    fun `intents without legs expose none`() {
        val margin = PerpsTradeIntent.Margin(
            operationId = OPERATION_ID,
            scope = SCOPE,
            marketId = MARKET_ID,
            positionId = POSITION_ID,
            amountQuote = 1_000_000,
            addToPosition = true,
        )

        assertNull(margin.autoCloseSpec())
        assertNull(close(autoClose = null).autoCloseSpec())
    }

    private fun open() = PerpsTradeIntent.Open(
        operationId = OPERATION_ID,
        scope = SCOPE,
        marketId = MARKET_ID,
        side = PerpsSide.Long,
        marginBudgetQuote = 1_000_000,
        initialMarginBps = 1_000,
        clientOrderIndex = 100,
        order = PerpsOrderSpec.Market(),
        autoClose = SPEC,
    )

    private fun add() = PerpsTradeIntent.Add(
        operationId = OPERATION_ID,
        scope = SCOPE,
        marketId = MARKET_ID,
        positionId = POSITION_ID,
        side = PerpsSide.Long,
        marginBudgetQuote = 1_000_000,
        initialMarginBps = 1_000,
        clientOrderIndex = 100,
        order = PerpsOrderSpec.Market(),
        autoClose = SPEC,
    )

    private fun close(autoClose: PerpsAutoCloseSpec? = SPEC) = PerpsTradeIntent.Close(
        operationId = OPERATION_ID,
        scope = SCOPE,
        marketId = MARKET_ID,
        positionId = POSITION_ID,
        side = PerpsSide.Long,
        positionBaseAmount = 600,
        baseAmount = 300,
        maxSlippagePpm = 50_000,
        clientOrderIndex = 100,
        autoClose = autoClose,
    )

    private fun autoClose() = PerpsTradeIntent.AutoClose(
        operationId = OPERATION_ID,
        scope = SCOPE,
        marketId = MARKET_ID,
        positionId = POSITION_ID,
        side = PerpsSide.Long,
        autoClose = SPEC,
    )

    private companion object {
        const val WALLET_ID = "wallet"
        const val OPERATION_ID = "operation"
        const val POSITION_ID = "position"
        const val MARKET_ID = 1L
        const val NOW = 1_800_000_000_000L
        val SCOPE = PerpsScope(
            walletId = WALLET_ID,
            venue = "lighter",
            chainId = 304,
            environment = "mainnet",
            accountIndex = 1,
            keyBindingId = "binding",
            expectedPublicKey = "0xkey",
        )
        val SPEC = PerpsAutoCloseSpec(
            takeProfit = PerpsAutoCloseLeg(triggerPrice = 90_000, expiryUnixMs = NOW, clientOrderIndex = 101),
            stopLoss = PerpsAutoCloseLeg(triggerPrice = 78_000, expiryUnixMs = NOW, clientOrderIndex = 102),
        )
    }
}
