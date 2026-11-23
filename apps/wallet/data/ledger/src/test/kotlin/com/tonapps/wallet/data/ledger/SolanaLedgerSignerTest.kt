package com.tonapps.wallet.data.ledger

import com.tonapps.bus.core.IssueHelper
import com.tonapps.ledgerkit.LedgerConfirmDetail
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerProgress
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.apdu.ApduResponse
import com.tonapps.ledgerkit.util.Base58
import com.tonapps.wallet.data.ledger.signing.SolanaLedgerSigner
import com.tonapps.wallet.data.ledger.signing.WalletCoreCompiler
import com.tonapps.wallet.data.ledger.solana.SolanaTokenRecipient
import com.tonapps.wallet.data.ledger.solana.SolanaTrustedNames
import com.tonapps.wallet.data.ledger.solana.SolanaWireTransaction
import com.tonapps.ledgerkit.transport.LedgerModel
import com.trustwallet.core.SolanaAddress
import com.trustwallet.core.solana.CreateAndTransferToken
import com.trustwallet.core.solana.TokenTransfer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import com.trustwallet.core.AnySigner
import com.trustwallet.core.CoinType
import com.trustwallet.core.Curve
import com.trustwallet.core.PrivateKey
import com.trustwallet.core.WalletCoreLibLoader
import com.trustwallet.core.solana.Encoding
import com.trustwallet.core.solana.SigningInput
import com.trustwallet.core.solana.SigningOutput
import com.trustwallet.core.solana.Transfer
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64

class SolanaLedgerSignerTest {

    init {
        WalletCoreLibLoader.loadLibrary()
    }

    private val privateKey = ByteArray(32) { (it * 7 + 3).toByte() }
    private val publicKey = PrivateKey(privateKey).getPublicKeyEd25519().data
    private val path = LedgerDerivation.solana(0)

    private val noTrustedNames = mockk<SolanaTrustedNames> {
        coEvery { certificate(any()) } returns null
        coEvery { descriptor(any(), any()) } returns null
    }

    private val recorded = ArrayList<Throwable>()

