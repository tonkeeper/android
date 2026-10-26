package com.tonapps.swap.screens.swap

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.data.multichain.exchange.SwapQuote
import io.exchangeapi.models.CrossSwapAggregator
import io.exchangeapi.models.CrossSwapExactType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Every aggregator the backend can answer with, selling a chain coin and selling a token, on every
 * chain family the wallet swaps from. Each case runs the real repository over a response recorded
 * from that aggregator for that pair, so the provider mapping, the base-unit amounts, the decimals
 * of both sides and the address the request is sent for are checked against what the backend
 * actually returns — a token source and a coin source share no code path in the request or in the
 * display, and neither do two chain families.
 *
 * The payloads those same responses carry are parsed in [SwapPayloadMatrixTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class SwapQuoteMatrixTest(private val case: Case) : BaseUnitTest() {

    private lateinit var env: SwapTestEnv

    @Before
    fun setUp() {
        env = SwapTestEnv(
            context = context,
            timeSource = timeSource,
            features = features,
            swapKitEnabled = case.swapKitEnabled,
        )
        env.quoteAnswer = { SwapFixtures.quote(case.fixture) }
        // The shared TON accounts are deliberately poor (the "insufficient funds" cases measure
        // against them); here every case must reach an enabled Continue.
        env.accounts[SwapFixtures.TON_ASSET_ID] =
            testAccount(SwapFixtures.tonAsset, balance = "100000000000", price = "1.43")
        env.accounts[SwapFixtures.USDT_TON_ASSET_ID] =
            testAccount(SwapFixtures.usdtTonAsset, balance = "200000000")
    }

    @Test
    fun quotesThePair() = swapTest(env) {
        val paired = after {
            sendAction(SwapAction.Asset.SelectSell(case.sell))
            sendAction(SwapAction.Asset.SelectBuy(case.buy))
        }
        assertEquals(case.sell.id, paired.sellAccount?.asset?.id)
        assertEquals(case.buy.id, paired.buyAccount?.asset?.id)

        val quoted = after { sendAction(SwapAction.Amount.SetSell(case.typedAmount)) }

        val request = quoteRequests.last()
        assertEquals(case.sell.id, request.sourceAsset)
        assertEquals(case.buy.id, request.destinationAsset)
        assertEquals(case.sellAddress, request.senderAddress)
        assertEquals(case.buyAddress, request.recipientAddress)
        assertEquals(CrossSwapExactType.exact_input, request.exactType)
        assertEquals(case.sourceBaseAmount, request.sourceAmount)
        // The hard switch is what pins the request to SwapKit; without it the backend fans out.
        when (case.swapKitEnabled) {
            true -> assertEquals(listOf(CrossSwapAggregator.swapkit), request.aggregators)
            false -> assertNull(request.aggregators)
        }

        val quote = quoted.quote!!
        assertEquals(case.provider, quote.quote.provider)
        assertEquals(BigInteger.parseString(case.sourceBaseAmount), quote.quote.sourceBaseAmount)
        assertEquals(BigInteger.parseString(case.buyBaseAmount), quote.quote.buyBaseAmount)
        assertEquals(case.typedAmount, quote.sellAmount)
        assertEquals(case.buyAmount, quote.buyAmount)
        // The label speaks chainkit's name for the coin, which is not always the backend's
        // (TON is "GRAM" there), so the account it was built from is what it has to agree with.
        val sellSymbol = paired.sellAccount!!.asset.value.symbol
        assertTrue(quote.rateLabel, quote.rateLabel.startsWith("1 $sellSymbol"))
        assertTrue(quoted.continueEnabled)
    }

    data class Case(
        val name: String,
        val fixture: String,
        val provider: SwapQuote.Provider,
        val swapKitEnabled: Boolean,
        val sell: AssetEntity,
        val buy: AssetEntity,
        val sellAddress: String,
        val buyAddress: String,
        val typedAmount: String,
        val sourceBaseAmount: String,
        val buyBaseAmount: String,
        val buyAmount: String,
    ) {
        override fun toString(): String = name
    }

    companion object {

        private fun swapsXyz(
            name: String,
            sell: AssetEntity,
            buy: AssetEntity,
            typedAmount: String,
            sourceBaseAmount: String,
            buyBaseAmount: String,
            buyAmount: String,
        ) = Case(
            name = name,
            fixture = name,
            provider = SwapQuote.Provider.SwapXyz,
            swapKitEnabled = false,
            sell = sell,
            buy = buy,
            sellAddress = TestWallet.addressOf(sell),
            buyAddress = TestWallet.addressOf(buy),
            typedAmount = typedAmount,
            sourceBaseAmount = sourceBaseAmount,
            buyBaseAmount = buyBaseAmount,
            buyAmount = buyAmount,
        )

        private fun swapKit(
            name: String,
            sell: AssetEntity,
            buy: AssetEntity,
            typedAmount: String,
            sourceBaseAmount: String,
            buyBaseAmount: String,
            buyAmount: String,
        ) = Case(
            name = name,
            fixture = name,
            provider = SwapQuote.Provider.SwapKit,
            swapKitEnabled = true,
            sell = sell,
            buy = buy,
            sellAddress = TestWallet.addressOf(sell),
            buyAddress = TestWallet.addressOf(buy),
            typedAmount = typedAmount,
            sourceBaseAmount = sourceBaseAmount,
            buyBaseAmount = buyBaseAmount,
            buyAmount = buyAmount,
        )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = listOf(
            swapsXyz(
                name = "swapsxyz_evm",
                sell = SwapFixtures.ethAsset,
                buy = SwapFixtures.usdtEthAsset,
                typedAmount = "0.01",
                sourceBaseAmount = "10000000000000000",
                buyBaseAmount = "24864135",
                buyAmount = "24.864135",
            ),
            swapsXyz(
                name = "swapsxyz_evm_token",
                sell = SwapFixtures.usdtEthAsset,
                buy = SwapFixtures.ethAsset,
                typedAmount = "20",
                sourceBaseAmount = "20000000",
                buyBaseAmount = "7883888919510323",
                buyAmount = "0.00788389",
            ),
            swapsXyz(
                name = "swapsxyz_btc",
                sell = SwapFixtures.btcAsset,
                buy = SwapFixtures.tonAsset,
                typedAmount = "0.002",
                sourceBaseAmount = "200000",
                buyBaseAmount = "110814000000",
                buyAmount = "110.814",
            ),
            swapsXyz(
                name = "swapsxyz_ton",
                sell = SwapFixtures.tonAsset,
                buy = SwapFixtures.ethAsset,
                typedAmount = "50",
                sourceBaseAmount = "50000000000",
                buyBaseAmount = "27887403905000000",
                buyAmount = "0.0278874",
            ),
            // The pair the screen opens on, and the only one whose payload is a signed-message boc.
            swapsXyz(
                name = "swapsxyz_ton_boc",
                sell = SwapFixtures.tonAsset,
                buy = SwapFixtures.usdtTonAsset,
                typedAmount = "5",
                sourceBaseAmount = "5000000000",
                buyBaseAmount = "6999676",
                buyAmount = "6.999676",
            ),
            swapsXyz(
                name = "swapsxyz_ton_token",
                sell = SwapFixtures.usdtTonAsset,
                buy = SwapFixtures.ethAsset,
                typedAmount = "50",
                sourceBaseAmount = "50000000",
                buyBaseAmount = "19670684959999996",
                buyAmount = "0.01967068",
            ),
            swapsXyz(
                name = "swapsxyz_tron",
                sell = SwapFixtures.trxAsset,
                buy = SwapFixtures.usdtTronAsset,
                typedAmount = "1000",
                sourceBaseAmount = "1000000000",
                buyBaseAmount = "329461097",
                buyAmount = "329.461097",
            ),
            swapsXyz(
                name = "swapsxyz_tron_token",
                sell = SwapFixtures.usdtTronAsset,
                buy = SwapFixtures.ethAsset,
                typedAmount = "50",
                sourceBaseAmount = "50000000",
                buyBaseAmount = "19667676880000000",
                buyAmount = "0.01966768",
            ),
            swapKit(
                name = "swapkit_evm",
                sell = SwapFixtures.ethAsset,
                buy = SwapFixtures.btcAsset,
                typedAmount = "0.02",
                sourceBaseAmount = "20000000000000000",
                buyBaseAmount = "61835",
                buyAmount = "0.00061835",
            ),
            swapKit(
                name = "swapkit_evm_token",
                sell = SwapFixtures.usdtEthAsset,
                buy = SwapFixtures.btcAsset,
                typedAmount = "20",
                sourceBaseAmount = "20000000",
                buyBaseAmount = "24009",
                buyAmount = "0.00024009",
            ),
            swapKit(
                name = "swapkit_btc",
                sell = SwapFixtures.btcAsset,
                buy = SwapFixtures.ethAsset,
                typedAmount = "0.001",
                sourceBaseAmount = "100000",
                buyBaseAmount = "31741961683165520",
                buyAmount = "0.03174196",
            ),
            swapKit(
                name = "swapkit_ton_token",
                sell = SwapFixtures.usdtTonAsset,
                buy = SwapFixtures.btcAsset,
                typedAmount = "100",
                sourceBaseAmount = "100000000",
                buyBaseAmount = "123854",
                buyAmount = "0.00123854",
            ),
            swapKit(
                name = "swapkit_tron",
                sell = SwapFixtures.trxAsset,
                buy = SwapFixtures.ethAsset,
                typedAmount = "3000",
                sourceBaseAmount = "3000000000",
                buyBaseAmount = "401919404644540736",
                buyAmount = "0.4019194",
            ),
            swapKit(
                name = "swapkit_tron_token",
                sell = SwapFixtures.usdtTronAsset,
                buy = SwapFixtures.ethAsset,
                typedAmount = "50",
                sourceBaseAmount = "50000000",
                buyBaseAmount = "19925638769453638",
                buyAmount = "0.01992564",
            ),
        ).map { arrayOf(it) }
    }
}
