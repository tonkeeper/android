package com.tonapps.tonkeeper.ui.screen.battery.refill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryRefillSelectionTest {

    @Test
    fun thePreviousPackStaysWhileEnabled() {
        val packs = listOf(PackChoice("a", 0, true), PackChoice("b", 0, true), PackChoice("c", 0, true))

        assertEquals("c", selectPack("c", packs))
    }

    @Test
    fun aDisabledPreviousPackGivesWayToTheMiddleEnabledOne() {
        val packs = listOf(
            PackChoice("a", 0, false),
            PackChoice("b", 0, true),
            PackChoice("c", 0, true),
            PackChoice("d", 0, true),
        )

        assertEquals("c", selectPack("a", packs))
    }

    @Test
    fun theOnlyEnabledPackIsSelected() {
        val packs = listOf(PackChoice("a", 0, false), PackChoice("b", 0, true), PackChoice("c", 0, false))

        assertEquals("b", selectPack(null, packs))
    }

    @Test
    fun withNoEnabledPackThePreviousOneIsKept() {
        val packs = listOf(PackChoice("a", 0, false), PackChoice("b", 0, false))

        assertEquals("b", selectPack("b", packs))
    }

    @Test
    fun anEmptyListSelectsNothing() {
        assertNull(selectPack("a", emptyList()))
    }

    @Test
    fun aSinglePackIsNoBestValue() {
        assertNull(bestValueId(listOf(PackChoice("a", 100, true))))
    }

    @Test
    fun theLargestOfThreePacksIsTheBestValue() {
        val packs = listOf(PackChoice("a", 100, true), PackChoice("b", 500, true), PackChoice("c", 250, true))

        assertEquals("b", bestValueId(packs))
    }

    @Test
    fun aStoredTokenWaitsForTheTokensToLoad() {
        assertEquals(MethodChoice.Loading, resolveMethod(TOKEN_A, tokens = null, storeAvailable = true))
    }

    @Test
    fun aStoredTokenThatIsGoneFallsBackToTheStore() {
        assertEquals(MethodChoice.Store, resolveMethod(TOKEN_A, tokens = listOf(TOKEN_B), storeAvailable = true))
    }

    @Test
    fun aStoredTokenThatIsGoneFallsBackToTheFirstTokenWithoutTheStore() {
        assertEquals(
            MethodChoice.Token(TOKEN_B),
            resolveMethod(TOKEN_A, tokens = listOf(TOKEN_B, TOKEN_C), storeAvailable = false),
        )
    }

    @Test
    fun nothingStoredPicksTheStoreWhenAvailable() {
        assertEquals(MethodChoice.Store, resolveMethod(null, tokens = listOf(TOKEN_A), storeAvailable = true))
    }

    @Test
    fun nothingStoredNoStoreAndNoTokensIsNone() {
        assertEquals(MethodChoice.None, resolveMethod(null, tokens = emptyList(), storeAvailable = false))
    }

    @Test
    fun aStoredStoreThatIsUnavailableFallsBackToTheFirstToken() {
        assertEquals(
            MethodChoice.Token(TOKEN_A),
            resolveMethod(STORE_PAY_METHOD, tokens = listOf(TOKEN_A, TOKEN_B), storeAvailable = false),
        )
    }

    @Test
    fun aStoreNotAllowedStaticallyIsUnavailableWhateverIsStillLoading() {
        assertEquals(
            false,
            storeAvailability(staticAllowed = false, iapKnown = false, productsLoaded = false, hasPricedPack = false),
        )
    }

    @Test
    fun productsNotLoadedLeaveTheStoreUnknown() {
        assertNull(storeAvailability(staticAllowed = true, iapKnown = true, productsLoaded = false, hasPricedPack = false))
    }

    @Test
    fun loadedProductsWithoutAPricedPackMakeTheStoreUnavailable() {
        assertEquals(
            false,
            storeAvailability(staticAllowed = true, iapKnown = true, productsLoaded = true, hasPricedPack = false),
        )
    }

    @Test
    fun aBusyScreenKeepsTheSelectedTitleAndDisablesTheButton() {
        assertEquals(
            ButtonChoice("$5", enabled = false),
            buttonState(hasPacks = true, selectedTitle = "$5", selectedEnabled = true, busy = true),
        )
    }

    @Test
    fun noPacksMeanNoButton() {
        assertNull(buttonState(hasPacks = false, selectedTitle = null, selectedEnabled = false, busy = false))
    }

    @Test
    fun theDefaultPackStaysSelectedDuringAPurchase() {
        val packs = listOf(PackChoice("a", 0, true), PackChoice("b", 0, true), PackChoice("c", 0, true))
        val beforePurchase = selectPack(null, packs)

        val duringPurchase = selectPack(null, packs)
        val button = buttonState(hasPacks = true, selectedTitle = duringPurchase, selectedEnabled = true, busy = true)

        assertEquals("b", beforePurchase)
        assertEquals(beforePurchase, duringPurchase)
        assertEquals("b", button?.title)
        assertFalse(button?.enabled ?: true)
    }

    private companion object {
        const val TOKEN_A = "0:aaa"
        const val TOKEN_B = "0:bbb"
        const val TOKEN_C = "0:ccc"
    }
}
