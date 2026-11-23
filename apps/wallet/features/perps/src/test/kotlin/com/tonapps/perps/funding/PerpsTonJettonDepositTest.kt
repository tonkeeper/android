package com.tonapps.perps.funding

import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.blockchain.ton.extensions.toAccountId
import com.tonapps.blockchain.ton.extensions.toBoc
import com.tonapps.bus.generated.Events.Perps.PerpsSourceScreen
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.DEPOSIT_EXECUTION_ID
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.WALLET_ID
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.MsgAddressInt
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.tlb.storeTlb
import java.math.BigInteger
import java.util.Base64

class PerpsTonJettonDepositTest : BaseUnitTest() {

    private lateinit var env: PerpsFundingTestEnv

    @Before
    fun setUp() {
        env = PerpsFundingTestEnv()
        env.accounts[TestAssets.ton.id] = testAccount(TestAssets.ton, balance = "2000000000", address = SENDER)
        env.accounts[TestAssets.usdtTon.id] = testAccount(TestAssets.usdtTon, balance = "20000000", address = SENDER)
    }

    @Test
    fun theWalletKnowsItsOwnUsdtWalletWithoutAskingTheChain() {
        val wallet = PerpsTonJettonDepositReader.usdtWallet(AddrStd(SENDER))

        assertEquals(SENDER_USDT_WALLET, wallet.toAccountId())
    }

    @Test
    fun usdtOnTonOpensAsTheOneTransferIntoOmnistonsEscrow() = runTest {
        quoteUsdtDeposits { listOf(transferPayload()) }

        val request = env.depositService.confirmRequest(WALLET_ID, usdtSource(), ESCROW_AMOUNT, PerpsSourceScreen.PerpsHome)

        val deposit = request.type as ConfirmType.PerpetualDeposit
        assertEquals(ESCROW_AMOUNT.toString(), deposit.amount.toString())
        assertEquals("Omniston", deposit.verifiedPayment?.provider)
        assertEquals(ESCROW_AMOUNT.toString(), deposit.verifiedPayment?.amount.toString())
        assertEquals(PerpsFundingAssetId.USDT_TON, env.quoteBodies.single().getValue("source_asset").toString().trim('"'))
    }

    @Test
    fun usdtOnTonIsReportedPendingAsAnOmnistonSale() = runTest {
        quoteUsdtDeposits { listOf(transferPayload()) }
        val request = env.depositService.confirmRequest(WALLET_ID, usdtSource(), ESCROW_AMOUNT, PerpsSourceScreen.PerpsHome)
        val payload = (request.type as ConfirmType.PerpetualDeposit).operation.payloads.single()
        val tracker = PerpsDepositTracker(env.journal, env.recovery, env.depositService, env.provider)

        val swap = checkNotNull(tracker.pendingSwap(request, payload))

        assertEquals("omniston", swap.service)
        assertEquals(DEPOSIT_EXECUTION_ID, swap.serviceTxId)
        assertEquals(ESCROW_AMOUNT.toString(), swap.sellBaseAmount.toString())
    }

    @Test
    fun theSendModeMayOnlyPayTheFeesApartAndIgnoreErrors() = runTest {
        listOf(null, 0, 1, 2, 3).forEach { mode ->
            quoteUsdtDeposits { listOf(transferPayload(mode = mode)) }

            val request = env.depositService.confirmRequest(WALLET_ID, usdtSource(), ESCROW_AMOUNT, PerpsSourceScreen.PerpsHome)

            assertEquals("mode $mode", "Omniston", (request.type as ConfirmType.PerpetualDeposit).verifiedPayment?.provider)
        }
    }

