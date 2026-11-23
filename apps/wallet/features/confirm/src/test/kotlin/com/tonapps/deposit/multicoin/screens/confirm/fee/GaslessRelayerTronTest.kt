package com.tonapps.deposit.multicoin.screens.confirm.fee

import kotlinx.serialization.json.jsonPrimitive
import com.tonapps.chainkit.core.chain.model.account.Address
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * The gasless 2.0 wire format on TRON: a call asked as `prebuild` {data}, the transaction that makes
 * it in a string, and the relayer's answer signed only where it is that very transaction.
 */
class GaslessRelayerTronTest : BaseUnitTest() {

    private val gaslessApi = mockk<GaslessApi>()
    private val api = mockk<API>()
    private val gasless = GaslessRelayer(api)
    private val auth = Authorization(deviceToken = "device", walletId = TestWallet.ID)

    private val usdt by lazy { testAccount(TestAssets.usdtTron, balance = "5000000", address = SENDER) }

    private val request = slot<GaslessV2EstimateRequest>()

    @Before
    fun setUp() {
        every { api.batteryGasless(any()) } returns gaslessApi
        every { gaslessApi.gaslessEstimate(capture(request), any(), any()) } returns decode<GaslessV2EstimateResponse>(ESTIMATE)
    }

    @Test
    fun theRelayerNamesTronItsOwnWay() {
        assertEquals("tron/mainnet", GaslessRelayer.chainId(Chain.Tron.Mainnet))
    }

    @Test
    fun aTransactionIsAskedAsPrebuildInAString() {
        val built = transaction(RAW_DATA)

        gasless.estimate(auth, call(built))

        assertEquals("tron/mainnet", request.captured.chainId)
        assertEquals(SENDER, request.captured.senderAddress)
        assertEquals("prebuild", request.captured.payload.type)
        val meta = Json.parseToJsonElement(Serializer.JSON.encodeToString(request.captured.payload)).jsonObject.getValue("meta")
        assertEquals(buildJsonObject { put("data", built) }, meta)
    }

    @Test
    fun aCallOffTronIsNotOne() {
        assertNull(GaslessRelayer.tronCall(testAccount(TestAssets.eth), transaction(RAW_DATA)))
    }

    @Test
    fun theTransactionAskedForIsSigned() {
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns emulation(answered(RAW_DATA))

        val emulation = gasless.emulate(auth, listOf(call(transaction(RAW_DATA))), Asset.Battery.ID)

        val payload = emulation.operation.payloads.single()
        assertEquals(Payload.Type.Prebuild, payload.type)
        assertEquals(answered(RAW_DATA), (payload.meta as PayloadMeta.Tron.Prebuild).data)
    }

    @Test
    fun theRawDataIsComparedWhateverItsCase() {
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns emulation(answered(RAW_DATA.uppercase()))

        gasless.emulate(auth, listOf(call(transaction(RAW_DATA))), Asset.Battery.ID)
    }

    @Test
    fun anotherTransactionIsRefused() {
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns emulation(answered(OTHER_RAW_DATA))

        assertThrows(IllegalStateException::class.java) {
            gasless.emulate(auth, listOf(call(transaction(RAW_DATA))), Asset.Battery.ID)
        }
    }

    @Test
    fun anAnswerWithNoRawDataIsRefused() {
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns emulation("""{"txID":"$TX_ID"}""")

        assertThrows(IllegalStateException::class.java) {
            gasless.emulate(auth, listOf(call(transaction(RAW_DATA))), Asset.Battery.ID)
        }
    }

    /** A TRON `main` goes as the swap states it: `main`, with its address, value, calldata and memo. */
    @Test
    fun aMainIsAskedAsItIs() {
        gasless.estimate(auth, GaslessRelayer.tronMain(usdt, main())!!)

        assertEquals("main", request.captured.payload.type)
        val meta = Json.parseToJsonElement(Serializer.JSON.encodeToString(request.captured.payload)).jsonObject.getValue("meta").jsonObject
        assertEquals(USDT_CONTRACT, meta.getValue("to").jsonPrimitive.content)
        assertEquals("0", meta.getValue("value").jsonPrimitive.content)
        assertEquals(TRANSFER, meta.getValue("data").jsonPrimitive.content)
        assertEquals(MEMO, meta.getValue("memo").jsonPrimitive.content)
    }

