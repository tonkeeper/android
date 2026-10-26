package com.tonapps.deposit.multicoin.screens.confirm.fee

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.SignedOperation
import com.tonapps.chainkit.core.op.SignedPayload
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.Authorization
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import io.Serializer
import io.batteryapi.apis.GaslessApi
import io.batteryapi.models.GaslessV2EmulateRequest
import io.batteryapi.models.GaslessV2EmulateResponse
import io.batteryapi.models.GaslessV2EstimateRequest
import io.batteryapi.models.GaslessV2EstimateResponse
import io.batteryapi.models.GaslessV2SendRequest
import io.infrastructure.ClientError
import io.infrastructure.ClientException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The gasless 2.0 wire format against responses recorded from production for an Ethereum transfer of
 * 1 USDT: what the relayer is asked, and what is read back from it.
 */
class EvmGaslessTest : BaseUnitTest() {

    private val gaslessApi = mockk<GaslessApi>()
    private val api = mockk<API>()
    private val gasless = EvmGasless(api)
    private val auth = Authorization(deviceToken = "device", walletId = TestWallet.ID)

    private val usdt by lazy { testAccount(TestAssets.usdtEth, balance = "2006121") }
    private val transfer by lazy {
        EvmGasless.transfer(usdt, TestWallet.ETH_ADDRESS, BigInteger.fromInt(1_000_000))!!
    }

    @Before
    fun setUp() {
        every { api.batteryGasless(any()) } returns gaslessApi
    }

    @Test
    fun aCoinTransferIsTheCallItself() {
        val eth = testAccount(TestAssets.eth)
        val amount = BigInteger.parseString("100000000000000")

        val call = EvmGasless.transfer(eth, TestWallet.ETH_ADDRESS, amount)!!

        assertEquals("eth/mainnet", call.chainId)
        assertEquals(TestWallet.ETH_ADDRESS, call.sender)
        assertEquals(TestWallet.ETH_ADDRESS, call.to)
        assertEquals(amount, call.value)
        assertEquals("0x", call.data)
    }

    @Test
    fun aTokenTransferCallsTheTokenContract() {
        assertEquals("eth/mainnet", transfer.chainId)
        assertEquals(USDT_CONTRACT, transfer.to)
        assertEquals(BigInteger.ZERO, transfer.value)
        assertEquals(USDT_TRANSFER, transfer.data)
    }

    @Test
    fun aSwapWithCalldataIsThatCall() {
        val call = EvmGasless.swap(usdt, swap(amount = 0, data = SWAP_CALLDATA))!!

        assertEquals(EvmGasless.Call.Type.Swap, call.type)
        assertEquals("eth/mainnet", call.chainId)
        assertEquals(TestWallet.ETH_ADDRESS, call.sender)
        assertEquals(ROUTER, call.to)
        assertEquals(BigInteger.ZERO, call.value)
        assertEquals(SWAP_CALLDATA, call.data)
    }

    @Test
    fun aSwapWithoutCalldataIsATransferToTheRoutesAddress() {
        val call = EvmGasless.swap(usdt, swap(amount = 1_000_000, data = null, to = TestWallet.ETH_ADDRESS))!!

        assertEquals(EvmGasless.Call.Type.Swap, call.type)
        assertEquals(USDT_CONTRACT, call.to)
        assertEquals(BigInteger.ZERO, call.value)
        assertEquals(USDT_TRANSFER, call.data)
    }

    @Test
    fun aTransferOffEvmHasNoGaslessCall() {
        val jetton = testAccount(TestAssets.usdtTon)

        assertNull(EvmGasless.transfer(jetton, TestWallet.TON_ADDRESS, BigInteger.ONE))
        assertNull(EvmGasless.transfer(usdt, "not-an-address", BigInteger.ONE))
    }


    @Test
    fun aChainIsNamedAsTheRelayerSpellsIt() {
        assertEquals("eth/mainnet", EvmGasless.chainId(Chain.Ethereum.Mainnet))
        assertEquals("arb/mainnet", EvmGasless.chainId(Chain.Arbitrum.Mainnet))
        assertEquals("base/mainnet", EvmGasless.chainId(Chain.Base.Mainnet))
        assertNull(EvmGasless.chainId(Chain.Ton.Mainnet))
        assertNull(EvmGasless.chainId(Chain.Tron.Mainnet))
    }