    @Test
    fun anyOtherPaymentFromUsdtOnTonIsNoQuote() = runTest {
        val refused: List<Pair<String, () -> List<JsonObject>>> = listOf(
            "another jetton wallet" to { listOf(transferPayload(to = TestWallet.TON_ADDRESS)) },
            "the wallet's jetton wallet on the masterchain" to { listOf(transferPayload(to = "-1:" + SENDER_USDT_WALLET.substringAfter(':'))) },
            "more than one GRAM for the fees" to { listOf(transferPayload(value = BigInteger.valueOf(1_000_000_001L))) },
            "no coin for the fees" to { listOf(transferPayload(value = BigInteger.ZERO)) },
            "less coin than the transfer forwards" to { listOf(transferPayload(value = ESCROW_FORWARD - BigInteger.ONE)) },
            "a second message" to { listOf(transferPayload(), transferPayload(id = "more")) },
            "mode 128" to { listOf(transferPayload(mode = 128)) },
            "mode 64" to { listOf(transferPayload(mode = 64)) },
            "state init" to { listOf(transferPayload(meta = buildJsonObject { put("state_init", escrowTransfer()) })) },
            "a token transfer" to { listOf(transferPayload(meta = buildJsonObject { put("token", PerpsFundingAssetId.USDT_TON_MASTER) })) },
            "a comment" to { listOf(transferPayload(meta = buildJsonObject { put("comment", "deposit") })) },
            "no body" to { listOf(transferPayload(body = null)) },
            "a body that is no BOC" to { listOf(transferPayload(body = "te6cc")) },
            "a text comment" to { listOf(transferPayload(body = env.tonComment("deposit"))) },
            "a payment to Relay" to { listOf(env.tonPayload("pay", value = ESCROW_AMOUNT)) },
            "fewer jettons" to { listOf(transferPayload(body = transfer(amount = ESCROW_AMOUNT - BigInteger.ONE))) },
            "more jettons" to { listOf(transferPayload(body = transfer(amount = ESCROW_AMOUNT + BigInteger.ONE))) },
            "jettons to the sender" to { listOf(transferPayload(body = transfer(escrow = AddrStd(SENDER)))) },
            "jettons to another contract" to { listOf(transferPayload(body = transfer(escrow = AddrStd(TestWallet.TON_ADDRESS)))) },
            "the excess to another account" to { listOf(transferPayload(body = transfer(response = AddrStd(TestWallet.TON_ADDRESS)))) },
            "a custom payload" to { listOf(transferPayload(body = transfer(customPayload = true))) },
            "nothing forwarded" to { listOf(transferPayload(body = transfer(forward = BigInteger.ZERO))) },
            "the order inline" to { listOf(transferPayload(body = transfer(orderInline = true))) },
            "another order" to { listOf(transferPayload(body = transfer(orderOp = 0x12345678L))) },
            "a refund to another account" to { listOf(transferPayload(body = transfer(refund = AddrStd(TestWallet.TON_ADDRESS)))) },
            "another op" to { listOf(transferPayload(body = transfer(op = 0x178d4519L))) },
        )

        val opened = refused.mapNotNull { (name, payloads) ->
            quoteUsdtDeposits { payloads() }

            val failure = try {
                env.depositService.confirmRequest(WALLET_ID, usdtSource(), ESCROW_AMOUNT, PerpsSourceScreen.PerpsHome)
                null
            } catch (e: PerpsDepositException.QuoteRejected) {
                e
            }

            name.takeIf { failure?.toDepositQuoteError() != PerpsDepositQuoteError.QuoteFailed }
        }

        assertEquals(emptyList<String>(), opened)
        assertTrue(env.journal.list(WALLET_ID).isEmpty())
    }

    @Test
    fun theTransfersBuiltHereDifferFromTheMainnetOneOnlyByWhatTheyChange() = runTest {
        quoteUsdtDeposits { listOf(transferPayload(body = transfer())) }

        val request = env.depositService.confirmRequest(WALLET_ID, usdtSource(), ESCROW_AMOUNT, PerpsSourceScreen.PerpsHome)

        assertEquals("Omniston", (request.type as ConfirmType.PerpetualDeposit).verifiedPayment?.provider)
    }

    @Test
    fun onlyOmnistonPaysOutUsdtOnTon() = runTest {
        listOf("relay", "funxyz", "direct").forEach { provider ->
            env.quoteDeposits(provider = provider, credited = CREDITED, executionId = DEPOSIT_EXECUTION_ID) { listOf(transferPayload()) }

            val failure = try {
                env.depositService.confirmRequest(WALLET_ID, usdtSource(), ESCROW_AMOUNT, PerpsSourceScreen.PerpsHome)
                null
            } catch (e: PerpsDepositException.QuoteRejected) {
                e
            }

            assertEquals(provider, PerpsDepositQuoteError.QuoteFailed, failure?.toDepositQuoteError())
        }
    }

