package com.tonapps.wallet.data.ledger

import com.tonapps.ledgerkit.apdu.ApduResponse
import com.tonapps.wallet.data.ledger.signing.TronLedgerSigner
import com.trustwallet.core.AnySigner
import com.trustwallet.core.CoinType
import com.trustwallet.core.WalletCoreLibLoader
import com.trustwallet.core.tron.BlockHeader
import com.trustwallet.core.tron.SigningInput
import com.trustwallet.core.tron.SigningOutput
import com.trustwallet.core.tron.Transaction
import com.trustwallet.core.tron.TransferContract
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Test
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Sign
import java.math.BigInteger
import java.security.MessageDigest

class TronLedgerSignerTest {

    init {
        WalletCoreLibLoader.loadLibrary()
    }

    private val privateKey = ByteArray(32) { (it * 5 + 1).toByte() }
    private val keyPair = ECKeyPair.create(BigInteger(1, privateKey))

    @Test
    fun `trx transfer compiled from a ledger signature equals the software signature`() = runTest {
        val input = SigningInput(
            transaction = Transaction(
                timestamp = 1_539_295_479_000,
                expiration = 1_539_331_479_000,
                block_header = BlockHeader(
                    timestamp = 1_539_295_479_000,
                    tx_trie_root = ByteArray(32) { 1 }.toByteString(),
                    parent_hash = ByteArray(32) { 2 }.toByteString(),
                    number = 3_111_739,
                    witness_address = ByteArray(21) { 3 }.toByteString(),
                    version = 3,
                ),
                transfer = TransferContract(
                    owner_address = "TJRyWwFs9wTFGZg3JbrVriFbNfCug5tDeC",
                    to_address = "THTR75o8xXAgCTQqpiot2AFRAjvW1tSbVV",
                    amount = 2_000_000,
                ),
            ),
        )
        var raw = ByteArray(0)
        val transport = FakeLedgerTransport("Tron") { apdu ->
            val p1 = apdu[2].toInt() and 0xff
            val data = apdu.copyOfRange(5, apdu.size)
            raw = if (p1 == 0x10 || p1 == 0x00) {
                val depth = data[0].toInt()
                data.copyOfRange(1 + depth * 4, data.size)
            } else {
                raw + data
            }
            if (p1 == 0x00 || p1 == 0x80) {
                ApduResponse(ByteArray(0), 0x9000)
            } else {
                val hash = MessageDigest.getInstance("SHA-256").digest(raw)
                val signature = Sign.signMessage(hash, keyPair, false)
                ApduResponse(signature.r + signature.s + byteArrayOf((signature.v[0] - 27).toByte()), 0x9000)
            }
        }
        val compiled = TronLedgerSigner().signTransaction(transport.scope(), LedgerDerivation.tron(0), input.encode(), 1, 1)
        val ledger = SigningOutput.ADAPTER.decode(compiled)
        val software = SigningOutput.ADAPTER.decode(AnySigner.sign(input.copy(private_key = privateKey.toByteString()).encode(), CoinType.Tron))
        assertEquals(software.id.hex(), ledger.id.hex())
        assertEquals(software.signature.hex(), ledger.signature.hex())
        assertEquals(software.json, ledger.json)
    }
}
