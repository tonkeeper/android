package com.tonapps.wallet.data.ledger.analytics

import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.DefaultEvents
import com.tonapps.bus.generated.Events.LedgerFlow.LedgerDiscoveryErrorFrom
import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.transport.LedgerModel
import com.tonapps.wallet.data.ledger.LedgerChainApp
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerAppsSkippedTest {

    private val tracked = ArrayList<Pair<String, Map<String, Any>>>()
    private val events = DefaultEvents(
        object : EventExecutor {
            override fun trackEvent(eventName: String, props: Map<String, Any>) {
                tracked += eventName to props
            }
        },
    )

    @Test
    fun `every skipped app is reported once with its reason and the device model`() {
        events.ledgerFlow.ledgerAppsSkipped(
            from = LedgerDiscoveryErrorFrom.AddNetworks,
            skipped = mapOf(
                LedgerChainApp.Bitcoin to LedgerFailure.AppUpdateRequired(LedgerAppId.Bitcoin, "2.1.0", "2.1.2"),
                LedgerChainApp.Solana to LedgerFailure.AppNotInstalled(LedgerAppId.Solana),
            ),
            model = LedgerModel.NanoX,
        )

        assertEquals(
            listOf(
                "ledger_discovery_error" to mapOf(
                    "from" to "add_networks",
                    "ledger_app" to "bitcoin",
                    "failure_type" to "app_update_required",
                    "ledger_model" to "nano_x",
                ),
                "ledger_discovery_error" to mapOf(
                    "from" to "add_networks",
                    "ledger_app" to "solana",
                    "failure_type" to "app_not_installed",
                    "ledger_model" to "nano_x",
                ),
            ),
            tracked,
        )
    }

    @Test
    fun `an app the user closed the request for sends nothing`() {
        events.ledgerFlow.ledgerAppsSkipped(
            from = LedgerDiscoveryErrorFrom.Import,
            skipped = mapOf(LedgerChainApp.Tron to LedgerFailure.Cancelled),
            model = null,
        )

        assertEquals(emptyList<Pair<String, Map<String, Any>>>(), tracked)
    }
}
