package com.tonapps.wallet.data.ledger.lab

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.bus.core.IssueHelper
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Derivation
import com.tonapps.chainkit.core.chain.model.account.PubKey
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerAppSetting
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.controller.LedgerPurpose
import com.tonapps.ledgerkit.controller.LedgerRequest
import com.tonapps.ledgerkit.util.Base58
import com.tonapps.wallet.data.ledger.LedgerDerivation
import com.tonapps.wallet.data.ledger.signing.SolanaLedgerSigner
import com.tonapps.wallet.data.ledger.solana.SolanaTrustedNames
import okhttp3.OkHttpClient
import com.trustwallet.core.AnySigner
import com.trustwallet.core.CoinType
import com.trustwallet.core.Curve
import com.trustwallet.core.HDWallet
import com.trustwallet.core.WalletCoreLibLoader
import com.trustwallet.core.SolanaAddress
import com.trustwallet.core.solana.CreateAndTransferToken
import com.trustwallet.core.solana.Encoding
import com.trustwallet.core.solana.RawMessage
import com.trustwallet.core.solana.TokenTransfer
import com.trustwallet.core.solana.SigningInput
import com.trustwallet.core.solana.SigningOutput
import com.trustwallet.core.solana.Transfer
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64

class LedgerLabSolanaTest {

    private lateinit var wallet: HDWallet
    private val signer = SolanaLedgerSigner(SolanaTrustedNames(OkHttpClient(), SolanaTrustedNames.SignatureKind.Test))
    private val path = LedgerDerivation.solana(0)
    private val recorded = ArrayList<Throwable>()

