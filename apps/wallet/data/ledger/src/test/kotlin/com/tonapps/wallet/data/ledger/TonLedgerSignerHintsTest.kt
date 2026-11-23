package com.tonapps.wallet.data.ledger

import com.tonapps.ledger.ton.KnownJetton
import com.tonapps.ledger.ton.TonPayloadFormat
import com.tonapps.ledger.ton.Transaction
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.apdu.ApduResponse
import com.tonapps.wallet.data.ledger.signing.TonLedgerSigner
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.MsgAddressInt
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.tlb.storeTlb
import java.io.ByteArrayOutputStream
import java.math.BigInteger

class TonLedgerSignerHintsTest {

    private val signer = TonLedgerSigner(clock = { (TIMEOUT - 60) * 1000L })

    private val whales = TonPayloadFormat.WhalesDeposit(queryId = BigInteger.valueOf(7), gasLimit = Coins.ofNano(100_000_000))

    private val usdt = TonPayloadFormat.JettonTransfer(
        queryId = null,
        coins = Coins.ofNano(1_000_000),
        receiverAddress = AddrStd.parse("EQDKbjIcfM6ezt8KjKJJLshZJJSqX7XOA4ff-W72r5gqPrHF"),
        excessesAddress = AddrStd.parse("EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs"),
        customPayload = null,
        forwardAmount = Coins.ofNano(1),
        forwardPayload = null,
        knownJetton = KnownJetton(jettonId = 0, workchain = 0),
    )

    @Test
    fun `whales deposit on ton app 2_6 is sent as a blind payload`() = runTest {
        assertArrayEquals(byteArrayOf(0), signAndCapture("2.6.4").afterPayloadHash())
    }

    @Test
    fun `whales deposit on ton app 2_8 carries the whales hint`() = runTest {
        assertArrayEquals(byteArrayOf(1, 0, 0, 0, 0x0b), signAndCapture("2.8.1").afterPayloadHash().copyOfRange(0, 5))
    }

    @Test
    fun `the legacy path drops the whales hint for a ton app that lacks it`() {
        val asked = mutableListOf<String>()

        val old = transaction(whales).withWhalesHintSupportedBy { required -> asked += required; false }
        val current = transaction(whales).withWhalesHintSupportedBy { true }
        val jetton = transaction(usdt).withWhalesHintSupportedBy { false }

        assertEquals(listOf(TonPayloadFormat.WHALES_MIN_APP_VERSION), asked)
        assertEquals(whales.toCell().hash(), (old.payload as TonPayloadFormat.Unsafe).message.hash())
        assertEquals(whales, current.payload)
        assertEquals(usdt, jetton.payload)
    }

    @Test
    fun `known jetton the ton app cannot parse is signed again without the hint`() = runTest {
        val requests = signJetton("2.8.1", firstStatus = 0xb010)

        assertEquals(2, requests.size)
        assertEquals(JETTON_FLAG_KNOWN, requests[0].jettonFlags())
        assertEquals(0, requests[1].jettonFlags())
    }

    @Test
    fun `known jetton hint is dropped below the jettons minimum ton app version`() = runTest {
        val requests = signJetton("2.2.0", firstStatus = 0x6985)

        assertEquals(1, requests.size)
        assertEquals(0, requests[0].jettonFlags())
    }

    @Test
    fun `request whose deadline passed is refused before anything reaches the device`() = runTest {
        var exchanges = 0
        val transport = FakeLedgerTransport("TON", "2.8.1") { _ ->
            exchanges++
            ApduResponse(ByteArray(0), 0x9000)
        }
        val failure = try {
            TonLedgerSigner(clock = { TIMEOUT * 1000L }).signTransaction(transport.scope(), accountIndex = 0, transaction = transaction(usdt), step = 1, totalSteps = 1)
            null
        } catch (e: LedgerFailureException) {
            e.failure
        }
        assertEquals(LedgerFailure.RequestExpired, failure)
        assertEquals(0, exchanges)
    }

