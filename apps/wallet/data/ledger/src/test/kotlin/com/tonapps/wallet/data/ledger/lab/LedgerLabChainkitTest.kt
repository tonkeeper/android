package com.tonapps.wallet.data.ledger.lab

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.ton.contract.WalletV4R2Contract
import com.tonapps.blockchain.ton.extensions.cellFromBase64
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Derivation
import com.tonapps.chainkit.core.chain.model.account.PubKey
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.ledgerkit.controller.LedgerPurpose
import com.tonapps.ledgerkit.controller.LedgerRequest
import com.tonapps.wallet.data.ledger.LedgerDerivation
import com.tonapps.wallet.data.ledger.signing.EvmLedgerSigner
import com.tonapps.wallet.data.ledger.signing.EvmTokenDescriptors
import com.tonapps.wallet.data.ledger.signing.TonLedgerSigner
import com.tonapps.wallet.data.ledger.signing.TronLedgerSigner
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.trustwallet.core.AnyAddress
import com.trustwallet.core.AnySigner
import com.trustwallet.core.CoinType
import com.trustwallet.core.HDWallet
import com.trustwallet.core.WalletCoreLibLoader
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.io.bytestring.ByteString as IoByteString
import okhttp3.OkHttpClient
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.ton.kotlin.crypto.PublicKeyEd25519
import com.trustwallet.core.ethereum.SigningInput as EthSigningInput
import com.trustwallet.core.ethereum.SigningOutput as EthSigningOutput
import com.trustwallet.core.theopennetwork.SigningInput as TonSigningInput
import com.trustwallet.core.theopennetwork.SigningOutput as TonSigningOutput
import com.trustwallet.core.tron.SigningInput as TronSigningInput
import com.trustwallet.core.tron.SigningOutput as TronSigningOutput

class LedgerLabChainkitTest {

    private lateinit var wallet: HDWallet

    private val provider get() = LabChainKit.provider

    @Before
    fun setUp() {
        assumeTrue("Ledger lab is not running", SpeculosLab.isRunning)
        WalletCoreLibLoader.loadLibrary()
        wallet = HDWallet(SpeculosLab.seed, "")
        SpeculosLab.policy("approve")
    }

    private fun request() = LedgerRequest("lab", "Stax lab", LedgerPurpose.SignTransaction)

    @Test
    fun `ton transfer with a comment built by chainkit equals the software signature`() = runBlocking {
        val chain = Chain.Ton.Mainnet
        val publicKey = tonPrivateKey().getPublicKeyEd25519().data
        val contract = WalletV4R2Contract(publicKey = PublicKeyEd25519(IoByteString(publicKey)))
        val address = contract.address.toString(userFriendly = true, bounceable = false)
        val account = Account(
            address = Address.force(address, chain, Address.Type.TonV4R2),
            asset = chain.toAsset(),
            derivation = Derivation.DefaultPath,
            publicKey = PubKey(publicKey.toHex(), ""),
        )
        val transfer = Transaction.Transfer(
            account = account,
            amount = BigInteger.fromLong(50_000_000),
            isMax = false,
            to = Address.force(TON_DESTINATION, chain),
            memo = "hello ledger",
        )
        val input = unsignedInput(transfer, Fee.Value(chain.toAsset(), BigInteger.fromLong(5_000_000)), BigInteger.fromInt(5))

        val ledger = SpeculosLab.controller().run(request()) {
            TonLedgerSigner().signChainkitInput(this, 0, publicKey, input, waitedSeconds = 0, step = 1, totalSteps = 1)
        }

        val decoded = TonSigningInput.ADAPTER.decode(input)
        val software = TonSigningOutput.ADAPTER.decode(
            AnySigner.sign(
                decoded.copy(
                    private_key = tonPrivateKey().data.toByteString(),
                    expire_at = decoded.expire_at + HARDWARE_EXPIRY_EXTENSION_SECONDS,
                ).encode(),
                CoinType.TON,
            ),
        )
        assertEquals(
            software.encoded.cellFromBase64().hash().toByteArray().toHex(),
            TonSigningOutput.ADAPTER.decode(ledger).encoded.cellFromBase64().hash().toByteArray().toHex(),
        )
    }

