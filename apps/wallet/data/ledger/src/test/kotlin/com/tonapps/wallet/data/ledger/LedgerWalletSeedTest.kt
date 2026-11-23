package com.tonapps.wallet.data.ledger

import com.tonapps.blockchain.ton.contract.WalletV4R2Contract
import com.tonapps.chainkit.core.chain.model.account.LedgerKeyError
import com.tonapps.chainkit.core.chain.model.account.LedgerWallet
import com.tonapps.chainkit.core.chain.model.account.WalletKind
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.apdu.ApduResponse
import com.tonapps.wallet.data.ledger.signing.TonLedgerSigner
import com.tonapps.wallet.data.ledger.ton.TonProofHash
import com.trustwallet.core.WalletCoreLibLoader
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ton.kotlin.crypto.PrivateKeyEd25519
import org.ton.kotlin.crypto.PublicKeyEd25519
import kotlinx.io.bytestring.ByteString as IoByteString
import java.nio.ByteOrder
import java.nio.ByteBuffer
import java.security.MessageDigest

class LedgerWalletSeedTest {

    @Before
    fun setUp() {
        WalletCoreLibLoader.loadLibrary()
    }

    @Test
    fun `the proof the ton app signs is the one the wallet is derived from`() {
        val proofHash = TonProofHash.compute(TON_PUBLIC_KEY.hexToBytes(), LedgerWallet.PROOF_DOMAIN, LedgerWallet.PROOF_TIMESTAMP, LedgerWallet.PROOF_PAYLOAD)

        assertEquals(PROOF_HASH, proofHash.hex())
        assertArrayEquals(LedgerWallet.proofHash(TON_PUBLIC_KEY.hexToBytes()).unwrap(), proofHash)
    }

    @Test
    fun `a real ledger ton app proof verifies against the proof message`() {
        val proofHash = TonProofHash.compute(DEVICE_TON_PUBLIC_KEY.hexToBytes(), LedgerWallet.PROOF_DOMAIN, LedgerWallet.PROOF_TIMESTAMP, DEVICE_PROOF_PAYLOAD)

        assertEquals(DEVICE_PROOF_HASH, proofHash.hex())
        assertTrue(TonProofHash.verify(DEVICE_TON_PUBLIC_KEY.hexToBytes(), proofHash, DEVICE_SIGNATURE.hexToBytes()))
    }

    @Test
    fun `the wallet key proof uses the fixed domain, timestamp and payload`() = runTest {
        val device = FakeTonDevice(seed = 7)
        val transport = FakeLedgerTransport("TON", handler = device::handle)

        val signature = TonLedgerSigner().signWalletKeyProof(transport.scope(), accountIndex = 2, device.publicKey, step = 1, totalSteps = 1)

        val request = requireNotNull(device.proofRequest)
        val path = request.copyOfRange(0, 25)
        val expectedPath = ByteBuffer.allocate(25).apply {
            put(6)
            listOf(44, 607, 0, 0, 2, 0).forEach { putInt((it + 0x80000000).toInt()) }
        }.array()
        assertArrayEquals(expectedPath, path)
        val domain = LedgerWallet.PROOF_DOMAIN.toByteArray()
        assertEquals(domain.size, request[25].toInt())
        assertArrayEquals(domain, request.copyOfRange(26, 26 + domain.size))
        assertArrayEquals(ByteArray(8), request.copyOfRange(26 + domain.size, 34 + domain.size))
        assertArrayEquals(LedgerWallet.PROOF_PAYLOAD.toByteArray(), request.copyOfRange(34 + domain.size, request.size))
        assertEquals(64, signature.size)
    }

    @Test
    fun `the signed proof gives a ledger wallet`() = runTest {
        val wallet = ledgerWallet(FakeTonDevice(seed = 5))

        assertEquals(WalletKind.Ledger, wallet.walletKeyPair().kind)
        assertTrue(wallet.walletIdV2().startsWith(LEDGER_WALLET_ID_PREFIX))
    }

    @Test
    fun `the same ledger gives the same wallet seed on every run`() = runTest {
        val first = ledgerWallet(FakeTonDevice(seed = 9)).seed()
        val second = ledgerWallet(FakeTonDevice(seed = 9)).seed()
        val other = ledgerWallet(FakeTonDevice(seed = 10)).seed()

        assertArrayEquals(first, second)
        assertFalse(first.contentEquals(other))
    }

    @Test
    fun `another account's proof is not a wallet seed`() = runTest {
        val device = FakeTonDevice(seed = 4)
        val transport = FakeLedgerTransport("TON", handler = device::handle)
        val signature = TonLedgerSigner().signWalletKeyProof(transport.scope(), accountIndex = 0, device.publicKey, step = 1, totalSteps = 1)

        val result = LedgerWallet.fromProofSignature(FakeTonDevice(seed = 6).publicKey, signature)

        assertTrue(result.error is LedgerKeyError.NotWalletKeyProof)
    }

