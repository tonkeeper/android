package com.tonapps.deposit.multicoin.screens.confirm.fee

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.Authorization
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import com.tonapps.wallet.testing.testAsset
import io.Serializer
import io.batteryapi.apis.GaslessApi
import io.batteryapi.models.GaslessV2EmulateResponse
import io.batteryapi.models.GaslessV2EstimateRequest
import io.batteryapi.models.GaslessV2EstimateResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.util.Base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * The gasless 2.0 wire format on Solana: a transfer asked as `main` {to, value, token, memo}, a
 * provider's transaction as `prebuild` {data}, and a transaction the relayer pays read back.
 */
class GaslessRelayerSolanaTest : BaseUnitTest() {

    private val gaslessApi = mockk<GaslessApi>()
    private val api = mockk<API>()
    private val gasless = GaslessRelayer(api)
    private val auth = Authorization(deviceToken = "device", walletId = TestWallet.ID)

    private val sol by lazy { testAccount(SOL, balance = "1000000000", address = SENDER) }
    private val usdt by lazy { testAccount(USDT, balance = "5000000", address = SENDER) }

    private val request = slot<GaslessV2EstimateRequest>()

    @Before
    fun setUp() {
        every { api.batteryGasless(any()) } returns gaslessApi
        every { gaslessApi.gaslessEstimate(capture(request), any(), any()) } returns decode<GaslessV2EstimateResponse>(ESTIMATE)
    }

    @Test
    fun theRelayerNamesSolanaItsOwnWay() {
        assertEquals("sol/mainnet", GaslessRelayer.chainId(Chain.Solana.Mainnet))
    }

    @Test
    fun aSolTransferIsAskedAsMain() {
        gasless.estimate(auth, GaslessRelayer.solanaTransfer(sol, RECIPIENT, BigInteger.fromInt(1_000), memo = "hi")!!)

        assertEquals("sol/mainnet", request.captured.chainId)
        assertEquals(SENDER, request.captured.senderAddress)
        assertEquals("main", request.captured.payload.type)
        assertEquals(json("""{ "to": "$RECIPIENT", "value": "1000", "memo": "hi" }"""), askedMeta())
    }

    @Test
    fun anSplTransferNamesItsMint() {
        gasless.estimate(auth, GaslessRelayer.solanaTransfer(usdt, RECIPIENT, BigInteger.fromInt(1_000_000), memo = null)!!)

        assertEquals("main", request.captured.payload.type)
        assertEquals(json("""{ "to": "$RECIPIENT", "value": "1000000", "token": "$USDT_MINT" }"""), askedMeta())
    }

    @Test
    fun aBlankMemoIsNone() {
        val call = GaslessRelayer.solanaTransfer(sol, RECIPIENT, BigInteger.ONE, memo = "  ")!!

        assertNull((call.solana as PayloadMeta.Solana.Main).memo)
    }

    @Test
    fun aTransferOffSolanaIsNotOne() {
        assertNull(GaslessRelayer.solanaTransfer(testAccount(TestAssets.eth), TestWallet.ETH_ADDRESS, BigInteger.ONE, memo = null))
    }

    @Test
    fun aDepositSwapIsTheTransferItPays() {
        val call = GaslessRelayer.swap(usdt, swap(data = null, memo = "deposit-42"))!!
        gasless.estimate(auth, call)

        assertEquals(GaslessRelayer.Call.Type.Swap, call.type)
        assertEquals("main", request.captured.payload.type)
        assertEquals(
            json("""{ "to": "$RECIPIENT", "value": "1000000", "token": "$USDT_MINT", "memo": "deposit-42" }"""),
            askedMeta(),
        )
    }

    @Test
    fun aProviderTransactionIsAskedAsPrebuild() {
        val provided = transaction(payer = SENDER_KEY)

        val call = GaslessRelayer.swap(usdt, swap(data = provided))!!
        gasless.estimate(auth, call)

        assertEquals(GaslessRelayer.Call.Type.Swap, call.type)
        assertEquals("prebuild", request.captured.payload.type)
        assertEquals(json("""{ "data": "$provided" }"""), askedMeta())
    }

    @Test
    fun aTransactionTheRelayerPaysIsSigned() {
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns emulation(transaction(payer = RELAYER_KEY))

        val emulation = gasless.emulate(auth, listOf(providerSwap()), Asset.Battery.ID)

        assertEquals(listOf(Payload.Type.Prebuild), emulation.operation.payloads.map { it.type })
    }

