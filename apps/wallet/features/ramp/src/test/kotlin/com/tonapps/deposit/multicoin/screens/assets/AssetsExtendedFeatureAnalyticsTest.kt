package com.tonapps.deposit.multicoin.screens.assets

import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.DefaultEvents
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowDepositScreen
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.deposit.multicoin.data.RampAsset
import com.tonapps.deposit.multicoin.data.RampRepository
import com.tonapps.deposit.screens.ramp.RampType
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AssetsExtendedFeatureAnalyticsTest : BaseUnitTest() {

    private val tracked = mutableListOf<Pair<String, Map<String, Any>>>()

    @Before
    fun recordAnalytics() {
        val recording = object : EventExecutor {
            override fun trackEvent(eventName: String, props: Map<String, Any>) {
                tracked += eventName to props
            }
        }
        val helper = mockk<AnalyticsHelper> {
            every { events } returns DefaultEvents(recording)
            every { eventsTaggedWith(any()) } returns DefaultEvents(recording)
        }
        mockkObject(AnalyticsHelper.Companion)
        every { AnalyticsHelper.Default } returns helper
    }

    @After
    fun stopRecordingAnalytics() {
        unmockkObject(AnalyticsHelper.Companion)
    }

    @Test
    fun `selecting an asset reports its deposit screen`() {
        for ((screen, expected) in listOf(
            DepositFlowDepositScreen.BuyCrypto to "buy_crypto",
            DepositFlowDepositScreen.AddFunds to "add_funds",
        )) {
            val feature = AssetsExtendedFeature(
                data = AssetsExtendedFeatureData(
                    walletId = "wallet",
                    rampType = RampType.RampOn,
                    analyticsFrom = DepositFlowFrom.WalletScreen,
                    depositScreen = screen,
                ),
                rampRepository = mockk<RampRepository>(relaxed = true),
            )
            feature.onSelected(RampAsset(TestAssets.usdtTon, "TON", listOf("USD")))
            val event = tracked.last { it.first == "deposit_click_fiat_asset" }
            assertEquals(expected, event.second["deposit_screen"])
        }
    }
}
