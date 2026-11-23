package com.tonapps.wallet.data.ledger.lab

import com.tonapps.ledgerkit.app.btc.BtcRawTransaction
import com.tonapps.ledgerkit.app.btc.BtcScripts
import com.tonapps.ledgerkit.controller.LedgerPurpose
import com.tonapps.ledgerkit.controller.LedgerRequest
import com.tonapps.wallet.data.ledger.LedgerDerivation
import com.tonapps.wallet.data.ledger.signing.BitcoinLedgerSigner
import com.trustwallet.core.CoinType
import com.trustwallet.core.HDVersion
import com.trustwallet.core.HDWallet
import com.trustwallet.core.Purpose
import com.trustwallet.core.WalletCoreLibLoader
import com.trustwallet.core.bitcoin.SigningOutput
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.crypto.digests.RIPEMD160Digest
import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

class LedgerLabBitcoinTest {

    private lateinit var wallet: HDWallet

    @Before
    fun setUp() {
        assumeTrue("Ledger lab is not running", SpeculosLab.isRunning)
        WalletCoreLibLoader.loadLibrary()
        wallet = HDWallet(SpeculosLab.seed, "")
        SpeculosLab.policy("approve")
    }

    private fun publicKey(change: Int, index: Int): ByteArray =
        requireNotNull(wallet.getKey(CoinType.Bitcoin, "m/84'/0'/0'/$change/$index")).getPublicKeySecp256k1(true).data

    @Test
    fun `two input payment with change is signed by the account keys`() = runBlocking {
        val receiveKey = publicKey(0, 0)
        val ownScript = BtcScripts.p2wpkhScript(receiveKey)
        val first = previousTransaction(ownScript, 70_000, salt = 1)
        val second = previousTransaction(ownScript, 45_000, salt = 2)
        val recipientScript = BtcScripts.p2wpkhScript(publicKey(0, 7))
        val spend = BitcoinLedgerSigner.PlannedSpend(
            inputs = listOf(input(first, 70_000, ownScript), input(second, 45_000, ownScript)),
            outputs = listOf(
                BitcoinLedgerSigner.PlannedOutput(80_000, recipientScript, isOwnChange = false),
                BitcoinLedgerSigner.PlannedOutput(33_500, ownScript, isOwnChange = true),
            ),
        )
        val encoded = SpeculosLab.controller().run(LedgerRequest("lab", "Stax lab", LedgerPurpose.SignTransaction)) {
            BitcoinLedgerSigner(mockk(relaxed = true)).sign(
                scope = this,
                spend = spend,
                accountIndex = 0,
                expectedFingerprint = fingerprint(),
                expectedZpub = requireNotNull(wallet.getExtendedPublicKey(Purpose.BIP84, CoinType.Bitcoin, HDVersion.ZPUB)),
                step = 1,
                totalSteps = 1,
            )
        }
        val signed = BtcRawTransaction.parse(SigningOutput.ADAPTER.decode(encoded).encoded.toByteArray())
        assertEquals(2, signed.inputs.size)
        assertEquals(listOf(80_000L, 33_500L), signed.outputs.map { it.amount })
        signed.inputs.forEachIndexed { index, input ->
            val witness = signed.witnesses[index]
            assertEquals(2, witness.size)
            assertArrayEquals(receiveKey, witness[1])
            val signature = witness[0]
            assertEquals(1, signature.last().toInt())
            val amount = spend.inputs[index].amount
            assertTrue("input $index signature does not verify", verify(receiveKey, bip143(signed, index, receiveKey, amount), signature.copyOfRange(0, signature.size - 1)))
        }
    }

    private fun input(previous: ByteArray, amount: Long, script: ByteArray) = BitcoinLedgerSigner.PlannedInput(
        previousTxidInternal = doubleSha256(previous),
        previousIndex = 0,
        amount = amount,
        scriptPubKey = script,
        path = LedgerDerivation.bitcoinAddress(0, 0, 0),
        previousTransaction = previous,
    )

    private fun previousTransaction(script: ByteArray, amount: Long, salt: Int): ByteArray = ByteArrayOutputStream().apply {
        write(le(2, 4))
        write(1)
        write(ByteArray(32) { (it + salt).toByte() })
        write(le(0, 4))
        write(0)
        write(le(0xffffffffL, 4))
        write(1)
        write(le(amount, 8))
        write(script.size)
        write(script)
        write(le(0, 4))
    }.toByteArray()

    private fun bip143(tx: BtcRawTransaction, index: Int, publicKey: ByteArray, amount: Long): ByteArray {
        val prevouts = ByteArrayOutputStream().apply { tx.inputs.forEach { write(it.previousTxidInternal); write(le(it.previousIndex, 4)) } }.toByteArray()
        val sequences = ByteArrayOutputStream().apply { tx.inputs.forEach { write(le(it.sequence, 4)) } }.toByteArray()
        val outputs = ByteArrayOutputStream().apply { tx.outputs.forEach { write(le(it.amount, 8)); write(it.script.size); write(it.script) } }.toByteArray()
        val input = tx.inputs[index]
        val scriptCode = byteArrayOf(0x19, 0x76, 0xa9.toByte(), 0x14) + hash160(publicKey) + byteArrayOf(0x88.toByte(), 0xac.toByte())
        val preimage = ByteArrayOutputStream().apply {
            write(le(tx.version, 4))
            write(doubleSha256(prevouts))
            write(doubleSha256(sequences))
            write(input.previousTxidInternal)
            write(le(input.previousIndex, 4))
            write(scriptCode)
            write(le(amount, 8))
            write(le(input.sequence, 4))
            write(doubleSha256(outputs))
            write(le(tx.lockTime, 4))
            write(le(1, 4))
        }.toByteArray()
        return doubleSha256(preimage)
    }

    private fun verify(publicKey: ByteArray, hash: ByteArray, der: ByteArray): Boolean {
        val curve = CustomNamedCurves.getByName("secp256k1")
        val key = ECPublicKeyParameters(curve.curve.decodePoint(publicKey), ECDomainParameters(curve.curve, curve.g, curve.n, curve.h))
        val sequence = ASN1Sequence.getInstance(der)
        return ECDSASigner().apply { init(false, key) }.verifySignature(
            hash,
            (sequence.getObjectAt(0) as ASN1Integer).positiveValue,
            (sequence.getObjectAt(1) as ASN1Integer).positiveValue,
        )
    }

    private fun fingerprint(): String {
        val master = requireNotNull(wallet.getKey(CoinType.Bitcoin, "m")).getPublicKeySecp256k1(true).data
        return hash160(master).copyOfRange(0, 4).joinToString("") { "%02x".format(it) }
    }

    private fun hash160(data: ByteArray): ByteArray {
        val sha = MessageDigest.getInstance("SHA-256").digest(data)
        return ByteArray(20).also { RIPEMD160Digest().apply { update(sha, 0, sha.size); doFinal(it, 0) } }
    }

    private fun doubleSha256(data: ByteArray): ByteArray {
        val sha = MessageDigest.getInstance("SHA-256")
        return sha.digest(sha.digest(data))
    }

    private fun le(value: Long, size: Int): ByteArray = ByteArray(size) { ((value ushr (8 * it)) and 0xff).toByte() }
}
