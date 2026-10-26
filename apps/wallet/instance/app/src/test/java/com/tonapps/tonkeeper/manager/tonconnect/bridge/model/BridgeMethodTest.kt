package com.tonapps.tonkeeper.manager.tonconnect.bridge.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BridgeMethodTest {

    @Test
    fun resolvesSignMessage() {
        assertEquals(BridgeMethod.SIGN_MESSAGE, BridgeMethod.of("signMessage"))
    }

    @Test
    fun unknownMethodsFallBackToUnknown() {
        assertEquals(BridgeMethod.UNKNOWN, BridgeMethod.of("signTransaction"))
    }
}
