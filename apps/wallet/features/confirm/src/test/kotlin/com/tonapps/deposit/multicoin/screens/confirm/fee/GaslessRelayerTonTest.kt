package com.tonapps.deposit.multicoin.screens.confirm.fee

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.op.Intent
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.Authorization
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import io.Serializer
import io.batteryapi.apis.GaslessApi
import io.batteryapi.models.GaslessV2EmulateResponse
import io.batteryapi.models.GaslessV2EstimateRequest
import io.batteryapi.models.GaslessV2EstimateResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ton.cell.buildCell
import org.ton.tlb.storeTlb
import org.ton.block.Coins

/**
 * The gasless 2.0 wire format on TON: a swap's message asked as `main` as the operation states it, and
 * the relayer's answer signed only where it is that message, delivered by the relayer.
 */
class GaslessRelayerTonTest : BaseUnitTest() {

    private val gaslessApi = mockk<GaslessApi>()
    private val api = mockk<API>()
    private val gasless = GaslessRelayer(api)
    private val auth = Authorization(deviceToken = "device", walletId = TestWallet.ID)

    private val jetton by lazy { testAccount(TestAssets.usdtTon, balance = "50000000", address = TestWallet.TON_ADDRESS) }

    private val request = slot<GaslessV2EstimateRequest>()

    @Before
    fun setUp() {
        every { api.batteryGasless(any()) } returns gaslessApi
        every { gaslessApi.gaslessEstimate(capture(request), any(), any()) } returns decode<GaslessV2EstimateResponse>(ESTIMATE)
    }

    @Test
    fun theRelayerNamesTonItsOwnWay() {
        assertEquals("ton/mainnet", GaslessRelayer.chainId(Chain.Ton.Mainnet))
    }

    @Test
    fun aMessageIsAskedAsMainAsTheOperationStatesIt() {
        gasless.estimate(auth, call())

        assertEquals("ton/mainnet", request.captured.chainId)
        assertEquals(TestWallet.TON_ADDRESS, request.captured.senderAddress)
        assertEquals("main", request.captured.payload.type)
        val meta = Json.parseToJsonElement(Serializer.JSON.encodeToString(request.captured.payload)).jsonObject.getValue("meta")
        assertEquals(Json.parseToJsonElement("""{"to": "$ROUTER", "value": "260000000", "body": "$BODY"}"""), meta)
    }

    @Test
    fun aSwapIsHeldToWhatItSells() {
        val intent = checkNotNull(call().intent)

        assertEquals(Intent.Spend(jetton.asset.value, SOLD), intent.spends?.single())
        assertNull(intent.to)
    }

    @Test
    fun aSwapOffTonIsNotOne() {
        assertNull(GaslessRelayer.tonSwap(testAccount(TestAssets.eth), message(), SOLD))
    }

    /** The relayer says it delivers the message itself as `signature_type`: the answer is signed as it stands. */
    @Test
    fun theMessageTheRelayerDeliversIsSigned() {
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns emulation(ANSWERED)

        val emulation = gasless.emulate(auth, listOf(call()), Asset.Battery.ID)

        val payload = emulation.operation.payloads.single()
        assertEquals(Payload.Type.Main, payload.type)
        val answered = payload.meta as PayloadMeta.Ton.Main
        assertEquals(BigInteger.fromInt(260_000_000), answered.value)
        assertEquals(3, answered.mode)
        assertEquals(1794838489L, answered.expireAt)
        assertEquals(BigInteger.fromInt(102), emulation.fee)
    }

