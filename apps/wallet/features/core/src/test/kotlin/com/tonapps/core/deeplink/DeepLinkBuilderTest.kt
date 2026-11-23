package com.tonapps.core.deeplink

import com.tonapps.bus.generated.Events.Perps.PerpsEntryPoint
import com.trustwallet.core.WalletCoreLibLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Test

class DeepLinkBuilderTest {

    private val tonAddress = "UQDSIRiH6lXGVPlw15N_-At-A9d0BXGq2jVo3ArxCwLKz8Mj"
    private val tronAddress = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"

    @Test
    fun dropAmountOnlyQueryKeepsTextWithoutQuery() {
        assertEquals(tonAddress, DeepLinkBuilder.dropAmountOnlyQuery(tonAddress))
    }

    @Test
    fun dropAmountOnlyQueryDropsAmountAndEmptyQuery() {
        assertEquals(tonAddress, DeepLinkBuilder.dropAmountOnlyQuery("$tonAddress?amount=1"))
        assertEquals(tonAddress, DeepLinkBuilder.dropAmountOnlyQuery("$tonAddress?"))
    }

    @Test
    fun dropAmountOnlyQueryRejectsOtherParameters() {
        assertNull(DeepLinkBuilder.dropAmountOnlyQuery("$tonAddress?amount=1&text=hi"))
        assertNull(DeepLinkBuilder.dropAmountOnlyQuery("$tonAddress?text=hi"))
        assertNull(DeepLinkBuilder.dropAmountOnlyQuery("$tonAddress?jetton=EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs"))
    }

    @Test
    fun plainAddressOrNullAcceptsAddressWithAmountOnlyQuery() {
        assertEquals(tonAddress, DeepLinkBuilder.plainAddressOrNull(tonAddress))
        assertEquals(tonAddress, DeepLinkBuilder.plainAddressOrNull("$tonAddress?amount=1"))
        assertEquals(tonAddress, DeepLinkBuilder.plainAddressOrNull("$tonAddress?"))
        assertEquals(tronAddress, DeepLinkBuilder.plainAddressOrNull("$tronAddress?amount=200"))
    }

    @Test
    fun plainAddressOrNullRejectsOtherParametersAndNonAddresses() {
        assertNull(DeepLinkBuilder.plainAddressOrNull("$tonAddress?amount=1&text=hi"))
        assertNull(DeepLinkBuilder.plainAddressOrNull("$tonAddress?text=hi"))
        assertNull(DeepLinkBuilder.plainAddressOrNull("garbage?amount=1"))
        assertNull(DeepLinkBuilder.plainAddressOrNull("?amount=1"))
    }

    @Test
    fun perpsLinkKeepsDestination() {
        assertEquals("tonkeeper://perps", DeepLinkBuilder.perps(DeepLinkRoute.Perps.Home(), entryPoint = null))
        assertEquals("tonkeeper://perps/market/7", DeepLinkBuilder.perps(DeepLinkRoute.Perps.Market(7), entryPoint = null))
        assertEquals(
            "tonkeeper://perps/market/7/auto-close",
            DeepLinkBuilder.perps(DeepLinkRoute.Perps.AutoClose(7), entryPoint = null),
        )
        assertEquals("tonkeeper://perps/market/ANT", DeepLinkBuilder.perps(DeepLinkRoute.Perps.MarketSymbol("ANT"), entryPoint = null))
    }

    @Test
    fun perpsLinkMarksEntryPointOnlyWhenAsked() {
        assertEquals(
            "tonkeeper://perps/market/7?entry_point=main_banner",
            DeepLinkBuilder.perps(DeepLinkRoute.Perps.Market(7, PerpsEntryPoint.MainBanner), PerpsEntryPoint.MainBanner),
        )
        assertEquals(
            "tonkeeper://perps?entry_point=loyalty",
            DeepLinkBuilder.perps(DeepLinkRoute.Perps.Home(), PerpsEntryPoint.Loyalty),
        )
        assertEquals(
            "tonkeeper://perps",
            DeepLinkBuilder.perps(DeepLinkRoute.Perps.Home(PerpsEntryPoint.MainBanner), entryPoint = null),
        )
    }

    companion object {

        @JvmStatic
        @BeforeClass
        fun loadWalletCore() {
            WalletCoreLibLoader.loadLibrary()
        }
    }
}
