package com.tonapps.wallet.data.ledger.lab

import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.core
import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerAppSetting
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.controller.LedgerPurpose
import com.tonapps.ledgerkit.controller.LedgerRequest
import com.tonapps.wallet.data.ledger.LedgerDerivation
import com.tonapps.wallet.data.ledger.signing.EvmLedgerSigner
import com.tonapps.wallet.data.ledger.signing.EvmTokenDescriptors
import com.tonapps.wallet.data.ledger.signing.EvmTypedDataFilters
import com.trustwallet.core.AnyAddress
import com.trustwallet.core.AnySigner
import com.trustwallet.core.CoinType
import com.trustwallet.core.EthereumMessageSigner
import com.trustwallet.core.HDWallet
import com.trustwallet.core.WalletCoreLibLoader
import com.trustwallet.core.ethereum.SigningInput
import com.trustwallet.core.ethereum.SigningOutput
import com.trustwallet.core.ethereum.Transaction
import com.trustwallet.core.ethereum.TransactionMode
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.math.BigInteger

class LedgerLabEvmTest {

    private lateinit var wallet: HDWallet
    private val signer = EvmLedgerSigner()
    private val path = LedgerDerivation.ethereum(0)

    private val privateKey get() = requireNotNull(wallet.getKey(CoinType.Ethereum, "m/44'/60'/0'/0/0"))

    @Before
    fun setUp() {
        assumeTrue("Ledger lab is not running", SpeculosLab.isRunning)
        WalletCoreLibLoader.loadLibrary()
        wallet = HDWallet(SpeculosLab.seed, "")
        SpeculosLab.policy("approve")
        SpeculosLab.restart("Ethereum")
    }

    private fun request() = LedgerRequest("lab", "Stax lab", LedgerPurpose.SignTransaction)

    @Test
    fun `address verification on the device returns the account address`() = runBlocking {
        val address = SpeculosLab.controller().run(request()) { signer.readAddress(this, path, display = true, expectedAddress = "lab") }
        assertEquals(AnyAddress(privateKey.getPublicKeySecp256k1(false), CoinType.Ethereum).description.lowercase(), address.lowercase())
    }

    @Test
    fun `eip1559 native transfer on base equals the software signature`() = runBlocking {
        assertMatchesSoftware(Chain.Base.Mainnet, input(8453, TransactionMode.Enveloped, RECIPIENT, native(1_000_000_000_000_000)))
    }

    @Test
    fun `legacy native transfer on bsc equals the software signature`() = runBlocking {
        assertMatchesSoftware(Chain.Smartchain.Mainnet, input(56, TransactionMode.Legacy, RECIPIENT, native(2_000_000_000_000_000)))
    }

    @Test
    fun `usdt transfer on ethereum is clear signed with the ledger descriptor`() = runBlocking {
        val input = input(1, TransactionMode.Enveloped, USDT, Transaction(erc20_transfer = Transaction.ERC20Transfer(to = RECIPIENT, amount = amount(12_345_678))))
        val descriptor = EvmTokenDescriptors(OkHttpClient()).forTransaction(input.encode())
        assertNotNull(descriptor)
        assertMatchesSoftware(Chain.Ethereum.Mainnet, input, descriptor)
    }

    @Test
    fun `usdc approve on base is clear signed with the ledger descriptor`() = runBlocking {
        val input = input(8453, TransactionMode.Enveloped, USDC_BASE, Transaction(erc20_approve = Transaction.ERC20Approve(spender = RECIPIENT, amount = amount(5_000_000))))
        val descriptor = EvmTokenDescriptors(OkHttpClient()).forTransaction(input.encode())
        assertNotNull(descriptor)
        assertMatchesSoftware(Chain.Base.Mainnet, input, descriptor)
    }

