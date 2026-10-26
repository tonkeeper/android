package com.tonapps.tonkeeper.extensions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostMatchesTest {

    @Test
    fun `the host itself matches`() {
        assertTrue(hostMatches("vaults.fyi", "vaults.fyi"))
    }

    @Test
    fun `a subdomain matches`() {
        assertTrue(hostMatches("app.vaults.fyi", "vaults.fyi"))
    }

    @Test
    fun `a nested subdomain matches`() {
        assertTrue(hostMatches("earn.app.vaults.fyi", "vaults.fyi"))
    }

    @Test
    fun `a host merely ending with the same text is refused`() {
        assertFalse(hostMatches("evil-vaults.fyi", "vaults.fyi"))
    }

    @Test
    fun `the base as a prefix of another domain is refused`() {
        assertFalse(hostMatches("vaults.fyi.attacker.com", "vaults.fyi"))
    }

    @Test
    fun `matching ignores case`() {
        assertTrue(hostMatches("APP.Vaults.Fyi", "vaults.fyi"))
    }

    @Test
    fun `the relation does not hold the other way round`() {
        assertFalse(hostMatches("vaults.fyi", "app.vaults.fyi"))
    }

    @Test
    fun `a missing host never matches`() {
        assertFalse(hostMatches(null, "vaults.fyi"))
        assertFalse(hostMatches("vaults.fyi", null))
    }
}