    @Test
    fun `a proof signed over a different message is rejected`() = runTest {
        val device = FakeTonDevice(seed = 3, signsWrongHash = true)
        val transport = FakeLedgerTransport("TON", handler = device::handle)
        val failure = try {
            TonLedgerSigner().signWalletKeyProof(transport.scope(), accountIndex = 0, device.publicKey, step = 1, totalSteps = 1)
            null
        } catch (e: LedgerFailureException) {
            e.failure
        }
        assertTrue(failure is LedgerFailure.Unexpected)
    }

    @Test
    fun `a dapp proof with the wallet key parameters never reaches the device`() = runTest {
        val device = FakeTonDevice(seed = 8)
        val transport = FakeLedgerTransport("TON", handler = device::handle)
        val requests = listOf(
            Triple(LedgerWallet.PROOF_DOMAIN, DAPP_TIMESTAMP, "payload"),
            Triple("app.example.com", DAPP_TIMESTAMP, LedgerWallet.PROOF_PAYLOAD),
            Triple("app.example.com", LedgerWallet.PROOF_TIMESTAMP, "payload"),
        )

        requests.forEach { (domain, timestamp, payload) ->
            val failure = try {
                TonLedgerSigner().signProof(transport.scope(), accountIndex = 0, device.publicKey, domain, timestamp, payload)
                null
            } catch (e: LedgerFailureException) {
                e.failure
            }
            assertTrue(failure is LedgerFailure.InvalidPayload)
        }
        assertEquals(null, device.proofRequest)
    }

    private suspend fun ledgerWallet(device: FakeTonDevice): LedgerWallet {
        val transport = FakeLedgerTransport("TON", handler = device::handle)
        val signature = TonLedgerSigner().signWalletKeyProof(transport.scope(), accountIndex = 0, device.publicKey, step = 1, totalSteps = 1)
        return LedgerWallet.fromProofSignature(device.publicKey, signature).unwrap()
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private companion object {
        const val TON_PUBLIC_KEY = "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"
        const val PROOF_HASH = "3416d58a3123d2300eed47b8250ee070419f95bf71e692e664aa67590afafda9"
        const val DEVICE_TON_PUBLIC_KEY = "b8c2336996bd97a7789b6deec787797961856628ee518694152ae056387fc9af"
        const val DEVICE_PROOF_PAYLOAD = "keeper-ledger-wallet-key-v1"
        const val DEVICE_PROOF_HASH = "25796ae317c4261d503e6181f5507414b8c070bfd3634d79e2db90839fea0dcb"
        const val DEVICE_SIGNATURE = "d66c2d9a4a74c97910dd7b1c27c3b70db7d9df7f1371122355b23052b9e71e5aed0654b9a85c5232b7449992040076a70c89e94477b93757a16a39f4c8b5260b"
        const val LEDGER_WALLET_ID_PREFIX = "aka"
        const val DAPP_TIMESTAMP = 1_793_000_000L
    }

    private class FakeTonDevice(seed: Int, private val signsWrongHash: Boolean = false) {

        private val privateKey = PrivateKeyEd25519(ByteArray(32) { (it * 31 + seed).toByte() })
        val publicKey: ByteArray = privateKey.publicKey().key.toByteArray()
        var proofRequest: ByteArray? = null

        fun handle(apdu: ByteArray): ApduResponse {
            val ins = apdu[1].toInt() and 0xff
            val data = apdu.copyOfRange(5, apdu.size)
            return when (ins) {
                0x03 -> ApduResponse(byteArrayOf(2, 9, 1), 0x9000)
                0x05 -> ApduResponse(publicKey, 0x9000)
                0x08 -> {
                    proofRequest = data
                    val hash = if (signsWrongHash) {
                        MessageDigest.getInstance("SHA-256").digest(data)
                    } else {
                        tonConnectHash(data)
                    }
                    val signature = privateKey.signToByteArray(hash)
                    ApduResponse(byteArrayOf(64) + signature + byteArrayOf(32) + hash, 0x9000)
                }
                else -> ApduResponse(ByteArray(0), 0x6d00)
            }
        }

        private fun tonConnectHash(data: ByteArray): ByteArray {
            val pathLength = 1 + data[0] * 4
            val domainLength = data[pathLength].toInt()
            val domain = data.copyOfRange(pathLength + 1, pathLength + 1 + domainLength)
            val timestamp = ByteBuffer.wrap(data, pathLength + 1 + domainLength, 8).long
            val payload = data.copyOfRange(pathLength + 1 + domainLength + 8, data.size)
            val address = WalletV4R2Contract(publicKey = PublicKeyEd25519(IoByteString(publicKey))).address
            val message = "ton-proof-item-v2/".toByteArray() +
                ByteBuffer.allocate(4).putInt(address.workchainId).array() +
                address.address.toByteArray() +
                ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(domainLength).array() +
                domain +
                ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(timestamp).array() +
                payload
            val sha = MessageDigest.getInstance("SHA-256")
            val inner = sha.digest(message)
            return sha.digest(byteArrayOf(0xff.toByte(), 0xff.toByte()) + "ton-connect".toByteArray() + inner)
        }
    }
}
