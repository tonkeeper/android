package com.tonapps.core.deeplink

import com.tonapps.core.deeplink.DeepLinkRoute.Transfer.Companion.MAX_EXP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferLinkDeadlineTest {

    @Test
    fun aLinkWithoutExpLastsTheMaximumFromTheServersNow() {
        val link = transfer(exp = null)

        assertEquals(NOW + MAX_EXP, link.validUntil(NOW))
        assertFalse(link.isExpired(NOW))
    }

    @Test
    fun aLinkWithZeroExpLastsTheMaximumFromTheServersNow() {
        assertEquals(NOW + MAX_EXP, transfer(exp = 0).validUntil(NOW))
    }

    @Test
    fun aLinksOwnExpIsKeptUpToTheMaximum() {
        assertEquals(NOW + 60, transfer(exp = NOW + 60).validUntil(NOW))
        assertEquals(NOW + MAX_EXP, transfer(exp = NOW + MAX_EXP * 6).validUntil(NOW))
    }

    @Test
    fun aLinkIsExpiredShortlyBeforeItsExp() {
        assertTrue(transfer(exp = NOW - 1).isExpired(NOW))
        assertTrue(transfer(exp = NOW + 10).isExpired(NOW))
        assertFalse(transfer(exp = NOW + 60).isExpired(NOW))
    }

    @Test
    fun aTonConnectRequestKeepsTheLinksDeadlineRule() {
        assertEquals(NOW + MAX_EXP, DeepLinkRoute.Transfer.validUntil(0, NOW))
        assertEquals(NOW + 60, DeepLinkRoute.Transfer.validUntil(NOW + 60, NOW))
        assertEquals(NOW + MAX_EXP, DeepLinkRoute.Transfer.validUntil(NOW + MAX_EXP * 6, NOW))
        assertTrue(DeepLinkRoute.Transfer.isExpired(NOW + 10, NOW))
        assertFalse(DeepLinkRoute.Transfer.isExpired(NOW + 60, NOW))
    }

    private fun transfer(exp: Long?) = DeepLinkRoute.Transfer(
        exp = exp,
        address = "EQD",
        amount = null,
        text = null,
        assetId = null,
        jettonAddress = null,
        bin = null,
        initStateBase64 = null,
    )

    private companion object {
        const val NOW = 1_800_000_000L
    }
}
