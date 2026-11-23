package com.tonapps.perps.data

import io.perpsapi.models.Position
import io.perpsapi.models.TradingFlags
import io.perpsapi.models.TradingScreen
import org.junit.Assert.assertEquals
import org.junit.Test

class PerpsTradingFlagsMappingTest {

    @Test
    fun flatScreenDoesNotDisableCloseOrMarginOfAPositionItDoesNotHold() {
        val flags = TradingScreen(position = null, flags = FLAT_MARKET_FLAGS).toPerpsTradingFlags()

        assertEquals(
            PerpsTradingFlags(
                openEnabled = true,
                closeEnabled = true,
                cancelEnabled = false,
                addMarginEnabled = true,
                removeMarginEnabled = true,
                autoCloseEnabled = false,
            ),
            flags,
        )
    }

    @Test
    fun screenHoldingThePositionKeepsItsRefusals() {
        val flags = TradingScreen(position = Position(marketIndex = 3), flags = FLAT_MARKET_FLAGS).toPerpsTradingFlags()

        assertEquals(
            PerpsTradingFlags(
                openEnabled = true,
                closeEnabled = false,
                cancelEnabled = false,
                addMarginEnabled = false,
                removeMarginEnabled = false,
                autoCloseEnabled = false,
            ),
            flags,
        )
    }

    @Test
    fun screenHoldingThePositionTreatsOmittedFlagsAsAllowedExceptOpen() {
        val flags = TradingScreen(position = Position(marketIndex = 3), flags = TradingFlags()).toPerpsTradingFlags()

        assertEquals(PerpsTradingFlags.ALL_ENABLED.copy(openEnabled = false), flags)
    }

    @Test
    fun screenWithoutFlagsStaysVenueSilent() {
        assertEquals(PerpsTradingFlags.VENUE_SILENT, TradingScreen(position = Position(marketIndex = 3)).toPerpsTradingFlags())
    }

    private companion object {
        val FLAT_MARKET_FLAGS = TradingFlags(
            openEnabled = true,
            closeEnabled = false,
            cancelEnabled = false,
            addMarginEnabled = false,
            removeMarginEnabled = false,
            autoCloseEnabled = false,
        )
    }
}