    @Before
    fun setUp() {
        assumeTrue("Ledger lab is not running", SpeculosLab.isRunning)
        WalletCoreLibLoader.loadLibrary()
        wallet = HDWallet(SpeculosLab.seed, "")
        SpeculosLab.policy("approve")
        SpeculosLab.restart("Solana")
        mockkObject(IssueHelper)
        every { IssueHelper.recordException(any(), any<Map<String, String>>()) } answers { recorded += firstArg<Throwable>() }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun request() = LedgerRequest("lab", "Stax lab", LedgerPurpose.SignTransaction)

    @Test
    fun `address verification on the device returns the account key`() = runBlocking {
        val key = SpeculosLab.controller().run(request()) {
            signer.readPublicKey(this, path, display = true, expectedAddress = "lab")
        }
        assertArrayEquals(publicKey(), key)
    }

    @Test
    fun `sol transfer built by chainkit equals the software transaction`() = runBlocking {
        val chain = Chain.Solana.Mainnet
        val transfer = Transaction.Transfer(
            account = Account(
                address = Address.force(Base58.encode(publicKey()), chain, Address.Type.Default),
                asset = chain.toAsset(),
                derivation = Derivation.DefaultPath,
                publicKey = PubKey(publicKey().toHex(), ""),
            ),
            amount = BigInteger.fromLong(1_000_000),
            isMax = false,
            to = Address.force(RECIPIENT, chain),
            memo = "ledger lab",
        )
        val inputs = LabChainKit.provider.buildUnsignedInputs(transfer, Fee.Value(chain.toAsset(), BigInteger.fromLong(5_000)), BigInteger.ZERO)
        val input = requireNotNull(inputs.getOrNull()) { "chainkit could not build the input: ${inputs.error}" }.single()

        val ledger = SpeculosLab.controller().run(request()) { signer.signChainkitInput(this, path, publicKey(), input, 1, 1) }

        val software = AnySigner.sign(SigningInput.ADAPTER.decode(input).copy(private_key = privateKey().data.toByteString()).encode(), CoinType.Solana)
        assertEquals(SigningOutput.ADAPTER.decode(software).encoded, SigningOutput.ADAPTER.decode(ledger).encoded)
    }

    @Test
    fun `usdc to a new token account is clear signed with the computed trusted name`() = runBlocking {
        assertTokenTransferClearSigned(newAccountInput(withKey = false), newAccountInput(withKey = true))
    }

    @Test
    fun `usdc to an existing token account is clear signed with its owner`() = runBlocking {
        assertTokenTransferClearSigned(existingAccountInput(withKey = false), existingAccountInput(withKey = true))
    }

    @Test
    fun `usdc transfer in the chainkit raw message shape is clear signed`() = runBlocking {
        val ledger = SpeculosLab.controller().run(request()) {
            signer.signChainkitInput(this, path, publicKey(), chainkitShapedInput(withKey = false).encode(), 1, 1)
        }
        val software = SigningOutput.ADAPTER.decode(AnySigner.sign(chainkitShapedInput(withKey = true).encode(), CoinType.Solana))
        assertEquals(software.encoded, SigningOutput.ADAPTER.decode(ledger).encoded)
    }

    private fun chainkitShapedInput(withKey: Boolean): SigningInput {
        val sender = Base58.encode(publicKey())
        val keys = listOf(
            sender,
            requireNotNull(SolanaAddress(RECIPIENT).defaultTokenAddress(USDC_MINT)),
            requireNotNull(SolanaAddress(sender).defaultTokenAddress(USDC_MINT)),
            RECIPIENT,
            USDC_MINT,
            SYSTEM_PROGRAM,
            TOKEN_PROGRAM,
            RENT_SYSVAR,
            ASSOCIATED_TOKEN_PROGRAM,
        )
        val transferChecked = byteArrayOf(12) + ByteArray(8) { (1_250_000L shr (8 * it)).toByte() } + byteArrayOf(6)
        val message = RawMessage.MessageLegacy(
            RawMessage.MessageHeader(1, 0, 6),
            keys,
            "11111111111111111111111111111111",
            listOf(
                RawMessage.Instruction(8, listOf(0, 1, 3, 4, 5, 6, 7), byteArrayOf(1).toByteString()),
                RawMessage.Instruction(6, listOf(2, 4, 1, 0), transferChecked.toByteString()),
            ),
        )
        return SigningInput(
            private_key = if (withKey) { privateKey().data.toByteString() } else { okio.ByteString.EMPTY },
            raw_message = RawMessage(emptyList(), message),
            tx_encoding = Encoding.Base64,
        )
    }

    @Test
    fun `certificate the device does not trust falls back to blind signing`() = runBlocking {
        val prodSigner = SolanaLedgerSigner(SolanaTrustedNames(OkHttpClient(), SolanaTrustedNames.SignatureKind.Prod))
        val failure = try {
            SpeculosLab.controller().run(request()) { prodSigner.signChainkitInput(this, path, publicKey(), newAccountInput(withKey = false).encode(), 1, 1) }
            null
        } catch (e: LedgerFailureException) {
            e.failure
        }
        assertEquals(LedgerFailure.SettingRequired(LedgerAppId.Solana, LedgerAppSetting.BlindSigning), failure)
        assertEquals(listOf("The Solana app rejected a trusted name"), recorded.map { it.message })
    }

    private suspend fun assertTokenTransferClearSigned(unsigned: SigningInput, withKey: SigningInput) {
        val ledger = SpeculosLab.controller().run(request()) { signer.signChainkitInput(this, path, publicKey(), unsigned.encode(), 1, 1) }
        val software = SigningOutput.ADAPTER.decode(AnySigner.sign(withKey.encode(), CoinType.Solana))
        assertEquals(software.encoded, SigningOutput.ADAPTER.decode(ledger).encoded)
    }

    private fun newAccountInput(withKey: Boolean): SigningInput {
        val sender = Base58.encode(publicKey())
        return SigningInput(
            private_key = if (withKey) { privateKey().data.toByteString() } else { okio.ByteString.EMPTY },
            recent_blockhash = "11111111111111111111111111111111",
            sender = sender,
            create_and_transfer_token_transaction = CreateAndTransferToken(
                recipient_main_address = RECIPIENT,
                token_mint_address = USDC_MINT,
                recipient_token_address = requireNotNull(SolanaAddress(RECIPIENT).defaultTokenAddress(USDC_MINT)),
                sender_token_address = requireNotNull(SolanaAddress(sender).defaultTokenAddress(USDC_MINT)),
                amount = 1_250_000,
                decimals = 6,
            ),
            tx_encoding = Encoding.Base64,
        )
    }

    private fun existingAccountInput(withKey: Boolean): SigningInput {
        val sender = Base58.encode(publicKey())
        return SigningInput(
            private_key = if (withKey) { privateKey().data.toByteString() } else { okio.ByteString.EMPTY },
            recent_blockhash = "11111111111111111111111111111111",
            sender = sender,
            token_transfer_transaction = TokenTransfer(
                token_mint_address = USDC_MINT,
                sender_token_address = requireNotNull(SolanaAddress(sender).defaultTokenAddress(USDC_MINT)),
                recipient_token_address = EXISTING_TOKEN_ACCOUNT,
                amount = 3_000_000,
                decimals = 6,
            ),
            tx_encoding = Encoding.Base64,
        )
    }

    @Test
    fun `dapp transaction is signed into our slot`() = runBlocking {
        val software = SigningOutput.ADAPTER.decode(AnySigner.sign(dappTransfer(withKey = true).encode(), CoinType.Solana))
        val signed = Base64.getDecoder().decode(software.encoded)
        val unsigned = signed.copyOf().also { it.fill(0, 1, 65) }

        val answer = SpeculosLab.controller().run(request()) {
            signer.signWireTransaction(this, path, publicKey(), Base64.getEncoder().encodeToString(unsigned))
        }

        assertEquals(software.encoded, Json.parseToJsonElement(answer).jsonObject.getValue("transaction").jsonPrimitive.content)
    }

    @Test
    fun `unknown program needs blind signing and signs once it is on`() = runBlocking {
        val message = byteArrayOf(1, 0, 1, 2) + publicKey() + ByteArray(32) { (it + 1).toByte() } + ByteArray(32) { 3 } +
            byteArrayOf(1, 1, 1, 0, 3, 1, 2, 3)
        val wire = Base64.getEncoder().encodeToString(byteArrayOf(1) + ByteArray(64) + message)
        val failure = try {
            SpeculosLab.controller().run(request()) { signer.signWireTransaction(this, path, publicKey(), wire) }
            null
        } catch (e: LedgerFailureException) {
            e.failure
        }
        assertEquals(LedgerFailure.SettingRequired(LedgerAppId.Solana, LedgerAppSetting.BlindSigning), failure)

        SpeculosLab.restart("Solana")
        SpeculosLab.toggleSetting("Solana", "Blind signing")
        try {
            val answer = SpeculosLab.controller().run(request()) { signer.signWireTransaction(this, path, publicKey(), wire) }
            val signature = Base58.decode(Json.parseToJsonElement(answer).jsonObject.getValue("signature").jsonPrimitive.content)
            assertArrayEquals(requireNotNull(privateKey().sign(message, Curve.ED25519)), signature)
        } finally {
            SpeculosLab.restart("Solana")
        }
    }

    private fun dappTransfer(withKey: Boolean) = SigningInput(
        private_key = if (withKey) { privateKey().data.toByteString() } else { okio.ByteString.EMPTY },
        recent_blockhash = "11111111111111111111111111111111",
        sender = Base58.encode(publicKey()),
        transfer_transaction = Transfer(recipient = RECIPIENT, value_ = 2_000_000),
        tx_encoding = Encoding.Base64,
    )

    private fun privateKey() = requireNotNull(wallet.getKey(CoinType.Solana, "m/44'/501'/0'/0'"))

    private fun publicKey(): ByteArray = privateKey().getPublicKeyEd25519().data

    private fun ByteArray.toHex(): String = "0x" + joinToString("") { "%02x".format(it) }

    private companion object {
        const val RECIPIENT = "EN2sCsJ1WDV8UFqsiTXHcUPUxQ4juE71eCknHYYMifkd"
        const val USDC_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
        const val EXISTING_TOKEN_ACCOUNT = "FGETo8T8wMcN2wCjav8VK6eh3dLk63evNDPxzLSJra8B"
        const val SYSTEM_PROGRAM = "11111111111111111111111111111111"
        const val TOKEN_PROGRAM = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
        const val RENT_SYSVAR = "SysvarRent111111111111111111111111111111111"
        const val ASSOCIATED_TOKEN_PROGRAM = "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL"
    }
}