    @Test
    fun estimatePricesTheTransferInEveryAsset() {
        val request = slot<GaslessV2EstimateRequest>()
        every { gaslessApi.gaslessEstimate(capture(request), any(), any()) } returns decode<GaslessV2EstimateResponse>(ESTIMATE)

        val estimations = gasless.estimate(auth, transfer)

        assertEquals(listOf(Asset.Battery.ID, USDC_ID, USDT_ID), estimations.map { it.assetId })
        assertEquals(listOf("18", "115304", "115304"), estimations.map { it.fee.toString() })
        assertEquals(listOf(true, false, true), estimations.map { it.available })

        assertEquals(
            json(
                """
                {
                  "chain_id": "eth/mainnet",
                  "sender_address": "${TestWallet.ETH_ADDRESS}",
                  "payload": {
                    "id": "p_01",
                    "type": "main",
                    "chain_id": "eth/mainnet",
                    "version": 1,
                    "meta": { "to": "$USDT_CONTRACT", "value": "0", "data": "$USDT_TRANSFER" }
                  }
                }
                """
            ),
            json(Serializer.JSON.encodeToString(request.captured)),
        )
    }

    @Test
    fun theSignedDocumentsGoBackInTheRelayersEnvelope() {
        val request = slot<GaslessV2SendRequest>()
        every { gaslessApi.gaslessSend(capture(request), any(), any()) } returns mockk()
        val chain = Chain.Ethereum.Mainnet
        val signed = SignedOperation(
            id = "bcef74e1-b940-4142-b7a7-76161850971e",
            payloads = listOf(
                SignedPayload(
                    payloadId = "p_auth",
                    type = SignedPayload.Type.Eip7702Authorization,
                    chain = chain,
                    data = buildJsonObject {
                        put("y_parity", 1)
                        put("r", "0x01")
                        put("s", "0x02")
                    },
                ),
                SignedPayload(
                    payloadId = "p_user",
                    type = SignedPayload.Type.Eip7821HermesDelegate,
                    chain = chain,
                    data = JsonPrimitive("0x03"),
                ),
            ),
        )

        gasless.send(auth, signed)

        assertEquals(
            json(
                """
                {
                  "operation": {
                    "id": "bcef74e1-b940-4142-b7a7-76161850971e",
                    "payloads": [
                      {
                        "type": "eip7702/authorization/signed",
                        "chain_id": "eth/mainnet",
                        "payload_id": "p_auth",
                        "meta": { "data": { "y_parity": 1, "r": "0x01", "s": "0x02" } }
                      },
                      {
                        "type": "eip7821/hermes/delegate/signed",
                        "chain_id": "eth/mainnet",
                        "payload_id": "p_user",
                        "meta": { "data": "0x03" }
                      }
                    ]
                  }
                }
                """
            ),
            json(Serializer.JSON.encodeToString(request.captured)),
        )
    }

    @Test
    fun emulateReadsThePriceAndTheDocumentsToSign() {
        val request = slot<GaslessV2EmulateRequest>()
        every { gaslessApi.gaslessEmulate(capture(request), any(), any()) } returns decode<GaslessV2EmulateResponse>(EMULATE_BATTERY)

        val emulation = gasless.emulate(auth, transfer, Asset.Battery.ID)

        assertEquals("transfer", request.captured.type)
        assertEquals(Asset.Battery.ID, request.captured.gaslessAsset)
        assertEquals(1, request.captured.payloads.size)

        assertEquals("19", emulation.fee.toString())
        assertEquals(1790762591000L, emulation.expiresAt)
        assertEquals("bcef74e1-b940-4142-b7a7-76161850971e", emulation.operation.id)
        assertEquals(
            listOf(Payload.Type.Eip7702Authorization, Payload.Type.Eip7821HermesDelegate),
            emulation.operation.payloads.map { it.type },
        )
    }

    @Test
    fun anUnknownFeeDoesNotDiscardKnownEstimations() {
        val response = ESTIMATE.replaceFirst("\"type\": \"value\"", "\"type\": \"future-fee\"")
        every { gaslessApi.gaslessEstimate(any(), any(), any()) } returns decode<GaslessV2EstimateResponse>(response)

        val estimations = gasless.estimate(auth, transfer)

        assertEquals(listOf(USDC_ID, USDT_ID), estimations.map { it.assetId })
        assertEquals(listOf("115304", "115304"), estimations.map { it.fee.toString() })
    }