    private suspend fun signJetton(appVersion: String, firstStatus: Int): List<ByteArray> {
        val requests = mutableListOf<ByteArray>()
        var current = ByteArrayOutputStream()
        val transport = FakeLedgerTransport("TON", appVersion) { apdu ->
            val ins = apdu[1].toInt() and 0xff
            val p2 = apdu[3].toInt() and 0xff
            when {
                ins == INS_SIGN_TX && p2 == P2_PATH -> {
                    current = ByteArrayOutputStream()
                    ApduResponse(ByteArray(0), 0x9000)
                }
                ins == INS_SIGN_TX && p2 == P2_MORE -> {
                    current.write(apdu, 5, apdu.size - 5)
                    ApduResponse(ByteArray(0), 0x9000)
                }
                ins == INS_SIGN_TX -> {
                    current.write(apdu, 5, apdu.size - 5)
                    requests += current.toByteArray()
                    ApduResponse(ByteArray(0), if (requests.size == 1) { firstStatus } else { 0x6985 })
                }
                else -> ApduResponse(ByteArray(0), 0x6d00)
            }
        }
        val failure = try {
            signer.signTransaction(transport.scope(), accountIndex = 0, transaction = transaction(usdt), step = 1, totalSteps = 1)
            null
        } catch (e: LedgerFailureException) {
            e.failure
        }
        assertEquals(LedgerFailure.UserRefused(0x6985), failure)
        return requests
    }

    private fun ByteArray.jettonFlags(): Int {
        val hints = afterHash(usdtCell().hash().toByteArray())
        assertArrayEquals(byteArrayOf(1, 0, 0, 0, 1), hints.copyOfRange(0, 5))
        return hints[7].toInt() and 0xff
    }

    private fun usdtCell(): Cell = CellBuilder.beginCell()
        .storeUInt(0x0f8a7ea5, 32)
        .storeUInt(0, 64)
        .storeTlb(Coins, usdt.coins)
        .storeTlb(MsgAddressInt, usdt.receiverAddress)
        .storeTlb(MsgAddressInt, usdt.excessesAddress)
        .storeBit(false)
        .storeTlb(Coins, usdt.forwardAmount)
        .storeBit(false)
        .endCell()

    private suspend fun signAndCapture(appVersion: String): ByteArray {
        val request = ByteArrayOutputStream()
        val transport = FakeLedgerTransport("TON", appVersion) { apdu ->
            val ins = apdu[1].toInt() and 0xff
            val p2 = apdu[3].toInt() and 0xff
            when {
                ins == INS_SIGN_TX && p2 == P2_PATH -> ApduResponse(ByteArray(0), 0x9000)
                ins == INS_SIGN_TX && p2 == P2_MORE -> {
                    request.write(apdu, 5, apdu.size - 5)
                    ApduResponse(ByteArray(0), 0x9000)
                }
                ins == INS_SIGN_TX -> {
                    request.write(apdu, 5, apdu.size - 5)
                    ApduResponse(ByteArray(0), 0x6985)
                }
                else -> ApduResponse(ByteArray(0), 0x6d00)
            }
        }
        val failure = try {
            signer.signTransaction(transport.scope(), accountIndex = 0, transaction = transaction(whales), step = 1, totalSteps = 1)
            null
        } catch (e: LedgerFailureException) {
            e.failure
        }
        assertEquals(LedgerFailure.UserRefused(0x6985), failure)
        return request.toByteArray()
    }

    private fun ByteArray.afterPayloadHash(): ByteArray = afterHash(whales.toCell().hash().toByteArray())

    private fun ByteArray.afterHash(hash: ByteArray): ByteArray {
        val start = (0..size - hash.size).first { offset -> hash.indices.all { this[offset + it] == hash[it] } }
        return copyOfRange(start + hash.size, size)
    }

    private fun transaction(payload: TonPayloadFormat) = Transaction(
        destination = AddrStd.parse("EQDKbjIcfM6ezt8KjKJJLshZJJSqX7XOA4ff-W72r5gqPrHF"),
        sendMode = 3,
        seqno = 1,
        timeout = TIMEOUT.toInt(),
        bounceable = true,
        coins = Coins.ofNano(1_000_000_000),
        payload = payload,
    )

    private companion object {
        const val TIMEOUT = 1_700_000_000L
        const val INS_SIGN_TX = 0x06
        const val P2_PATH = 0x03
        const val P2_MORE = 0x02
        const val JETTON_FLAG_KNOWN = 0x02
    }
}
