package com.tonapps.wallet.data.multichain.account

import android.util.Log
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.WalletAuth
import com.tonapps.chainkit.core.chain.model.account.WalletKeyPair
import com.tonapps.chainkit.core.chain.model.account.WalletKind
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.data.multichain.wallet.McWalletType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import com.trustwallet.core.WalletCoreLibLoader
import io.walletapi.models.RegisterWallets200Response
import io.walletapi.models.RegisterWalletsRequest
import io.walletapi.models.WalletChallenge
import io.walletapi.models.WalletRegisterResult
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.After
import org.junit.Test

class WalletRegistrationProofTest {

    private val tonAccount = AccountEntity(
        walletId = LEGACY_ID,
        network = Chain.Ton.Mainnet.network.type.id,
        mode = Chain.Ton.Mainnet.network.mode.id,
        displayAddress = "0:27bafbc6d4a559505c397864e9e2a68c69ffbea37c61f34b54d114231d3906f3",
        publicKey = "",
        segwitPublicKey = "",
        addressType = Address.Type.TonV5R1,
        id = "ton-v5r1",
    )
    private val solAccount = tonAccount.copy(
        network = Chain.Solana.Mainnet.network.type.id,
        mode = Chain.Solana.Mainnet.network.mode.id,
        displayAddress = "HDYQ9UHgvKavsWNoLwWfi5sRNEpFEm6Nrm2EaLK13NzR",
        addressType = Address.Type.Default,
        id = "sol",
    )

    private val api = mockk<API>(relaxed = true)
    private val accountDao = mockk<AccountDao>(relaxed = true) {
        coEvery { getAccountsByWalletId(LEGACY_ID) } returns listOf(tonAccount, solAccount)
    }
    private val sent = slot<RegisterWalletsRequest>()

    init {
        WalletCoreLibLoader.loadLibrary()
        mockkStatic(Log::class)
        every { Log.println(any(), any(), any()) } returns 0
        mockkObject(WalletKeyPair.Companion)
        every { WalletKeyPair.fromPrivateKey(any(), any<WalletKind>()) } returns mockk(relaxed = true)
        every { api.multichain.wallets.getWalletChallenge() } returns WalletChallenge(challenge = "challenge", expiresAt = java.time.OffsetDateTime.now())
        every { api.multichain.auth.registerWallets(capture(sent), any()) } returns RegisterWallets200Response(
            results = listOf(WalletRegisterResult(index = 0, status = WalletRegisterResult.Status.ok, walletId = PROOF_ID)),
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun repository(isHardware: Boolean) = AccountRepositoryImpl(
        accountDao = accountDao,
        walletDao = mockk {
            coEvery { getWallet(LEGACY_ID) } returns McWalletEntity(
                credentialId = if (isHardware) { "hw:$LEGACY_ID" } else { "credential" },
                name = "Wallet",
                id = LEGACY_ID,
                color = 0,
                type = McWalletType.Multicoin,
            )
        },
        credentialDao = mockk(),
        walletBundleDao = mockk(),
        jsonCacheRepository = mockk(),
        appKeyRepository = mockk { coEvery { getAppPrivateKey(LEGACY_ID) } returns ByteArray(32) { 7 } },
        deviceRepository = mockk { coEvery { requireDeviceId() } returns "device" },
        provider = mockk {
            every { signWalletRegisterProof(any(), any(), any(), any()) } returns mockk<WalletAuth.RegisterProof> {
                every { walletId } returns PROOF_ID
                every { signature } returns "signature"
            }
        },
        settings = mockk(relaxed = true),
        api = api,
        perpetualRepository = mockk(),
        earnRepository = mockk(),
    )

    @Test
    fun `wallet stored under an older id is still registered with the id its proof carries`() = runTest {
        repository(isHardware = false).removeChains(LEGACY_ID, listOf(Chain.Solana.Mainnet))

        assertEquals(listOf(PROOF_ID), sent.captured.wallets.map { it.walletId })
        coVerify { accountDao.deleteAccounts(LEGACY_ID, Chain.Solana.Mainnet) }
    }

    @Test
    fun `hardware wallet whose proof names another wallet is not registered`() = runTest {
        val repository = repository(isHardware = true)
        assertThrows(WalletRegistrationException::class.java) {
            runBlocking { repository.removeChains(LEGACY_ID, listOf(Chain.Solana.Mainnet)) }
        }
        assertFalse(sent.isCaptured)
    }

    private companion object {
        const val LEGACY_ID = "6humw3uhgiu77f4jlicetrwm2ql6tnq3"
        const val PROOF_ID = "aiazarnfre5ah7h223zikol6tcrkbeptxibq"
    }
}