    @Test
    fun `split swap route built by chainkit is signed message by message with consecutive seqnos`() = runBlocking {
        val chain = Chain.Ton.Mainnet
        val publicKey = tonPrivateKey().getPublicKeyEd25519().data
        val usdt = AssetEntity(id = USDT_TON, name = "Tether USD", symbol = "USDT", decimals = 6, imageUrl = "").value
        val legs = splitRouteMessages().map { message ->
            Transaction.Swap(
                account = tonAccount(publicKey, usdt),
                amount = message.amount,
                destination = tonAccount(publicKey, chain.toAsset()),
                to = Address.force(message.to, chain),
                data = message.payload,
            )
        }
        val fee = Fee.Value(chain.toAsset(), BigInteger.fromLong(12_000_000))
        val inputs = legs.mapIndexed { index, leg -> unsignedInput(leg, fee, BigInteger.fromInt(SPLIT_SEQNO + index)) }

        SpeculosLab.toggleSetting("TON", "Blind signing")
        val signed = try {
            SpeculosLab.controller().run(request()) {
                inputs.mapIndexed { index, input ->
                    TonLedgerSigner().signChainkitInput(this, 0, publicKey, input, waitedSeconds = 0, step = index + 1, totalSteps = inputs.size)
                }
            }
        } finally {
            SpeculosLab.toggleSetting("TON", "Blind signing")
        }

        assertEquals(legs.size, signed.size)
        inputs.zip(signed).forEachIndexed { index, (input, ledger) ->
            val decoded = TonSigningInput.ADAPTER.decode(input)
            assertEquals("seqno #$index", SPLIT_SEQNO + index, decoded.sequence_number)
            assertEquals("payload #$index", legs[index].data, decoded.messages.single().custom_payload)
            val software = TonSigningOutput.ADAPTER.decode(
                AnySigner.sign(
                    decoded.copy(
                        private_key = tonPrivateKey().data.toByteString(),
                        expire_at = decoded.expire_at + HARDWARE_EXPIRY_EXTENSION_SECONDS,
                    ).encode(),
                    CoinType.TON,
                ),
            )
            assertEquals(
                "message #$index",
                software.encoded.cellFromBase64().hash().toByteArray().toHex(),
                TonSigningOutput.ADAPTER.decode(ledger).encoded.cellFromBase64().hash().toByteArray().toHex(),
            )
        }
    }

    @Test
    fun `eth transfer on base built by chainkit equals the software signature`() = runBlocking {
        val chain = Chain.Base.Mainnet
        val transfer = Transaction.Transfer(
            account = evmAccount(chain, chain.toAsset()),
            amount = BigInteger.fromLong(1_000_000_000_000),
            isMax = false,
            to = Address.force(EVM_RECIPIENT, chain),
        )
        assertEvmMatchesSoftware(chain, unsignedInput(transfer, eip1559(chain), BigInteger.fromInt(3)), descriptor = null)
    }

    @Test
    fun `usdt transfer on ethereum built by chainkit is clear signed and equals the software signature`() = runBlocking {
        val chain = Chain.Ethereum.Mainnet
        val usdt = AssetEntity(id = USDT_ETH, name = "Tether USD", symbol = "USDT", decimals = 6, imageUrl = "").value
        val transfer = Transaction.Transfer(
            account = evmAccount(chain, usdt),
            amount = BigInteger.fromLong(12_345_678),
            isMax = false,
            to = Address.force(EVM_RECIPIENT, chain),
        )
        val input = unsignedInput(transfer, eip1559(chain), BigInteger.fromInt(4))
        val descriptor = EvmTokenDescriptors(OkHttpClient()).forTransaction(input)
        assertNotNull("chainkit's ERC-20 transfer is not recognised for clear signing", descriptor)
        assertEvmMatchesSoftware(chain, input, descriptor)
    }

    @Test
    fun `trx transfer built by chainkit equals the software signature`() = runBlocking {
        val chain = Chain.Tron.Mainnet
        val publicKey = tronPrivateKey().getPublicKeySecp256k1(false)
        val account = Account(
            address = Address.force(AnyAddress(publicKey, CoinType.Tron).description, chain, Address.Type.Default),
            asset = chain.toAsset(),
            derivation = Derivation.DefaultPath,
            publicKey = PubKey(publicKey.data.toHex(), ""),
        )
        val transfer = Transaction.Transfer(
            account = account,
            amount = BigInteger.fromLong(1_500_000),
            isMax = false,
            to = Address.force(TRON_RECIPIENT, chain),
        )
        val input = unsignedInput(transfer, Fee.Value(chain.toAsset(), BigInteger.fromLong(1_100_000)), BigInteger.ZERO)

        val ledger = TronSigningOutput.ADAPTER.decode(
            SpeculosLab.controller().run(request()) {
                TronLedgerSigner().signTransaction(this, LedgerDerivation.tron(0), input, 1, 1)
            },
        )
        val software = TronSigningOutput.ADAPTER.decode(
            AnySigner.sign(
                TronSigningInput.ADAPTER.decode(input).copy(private_key = tronPrivateKey().data.toByteString()).encode(),
                CoinType.Tron,
            ),
        )
        assertEquals(software.id.hex(), ledger.id.hex())
        assertEquals(software.signature.hex(), ledger.signature.hex())
    }

