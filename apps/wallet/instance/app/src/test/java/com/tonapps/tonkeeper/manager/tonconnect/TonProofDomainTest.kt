package com.tonapps.tonkeeper.manager.tonconnect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TonProofDomainTest {

    @Test
    fun `an injected connect signs the page host, not the manifest host`() {
        assertEquals("evil.com", tonProofDomain(jsInject = true, pageHost = "evil.com", manifestHost = "getgems.io"))
    }

    @Test
    fun `an injected connect signs the page host when it differs from the manifest subdomain`() {
        assertEquals("app.example.com", tonProofDomain(jsInject = true, pageHost = "app.example.com", manifestHost = "example.com"))
    }

    @Test
    fun `an injected connect without a page host has nothing to sign`() {
        assertNull(tonProofDomain(jsInject = true, pageHost = null, manifestHost = "getgems.io"))
        assertNull(tonProofDomain(jsInject = true, pageHost = "", manifestHost = "getgems.io"))
    }

    @Test
    fun `a QR or deeplink connect signs the manifest host and ignores the referrer`() {
        assertEquals("getgems.io", tonProofDomain(jsInject = false, pageHost = "t.me", manifestHost = "getgems.io"))
        assertEquals("getgems.io", tonProofDomain(jsInject = false, pageHost = null, manifestHost = "getgems.io"))
    }
}
