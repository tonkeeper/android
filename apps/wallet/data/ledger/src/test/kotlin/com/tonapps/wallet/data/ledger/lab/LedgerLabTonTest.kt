package com.tonapps.wallet.data.ledger.lab

import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerAppSetting
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.controller.LedgerPurpose
import com.tonapps.ledgerkit.controller.LedgerRequest
import com.tonapps.blockchain.ton.extensions.cellFromBase64
import com.tonapps.chainkit.core.chain.model.account.LedgerWallet
import com.tonapps.wallet.data.ledger.signing.TonKeyReader
import com.tonapps.wallet.data.ledger.signing.TonLedgerSigner
import com.trustwallet.core.CoinType
import com.trustwallet.core.HDWallet
import com.trustwallet.core.WalletCoreLibLoader
import com.trustwallet.core.theopennetwork.JettonTransfer
import com.trustwallet.core.theopennetwork.SigningInput
import com.trustwallet.core.theopennetwork.SigningOutput
import com.trustwallet.core.theopennetwork.Transfer
import com.trustwallet.core.theopennetwork.WalletVersion
import kotlinx.coroutines.runBlocking
import kotlinx.io.bytestring.ByteString
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.ton.block.Message
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.kotlin.crypto.PublicKeyEd25519
import org.ton.tlb.constructor.AnyTlbConstructor
import org.ton.tlb.loadTlb
import java.math.BigInteger

class LedgerLabTonTest {

    private lateinit var wallet: HDWallet
    private val signer = TonLedgerSigner()

    @Before
    fun setUp() {
        assumeTrue("Ledger lab is not running", SpeculosLab.isRunning)
        WalletCoreLibLoader.loadLibrary()
        wallet = HDWallet(SpeculosLab.seed, "")
        SpeculosLab.policy("approve")
    }

    private fun publicKey(index: Int): ByteArray =
        requireNotNull(wallet.getKey(CoinType.TON, "m/44'/607'/0'/0'/$index'/0'")).getPublicKeyEd25519().data

    private fun request() = LedgerRequest("lab", "Stax lab", LedgerPurpose.SignTransaction)

    @Test
    fun `address verification on the device returns the account key`() = runBlocking {
        val key = SpeculosLab.controller().run(request()) {
            TonKeyReader.readPublicKey(this, 0, display = true, expectedAddress = "lab")
        }
        assertArrayEquals(publicKey(0), key)
    }

    @Test
    fun `wallet key proof is the same on every run and differs per account`() = runBlocking {
        val controller = SpeculosLab.controller()
        val first = controller.run(request()) { signer.signWalletKeyProof(this, 0, publicKey(0), 1, 1) }
        val second = controller.run(request()) { signer.signWalletKeyProof(this, 0, publicKey(0), 1, 1) }
        val other = controller.run(request()) { signer.signWalletKeyProof(this, 1, publicKey(1), 1, 1) }
        assertArrayEquals(first, second)
        assertArrayEquals(LedgerWallet.fromProofSignature(publicKey(0), first).unwrap().seed(), LedgerWallet.fromProofSignature(publicKey(0), second).unwrap().seed())
        assertFalse(first.contentEquals(other))
    }

    @Test
    fun `ton connect proof verifies with the account key`() = runBlocking {
        val signature = SpeculosLab.controller().run(request()) {
            signer.signProof(this, 0, publicKey(0), "app.example.com", System.currentTimeMillis() / 1000, "payload-123")
        }
        assertEquals(64, signature.size)
    }

    @Test
    fun `ton transfer with a comment is signed by the account key`() = runBlocking {
        val output = sign(
            Transfer(dest = DESTINATION, amount = amount(500_000_000), mode = 3, comment = "hello ledger", bounceable = false),
            seqno = 5,
        )
        assertSignedBy(output, publicKey(0), expectedSeqno = 5)
    }

    @Test
    fun `undeployed wallet first transfer carries the state init`() = runBlocking {
        val output = sign(Transfer(dest = DESTINATION, amount = amount(10_000_000), mode = 3, bounceable = false), seqno = 0)
        val message = parse(output)
        assertTrue(message.init.value != null)
        assertSignedBy(output, publicKey(0), expectedSeqno = 0)
    }

    @Test
    fun `jetton transfer is signed by the account key`() = runBlocking {
        val output = sign(
            Transfer(
                dest = JETTON_WALLET,
                amount = amount(50_000_000),
                mode = 3,
                bounceable = true,
                jetton_transfer = JettonTransfer(
                    query_id = 0,
                    jetton_amount = BigInteger.valueOf(1_500_000).toByteArray().toByteString(),
                    to_owner = DESTINATION,
                    response_address = ownAddress(),
                    forward_amount = amount(1),
                ),
            ),
            seqno = 7,
        )
        assertSignedBy(output, publicKey(0), expectedSeqno = 7)
    }

    @Test
    fun `non latin comment needs the blind signing setting`() = runBlocking {
        val failure = try {
            sign(Transfer(dest = DESTINATION, amount = amount(1_000_000), mode = 3, comment = "привет", bounceable = false), seqno = 8)
            null
        } catch (e: LedgerFailureException) {
            e.failure
        }
        assertEquals(LedgerFailure.SettingRequired(LedgerAppId.Ton, LedgerAppSetting.BlindSigning), failure)
    }

    private suspend fun sign(transfer: Transfer, seqno: Int): SigningOutput {
        val input = SigningInput(
            messages = listOf(transfer),
            sequence_number = seqno,
            expire_at = (System.currentTimeMillis() / 1000 + 600).toInt(),
            wallet_version = WalletVersion.WALLET_V4_R2,
        )
        val encoded = SpeculosLab.controller().run(request()) {
            signer.signChainkitInput(this, 0, publicKey(0), input.encode(), waitedSeconds = 0, step = 1, totalSteps = 1)
        }
        return SigningOutput.ADAPTER.decode(encoded)
    }

    private fun parse(output: SigningOutput): Message<Cell> {
        val cell = output.encoded.cellFromBase64()
        return cell.parse { loadTlb(Message.tlbCodec(AnyTlbConstructor)) }
    }

    private fun assertSignedBy(output: SigningOutput, publicKey: ByteArray, expectedSeqno: Int) {
        val message = parse(output)
        val body = message.body.x ?: requireNotNull(message.body.y).value
        val slice = body.beginParse()
        val signature = slice.loadBits(512).toByteArray()
        val order = CellBuilder.createCell {
            storeBits(slice.loadBits(slice.remainingBits))
            slice.refs.drop(slice.refsPosition).forEach { storeRef(it) }
        }
        assertTrue("signature does not verify", PublicKeyEd25519(ByteString(publicKey)).verifySignature(order.hash().toByteArray(), signature))
        val orderSlice = order.beginParse()
        assertEquals(698983191L, orderSlice.loadUInt(32).toLong())
        orderSlice.loadUInt(32)
        assertEquals(expectedSeqno.toLong(), orderSlice.loadUInt(32).toLong())
    }

    private fun ownAddress(): String = com.tonapps.blockchain.ton.contract.WalletV4R2Contract(
        publicKey = PublicKeyEd25519(ByteString(publicKey(0))),
    ).address.toString(userFriendly = true, bounceable = false)

    private fun amount(value: Long) = BigInteger.valueOf(value).toByteArray().toByteString()

    private companion object {
        const val DESTINATION = "UQCD39VS5jcptHL8vMjEXrzGaRcCVYto7HUn4bpAOg8xqEBI"
        const val JETTON_WALLET = "EQDKbjIcfM6ezt8KjKJJLshZJJSqX7XOA4ff-W72r5gqPrHF"
    }
}