    @Test
    fun `unknown token transfer needs the blind signing setting`() = runBlocking {
        val input = input(8453, TransactionMode.Enveloped, UNKNOWN_TOKEN, Transaction(erc20_transfer = Transaction.ERC20Transfer(to = RECIPIENT, amount = amount(1))))
        assertNull(EvmTokenDescriptors(OkHttpClient()).forTransaction(input.encode()))
        val failure = try {
            assertMatchesSoftware(Chain.Base.Mainnet, input)
            null
        } catch (e: LedgerFailureException) {
            e.failure
        }
        assertEquals(LedgerFailure.SettingRequired(LedgerAppId.Ethereum, LedgerAppSetting.BlindSigning), failure)
    }

    @Test
    fun `personal sign of text and hex equals the software signature`() = runBlocking {
        for (message in listOf("Sign in to Keeper lab", "0x48656c6c6f")) {
            val ledger = SpeculosLab.controller().run(request()) { signer.signPersonalMessage(this, path, message) }
            val softwareInput = if (message.startsWith("0x")) { String(message.removePrefix("0x").decodeHex().toByteArray()) } else { message }
            assertEquals(EthereumMessageSigner.signMessage(privateKey, softwareInput).removePrefix("0x"), ledger.removePrefix("0x"))
        }
    }

    @Test
    fun `a long personal message paged through on the device equals the software signature`() = runBlocking {
        val message = (1..12).joinToString("\n") { "Line $it of a sign-in message the Ethereum app pages through." }
        val ledger = SpeculosLab.controller().run(request()) { signer.signPersonalMessage(this, path, message) }
        assertEquals(EthereumMessageSigner.signMessage(privateKey, message).removePrefix("0x"), ledger.removePrefix("0x"))
    }

    @Test
    fun `typed data without raw messages asks for the setting`() = runBlocking {
        val failure = try {
            SpeculosLab.controller().run(request()) { signer.signTypedData(this, path, PERMIT, null) }
            null
        } catch (e: LedgerFailureException) {
            e.failure
        }
        assertEquals(LedgerFailure.SettingRequired(LedgerAppId.Ethereum, LedgerAppSetting.RawMessages), failure)
    }

    @Test
    fun `permit typed data equals the software signature`() = runBlocking {
        assertTypedDataMatches(PERMIT)
    }

    @Test
    fun `nested typed data with arrays equals the software signature`() = runBlocking {
        assertTypedDataMatches(MAIL)
    }

    @Test
    fun `usdc permit on ethereum is clear signed with ledger filters without raw messages`() = runBlocking {
        assertClearSigned(USDC_PERMIT)
    }

    @Test
    fun `permit2 allowance on base is clear signed with the token joined to its amount`() = runBlocking {
        assertClearSigned(PERMIT2_SINGLE)
    }

    @Test
    fun `uniswapx order on base is clear signed with filters inside the outputs array`() = runBlocking {
        assertClearSigned(dutchOrder(outputs = DUTCH_OUTPUT))
    }

    @Test
    fun `uniswapx order with no outputs discards the filters of the empty array`() = runBlocking {
        assertClearSigned(dutchOrder(outputs = ""))
    }

    private suspend fun assertClearSigned(json: String) {
        val clearSigning = EvmTypedDataFilters(EvmTokenDescriptors(OkHttpClient())).clearSigning(json)
        assertNotNull(clearSigning)
        val ledger = SpeculosLab.controller().run(request()) { signer.signTypedData(this, path, json, clearSigning) }
        assertEquals(EthereumMessageSigner.signTypedMessage(privateKey, json).removePrefix("0x"), ledger.removePrefix("0x"))
    }

    private suspend fun assertTypedDataMatches(json: String) {
        SpeculosLab.toggleSetting("Ethereum", "Raw messages")
        try {
            val ledger = SpeculosLab.controller().run(request()) { signer.signTypedData(this, path, json, null) }
            assertEquals(EthereumMessageSigner.signTypedMessage(privateKey, json).removePrefix("0x"), ledger.removePrefix("0x"))
        } finally {
            SpeculosLab.toggleSetting("Ethereum", "Raw messages")
        }
    }