    @Test
    fun anUnknownEmulationFeeIsRefused() {
        val response = EMULATE_BATTERY.replaceFirst("\"type\": \"value\"", "\"type\": \"future-fee\"")
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns decode<GaslessV2EmulateResponse>(response)

        assertThrows(IllegalStateException::class.java) {
            gasless.emulate(auth, transfer, Asset.Battery.ID)
        }
    }

    @Test
    fun emulateNamesTheOperationTheCallServes() {
        val request = slot<GaslessV2EmulateRequest>()
        every { gaslessApi.gaslessEmulate(capture(request), any(), any()) } returns decode<GaslessV2EmulateResponse>(EMULATE_BATTERY)

        gasless.emulate(auth, EvmGasless.swap(usdt, swap(amount = 0, data = SWAP_CALLDATA))!!, Asset.Battery.ID)

        assertEquals("swap", request.captured.type)
    }

    @Test
    fun anEmulationInAnotherAssetIsRefused() {
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns decode<GaslessV2EmulateResponse>(EMULATE_BATTERY)

        assertThrows(IllegalStateException::class.java) {
            gasless.emulate(auth, transfer, USDT_ID)
        }
    }

    @Test
    fun anOperationWithATransactionOfTheWalletsOwnIsRefused() {
        val response = EMULATE_BATTERY.replace("\"eip7702/authorization\"", "\"main\"")
            .replace(AUTHORIZATION_META, "{ \"to\": \"$USDT_CONTRACT\", \"value\": \"0\", \"data\": \"$USDT_TRANSFER\" }")
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns decode<GaslessV2EmulateResponse>(response)

        assertThrows(IllegalStateException::class.java) {
            gasless.emulate(auth, transfer, Asset.Battery.ID)
        }
    }

    @Test
    fun anExpiredEmulationIsToldFromOtherRejections() {
        assertTrue(EvmGasless.isExpired(rejection(404, "operation_not_found")))
        assertTrue(EvmGasless.isExpired(rejection(400, "emulation_expired")))
        assertFalse(EvmGasless.isExpired(rejection(400, "invalid_signature")))
        assertFalse(EvmGasless.isExpired(ClientException("Client error", 400, ClientError<Any>(body = "not json"))))
    }

    private fun swap(amount: Int, data: String?, to: String = ROUTER) = Transaction.Swap(
        account = usdt.value,
        amount = BigInteger.fromInt(amount),
        destination = testAccount(TestAssets.eth).value,
        to = Address.force(to, Chain.Ethereum.Mainnet),
        isMax = false,
        data = data,
    )

    private fun rejection(status: Int, code: String) = ClientException(
        message = "Client error : $status",
        statusCode = status,
        response = ClientError<Any>(
            body = """{"error": {"code": "$code", "message": "no such operation, emulate it first"}}""",
            statusCode = status,
        ),
    )

    private inline fun <reified T> decode(json: String): T = Serializer.JSON.decodeFromString(json)

    private fun json(text: String) = Json.parseToJsonElement(text)