    @Test
    fun gramIsStillPaidOnlyToRelay() = runTest {
        env.accounts[TestAssets.ton.id] = testAccount(TestAssets.ton, balance = "10000000000")
        quoteUsdtDeposits { listOf(env.tonPayload("pay", value = GRAM_AMOUNT)) }

        val failure = try {
            env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.ton.id), GRAM_AMOUNT, PerpsSourceScreen.PerpsHome)
            null
        } catch (e: PerpsDepositException.QuoteRejected) {
            e
        }

        assertEquals(PerpsDepositQuoteError.QuoteFailed, failure?.toDepositQuoteError())
    }

    private fun quoteUsdtDeposits(payloads: (asked: JsonObject) -> List<JsonObject>) {
        env.quoteDeposits(provider = "omniston", credited = CREDITED, executionId = DEPOSIT_EXECUTION_ID, payloads = payloads)
    }

    private fun usdtSource(): PerpsDepositSource = env.source(TestAssets.usdtTon.id)

    private fun transferPayload(
        id: String = "pay",
        to: String = SENDER_USDT_WALLET,
        value: BigInteger = ESCROW_ATTACHED,
        body: String? = escrowTransfer(),
        mode: Int? = 3,
        meta: JsonObject = JsonObject(emptyMap()),
    ): JsonObject = env.tonPayload(id, to = to, value = value, body = body, mode = mode, meta = meta)

    private fun escrowTransfer(): String {
        val resource = checkNotNull(javaClass.getResource(ESCROW_TRANSFER)) { "no $ESCROW_TRANSFER" }
        return resource.readText().trim()
    }

    private fun transfer(
        op: Long = JETTON_TRANSFER_OP,
        amount: BigInteger = ESCROW_AMOUNT,
        escrow: AddrStd = AddrStd(OMNISTON_ESCROW),
        response: AddrStd = AddrStd(SENDER),
        customPayload: Boolean = false,
        forward: BigInteger = ESCROW_FORWARD,
        orderInline: Boolean = false,
        orderOp: Long = ESCROW_ORDER_OP,
        refund: AddrStd = AddrStd(SENDER),
    ): String {
        val order = CellBuilder.createCell {
            storeUInt(orderOp, OP_BITS)
            storeBytes(ByteArray(QUOTE_ID_BYTES) { 7 })
            storeBit(false)
            storeTlb(MsgAddressInt, refund)
        }
        val cell = CellBuilder.createCell {
            storeUInt(op, OP_BITS)
            storeUInt(QUERY_ID, QUERY_ID_BITS)
            storeTlb(Coins, Coins.ofNano(amount.toLong()))
            storeTlb(MsgAddressInt, escrow)
            storeTlb(MsgAddressInt, response)
            if (customPayload) {
                storeBit(true)
                storeRef(Cell.empty())
            } else {
                storeBit(false)
            }
            storeTlb(Coins, Coins.ofNano(forward.toLong()))
            if (orderInline) {
                storeBit(false)
                storeUInt(orderOp, OP_BITS)
            } else {
                storeBit(true)
                storeRef(order)
            }
        }
        return Base64.getEncoder().encodeToString(cell.toBoc())
    }

    private companion object {
        const val ESCROW_TRANSFER = "omniston_escrow_transfer_usdt_ton_2026_11_12.txt"
        const val SENDER = "EQDamtiO59XPhaArP9StLPhkFPVCDcWm2Vi6nmkhE_Ik_56b"
        const val SENDER_USDT_WALLET = "0:a810268436d216ddfdb86603764702a7cc24b8fd5f7bed6a7c424cfaf8edbb8a"
        const val OMNISTON_ESCROW = "0:f26cef3c97696bac09802d152aa6680413fb6ec64e75d05e18778c636d06710b"
        const val JETTON_TRANSFER_OP = 0x0f8a7ea5L
        const val ESCROW_ORDER_OP = 0xc0a5f0d9L
        const val OP_BITS = 32
        const val QUERY_ID = 42L
        const val QUERY_ID_BITS = 64
        const val QUOTE_ID_BYTES = 32
        val ESCROW_AMOUNT: BigInteger = BigInteger.valueOf(9_070_000L)
        val ESCROW_ATTACHED: BigInteger = BigInteger.valueOf(400_000_000L)
        val ESCROW_FORWARD: BigInteger = BigInteger.valueOf(300_000_000L)
        val GRAM_AMOUNT: BigInteger = BigInteger.valueOf(3_000_000_000L)
        val CREDITED: BigInteger = BigInteger.valueOf(8_900_000L)
    }
}
