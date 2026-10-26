package com.tonapps.bus.core.aptabase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AptabaseEndpointTest {

    @Test
    fun selfHostedKeyUsesConfiguredHostWithoutTrailingSlash() {
        assertEquals(
            "https://anonymous-analytics.tonkeeper.com",
            AptabaseEndpoint.resolve("A-SH-4314447490", "https://anonymous-analytics.tonkeeper.com/"),
        )
    }

    @Test
    fun selfHostedKeyWithoutHostIsDisabled() {
        assertNull(AptabaseEndpoint.resolve("A-SH-4314447490", null))
        assertNull(AptabaseEndpoint.resolve("A-SH-4314447490", "  "))
    }

    @Test
    fun cloudRegionsIgnoreConfiguredHost() {
        assertEquals("https://us.aptabase.com", AptabaseEndpoint.resolve("A-US-123", "https://ignored"))
        assertEquals("https://eu.aptabase.com", AptabaseEndpoint.resolve("A-EU-123", null))
    }

    @Test
    fun malformedKeyIsDisabled() {
        assertNull(AptabaseEndpoint.resolve("A-XX-123", "https://host"))
        assertNull(AptabaseEndpoint.resolve("broken", "https://host"))
        assertNull(AptabaseEndpoint.resolve("A-SH-1-2", "https://host"))
    }
}
