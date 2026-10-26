package com.tonapps.tonkeeper.manager.tonconnect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignMessageTimeoutTest {

    private val now = 1_800_000_000L

    @Test
    fun missingValidUntilExpiresAfterDefaultTtl() {
        assertEquals(now + 300, TonConnectSignMessageValidation.signValidUntil(0, now))
    }

    @Test
    fun validUntilWithinCapIsSignedAsRequested() {
        assertEquals(now + 590, TonConnectSignMessageValidation.signValidUntil(now + 590, now))
    }

    @Test
    fun validUntilBeyondCapIsCappedAtTenMinutes() {
        assertEquals(now + 600, TonConnectSignMessageValidation.signValidUntil(now + 3600, now))
    }

    @Test
    fun expiredValidUntilIsRejectedBeforeSigning() {
        assertNull(TonConnectSignMessageValidation.signValidUntil(now, now))
    }
}