    /** What the relayer builds of a `main` is signed only where it is that `main`'s transaction. */
    @Test
    fun aTransactionThatIsNotTheMainsIsRefused() {
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns emulation(answered(RAW_DATA))

        assertThrows(IllegalStateException::class.java) {
            gasless.emulate(auth, listOf(GaslessRelayer.tronMain(usdt, main())!!), Asset.Battery.ID)
        }
    }

    private fun main() = PayloadMeta.Tron.Main(
        to = Address.force(USDT_CONTRACT, Chain.Tron.Mainnet),
        value = BigInteger.ZERO,
        data = TRANSFER,
        memo = MEMO,
    )

    private fun call(transaction: String) = GaslessRelayer.tronCall(usdt, transaction)!!

    // The document as the wallet builds it: hex addresses, the raw data last but one.
    private fun transaction(rawData: String) = buildJsonObject {
        put("raw_data_hex", rawData)
        put("txID", TX_ID)
        put("visible", false)
    }.toString()

    // The same transaction as the relayer writes it back: its own key order.
    private fun answered(rawData: String) = buildJsonObject {
        put("visible", true)
        put("txID", TX_ID)
        put("raw_data_hex", rawData)
    }.toString()

    // Recorded from production for a 0.5 USDT transfer, with the transaction swapped for [document].
    private fun emulation(document: String) = decode<GaslessV2EmulateResponse>(
        """
        {
          "chain_id": "tron/mainnet",
          "sender_address": "$SENDER",
          "created_at": "2026-11-16T12:06:26Z",
          "expires_at": "2026-11-16T12:07:24Z",
          "asset_id": "battery/mainnet",
          "fee": { "type": "value", "asset_id": "battery/mainnet", "data": { "amount": "532" } },
          "operation": {
            "id": "44385269-d635-4049-aedb-a2737761264b",
            "type": "swap",
            "payloads": [
              {
                "id": "5a693eba-1cd9-4703-87b6-847498c9c0a0",
                "type": "prebuild",
                "chain_id": "tron/mainnet",
                "version": 1,
                "fee": { "type": "value", "asset_id": "battery/mainnet", "data": { "amount": "532" } },
                "meta": {
                  "tx_id": "$TX_ID",
                  "energy": 134194,
                  "bandwidth": 0,
                  "data": ${Json.encodeToString(JsonPrimitive(document))}
                }
              }
            ]
          }
        }
        """
    )

    private inline fun <reified T> decode(json: String): T = Serializer.JSON.decodeFromString(json)

    private companion object {
        const val SENDER = "TT2T17KZhoDu47i2E4FWxfG79zdkEWkU9N"
        const val TX_ID = "39c3edffd4ea4fea4b077ab35a32f9af51bd81792ccfc074babdaf7b87d5fbe6"
        const val RAW_DATA = "0a02921c2208024ade27fe0c7007"
        const val OTHER_RAW_DATA = "0a02921c2208024ade27fe0c7008"
        const val USDT_CONTRACT = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
        const val TRANSFER = "a9059cbb000000000000000000000000a614f803b6fd780986a42c78ec9c7f77e6ded13c00000000000000000000000000000000000000000000000000000000000f4240"
        const val MEMO = "71756f74652d3031"

        const val ESTIMATE = """
            {
              "chain_id": "tron/mainnet",
              "sender_address": "$SENDER",
              "created_at": "2026-11-16T12:06:26Z",
              "expires_at": "2026-11-16T12:07:24Z",
              "estimations": [
                {
                  "asset": { "id": "battery/mainnet", "name": "Battery", "symbol": "BATTERY", "decimals": 0 },
                  "available": true,
                  "fee": { "type": "value", "asset_id": "battery/mainnet", "data": { "amount": "532" } }
                }
              ]
            }
        """
    }
}