    private suspend fun assertMatchesSoftware(chain: Chain, input: SigningInput, descriptor: ByteArray? = null) {
        val compiled = SpeculosLab.controller().run(request()) {
            signer.signTransaction(this, path, chain, input.encode(), descriptor, 1, 1)
        }
        val ledger = SigningOutput.ADAPTER.decode(compiled)
        val software = SigningOutput.ADAPTER.decode(AnySigner.sign(input.copy(private_key = privateKey.data.toByteString()).encode(), chain.coin.core))
        assertEquals(software.encoded.hex(), ledger.encoded.hex())
    }

    private fun input(chainId: Long, mode: TransactionMode, to: String, transaction: Transaction) = SigningInput(
        chain_id = amount(chainId),
        nonce = amount(3),
        tx_mode = mode,
        gas_price = if (mode == TransactionMode.Legacy) { amount(3_000_000_000) } else { ByteString.EMPTY },
        gas_limit = amount(90_000),
        max_inclusion_fee_per_gas = if (mode == TransactionMode.Enveloped) { amount(1_000_000) } else { ByteString.EMPTY },
        max_fee_per_gas = if (mode == TransactionMode.Enveloped) { amount(20_000_000_000) } else { ByteString.EMPTY },
        to_address = to,
        transaction = transaction,
    )

    private fun native(value: Long) = Transaction(transfer = Transaction.Transfer(amount = amount(value)))

    private fun amount(value: Long): ByteString {
        val bytes = BigInteger.valueOf(value).toByteArray()
        return (if (bytes.size > 1 && bytes[0] == 0.toByte()) { bytes.copyOfRange(1, bytes.size) } else { bytes }).toByteString()
    }

