package com.tonapps.tonkeeper.extensions

import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.ton.contract.WalletVersion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletSignMessageTest {

    @Test
    fun `a W5 R1 wallet signs messages`() {
        assertTrue(wallet(WalletVersion.V5R1).isTonConnectSignMessageAvailable)
    }

    /** Gasless relayers take the R1 `internal_signed` layout only, as iOS assumes. */
    @Test
    fun `a W5 beta wallet does not`() {
        assertFalse(wallet(WalletVersion.V5BETA).isTonConnectSignMessageAvailable)
    }

    @Test
    fun `a V4 wallet does not`() {
        assertFalse(wallet(WalletVersion.V4R2).isTonConnectSignMessageAvailable)
    }

    @Test
    fun `a watch-only W5 R1 wallet does not`() {
        assertFalse(wallet(WalletVersion.V5R1, WalletType.Watch).isTonConnectSignMessageAvailable)
    }

    private fun wallet(version: WalletVersion, type: WalletType = WalletType.Default): WalletEntity {
        return WalletEntity.EMPTY.copy(version = version, type = type)
    }
}
