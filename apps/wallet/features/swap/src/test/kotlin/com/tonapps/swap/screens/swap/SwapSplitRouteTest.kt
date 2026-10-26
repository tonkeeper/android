package com.tonapps.swap.screens.swap

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.wallet.data.multichain.exchange.SwapQuoteDirection
import com.tonapps.wallet.data.multichain.exchange.SwapQuoteResult
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.testAccount
import io.exchangeapi.models.CrossSwapCalldataPayloadType
import io.exchangeapi.models.CrossSwapPayload
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class SwapSplitRouteTest(private val case: Case) : BaseUnitTest() {

    private lateinit var env: SwapTestEnv

    @Before
    fun setUp() {
        env = SwapTestEnv(context = context, timeSource = timeSource, features = features)
        env.quoteAnswer = { SwapFixtures.quote(case.quoteFixture) }
        case.prepareFixture?.let { fixture ->
            env.prepareAnswer = { SwapFixtures.prepare(fixture) }
        }
    }

    @Test
    fun keepsEveryMessageOfTheEnvelopeInOrder() = runTest(dispatchers.scheduler) {
        val sell = testAccount(SwapFixtures.usdtTonAsset, balance = SOURCE_AMOUNT)
        val quote = (env.swapRepo.fetchQuote(
            sell = sell,
            buy = testAccount(SwapFixtures.tonAsset, balance = "0"),
            amount = BigInteger.parseString(SOURCE_AMOUNT),
            slippageBps = 100,
            direction = SwapQuoteDirection.ExactInput,
            walletId = SwapFixtures.WALLET_ID,
        ) as SwapQuoteResult.Found).quote

        val route = env.swapRepo.prepareRoute(quote, sell.asset.value.chain, SwapFixtures.WALLET_ID)

        assertEquals(case.prepareFixture != null, env.prepareRequests.isNotEmpty())

        val envelope = envelopeMessages(case.mainPayload())
        assertEquals(EXPECTED_AMOUNTS.size, route.mainMessages.size)
        route.mainMessages.forEachIndexed { index, message ->
            assertEquals("to #$index", ROUTER_JETTON_WALLET, message.to)
            assertEquals("amount #$index", BigInteger.parseString(EXPECTED_AMOUNTS[index]), message.amount)
            assertEquals("data #$index", envelope[index], message.data)
            assertEquals("calldata type #$index", CrossSwapCalldataPayloadType.exact, message.calldataType)
            assertNull("fee #$index", message.fee)
            assertNull("mode #$index", message.mode)
        }
        assertNull("approval", route.approval)
    }

    private fun Case.mainPayload(): CrossSwapPayload {
        val payloads = prepareFixture
            ?.let { SwapFixtures.prepare(it).payloads }
            ?: SwapFixtures.quote(quoteFixture).routes.single().payloads.orEmpty()
        return payloads.single()
    }

    private fun envelopeMessages(payload: CrossSwapPayload): List<String> {
        return Json.parseToJsonElement(payload.payload).jsonArray.map { message ->
            message.jsonObject.getValue("payload").jsonPrimitive.content
        }
    }

    data class Case(
        val name: String,
        val quoteFixture: String,
        val prepareFixture: String? = null,
    ) {
        override fun toString(): String = name
    }

    companion object {
        private const val SOURCE_AMOUNT = "3000000000"
        private const val ROUTER_JETTON_WALLET = "EQC9bXRVHLtdjSa4EOcLlD8pNKhNWLeRGLFF0nNTDfl13uwH"
        private val EXPECTED_AMOUNTS = listOf("185000000", "330000000", "260000000")

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = listOf(
            Case(
                name = "inline",
                quoteFixture = "omniston_ton_token_split",
            ),
            Case(
                name = "prepare",
                quoteFixture = "omniston_ton_token_split_prepare",
                prepareFixture = "omniston_ton_token_split",
            ),
        ).map { arrayOf(it) }
    }
}
