package com.tonapps.wallet.data.multichain.account

import android.util.Log
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.LedgerWallet
import com.tonapps.chainkit.core.chain.model.account.WalletAuth
import com.tonapps.chainkit.core.chain.model.account.WalletKeyPair
import com.tonapps.security.multichain.MnemonicCoder
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.multichain.hardware.HardwareAccountDraft
import com.tonapps.wallet.data.multichain.hardware.HardwareWalletDescriptor
import com.tonapps.wallet.data.multichain.vault.WalletKeyRepository
import com.tonapps.wallet.data.multichain.wallet.CredentialDao
import com.tonapps.wallet.data.multichain.wallet.CredentialEntity
import com.tonapps.wallet.data.multichain.wallet.CredentialIds
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.data.multichain.wallet.McWalletType
import com.tonapps.wallet.data.multichain.wallet.WalletBundleDao
import com.tonapps.wallet.data.multichain.wallet.WalletDao
import com.trustwallet.core.WalletCoreLibLoader
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.walletapi.models.RegisterWallets200Response
import io.walletapi.models.WalletChallenge
import io.walletapi.models.WalletRegisterResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class HardwareWalletKeysTest {

    private val api = mockk<API>(relaxed = true)
    private val walletDao = mockk<WalletDao>(relaxed = true)
    private val credentialDao = mockk<CredentialDao>(relaxed = true)
    private val walletBundleDao = mockk<WalletBundleDao>(relaxed = true)
    private val appKeyRepository = mockk<WalletKeyRepository>(relaxed = true)
    private val mnemonicCoder = mockk<MnemonicCoder> {
        every { encryptBytes(any()) } answers { firstArg<ByteArray>().copyOf() }
        every { decryptBytes(any()) } answers { firstArg<ByteArray>().copyOf() }
    }

    init {
        WalletCoreLibLoader.loadLibrary()
        every { api.multichain.wallets.getWalletChallenge() } returns WalletChallenge(challenge = "challenge", expiresAt = java.time.OffsetDateTime.now())
        every { api.multichain.auth.registerWallets(any(), any()) } returns RegisterWallets200Response(
            results = listOf(WalletRegisterResult(index = 0, status = WalletRegisterResult.Status.ok, walletId = LEDGER_WALLET_ID)),
        )
        mockkStatic(Log::class)
        every { Log.println(any(), any(), any()) } returns 0
    }

    private fun repository() = AccountRepositoryImpl(
        accountDao = mockk(relaxed = true),
        walletDao = walletDao,
        credentialDao = credentialDao,
        walletBundleDao = walletBundleDao,
        jsonCacheRepository = mockk(),
        appKeyRepository = appKeyRepository,
        deviceRepository = mockk { coEvery { requireDeviceId() } returns "device" },
        provider = mockk {
            every { signWalletRegisterProof(any(), any(), any(), any()) } answers {
                val keyPair = firstArg<WalletKeyPair>()
                mockk<WalletAuth.RegisterProof> {
                    every { walletId } returns keyPair.walletIdV2()
                    every { signature } returns "signature"
                }
            }
        },
        settings = mockk(relaxed = true),
        api = api,
        perpetualRepository = mockk(),
        earnRepository = mockk(),
    )

    @Test
    fun `a ledger wallet is stored under its ledger id with the seed it came from`() = runTest {
        val credentials = slot<List<CredentialEntity>>()
        val sessionKey = slot<ByteArray>()
        coEvery { walletBundleDao.insertNewHardwareWallet(capture(credentials), any(), any()) } just runs
        every { appKeyRepository.sealedEntity(any(), capture(sessionKey)) } answers {
            CredentialEntity(data = byteArrayOf(1), id = CredentialIds.appKey(firstArg()))
        }
        val seed = ledgerSeed()

        val wallet = repository().createHardwareWallet(
            descriptor = HardwareWalletDescriptor(deviceId = "device", deviceName = "Stax", accountIndex = 0, derivationPaths = emptyMap()),
            accounts = listOf(HardwareAccountDraft(Chain.Ton.Mainnet, Address.Type.TonV4R2, TON_ADDRESS, TON_PUBLIC_KEY)),
            walletSeed = seed.copyOf(),
            mnemonicCoder = mnemonicCoder,
            name = "Ledger",
            emoji = "",
            color = 0,
        )

        assertEquals(LEDGER_WALLET_ID, wallet.id)
        assertArrayEquals(seed, credentials.captured.single { it.id == CredentialIds.hardwareAppKey(LEDGER_WALLET_ID) }.data)
        assertEquals(LEDGER_AUTH_KEY, sessionKey.captured.hex())
    }

    @Test
    fun `a ledger wallet key lost with the session comes back from its stored seed`() = runTest {
        val restored = slot<ByteArray>()
        every { walletDao.getWallets() } returns listOf(
            McWalletEntity(
                credentialId = CredentialIds.hardwareDescriptor(LEDGER_WALLET_ID),
                name = "Ledger",
                id = LEDGER_WALLET_ID,
                color = 0,
                type = McWalletType.Multicoin,
            )
        )
        every { appKeyRepository.hasAppKey(LEDGER_WALLET_ID) } returns false
        every { credentialDao.getData(CredentialIds.hardwareAppKey(LEDGER_WALLET_ID)) } returns ledgerSeed()
        coEvery { appKeyRepository.save(LEDGER_WALLET_ID, capture(restored)) } just runs

        repository().restoreHardwareAppKeys(mnemonicCoder)

        assertEquals(LEDGER_AUTH_KEY, restored.captured.hex())
    }

    private fun ledgerSeed(): ByteArray =
        LedgerWallet.fromProofSignature(TON_PUBLIC_KEY.hexToBytes(), PROOF_SIGNATURE.hexToBytes()).unwrap().seed()

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private companion object {
        const val TON_ADDRESS = "UQCiJG2EX7-MZEp-v6gLR9x07fa73VkwKjqtHd9jb3bYrMh3"
        const val TON_PUBLIC_KEY = "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"
        const val PROOF_SIGNATURE = "6d2886a1f35d7d963a3bd5466e55f8f2cbe946154514bc10618ba288bd5f7a56a7660fdb58deddb19bcd9a123b9cef755cbd5198d47e758cacdca37c86b85604"
        const val LEDGER_AUTH_KEY = "c878480d4f91bdad08453e5985c3485e2de2d04c60fa96d1e793b7e61a58132d"
        const val LEDGER_WALLET_ID = "akabnyroxo54kwlbwhm2762krvdqgscfcreq"
    }
}