    private companion object {
        const val RECIPIENT = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed"
        const val USDT = "0xdAC17F958D2ee523a2206206994597C13D831ec7"
        const val USDC_BASE = "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913"
        const val UNKNOWN_TOKEN = "0x1111111111111111111111111111111111111111"
        val PERMIT = """
            {"types":{"EIP712Domain":[{"name":"name","type":"string"},{"name":"version","type":"string"},{"name":"chainId","type":"uint256"},{"name":"verifyingContract","type":"address"}],
            "Permit":[{"name":"owner","type":"address"},{"name":"spender","type":"address"},{"name":"value","type":"uint256"},{"name":"nonce","type":"uint256"},{"name":"deadline","type":"uint256"}]},
            "primaryType":"Permit","domain":{"name":"USD Coin","version":"2","chainId":8453,"verifyingContract":"0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913"},
            "message":{"owner":"0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed","spender":"0x1111111254EEB25477B68fb85Ed929f73A960582","value":"1000000","nonce":"0","deadline":"1893456000"}}
        """.trimIndent()
        const val USDC_PERMIT = """
            {"types":{"EIP712Domain":[{"name":"name","type":"string"},{"name":"version","type":"string"},{"name":"chainId","type":"uint256"},{"name":"verifyingContract","type":"address"}],
            "Permit":[{"name":"owner","type":"address"},{"name":"spender","type":"address"},{"name":"value","type":"uint256"},{"name":"nonce","type":"uint256"},{"name":"deadline","type":"uint256"}]},
            "primaryType":"Permit","domain":{"name":"USD Coin","version":"2","chainId":1,"verifyingContract":"0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"},
            "message":{"owner":"0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed","spender":"0x87870Bca3F3fD6335C3F4ce8392D69350B4fA4E2","value":"2000000","nonce":"0","deadline":"1893456000"}}
        """
        const val PERMIT2_SINGLE = """
            {"types":{"EIP712Domain":[{"name":"name","type":"string"},{"name":"chainId","type":"uint256"},{"name":"verifyingContract","type":"address"}],
            "PermitDetails":[{"name":"token","type":"address"},{"name":"amount","type":"uint160"},{"name":"expiration","type":"uint48"},{"name":"nonce","type":"uint48"}],
            "PermitSingle":[{"name":"details","type":"PermitDetails"},{"name":"spender","type":"address"},{"name":"sigDeadline","type":"uint256"}]},
            "primaryType":"PermitSingle","domain":{"name":"Permit2","chainId":8453,"verifyingContract":"0x000000000022D473030F116dDEE9F6B43aC78BA3"},
            "message":{"details":{"token":"0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913","amount":"5000000","expiration":"1893456000","nonce":"0"},
            "spender":"0x3fC91A3afd70395Cd496C647d5a6CC9D4B2b7FAD","sigDeadline":"1893456000"}}
        """
        const val DUTCH_OUTPUT = """
            {"token":"0x4200000000000000000000000000000000000006","startAmount":"300000000000000","endAmount":"290000000000000","recipient":"0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed"}
        """

        val MAIL = """
            {"types":{"EIP712Domain":[{"name":"name","type":"string"},{"name":"version","type":"string"},{"name":"chainId","type":"uint256"},{"name":"verifyingContract","type":"address"}],
            "Person":[{"name":"name","type":"string"},{"name":"wallets","type":"address[]"}],
            "Mail":[{"name":"from","type":"Person"},{"name":"to","type":"Person[]"},{"name":"contents","type":"string"},{"name":"attachment","type":"bytes"}]},
            "primaryType":"Mail","domain":{"name":"Ether Mail","version":"1","chainId":1,"verifyingContract":"0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC"},
            "message":{"from":{"name":"Cow","wallets":["0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826","0xDeaDbeefdEAdbeefdEadbEEFdeadbeEFdEaDbeeF"]},
            "to":[{"name":"Bob","wallets":["0xbBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB","0xB0BdaBea57B0BDABeA57b0bdABEA57b0BDabEa57"]}],
            "contents":"Hello, Bob!","attachment":"0x1234"}}
        """.trimIndent()

        fun dutchOrder(outputs: String) = """
            {"types":{"EIP712Domain":[{"name":"name","type":"string"},{"name":"chainId","type":"uint256"},{"name":"verifyingContract","type":"address"}],
            "PermitWitnessTransferFrom":[{"name":"permitted","type":"TokenPermissions"},{"name":"spender","type":"address"},{"name":"nonce","type":"uint256"},{"name":"deadline","type":"uint256"},{"name":"witness","type":"DutchOrder"}],
            "TokenPermissions":[{"name":"token","type":"address"},{"name":"amount","type":"uint256"}],
            "DutchOrder":[{"name":"info","type":"OrderInfo"},{"name":"decayStartTime","type":"uint256"},{"name":"decayEndTime","type":"uint256"},{"name":"inputToken","type":"address"},{"name":"inputStartAmount","type":"uint256"},{"name":"inputEndAmount","type":"uint256"},{"name":"outputs","type":"DutchOutput[]"}],
            "OrderInfo":[{"name":"reactor","type":"address"},{"name":"swapper","type":"address"},{"name":"nonce","type":"uint256"},{"name":"deadline","type":"uint256"},{"name":"additionalValidationContract","type":"address"},{"name":"additionalValidationData","type":"bytes"}],
            "DutchOutput":[{"name":"token","type":"address"},{"name":"startAmount","type":"uint256"},{"name":"endAmount","type":"uint256"},{"name":"recipient","type":"address"}]},
            "primaryType":"PermitWitnessTransferFrom","domain":{"name":"Permit2","chainId":8453,"verifyingContract":"0x000000000022D473030F116dDEE9F6B43aC78BA3"},
            "message":{"permitted":{"token":"0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913","amount":"1000000"},"spender":"0x000000001Ec5656dcdB24D90DFa42742738De729","nonce":"1","deadline":"1893456000",
            "witness":{"info":{"reactor":"0x000000001Ec5656dcdB24D90DFa42742738De729","swapper":"0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed","nonce":"1","deadline":"1893456000",
            "additionalValidationContract":"0x0000000000000000000000000000000000000000","additionalValidationData":"0x"},
            "decayStartTime":"1893450000","decayEndTime":"1893456000","inputToken":"0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913","inputStartAmount":"1000000","inputEndAmount":"1000000",
            "outputs":[$outputs]}}}
        """.trimIndent()
    }
}
