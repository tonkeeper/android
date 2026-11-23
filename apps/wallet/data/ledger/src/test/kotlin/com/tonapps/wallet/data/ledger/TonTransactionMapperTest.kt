package com.tonapps.wallet.data.ledger

import com.tonapps.ledger.ton.KnownJetton
import com.tonapps.ledger.ton.TonPayloadFormat
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.wallet.data.ledger.ton.TonTransactionMapper
import com.trustwallet.core.theopennetwork.JettonTransfer
import com.trustwallet.core.theopennetwork.SigningInput
import com.trustwallet.core.theopennetwork.Transfer
import com.trustwallet.core.theopennetwork.WalletVersion
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ton.block.AddrStd
import java.math.BigInteger

class TonTransactionMapperTest {

    private val destination = "EQDKbjIcfM6ezt8KjKJJLshZJJSqX7XOA4ff-W72r5gqPrHF"
    private val owner = "UQCD39VS5jcptHL8vMjEXrzGaRcCVYto7HUn4bpAOg8xqEBI"

    @Test
    fun `ton transfer with ascii comment maps to a comment hint`() {
        val transaction = TonTransactionMapper.map(input(Transfer(dest = destination, amount = amount(1_500_000_000), mode = 3, comment = "hello", bounceable = true)))
        assertEquals(AddrStd.parse(destination), transaction.destination)
        assertEquals(3, transaction.sendMode)
        assertEquals(42, transaction.seqno)
        assertEquals(1_700_000_000, transaction.timeout)
        assertTrue(transaction.bounceable)
        assertEquals(TonPayloadFormat.Comment("hello"), transaction.payload)
    }

    @Test
    fun `jetton transfer keeps amounts addresses and comment`() {
        val transaction = TonTransactionMapper.map(
            input(
                Transfer(
                    dest = destination,
                    amount = amount(80_000_000),
                    mode = 3,
                    comment = "memo",
                    bounceable = false,
                    jetton_transfer = JettonTransfer(
                        query_id = 0,
                        jetton_amount = BigInteger("123456789000000000000").toByteArray().toByteString(),
                        to_owner = owner,
                        response_address = owner,
                        forward_amount = amount(1),
                    ),
                ),
            ),
        )
        val payload = transaction.payload as TonPayloadFormat.JettonTransfer
        assertEquals(null, payload.queryId)
        assertEquals(AddrStd.parse(owner), payload.receiverAddress)
        assertEquals(AddrStd.parse(owner), payload.excessesAddress)
        assertEquals(BigInteger("123456789000000000000"), BigInteger(payload.coins.amount.value.toByteArray()))
        assertTrue(payload.forwardPayload != null)
    }

    @Test
    fun `known jetton hint uses the wallet workchain rather than the response address`() {
        val transaction = TonTransactionMapper.map(
            input(
                Transfer(
                    dest = destination,
                    amount = amount(80_000_000),
                    mode = 3,
                    bounceable = true,
                    jetton_transfer = JettonTransfer(
                        query_id = 0,
                        jetton_amount = amount(1_000_000),
                        to_owner = owner,
                        response_address = MASTERCHAIN_RESPONSE,
                        forward_amount = amount(1),
                    ),
                ),
            ),
            jettonMaster = USDT_MASTER,
        )
        val payload = transaction.payload as TonPayloadFormat.JettonTransfer
        assertEquals(KnownJetton(jettonId = 0, workchain = 0), payload.knownJetton)
    }

    @Test
    fun `non ascii comment falls back to an unsafe payload`() {
        val transaction = TonTransactionMapper.map(input(Transfer(dest = destination, amount = amount(1), mode = 3, comment = "привет", bounceable = true)))
        assertTrue(transaction.payload is TonPayloadFormat.Unsafe)
    }

    @Test(expected = LedgerFailureException::class)
    fun `several messages are rejected`() {
        val transfer = Transfer(dest = destination, amount = amount(1), mode = 3, bounceable = true)
        TonTransactionMapper.map(input(transfer).copy(messages = listOf(transfer, transfer)))
    }

    private fun input(transfer: Transfer) = SigningInput(
        messages = listOf(transfer),
        sequence_number = 42,
        expire_at = 1_700_000_000,
        wallet_version = WalletVersion.WALLET_V4_R2,
    )

    private fun amount(value: Long) = BigInteger.valueOf(value).toByteArray().toByteString()

    private companion object {
        const val USDT_MASTER = "0:b113a994b5024a16719f69139328eb759596c38a25f59028b146fecdc3621dfe"
        const val MASTERCHAIN_RESPONSE = "-1:3333333333333333333333333333333333333333333333333333333333333333"
    }
}