    /** The Generic Payload names the same thing `format`: an answer in its words is taken as well. */
    @Test
    fun anAnswerThatSaysFormatIsTakenAsWell() {
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns
            emulation(ANSWERED.replace(""""signature_type": "internal"""", """"format": "int_msg""""))

        gasless.emulate(auth, listOf(call()), Asset.Battery.ID)
    }

    @Test
    fun anAnswerSignedForTheWalletToSendIsRefused() {
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns
            emulation(ANSWERED.replace(""""signature_type": "internal"""", """"format": "ext_msg""""))

        assertThrows(IllegalStateException::class.java) {
            gasless.emulate(auth, listOf(call()), Asset.Battery.ID)
        }
    }

    /** A `main` the wallet would send itself is a transaction it pays for: not what a relayer was asked for. */
    @Test
    fun anAnswerTheWalletWouldSendItselfIsRefused() {
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns
            emulation(ANSWERED.replace(""""signature_type": "internal",""", ""))

        assertThrows(IllegalStateException::class.java) {
            gasless.emulate(auth, listOf(call()), Asset.Battery.ID)
        }
    }

    /** The body is what moves the jetton: an answer that carries another one is not signed. */
    @Test
    fun anAnswerWithAnotherBodyIsRefused() {
        val other = buildCell { storeUInt(OTHER_OP, 32) }.base64()
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns emulation(ANSWERED.replace(BODY, other))

        assertThrows(IllegalStateException::class.java) {
            gasless.emulate(auth, listOf(call()), Asset.Battery.ID)
        }
    }

    /** The same body in another serialization, without its checksum, is the same body. */
    @Test
    fun theBodyIsComparedAsACell() {
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns emulation(ANSWERED.replace(BODY, BODY_NO_CRC))

        gasless.emulate(auth, listOf(call()), Asset.Battery.ID)
    }

    @Test
    fun anAnswerThatBouncesOtherwiseIsRefused() {
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns emulation(ANSWERED.replace(BOUNCES, NO_BOUNCE))

        assertThrows(IllegalStateException::class.java) {
            gasless.emulate(auth, listOf(GaslessRelayer.tonSwap(jetton, message().copy(bounce = true), SOLD)!!), Asset.Battery.ID)
        }
    }

    /** A message that states no bounce is sent as its address says: the router's `EQ` address bounces. */
    @Test
    fun anAnswerThatBouncesOtherwiseThanTheAddressSaysIsRefused() {
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns emulation(ANSWERED.replace(BOUNCES, NO_BOUNCE))

        assertThrows(IllegalStateException::class.java) {
            gasless.emulate(auth, listOf(call()), Asset.Battery.ID)
        }
    }

    @Test
    fun aMessageIsSentAsItsAddressSays() {
        assertEquals(true, GaslessRelayer.deliveredBounce(message()))
        assertEquals(false, GaslessRelayer.deliveredBounce(message().copy(to = Address.force(ROUTER_NON_BOUNCEABLE, Chain.Ton.Mainnet))))
        assertEquals(true, GaslessRelayer.deliveredBounce(message().copy(to = Address.force(ROUTER_RAW, Chain.Ton.Mainnet))))
        assertEquals(false, GaslessRelayer.deliveredBounce(message().copy(bounce = false)))
    }

    /** A message that deploys is answered with its state init: one without it is another message. */
    @Test
    fun anAnswerWithoutTheStateInitAskedForIsRefused() {
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns emulation(ANSWERED)

        assertThrows(IllegalStateException::class.java) {
            gasless.emulate(auth, listOf(GaslessRelayer.tonSwap(jetton, message().copy(stateInit = BODY), SOLD)!!), Asset.Battery.ID)
        }
    }

    /** A jetton deposit is asked as it is, but its answer is not signed here: the wallet's transfer is chainkit's to make. */
    @Test
    fun aJettonDepositsAnswerIsRefused() {
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns emulation(ANSWERED)
        val deposit = message().copy(body = null, token = Address.force(ROUTER, Chain.Ton.Mainnet))

        assertThrows(IllegalStateException::class.java) {
            gasless.emulate(auth, listOf(GaslessRelayer.tonSwap(jetton, deposit, SOLD)!!), Asset.Battery.ID)
        }
    }

    /** Each part of a split route is answered by its own message, in the order asked, with the seqno it goes at. */
    @Test
    fun eachPartIsAnsweredByItsOwnMessage() {
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns emulation(ANSWERED, payloads = 2, consecutive = true)

        val emulation = gasless.emulate(auth, listOf(call(), call()), Asset.Battery.ID)

        assertEquals(2, emulation.operation.payloads.size)
        assertEquals(listOf(277L, 278L), emulation.operation.payloads.map { emulation.tonSeqnos.getValue(it.id) })
    }

    /** Two parts at one seqno: the wallet would run one of them, and the route would be half sold. */
    @Test
    fun partsAtOneSeqnoAreRefused() {
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns emulation(ANSWERED, payloads = 2)

        assertThrows(IllegalStateException::class.java) {
            gasless.emulate(auth, listOf(call(), call()), Asset.Battery.ID)
        }
    }

    /** A seqno below zero is no wallet's: the wallet would refuse the request after the swipe. */
    @Test
    fun aNegativeSeqnoIsRefused() {
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns emulation(ANSWERED.replace(SEQNO_277, "\"seqno\": -1,"))

        assertThrows(IllegalStateException::class.java) {
            gasless.emulate(auth, listOf(call()), Asset.Battery.ID)
        }
    }

    /** A seqno past what a wallet counts to is refused, however the parts' numbers wrap around. */
    @Test
    fun aSeqnoPastTheWalletsRangeIsRefused() {
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns emulation(ANSWERED.replace(SEQNO_277, "\"seqno\": ${Long.MAX_VALUE},"))

        assertThrows(IllegalStateException::class.java) {
            gasless.emulate(auth, listOf(call()), Asset.Battery.ID)
        }
    }

    @Test
    fun partsWithSomeSeqnosStatedAreRefused() {
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns emulation(ANSWERED, payloads = 2, unstatedAfterFirst = true)

        assertThrows(IllegalStateException::class.java) {
            gasless.emulate(auth, listOf(call(), call()), Asset.Battery.ID)
        }
    }

    @Test
    fun partsAnsweredOutOfOrderAreRefused() {
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns emulation(ANSWERED, payloads = 2)
        val other = GaslessRelayer.tonSwap(jetton, message().copy(value = BigInteger.fromInt(150_000_000)), SOLD)!!

        assertThrows(IllegalStateException::class.java) {
            gasless.emulate(auth, listOf(other, call()), Asset.Battery.ID)
        }
    }

    /** A fee in a jetton comes back as one batch: the message asked for, then the jetton's transfer of the fee. */
    @Test
    fun aJettonFeeComesBackInOneBatch() {
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns batchEmulation(listOf(ASKED_CALL, feeCall()), USDT_TON)

        val emulation = gasless.emulate(auth, listOf(call()), USDT_TON, feeWallet = FEE_WALLET)

        val batch = emulation.operation.payloads.single().meta as PayloadMeta.Ton.Batch
        assertEquals(2, batch.calls.size)
        // What the user's intent is held to: the message asked for, the fee aside.
        assertEquals(1, (emulation.intended.payloads.single().meta as PayloadMeta.Ton.Batch).calls.size)
        assertEquals(279L, emulation.tonSeqnos.values.single())
    }

    @Test
    fun aFeeOutOfAnotherWalletIsRefused() {
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns batchEmulation(listOf(ASKED_CALL, feeCall()), USDT_TON)

        assertThrows(IllegalStateException::class.java) {
            gasless.emulate(auth, listOf(call()), USDT_TON, feeWallet = ROUTER_RAW)
        }
    }

    @Test
    fun aFeeOfAnotherAmountIsRefused() {
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns batchEmulation(listOf(ASKED_CALL, feeCall(amount = 97_551)), USDT_TON)

        assertThrows(IllegalStateException::class.java) {
            gasless.emulate(auth, listOf(call()), USDT_TON, feeWallet = FEE_WALLET)
        }
    }

    @Test
    fun aFeeInAModeOfItsOwnIsRefused() {
        // 128 would carry the wallet's whole balance with it, 35 destroy the wallet it empties, 7 fail the action phase.
        for (mode in listOf(128, 35, 7)) {
            val fee = feeCall().replace("\"mode\": 3", "\"mode\": $mode")
            every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns batchEmulation(listOf(ASKED_CALL, fee), USDT_TON)

            assertThrows("mode $mode", IllegalStateException::class.java) {
                gasless.emulate(auth, listOf(call()), USDT_TON, feeWallet = FEE_WALLET)
            }
        }
    }

    @Test
    fun aFeeInAModeThatCarriesNothingMoreIsTaken() {
        val separately = feeCall().replace("\"mode\": 3", "\"mode\": 1")
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns batchEmulation(listOf(ASKED_CALL, separately), USDT_TON)

        val emulation = gasless.emulate(auth, listOf(call()), USDT_TON, feeWallet = FEE_WALLET)

        assertEquals(2, (emulation.operation.payloads.single().meta as PayloadMeta.Ton.Batch).calls.size)
    }

    /** A message asked for in mode 128 leaves the fee after it nothing: the option is not offered. */
    @Test
    fun aFeeAfterAMessageThatCarriesAllIsRefused() {
        val wholeBalance = ASKED_CALL.replace("\"mode\": 3", "\"mode\": 128")
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns batchEmulation(listOf(wholeBalance, feeCall()), USDT_TON)
        val call = GaslessRelayer.tonSwap(jetton, message().copy(mode = 128), SOLD)!!

        val failure = assertThrows(IllegalStateException::class.java) {
            gasless.emulate(auth, listOf(call), USDT_TON, feeWallet = FEE_WALLET)
        }

        assertTrue(failure.message.orEmpty().contains("after one that carries all"))
    }

    /** Paid with the battery, a batch carries the messages asked for and nothing besides. */
    @Test
    fun aBatteryBatchWithAMessageBesidesIsRefused() {
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns batchEmulation(listOf(ASKED_CALL, feeCall()), Asset.Battery.ID)

        assertThrows(IllegalStateException::class.java) {
            gasless.emulate(auth, listOf(call()), Asset.Battery.ID)
        }
    }

    @Test
    fun aBatchOfOtherMessagesIsRefused() {
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns
            batchEmulation(listOf(ASKED_CALL.replace("\"260000000\"", "\"260000001\""), feeCall()), USDT_TON)

        assertThrows(IllegalStateException::class.java) {
            gasless.emulate(auth, listOf(call()), USDT_TON, feeWallet = FEE_WALLET)
        }
    }

    // The jetton's transfer of [amount] of the fee out of the account's wallet of it, as the relayer writes it.
    private fun feeCall(amount: Long = FEE): String {
        val body = buildCell {
            storeUInt(0x0f8a7ea5, 32)
            storeUInt(0, 64)
            storeTlb(Coins, Coins.ofNano(amount))
        }.base64()
        return """{"type": "main", "meta": {"to": "$FEE_WALLET", "value": "5000000", "bounce": true, "mode": 3, "body": "$body"}}"""
    }

    // Recorded from production for a jetton sent with its fee in USD₮, the bodies shortened.
    private fun batchEmulation(calls: List<String>, assetId: String) = decode<GaslessV2EmulateResponse>(
        """
        {
          "chain_id": "ton/mainnet",
          "sender_address": "${TestWallet.TON_ADDRESS}",
          "created_at": "2026-11-16T18:52:55Z",
          "expires_at": "2026-11-16T18:57:55Z",
          "asset_id": "$assetId",
          "fee": { "type": "value", "asset_id": "$assetId", "data": { "amount": "$FEE" } },
          "operation": {
            "id": "44385269-d635-4049-aedb-a2737761264b",
            "type": "transfer",
            "payloads": [
              {
                "id": "e1b9f2ab-2ff2-48d6-9523-606b2a936047",
                "type": "batch",
                "chain_id": "ton/mainnet",
                "version": 1,
                "fee": { "type": "value", "asset_id": "$assetId", "data": { "amount": "$FEE" } },
                "meta": {
                  "wallet_version": "v5",
                  "signature_type": "internal",
                  "seqno": 279,
                  "expire_at": 1794862675,
                  "calls": [${calls.joinToString(",")}]
                }
              }
            ]
          }
        }
        """
    )

    /** One message asked for is answered by one: a second would be signed beside it at the same seqno. */
    @Test
    fun aSecondAnswerToOneMessageIsRefused() {
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns emulation(ANSWERED, payloads = 2)

        assertThrows(IllegalStateException::class.java) {
            gasless.emulate(auth, listOf(call()), Asset.Battery.ID)
        }
    }

    @Test
    fun anAnswerToAnotherContractIsRefused() {
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns emulation(ANSWERED.replace(ROUTER_RAW, OTHER_RAW))

        assertThrows(IllegalStateException::class.java) {
            gasless.emulate(auth, listOf(call()), Asset.Battery.ID)
        }
    }

    @Test
    fun anAnswerCarryingOtherCoinsIsRefused() {
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns
            emulation(ANSWERED.replace(""""value": "260000000"""", """"value": "260000001""""))

        assertThrows(IllegalStateException::class.java) {
            gasless.emulate(auth, listOf(call()), Asset.Battery.ID)
        }
    }

    private fun call() = GaslessRelayer.tonSwap(jetton, message(), SOLD)!!

    private fun message() = PayloadMeta.Ton.Main(
        to = Address.force(ROUTER, Chain.Ton.Mainnet),
        value = BigInteger.fromInt(260_000_000),
        body = BODY,
    )

    // Recorded from production for a jetton sold for TON, the body shortened; [meta] is each payload's.
    // [consecutive] states each part's seqno one after the last; [unstatedAfterFirst] states the first only.
    private fun emulation(
        meta: String,
        payloads: Int = 1,
        consecutive: Boolean = false,
        unstatedAfterFirst: Boolean = false,
    ) = decode<GaslessV2EmulateResponse>(
        """
        {
          "chain_id": "ton/mainnet",
          "sender_address": "${TestWallet.TON_ADDRESS}",
          "created_at": "2026-11-16T14:09:49Z",
          "expires_at": "2026-11-16T14:14:49Z",
          "asset_id": "battery/mainnet",
          "fee": { "type": "value", "asset_id": "battery/mainnet", "data": { "amount": "102" } },
          "operation": {
            "id": "44385269-d635-4049-aedb-a2737761264b",
            "type": "swap",
            "payloads": [${(1..payloads).joinToString(",") { payload(it, seqnoOf(meta, it - 1, consecutive, unstatedAfterFirst)) }}]
          }
        }
        """
    )

    private fun seqnoOf(meta: String, index: Int, consecutive: Boolean, unstatedAfterFirst: Boolean): String = when {
        index > 0 && unstatedAfterFirst -> meta.replace(SEQNO_277, "")
        consecutive -> meta.replace(SEQNO_277, "\"seqno\": ${SEQNO + index},")
        else -> meta
    }

    private fun payload(index: Int, meta: String) = """
        {
          "id": "5a693eba-1cd9-4703-87b6-847498c9c0a$index",
          "type": "main",
          "chain_id": "ton/mainnet",
          "version": 1,
          "fee": { "type": "value", "asset_id": "battery/mainnet", "data": { "amount": "102" } },
          "meta": $meta
        }
    """

    private inline fun <reified T> decode(json: String): T = Serializer.JSON.decodeFromString(json)

    private companion object {
        // The router as the route names it and as the relayer answers it: one address, two spellings.
        const val ROUTER = "EQCI-Ihf6dqiu6qlMpduSeufqZdSGJsSLApaxPuTIs9qwtpW"
        const val ROUTER_RAW = "0:88f8885fe9daa2bbaaa532976e49eb9fa99752189b122c0a5ac4fb9322cf6ac2"
        const val ROUTER_NON_BOUNCEABLE = "UQCI-Ihf6dqiu6qlMpduSeufqZdSGJsSLApaxPuTIs9qwoeT"
        const val OTHER_RAW = "0:abababababababababababababababababababababababababababababababab"
        const val BODY = "te6cckEBAQEAAgAAAEysuc0="
        const val USDT_TON = "ton/mainnet/jetton/0:b113a994b5024a16719f69139328eb759596c38a25f59028b146fecdc3621dfe"
        const val FEE_WALLET = "0:2680c7f75115320de5c4b08022355391b0b92770f85707e728a490f844a69ba9"
        const val FEE = 97_550L
        const val ASKED_CALL = """{"type": "main", "meta": {"to": "$ROUTER_RAW", "value": "260000000", "bounce": true, "mode": 3, "body": "$BODY"}}"""
        const val SEQNO = 277
        const val SEQNO_277 = "\"seqno\": 277,"
        const val BODY_NO_CRC = "te6ccgEBAQEAAgAAAA=="
        const val OTHER_OP = 0x0f8a7ea5L
        const val BOUNCES = "\"bounce\": true"
        const val NO_BOUNCE = "\"bounce\": false"
        val SOLD: BigInteger = BigInteger.parseString("50000000000")

        const val ANSWERED = """
            {
              "value": "260000000",
              "bounce": true,
              "wallet_version": "v5",
              "signature_type": "internal",
              "seqno": 277,
              "to": "$ROUTER_RAW",
              "mode": 3,
              "body": "$BODY",
              "expire_at": 1794838489
            }
        """

        const val ESTIMATE = """
            {
              "chain_id": "ton/mainnet",
              "sender_address": "sender",
              "created_at": "2026-11-16T14:09:49Z",
              "expires_at": "2026-11-16T14:14:49Z",
              "estimations": [
                {
                  "asset": { "id": "battery/mainnet", "name": "Battery", "symbol": "BATTERY", "decimals": 0 },
                  "available": true,
                  "fee": { "type": "value", "asset_id": "battery/mainnet", "data": { "amount": "102" } }
                }
              ]
            }
        """
    }
}