    @Before
    fun setUp() {
        mockkObject(IssueHelper)
        every { IssueHelper.recordException(any(), any<Map<String, String>>()) } answers { recorded += firstArg<Throwable>() }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun signer(trustedNames: SolanaTrustedNames = noTrustedNames) = SolanaLedgerSigner(trustedNames)

    private fun tokenInput(withKey: Boolean) = SigningInput(
        private_key = if (withKey) { privateKey.toByteString() } else { okio.ByteString.EMPTY },
        recent_blockhash = "11111111111111111111111111111111",
        sender = Base58.encode(publicKey),
        token_transfer_transaction = TokenTransfer(
            token_mint_address = USDC_MINT,
            sender_token_address = requireNotNull(SolanaAddress(Base58.encode(publicKey)).defaultTokenAddress(USDC_MINT)),
            recipient_token_address = RECIPIENT_TOKEN_ACCOUNT,
            amount = 1_250_000,
            decimals = 6,
        ),
        tx_encoding = Encoding.Base64,
    )

    private fun transferInput(withKey: Boolean) = SigningInput(
        private_key = if (withKey) { privateKey.toByteString() } else { okio.ByteString.EMPTY },
        recent_blockhash = "11111111111111111111111111111111",
        sender = Base58.encode(publicKey),
        transfer_transaction = Transfer(recipient = RECIPIENT, value_ = 1_500_000, memo = "ledger"),
        tx_encoding = Encoding.Base64,
    )

    @Test
    fun `chainkit style transfer compiled from a ledger signature equals the software transaction`() = runTest {
        val transport = signingLedger(privateKey)
        val compiled = signer().signChainkitInput(transport.scope(), path, publicKey, transferInput(withKey = false).encode(), 1, 1)
        val software = SigningOutput.ADAPTER.decode(AnySigner.sign(transferInput(withKey = true).encode(), CoinType.Solana))
        assertEquals(software.encoded, SigningOutput.ADAPTER.decode(compiled).encoded)
    }

    @Test
    fun `wire transaction gets our signature and the dapp answer shape`() = runTest {
        val software = SigningOutput.ADAPTER.decode(AnySigner.sign(transferInput(withKey = true).encode(), CoinType.Solana))
        val signedBytes = Base64.getDecoder().decode(software.encoded)
        val unsigned = signedBytes.copyOf().also { it.fill(0, 1, 1 + SolanaWireTransaction.SIGNATURE_LENGTH) }

        val answer = signer().signWireTransaction(
            signingLedger(privateKey).scope(),
            path,
            publicKey,
            Base64.getEncoder().encodeToString(unsigned),
        )

        val json = Json.parseToJsonElement(answer).jsonObject
        assertEquals(software.encoded, json.getValue("transaction").jsonPrimitive.content)
        assertArrayEquals(signedBytes.copyOfRange(1, 65), Base58.decode(json.getValue("signature").jsonPrimitive.content))
    }

    @Test
    fun `versioned message with two signers only fills our slot`() = runTest {
        val other = ByteArray(32) { 5 }
        val message = byteArrayOf(0x80.toByte(), 2, 0, 1, 3) + other + publicKey + ByteArray(32) { 9 } + ByteArray(32) { 7 } + byteArrayOf(0, 0)
        val foreignSignature = ByteArray(64) { 4 }
        val wire = byteArrayOf(2) + foreignSignature + ByteArray(64) + message

        val answer = signer().signWireTransaction(signingLedger(privateKey).scope(), path, publicKey, Base64.getEncoder().encodeToString(wire))

        val signed = Base64.getDecoder().decode(Json.parseToJsonElement(answer).jsonObject.getValue("transaction").jsonPrimitive.content)
        assertArrayEquals(foreignSignature, signed.copyOfRange(1, 65))
        assertArrayEquals(requireNotNull(PrivateKey(privateKey).sign(message, Curve.ED25519)), signed.copyOfRange(65, 129))
        assertArrayEquals(message, signed.copyOfRange(129, signed.size))
    }

    @Test
    fun `transaction without our key never reaches the device`() = runTest {
        var reachedDevice = false
        val transport = FakeLedgerTransport("Solana", "1.16.0") {
            reachedDevice = true
            ApduResponse(ByteArray(0), 0x6985)
        }
        val message = byteArrayOf(1, 0, 1, 2) + ByteArray(32) { 5 } + ByteArray(32) { 6 } + ByteArray(32) { 7 } + byteArrayOf(0)
        val wire = byteArrayOf(1) + ByteArray(64) + message
        val failure = failureOf {
            signer().signWireTransaction(transport.scope(), path, publicKey, Base64.getEncoder().encodeToString(wire))
        }
        assertEquals(LedgerFailure.InvalidPayload(0, "The transaction is not signed by this account"), failure)
        assertTrue(!reachedDevice)
    }

    @Test
    fun `signature from another key is caught before the transaction leaves the phone`() = runTest {
        val failure = failureOf {
            signer().signChainkitInput(signingLedger(ByteArray(32) { 1 }).scope(), path, publicKey, transferInput(withKey = false).encode(), 1, 1)
        }
        assertEquals(LedgerFailure.Unexpected("The Ledger signed a different Solana transaction"), failure)
    }

    @Test
    fun `old solana app asks for an update`() = runTest {
        val transport = FakeLedgerTransport("Solana", "1.2.0") { ApduResponse(ByteArray(0), 0x9000) }
        val failure = failureOf {
            signer().signChainkitInput(transport.scope(), path, publicKey, transferInput(withKey = false).encode(), 1, 1)
        }
        assertEquals(LedgerFailure.AppUpdateRequired(com.tonapps.ledgerkit.LedgerAppId.Solana, "1.2.0", "1.3.1"), failure)
    }

    @Test
    fun `token transfer hands the device the certificate and the owner descriptor before signing`() = runTest {
        val certificate = SolanaTrustedNames.Certificate(ByteArray(87) { 1 }, ByteArray(70) { 2 })
        val descriptor = ByteArray(238) { 3 }
        val challenge = byteArrayOf(9, 8, 7, 6)
        val trustedNames = mockk<SolanaTrustedNames> {
            coEvery { certificate(LedgerModel.Stax) } returns certificate
            coEvery { descriptor(any(), any()) } returns descriptor
        }
        val sent = mutableListOf<ByteArray>()
        val compiled = signer(trustedNames).signChainkitInput(
            signingLedger(privateKey, sent = sent, challenge = challenge).scope(), path, publicKey, tokenInput(withKey = false).encode(), 1, 1,
        )

        val software = SigningOutput.ADAPTER.decode(AnySigner.sign(tokenInput(withKey = true).encode(), CoinType.Solana))
        assertEquals(software.encoded, SigningOutput.ADAPTER.decode(compiled).encoded)
        assertEquals(listOf("b006", "e020", "e021", "e006"), sent.map { "%02x%02x".format(it[0], it[1]) }.filter { it != "e004" })
        assertArrayEquals(certificate.descriptor + byteArrayOf(0x15, 70) + certificate.signature, sent.first { it[0] == 0xb0.toByte() }.copyOfRange(5, 5 + 87 + 2 + 70))
        assertEquals(0x04, sent.first { it[0] == 0xb0.toByte() }[2].toInt())
        assertArrayEquals(descriptor, sent.first { it[1] == 0x21.toByte() }.copyOfRange(5, 5 + 238))
        coVerify { trustedNames.descriptor(SolanaTokenRecipient.Existing(RECIPIENT_TOKEN_ACCOUNT, USDC_MINT), challenge) }
    }

    @Test
    fun `solana app older than trusted names signs without them`() = runTest {
        val trustedNames = mockk<SolanaTrustedNames>()
        val sent = mutableListOf<ByteArray>()
        signer(trustedNames).signChainkitInput(signingLedger(privateKey, sent = sent, version = "1.9.1").scope(), path, publicKey, tokenInput(withKey = false).encode(), 1, 1)
        assertTrue(sent.none { it[0] == 0xb0.toByte() || it[1] == 0x20.toByte() || it[1] == 0x21.toByte() })
    }

    @Test
    fun `certificate the device refuses leaves the blind signing path`() = runTest {
        val trustedNames = mockk<SolanaTrustedNames> {
            coEvery { certificate(any()) } returns SolanaTrustedNames.Certificate(ByteArray(10), ByteArray(10))
        }
        val sent = mutableListOf<ByteArray>()
        val compiled = signer(trustedNames).signChainkitInput(
            signingLedger(privateKey, sent = sent, certificateStatus = 0x6a81).scope(), path, publicKey, tokenInput(withKey = false).encode(), 1, 1,
        )
        val software = SigningOutput.ADAPTER.decode(AnySigner.sign(tokenInput(withKey = true).encode(), CoinType.Solana))
        assertEquals(software.encoded, SigningOutput.ADAPTER.decode(compiled).encoded)
        assertTrue(sent.none { it[1] == 0x21.toByte() })
        assertEquals(listOf("The Solana app rejected a trusted name"), recorded.map { it.message })
    }

    @Test
    fun `new recipient token account is resolved through its owner and mint`() {
        val input = SigningInput(
            sender = Base58.encode(publicKey),
            recent_blockhash = BLOCKHASH,
            create_and_transfer_token_transaction = CreateAndTransferToken(
                recipient_main_address = RECIPIENT,
                token_mint_address = USDC_MINT,
                recipient_token_address = RECIPIENT_TOKEN_ACCOUNT,
                sender_token_address = requireNotNull(SolanaAddress(Base58.encode(publicKey)).defaultTokenAddress(USDC_MINT)),
                amount = 1,
                decimals = 6,
            ),
        )
        assertEquals(SolanaTokenRecipient.Created(RECIPIENT, USDC_MINT, RECIPIENT_TOKEN_ACCOUNT), SolanaTokenRecipient.from(preImage(input)))
        assertEquals(null, SolanaTokenRecipient.from(preImage(transferInput(withKey = false))))
    }

    @Test
    fun `chainkit raw message creating the account idempotently names the owner`() {
        val message = legacyMessage(
            keys = listOf(SENDER, CHAINKIT_ATA, SENDER_JUP_ACCOUNT, CHAINKIT_RECIPIENT, JUP_MINT, SYSTEM_PROGRAM, TOKEN_PROGRAM, RENT_SYSVAR, ASSOCIATED_TOKEN_PROGRAM),
            instructions = listOf(
                Triple(8, listOf(0, 1, 3, 4, 5, 6, 7), byteArrayOf(1)),
                Triple(6, listOf(2, 4, 1, 0), transferChecked(2_000_000, 6)),
            ),
        )
        assertEquals(SolanaTokenRecipient.Created(CHAINKIT_RECIPIENT, JUP_MINT, CHAINKIT_ATA), SolanaTokenRecipient.from(message))
    }

    @Test
    fun `transfer to an existing token account names the account`() {
        val message = legacyMessage(
            keys = listOf(SENDER, SENDER_JUP_ACCOUNT, CHAINKIT_ATA, JUP_MINT, TOKEN_PROGRAM),
            instructions = listOf(Triple(4, listOf(1, 3, 2, 0), transferChecked(5, 6))),
        )
        assertEquals(SolanaTokenRecipient.Existing(CHAINKIT_ATA, JUP_MINT), SolanaTokenRecipient.from(message))
    }

    @Test
    fun `messages the device cannot attribute to one recipient get no trusted name`() {
        val keys = listOf(SENDER, SENDER_JUP_ACCOUNT, CHAINKIT_ATA, JUP_MINT, TOKEN_PROGRAM)
        val transfer = Triple(4, listOf(1, 3, 2, 0), transferChecked(5, 6))
        val twoTransfers = legacyMessage(keys, listOf(transfer, transfer))
        val lookupTableDestination = legacyMessage(keys, listOf(Triple(4, listOf(1, 3, 9, 0), transferChecked(5, 6))))
        val plainTransfer = legacyMessage(keys, listOf(Triple(4, listOf(1, 2, 0), byteArrayOf(3, 5, 0, 0, 0, 0, 0, 0, 0))))
        val wrongMint = legacyMessage(
            keys = listOf(SENDER, CHAINKIT_ATA, SENDER_JUP_ACCOUNT, CHAINKIT_RECIPIENT, JUP_MINT, USDC_MINT, TOKEN_PROGRAM, ASSOCIATED_TOKEN_PROGRAM),
            instructions = listOf(Triple(7, listOf(0, 1, 3, 5), byteArrayOf(1)), Triple(6, listOf(2, 4, 1, 0), transferChecked(5, 6))),
        )
        assertEquals(null, SolanaTokenRecipient.from(twoTransfers))
        assertEquals(null, SolanaTokenRecipient.from(lookupTableDestination))
        assertEquals(null, SolanaTokenRecipient.from(plainTransfer))
        assertEquals(null, SolanaTokenRecipient.from(wrongMint))
        assertEquals(null, SolanaTokenRecipient.from(byteArrayOf(1, 0)))
    }

    @Test
    fun `short vector lengths round trip`() {
        assertArrayEquals(byteArrayOf(0), SolanaWireTransaction.encodeShortVec(0))
        assertArrayEquals(byteArrayOf(0x7f), SolanaWireTransaction.encodeShortVec(127))
        assertArrayEquals(byteArrayOf(0x80.toByte(), 0x01), SolanaWireTransaction.encodeShortVec(128))
        assertArrayEquals(byteArrayOf(0xff.toByte(), 0xff.toByte(), 0x03), SolanaWireTransaction.encodeShortVec(0xffff))
    }

    @Test
    fun `truncated transaction is reported as malformed`() {
        try {
            SolanaWireTransaction.parse(byteArrayOf(1) + ByteArray(10))
            fail()
        } catch (e: LedgerFailureException) {
            assertEquals(LedgerFailure.InvalidPayload(0, "Malformed Solana transaction"), e.failure)
        }
    }

    @Test
    fun `message hash is shown only when blind signing is on`() = runTest {
        val shown = mutableListOf<LedgerConfirmDetail?>()
        for (blindSigning in listOf(false, true)) {
            signer().signChainkitInput(
                signingLedger(privateKey, blindSigning).scope { progress -> (progress as? LedgerProgress.Confirm)?.let { shown += it.detail } },
                path,
                publicKey,
                transferInput(withKey = false).encode(),
                1,
                1,
            )
        }
        val message = WalletCoreCompiler.preImage(CoinType.Solana, transferInput(withKey = false).encode())
        assertEquals(listOf(null, LedgerConfirmDetail.MessageHash(Base58.encode(MessageDigest.getInstance("SHA-256").digest(message)))), shown)
    }

    @Test
    fun `unreadable configuration only hides the hash hint`() = runTest {
        val shown = mutableListOf<LedgerConfirmDetail?>()
        val compiled = signer().signChainkitInput(
            signingLedger(privateKey, blindSigning = null).scope { progress -> (progress as? LedgerProgress.Confirm)?.let { shown += it.detail } },
            path,
            publicKey,
            transferInput(withKey = false).encode(),
            1,
            1,
        )
        val software = SigningOutput.ADAPTER.decode(AnySigner.sign(transferInput(withKey = true).encode(), CoinType.Solana))
        assertEquals(software.encoded, SigningOutput.ADAPTER.decode(compiled).encoded)
        assertEquals(listOf<LedgerConfirmDetail?>(null), shown)
        assertEquals(listOf("The Solana app configuration could not be read"), recorded.map { it.message })
    }

    private fun signingLedger(
        key: ByteArray,
        blindSigning: Boolean? = false,
        sent: MutableList<ByteArray> = mutableListOf(),
        challenge: ByteArray = byteArrayOf(1, 2, 3, 4),
        version: String = "1.16.0",
        certificateStatus: Int = 0x9000,
    ): FakeLedgerTransport {
        val payload = ByteArrayOutputStream()
        return FakeLedgerTransport("Solana", version) { apdu ->
            sent += apdu
            val cla = apdu[0].toInt() and 0xff
            val ins = apdu[1].toInt() and 0xff
            val p2 = apdu[3].toInt() and 0xff
            if (cla == 0xb0 && ins == 0x06) {
                return@FakeLedgerTransport ApduResponse(ByteArray(0), certificateStatus)
            }
            if (ins == 0x20) {
                return@FakeLedgerTransport ApduResponse(challenge, 0x9000)
            }
            if (ins == 0x21) {
                return@FakeLedgerTransport ApduResponse(ByteArray(0), 0x9000)
            }
            if (ins == 0x04) {
                return@FakeLedgerTransport when (blindSigning) {
                    null -> ApduResponse(ByteArray(0), 0x6a80)
                    else -> ApduResponse(byteArrayOf(if (blindSigning) { 1 } else { 0 }, 0, 1, 16, 0, 0, 0), 0x9000)
                }
            }
            if (ins != 0x06) {
                return@FakeLedgerTransport ApduResponse(ByteArray(0), 0x6d00)
            }
            if (p2 and 0x01 == 0) {
                payload.reset()
            }
            payload.write(apdu, 5, apdu.size - 5)
            if (p2 and 0x02 != 0) {
                return@FakeLedgerTransport ApduResponse(ByteArray(0), 0x9000)
            }
            val bytes = payload.toByteArray()
            val depth = bytes[1].toInt()
            val message = bytes.copyOfRange(2 + depth * 4, bytes.size)
            ApduResponse(requireNotNull(PrivateKey(key).sign(message, Curve.ED25519)), 0x9000)
        }
    }

    private fun preImage(input: SigningInput): ByteArray = WalletCoreCompiler.preImage(CoinType.Solana, input.encode())

    private fun transferChecked(amount: Long, decimals: Int): ByteArray =
        byteArrayOf(12) + ByteArray(8) { (amount shr (8 * it)).toByte() } + byteArrayOf(decimals.toByte())

    private fun legacyMessage(keys: List<String>, instructions: List<Triple<Int, List<Int>, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(1, 0, (keys.size - 1).toByte()))
        out.write(SolanaWireTransaction.encodeShortVec(keys.size))
        keys.forEach { out.write(Base58.decode(it)) }
        out.write(Base58.decode(BLOCKHASH))
        out.write(SolanaWireTransaction.encodeShortVec(instructions.size))
        instructions.forEach { (program, accounts, data) ->
            out.write(program)
            out.write(SolanaWireTransaction.encodeShortVec(accounts.size))
            accounts.forEach { out.write(it) }
            out.write(SolanaWireTransaction.encodeShortVec(data.size))
            out.write(data)
        }
        return out.toByteArray()
    }

