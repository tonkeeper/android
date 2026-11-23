package com.tonapps.wallet.data.earn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EarnPortfolioDirtyFlagsTest {

    @Test
    fun aWalletStaysDirtyForAMinuteAfterItsEarnTransaction() {
        val flags = EarnPortfolioDirtyFlags()
        flags.markDirty(WALLET_ID, nowMillis = MARKED_AT)

        assertTrue(flags.isDirty(WALLET_ID, nowMillis = MARKED_AT))
        assertTrue(flags.isDirty(WALLET_ID, nowMillis = MARKED_AT + 59_000))
        assertFalse(flags.isDirty(WALLET_ID, nowMillis = MARKED_AT + 60_000))
    }

    private companion object {
        private const val WALLET_ID = "wallet"
        private const val MARKED_AT = 1_000_000L
    }
}
