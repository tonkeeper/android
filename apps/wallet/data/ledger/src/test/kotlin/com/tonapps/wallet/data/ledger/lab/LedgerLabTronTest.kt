package com.tonapps.wallet.data.ledger.lab

import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerAppSetting
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.controller.LedgerPurpose
import com.tonapps.ledgerkit.controller.LedgerRequest
import com.tonapps.wallet.data.ledger.LedgerDerivation
import com.tonapps.wallet.data.ledger.signing.TronLedgerSigner
import com.trustwallet.core.AnyAddress
import com.trustwallet.core.AnySigner
import com.trustwallet.core.CoinType
import com.trustwallet.core.HDWallet
import com.trustwallet.core.TronMessageSigner
import com.trustwallet.core.WalletCoreLibLoader
import com.trustwallet.core.tron.BlockHeader
import com.trustwallet.core.tron.SigningInput
import com.trustwallet.core.tron.SigningOutput
import com.trustwallet.core.tron.Transaction
import com.trustwallet.core.tron.TransferContract
import com.trustwallet.core.tron.TransferTRC20Contract
import com.trustwallet.core.tron.TriggerSmartContract
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.math.BigInteger

class LedgerLabTronTest {

    private lateinit var wallet: HDWallet
    private val signer = TronLedgerSigner()
    private val path = LedgerDerivation.tron(0)

    private val privateKey get() = requireNotNull(wallet.getKey(CoinType.Tron, "m/44'/195'/0'/0/0"))

    private val owner get() = AnyAddress(privateKey.getPublicKeySecp256k1(false), CoinType.Tron).description

    @Before
    fun setUp() {
        assumeTrue("Ledger lab is not running", SpeculosLab.isRunning)
        WalletCoreLibLoader.loadLibrary()
        wallet = HDWallet(SpeculosLab.seed, "")
        SpeculosLab.policy("approve")
        SpeculosLab.restart("Tron")
    }

    private fun request() = LedgerRequest("lab", "Stax lab", LedgerPurpose.SignTransaction)

    @Test
    fun `address verification on the device returns the account address`() = runBlocking {
        val address = SpeculosLab.controller().run(request()) { signer.readAddress(this, path, display = true, expectedAddress = "lab") }
        assertEquals(owner, address)
    }

    @Test
    fun `trx transfer equals the software signature`() = runBlocking {
        assertMatchesSoftware(input(Transaction(transfer = TransferContract(owner_address = owner, to_address = RECIPIENT, amount = 1_500_000)).withHeader()))
    }

    @Test
    fun `usdt transfer equals the software signature`() = runBlocking {
        assertMatchesSoftware(
            input(
                Transaction(
                    fee_limit = 30_000_000,
                    transfer_trc20_contract = TransferTRC20Contract(
                        contract_address = USDT,
                        owner_address = owner,
                        to_address = RECIPIENT,
                        amount = BigInteger.valueOf(2_500_000).toByteArray().toByteString(),
                    ),
                ).withHeader(),
            ),
        )
    }

    @Test
    fun `large contract call needs the sign by hash setting`() = runBlocking {
        val failure = try {
            assertMatchesSoftware(
                input(
                    Transaction(
                        fee_limit = 100_000_000,
                        trigger_smart_contract = TriggerSmartContract(
                            owner_address = owner,
                            contract_address = USDT,
                            data_ = ByteArray(4 + 32 * 9) { (it % 7).toByte() }.toByteString(),
                        ),
                    ).withHeader(),
                ),
            )
            null
        } catch (e: LedgerFailureException) {
            e.failure
        }
        assertEquals(LedgerFailure.SettingRequired(LedgerAppId.Tron, LedgerAppSetting.SignByHash), failure)
    }

    @Test
    fun `personal message needs the sign by hash setting`() = runBlocking {
        val failure = try {
            SpeculosLab.controller().run(request()) { signer.signPersonalMessage(this, path, "Sign in to Keeper lab") }
            null
        } catch (e: LedgerFailureException) {
            e.failure
        }
        assertEquals(LedgerFailure.SettingRequired(LedgerAppId.Tron, LedgerAppSetting.SignByHash), failure)
    }

    @Test
    fun `with blind signing on messages and large calls equal the software signature`() = runBlocking {
        SpeculosLab.toggleSetting("Tron", "Blind signing")
        try {
            val ledger = SpeculosLab.controller().run(request()) { signer.signPersonalMessage(this, path, "Sign in to Keeper lab") }
            val signature = kotlinx.serialization.json.Json.parseToJsonElement(ledger).jsonObject.getValue("signature").jsonPrimitive.content
            assertEquals(TronMessageSigner.signMessage(privateKey, "Sign in to Keeper lab").removePrefix("0x"), signature.removePrefix("0x"))
            assertMatchesSoftware(
                input(
                    Transaction(
                        fee_limit = 100_000_000,
                        trigger_smart_contract = TriggerSmartContract(
                            owner_address = owner,
                            contract_address = USDT,
                            data_ = ByteArray(4 + 32 * 9) { (it % 7).toByte() }.toByteString(),
                        ),
                    ).withHeader(),
                ),
            )
        } finally {
            SpeculosLab.toggleSetting("Tron", "Blind signing")
        }
    }

    private suspend fun assertMatchesSoftware(input: SigningInput) {
        val compiled = SpeculosLab.controller().run(request()) { signer.signTransaction(this, path, input.encode(), 1, 1) }
        val ledger = SigningOutput.ADAPTER.decode(compiled)
        val software = SigningOutput.ADAPTER.decode(AnySigner.sign(input.copy(private_key = privateKey.data.toByteString()).encode(), CoinType.Tron))
        assertEquals(software.id.hex(), ledger.id.hex())
        assertEquals(software.signature.hex(), ledger.signature.hex())
    }

    private fun input(transaction: Transaction) = SigningInput(transaction = transaction)

    private fun Transaction.withHeader(): Transaction {
        val now = System.currentTimeMillis()
        return copy(
            timestamp = now,
            expiration = now + 10 * 60 * 1000,
            block_header = BlockHeader(
                timestamp = now - 3_000,
                tx_trie_root = ByteArray(32) { 1 }.toByteString(),
                parent_hash = ByteArray(32) { 2 }.toByteString(),
                number = 70_000_000,
                witness_address = ByteArray(21) { 0x41 }.toByteString(),
                version = 30,
            ),
        )
    }

    private companion object {
        const val RECIPIENT = "THTR75o8xXAgCTQqpiot2AFRAjvW1tSbVV"
        const val USDT = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
    }
}
