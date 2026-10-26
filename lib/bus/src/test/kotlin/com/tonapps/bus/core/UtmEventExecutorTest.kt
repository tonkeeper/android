package com.tonapps.bus.core

import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.DefaultEvents
import com.tonapps.bus.generated.Events.DeeplinkOpen.DeeplinkOpenFrom
import com.tonapps.bus.generated.Events.DeeplinkOpen.DeeplinkOpenLinkType
import org.junit.Assert.assertEquals
import org.junit.Test

class UtmEventExecutorTest {

    private class RecordingExecutor : EventExecutor {
        val events = ArrayList<Pair<String, Map<String, Any>>>()

        override fun trackEvent(eventName: String, props: Map<String, Any>) {
            events += eventName to props
        }
    }

    @Test
    fun taggedEventsCarryTheCampaignOnTopOfTheirOwnProps() {
        val recording = RecordingExecutor()
        val utm = UtmParameters(source = "newsletter", campaign = "autumn")

        DefaultEvents(UtmEventExecutor(recording, utm)).deeplinkOpen.deeplinkOpen(
            from = DeeplinkOpenFrom.AppLink,
            linkType = DeeplinkOpenLinkType.Staking,
            isColdStart = true,
        )

        val (name, props) = recording.events.single()
        assertEquals("deeplink_open", name)
        assertEquals(
            mapOf(
                "from" to "app_link",
                "link_type" to "staking",
                "is_cold_start" to true,
                "utm_source" to "newsletter",
                "utm_campaign" to "autumn",
            ),
            props,
        )
    }
}
