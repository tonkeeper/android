package com.tonapps.wallet.api.entity

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class PerpsAccessTest {

    private fun configWithState(state: String) = JSONObject(
        """{"flags":{"perps_enabled":true},"feature_access":{"perps":{"state":"$state","reason":"region"}}}""",
    )

    private fun enabledConfigWithFeatureAccess(featureAccess: String) = JSONObject(
        """{"flags":{"perps_enabled":true},"feature_access":$featureAccess}""",
    )

    @Test
    fun eachKnownStateParsesToItsValue() {
        assertEquals(PerpsAccess.Available, PerpsAccess.of(configWithState("available")))
        assertEquals(PerpsAccess.Restricted, PerpsAccess.of(configWithState("restricted")))
        assertEquals(PerpsAccess.Hidden, PerpsAccess.of(configWithState("hidden")))
    }

    @Test
    fun withoutFeatureAccessTheLegacyFlagDecides() {
        assertEquals(PerpsAccess.Available, PerpsAccess.of(JSONObject("""{"flags":{"perps_enabled":true}}""")))
        assertEquals(PerpsAccess.Hidden, PerpsAccess.of(JSONObject("""{"flags":{"perps_enabled":false}}""")))
        assertEquals(PerpsAccess.Hidden, PerpsAccess.of(JSONObject("{}")))
    }

    @Test
    fun nullFeatureAccessBehavesAsMissing() {
        assertEquals(PerpsAccess.Available, PerpsAccess.of(enabledConfigWithFeatureAccess("null")))
        assertEquals(
            PerpsAccess.Hidden,
            PerpsAccess.of(JSONObject("""{"flags":{"perps_enabled":false},"feature_access":null}""")),
        )
    }

    @Test
    fun malformedOrUnknownFeatureAccessIsHidden() {
        val malformed = listOf(
            "{}",
            """{"perps":null}""",
            """{"perps":{}}""",
            """{"perps":{"state":"blocked"}}""",
            """{"perps":{"state":1}}""",
            """{"perps":"restricted"}""",
            "\"restricted\"",
            "[]",
        )
        malformed.forEach { featureAccess ->
            assertEquals(featureAccess, PerpsAccess.Hidden, PerpsAccess.of(enabledConfigWithFeatureAccess(featureAccess)))
        }
    }
}