    private suspend fun unsignedInput(transaction: Transaction, fee: Fee, nonce: BigInteger): ByteArray {
        val inputs = provider.buildUnsignedInputs(transaction, fee, nonce)
        val encoded = requireNotNull(inputs.getOrNull()) { "chainkit could not build the input: ${inputs.error}" }
        assertEquals(1, encoded.size)
        return encoded.single()
    }

    private suspend fun assertEvmMatchesSoftware(chain: Chain, input: ByteArray, descriptor: ByteArray?) {
        val ledger = SpeculosLab.controller().run(request()) {
            EvmLedgerSigner().signTransaction(this, LedgerDerivation.ethereum(0), chain, input, descriptor, 1, 1)
        }
        val software = EthSigningOutput.ADAPTER.decode(
            AnySigner.sign(
                EthSigningInput.ADAPTER.decode(input).copy(private_key = evmPrivateKey().data.toByteString()).encode(),
                CoinType.Ethereum,
            ),
        )
        assertEquals(software.encoded.hex(), EthSigningOutput.ADAPTER.decode(ledger).encoded.hex())
    }

    private fun tonAccount(publicKey: ByteArray, asset: com.tonapps.chainkit.core.chain.model.account.Asset): Account {
        val contract = WalletV4R2Contract(publicKey = PublicKeyEd25519(IoByteString(publicKey)))
        return Account(
            address = Address.force(contract.address.toString(userFriendly = true, bounceable = false), Chain.Ton.Mainnet, Address.Type.TonV4R2),
            asset = asset,
            derivation = Derivation.DefaultPath,
            publicKey = PubKey(publicKey.toHex(), ""),
        )
    }

    private fun splitRouteMessages(): List<RouteMessage> {
        val json = requireNotNull(javaClass.classLoader?.getResourceAsStream(SPLIT_ROUTE)) { "missing test resource $SPLIT_ROUTE" }
            .bufferedReader()
            .use { it.readText() }
        return Json.parseToJsonElement(json).jsonArray.map { message ->
            val fields = message.jsonObject
            RouteMessage(
                to = fields.getValue("address").jsonPrimitive.content,
                amount = BigInteger.parseString(fields.getValue("amount").jsonPrimitive.content),
                payload = fields.getValue("payload").jsonPrimitive.content,
            )
        }
    }

    private class RouteMessage(val to: String, val amount: BigInteger, val payload: String)

    private fun evmAccount(chain: Chain, asset: com.tonapps.chainkit.core.chain.model.account.Asset): Account {
        val publicKey = evmPrivateKey().getPublicKeySecp256k1(false)
        return Account(
            address = Address.force(AnyAddress(publicKey, CoinType.Ethereum).description, chain, Address.Type.Default),
            asset = asset,
            derivation = Derivation.DefaultPath,
            publicKey = PubKey(publicKey.data.toHex(), ""),
        )
    }

    private fun eip1559(chain: Chain): Fee {
        val limit = BigInteger.fromLong(100_000)
        val price = BigInteger.fromLong(2_000_000_000)
        return Fee.Eip1559(chain.toAsset(), limit, price, price, price, price.multiply(limit))
    }

    private fun tonPrivateKey() = requireNotNull(wallet.getKey(CoinType.TON, "m/44'/607'/0'/0'/0'/0'"))

    private fun tronPrivateKey() = requireNotNull(wallet.getKey(CoinType.Tron, "m/44'/195'/0'/0/0"))

    private fun evmPrivateKey() = requireNotNull(wallet.getKey(CoinType.Ethereum, "m/44'/60'/0'/0/0"))

    private fun ByteArray.toHex(): String = "0x" + joinToString("") { "%02x".format(it) }

    private companion object {
        const val HARDWARE_EXPIRY_EXTENSION_SECONDS = 150
        const val TON_DESTINATION = "UQCD39VS5jcptHL8vMjEXrzGaRcCVYto7HUn4bpAOg8xqEBI"
        const val EVM_RECIPIENT = "0x0eE3CdEE4f010BF6c49aF86E9e67ef2bc80a508b"
        const val TRON_RECIPIENT = "THTR75o8xXAgCTQqpiot2AFRAjvW1tSbVV"
        const val USDT_ETH = "eth/mainnet/erc20/0xdAC17F958D2ee523a2206206994597C13D831ec7"
        const val USDT_TON = "ton/mainnet/jetton/0:b113a994b5024a16719f69139328eb759596c38a25f59028b146fecdc3621dfe"
        const val SPLIT_ROUTE = "lab_omniston_split_route.json"
        const val SPLIT_SEQNO = 11
    }
}