    private companion object {
        const val USDT_CONTRACT = "0xdAC17F958D2ee523a2206206994597C13D831ec7"
        const val USDT_TRANSFER =
            "0xa9059cbb0000000000000000000000000ee3cdee4f010bf6c49af86e9e67ef2bc80a508b00000000000000000000000000000000000000000000000000000000000f4240"
        const val USDC_ID = "eth/mainnet/erc20/0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"
        const val USDT_ID = "eth/mainnet/erc20/0xdac17f958d2ee523a2206206994597c13d831ec7"
        const val ROUTER = "0x7a250d5630B4cF539739dF2C5dAcb4c659F2488D"
        const val SWAP_CALLDATA = "0x38ed1739"
        const val AUTHORIZATION_META =
            """{ "address": "0x24b576BC271823bF9C24BE627B4363b9b00191e9", "nonce": 41, "chainId": 1 }"""


        const val ESTIMATE = """
            {
              "chain_id": "eth/mainnet",
              "sender_address": "0x0eE3CdEE4f010BF6c49aF86E9e67ef2bc80a508b",
              "created_at": "2026-09-30T09:51:17Z",
              "expires_at": "2026-09-30T10:01:17Z",
              "estimations": [
                {
                  "asset": { "id": "battery/mainnet", "name": "Battery", "symbol": "BATTERY", "decimals": 0, "chain": null },
                  "available": true,
                  "fee": { "type": "value", "asset_id": "battery/mainnet", "data": { "amount": "18" } }
                },
                {
                  "asset": { "id": "$USDC_ID", "name": "USDC", "symbol": "USDC", "decimals": 6, "chain": "eth" },
                  "available": false,
                  "fee": { "type": "value", "asset_id": "$USDC_ID", "data": { "amount": "115304" } },
                  "error": { "code": "insufficient_funds", "message": "the account holds none of this token" }
                },
                {
                  "asset": { "id": "$USDT_ID", "name": "USDT", "symbol": "USDT", "decimals": 6, "chain": "eth" },
                  "available": true,
                  "fee": { "type": "value", "asset_id": "$USDT_ID", "data": { "amount": "115304" } }
                }
              ]
            }
        """

        const val EMULATE_BATTERY = """
            {
              "chain_id": "eth/mainnet",
              "sender_address": "0x0eE3CdEE4f010BF6c49aF86E9e67ef2bc80a508b",
              "created_at": "2026-09-30T09:53:11Z",
              "expires_at": "2026-09-30T10:03:11Z",
              "asset_id": "battery/mainnet",
              "fee": { "type": "value", "asset_id": "battery/mainnet", "data": { "amount": "19" } },
              "account": {
                "address": "0x0eE3CdEE4f010BF6c49aF86E9e67ef2bc80a508b",
                "delegated": false,
                "delegated_to": "0x000000005c84F8Fd50b21CAC312528A64437030e",
                "requires_authorization": true,
                "authorization": { "chain_id": 1, "address": "0x24b576BC271823bF9C24BE627B4363b9b00191e9", "nonce": 41 }
              },
              "gas": {
                "estimated": "99141",
                "charged": "102115",
                "authorization_gas": "12500",
                "gas_price_wei": "212233558",
                "source": "estimateGas",
                "gas_price_source": "chain",
                "fee_native_wei": "21672229775170"
              },
              "operation": {
                "id": "bcef74e1-b940-4142-b7a7-76161850971e",
                "type": "transfer",
                "payloads": [
                  {
                    "id": "p_auth",
                    "type": "eip7702/authorization",
                    "chain_id": "eth/mainnet",
                    "version": 1,
                    "meta": $AUTHORIZATION_META
                  },
                  {
                    "id": "p_user",
                    "type": "eip7821/hermes/delegate",
                    "chain_id": "eth/mainnet",
                    "version": 1,
                    "fee": { "type": "value", "asset_id": "battery/mainnet", "data": { "amount": "19" } },
                    "meta": {
                      "digest": "0x25de48f18779d904969b06f9b652810bd8dc33055c6f5774c518b4a1acf98748",
                      "data": {
                        "domain": {
                          "chainId": 1,
                          "name": "Hermes",
                          "salt": "0x00000000000000000000000024b576bc271823bf9c24be627b4363b9b00191e9",
                          "verifyingContract": "0x0eE3CdEE4f010BF6c49aF86E9e67ef2bc80a508b",
                          "version": "v1.0.0"
                        },
                        "message": {
                          "calls": [
                            {
                              "data": "$USDT_TRANSFER",
                              "target": "$USDT_CONTRACT",
                              "value": "0"
                            }
                          ],
                          "deadline": "1790762591",
                          "mode": "0x0100000000007821000100000000000000000000000000000000000000000000",
                          "nonce": "0"
                        },
                        "primaryType": "Execute",
                        "types": {
                          "Call": [
                            { "name": "target", "type": "address" },
                            { "name": "value", "type": "uint256" },
                            { "name": "data", "type": "bytes" }
                          ],
                          "EIP712Domain": [
                            { "name": "name", "type": "string" },
                            { "name": "version", "type": "string" },
                            { "name": "chainId", "type": "uint256" },
                            { "name": "verifyingContract", "type": "address" },
                            { "name": "salt", "type": "bytes32" }
                          ],
                          "Execute": [
                            { "name": "mode", "type": "bytes32" },
                            { "name": "calls", "type": "Call[]" },
                            { "name": "nonce", "type": "uint256" },
                            { "name": "deadline", "type": "uint256" }
                          ]
                        }
                      }
                    }
                  }
                ]
              }
            }
        """
    }
}
