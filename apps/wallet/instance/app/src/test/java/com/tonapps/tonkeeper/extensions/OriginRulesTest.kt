package com.tonapps.tonkeeper.extensions

import org.junit.Assert.assertEquals
import org.junit.Test

class OriginRulesTest {

    @Test
    fun `a host is covered together with its subdomains`() {
        assertEquals(
            setOf("https://earn.example", "https://*.earn.example"),
            siteOriginRules("https", "earn.example", -1),
        )
    }

    @Test
    fun `an explicit port is kept in both rules`() {
        assertEquals(
            setOf("https://earn.example:8443", "https://*.earn.example:8443"),
            siteOriginRules("https", "earn.example", 8443),
        )
    }

    @Test
    fun `an ip literal gets no wildcard rule`() {
        assertEquals(setOf("https://10.0.2.2:8443"), siteOriginRules("https", "10.0.2.2", 8443))
        assertEquals(setOf("https://[::1]"), siteOriginRules("https", "[::1]", -1))
    }

    @Test
    fun `only https hosts are covered`() {
        assertEquals(emptySet<String>(), siteOriginRules("http", "earn.example", -1))
        assertEquals(emptySet<String>(), siteOriginRules("https", "", -1))
        assertEquals(emptySet<String>(), siteOriginRules("https", null, -1))
    }
}