    private suspend fun failureOf(block: suspend () -> Unit): LedgerFailure? = try {
        block()
        fail()
        null
    } catch (e: LedgerFailureException) {
        e.failure
    }

    private companion object {
        const val RECIPIENT = "EN2sCsJ1WDV8UFqsiTXHcUPUxQ4juE71eCknHYYMifkd"
        const val USDC_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
        const val RECIPIENT_TOKEN_ACCOUNT = "HQLyfXNLZyMCux7tYmp9nN7C8RfkPiWbP9JSokjCGdgv"
        const val BLOCKHASH = "6fSSyssBWg8ERmTDADDPLFxEKWcSvdxttfeKiGjKWdSy"
        const val SENDER = "HDYQ9UHgvKavsWNoLwWfi5sRNEpFEm6Nrm2EaLK13NzR"
        const val SENDER_JUP_ACCOUNT = "GW93Acm4wjqqYAa6G3Fcp6RJomFPDHaCU4r5fRQkAVov"
        const val CHAINKIT_RECIPIENT = "5Tn4mKWvWZ2iWA3DWGdywDWi15C7pZhC3P8QmvC7X3vF"
        const val CHAINKIT_ATA = "6B1GnXryUvnZ9vvoEuizwRB78VMNgu3MB7Q3Lw3cH2g3"
        const val JUP_MINT = "JUPyiwrYJFskUPiHa7hkeR8VUtAeFoSYbKedZNsDvCN"
        const val SYSTEM_PROGRAM = "11111111111111111111111111111111"
        const val TOKEN_PROGRAM = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
        const val RENT_SYSVAR = "SysvarRent111111111111111111111111111111111"
        const val ASSOCIATED_TOKEN_PROGRAM = "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL"
    }
}