    @Test
    fun aTransactionTheSenderPaysIsRefused() {
        every { gaslessApi.gaslessEmulate(any(), any(), any()) } returns emulation(transaction(payer = SENDER_KEY))

        assertThrows(IllegalStateException::class.java) {
            gasless.emulate(auth, listOf(providerSwap()), Asset.Battery.ID)
        }
    }

    private fun askedMeta() = json(Serializer.JSON.encodeToString(request.captured.payload)).jsonObject.getValue("meta")

    private fun providerSwap() = GaslessRelayer.swap(usdt, swap(data = transaction(payer = SENDER_KEY)))!!

    private fun swap(data: String?, memo: String? = null) = Transaction.Swap(
        account = usdt.value,
        amount = BigInteger.fromInt(1_000_000),
        destination = testAccount(SOL, address = SENDER).value,
        to = Address.force(RECIPIENT, Chain.Solana.Mainnet),
        isMax = false,
        data = data,
        memo = memo,
    )

    // Recorded from production for a 0.0005 SOL transfer, with the transaction swapped for [transaction].
    private fun emulation(transaction: String) = decode<GaslessV2EmulateResponse>(
        """
        {
          "chain_id": "sol/mainnet",
          "sender_address": "$SENDER",
          "created_at": "2025-10-30T13:36:29Z",
          "expires_at": "2025-10-30T13:37:29Z",
          "asset_id": "battery/mainnet",
          "fee": { "type": "value", "asset_id": "battery/mainnet", "data": { "amount": "1" } },
          "operation": {
            "id": "8e058bd8-8978-451d-971d-f9c0fb0f4b0e",
            "type": "transfer",
            "payloads": [
              {
                "id": "38d370c0-76a1-4fa3-a11b-b0952ae407e5",
                "type": "prebuild",
                "chain_id": "sol/mainnet",
                "version": 1,
                "fee": { "type": "value", "asset_id": "battery/mainnet", "data": { "amount": "1" } },
                "meta": {
                  "data": "$transaction",
                  "fee_payer": "23iwJpnxc7GQXgHV3dySRaAYW5ugsDaCH1soFGaBAJ15",
                  "fee_lamports": "10016",
                  "compute_unit_limit": 1517,
                  "priority_fee_micro_lamports": "10000",
                  "last_valid_block_height": 441326824
                }
              }
            ]
          }
        }
        """
    )

    // One signature slot, a message header (one signer, one read-only unsigned key) and two
    // account keys, the fee payer first: a message ChainKit's decoder accepts as a whole.
    private fun transaction(payer: ByteArray): String {
        val bytes = buildList {
            add(1)
            repeat(64) { add(0) }
            addAll(listOf(1, 0, 1))
            add(2)
            addAll(payer.map(Byte::toInt))
            addAll(SENDER_KEY.map(Byte::toInt))
            repeat(32) { add(0) }
            add(0)
        }.map(Int::toByte).toByteArray()
        return Base64.getEncoder().encodeToString(bytes)
    }

    private inline fun <reified T> decode(json: String): T = Serializer.JSON.decodeFromString(json)

    private fun json(text: String) = Json.parseToJsonElement(text)

    private companion object {
        const val USDT_MINT = "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB"
        const val RECIPIENT = "5Q544fKrFoe6tsEbD7S8EmxGTJYAKtTVhAW5Q5pge4j1"

        // Keys spelled out as bytes so the fee payer reads back as these addresses: 32 zero bytes, and
        // 31 zero bytes with a 1.
        val RELAYER_KEY = ByteArray(32)
        val SENDER_KEY = ByteArray(32).also { it[31] = 1 }
        const val SENDER = "1111111111111111111111111111111" + "2"

        val SOL = testAsset("sol/mainnet/coin", "SOL", "Solana", decimals = 9)
        val USDT = testAsset("sol/mainnet/spl/$USDT_MINT", "USDT", "Tether USD", decimals = 6)

        const val ESTIMATE = """
            {
              "chain_id": "sol/mainnet",
              "sender_address": "$SENDER",
              "created_at": "2026-10-30T13:36:29Z",
              "expires_at": "2026-10-30T13:37:29Z",
              "estimations": [
                {
                  "asset": { "id": "battery/mainnet", "name": "Battery", "symbol": "BATTERY", "decimals": 0 },
                  "available": true,
                  "fee": { "type": "value", "asset_id": "battery/mainnet", "data": { "amount": "1" } }
                }
              ]
            }
        """
    }
}
