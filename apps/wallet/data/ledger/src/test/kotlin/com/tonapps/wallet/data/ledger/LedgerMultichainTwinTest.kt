package com.tonapps.wallet.data.ledger

import com.tonapps.blockchain.model.legacy.Wallet
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.ton.extensions.publicKeyFromHex
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.trustwallet.core.WalletCoreLibLoader
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class LedgerMultichainTwinTest {

    private val legacy = mockk<WalletEntity> {
        every { id } returns LEGACY
        every { isLedger } returns true
        every { testnet } returns false
        every { ledger } returns WalletEntity.Ledger(deviceId = "AA:BB:CC:DD:EE:01", accountIndex = 1)
        every { publicKey } returns KEY.publicKeyFromHex()
        every { label } returns Wallet.Label("Legacy")
    }

    @Before
    fun setUp() {
        WalletCoreLibLoader.loadLibrary()
    }

    private fun interactor(twins: Map<String, Boolean>): LedgerWalletInteractor {
        val unified = mockk<UnifiedAccountRepository> {
            coEvery { getTonWalletById(LEGACY) } returns legacy
            coEvery { getTonWalletById(neq(LEGACY)) } returns null
        }
        val repository = mockk<McAccountRepository> {
            coEvery { findWalletIdsByAddress(Chain.Ton.Mainnet, any()) } returns twins.keys.toList()
            for ((walletId, isHardware) in twins) {
                coEvery { getWallet(walletId) } returns mockk<McWalletEntity> { every { this@mockk.isHardware } returns isHardware }
            }
        }
        return LedgerWalletInteractorImpl(mockk(), mockk(), repository, unified)
    }

    @Test
    fun `the multichain twin of a legacy ledger is the hardware wallet with its ton address`() = runTest {
        val twinId = interactor(mapOf("software" to false, "hardware" to true)).multichainTwinId(LEGACY)
        assertEquals("hardware", twinId)
    }

    @Test
    fun `a software wallet with the same address or an unknown wallet is not a twin`() = runTest {
        val interactor = interactor(mapOf("software" to false))
        assertNull(interactor.multichainTwinId(LEGACY))
        assertNull(interactor.multichainTwinId("unknown"))
    }

    private companion object {
        const val LEGACY = "legacy"
        const val KEY = "6dbbf455bcffce6166ae00f7146cd8264be47f48a43f4ec2ce6b3775341d82cd"
    }
}
