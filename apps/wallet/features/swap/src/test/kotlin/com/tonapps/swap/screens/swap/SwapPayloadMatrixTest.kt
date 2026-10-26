package com.tonapps.swap.screens.swap

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.data.multichain.exchange.SwapQuoteDirection
import com.tonapps.wallet.data.multichain.exchange.SwapQuoteResult
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.testAccount
import io.exchangeapi.models.CrossSwapCalldataPayloadType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * The signing payload behind every quote, for each aggregator on each chain family the wallet sells
 * from. This is the last thing the wallet reads before it builds a transaction: what the parser
 * takes out of the provider's payload is the address the money goes to and the amount that leaves
 * the wallet, so a family whose payload shape the parser does not know does not fail loudly — it
 * pays out to whatever the fallbacks resolve to, or refuses at the confirm screen.
 *
 * The shapes differ per provider *and* per family: swaps.xyz sends a deposit tx (`to`/`value`) for
 * every family but TON-to-TON, which is a signed-message boc keyed `address`/`amount`; SwapKit
 * sends the chain's own tx (hex-quantity EVM, a Tron tx with a fee limit) and leaves a token
 * transfer to be decoded out of the calldata. Payloads come inline on the quote; the ones SwapKit
 * does not inline are read from a recorded `/prepare` response, the same second round-trip the
 * wallet makes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class SwapPayloadMatrixTest(private val case: Case) : BaseUnitTest() {

    private lateinit var env: SwapTestEnv

    @Before
    fun setUp() {
        env = SwapTestEnv(context = context, timeSource = timeSource, features = features)
        env.quoteAnswer = { SwapFixtures.quote(case.fixture) }
        case.prepareFixture?.let { fixture ->
            env.prepareAnswer = { SwapFixtures.prepare(fixture) }
        }
    }

    @Test
    fun parsesTheSigningPayload() = runTest(dispatchers.scheduler) {
        val sell = account(case.sell)
        val quote = (env.swapRepo.fetchQuote(
            sell = sell,
            buy = account(case.buy),
            amount = BigInteger.parseString(case.sourceBaseAmount),
            slippageBps = 100,
            direction = SwapQuoteDirection.ExactInput,
            walletId = SwapFixtures.WALLET_ID,
        ) as SwapQuoteResult.Found).quote

        val route = env.swapRepo.prepareRoute(quote, sell.asset.value.chain, SwapFixtures.WALLET_ID)

        // A payload the provider inlined on the quote must not cost a second round-trip; one it
        // did not must be fetched, or the wallet would sign nothing.
        assertEquals(case.prepareFixture != null, env.prepareRequests.isNotEmpty())

        val main = route.mainMessages.single()
        assertEquals("to", case.to, main.to)
        assertEquals("amount", BigInteger.parseString(case.amount), main.amount)
        assertEquals("calldata type", case.calldataType, main.calldataType)
        assertEquals("fee", case.fee, main.fee)
        assertEquals("mode", case.mode, main.mode)
        when (val data = case.dataPrefix) {
            null -> assertNull("data: $main", main.data)
            else -> assertTrue("data: $main", main.data?.startsWith(data) == true)
        }
        assertNull("approval: $route", route.approval)

        val expectedProviderRouteId = case.prepareFixture
            ?.let { SwapFixtures.prepare(it).providerRouteId }
            ?: quote.providerTxId
        assertEquals("provider route id", expectedProviderRouteId, route.providerRouteId)
    }

    private fun account(asset: AssetEntity): AccountWithDetails =
        testAccount(asset, balance = "100000000000000000000")

    data class Case(
        val name: String,
        val fixture: String,
        val sell: AssetEntity,
        val buy: AssetEntity,
        val sourceBaseAmount: String,
        val to: String,
        val amount: String,
        val calldataType: CrossSwapCalldataPayloadType,
        val dataPrefix: String? = null,
        val fee: Fee? = null,
        val mode: Int? = null,
        val prepareFixture: String? = null,
    ) {
        override fun toString(): String = name
    }

    companion object {

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = listOf(
            // swaps.xyz: a deposit tx per family — `to`/`value`, nothing to decode.
            Case(
                name = "swapsxyz_evm",
                fixture = "swapsxyz_evm",
                sell = SwapFixtures.ethAsset,
                buy = SwapFixtures.btcAsset,
                sourceBaseAmount = "20000000000000000",
                to = "0x91d7f04bAFaB7832e557778d2C5b936391aAD4C5",
                amount = "10000000000000000",
                calldataType = CrossSwapCalldataPayloadType.flex,
            ),
            Case(
                name = "swapsxyz_evm_token",
                fixture = "swapsxyz_evm_token",
                sell = SwapFixtures.usdtEthAsset,
                buy = SwapFixtures.ethAsset,
                sourceBaseAmount = "20000000",
                to = "0x5721B720E23F1c35a4E361A9914D4AEaf6eCC062",
                amount = "20000000",
                calldataType = CrossSwapCalldataPayloadType.flex,
            ),
            Case(
                name = "swapsxyz_btc",
                fixture = "swapsxyz_btc",
                sell = SwapFixtures.btcAsset,
                buy = SwapFixtures.tonAsset,
                sourceBaseAmount = "200000",
                to = "bc1qzteau5ppgl69lkr8mk7qm4ss993e4t89kw9tp2",
                amount = "200000",
                calldataType = CrossSwapCalldataPayloadType.flex,
            ),
            Case(
                name = "swapsxyz_ton",
                fixture = "swapsxyz_ton",
                sell = SwapFixtures.tonAsset,
                buy = SwapFixtures.ethAsset,
                sourceBaseAmount = "50000000000",
                to = "UQAgjoHIpEnInHzcfYRdr-NdXZbN5AKJa8wRf_bdIqq8P00r",
                amount = "50000000000",
                calldataType = CrossSwapCalldataPayloadType.exact,
            ),
            // TON to TON is the odd one out: a boc to send, keyed the TonConnect way, with a
            // message value of its own (the sell amount plus the forward fee) and no deposit
            // address to fall back on if those keys are not read.
            Case(
                name = "swapsxyz_ton_boc",
                fixture = "swapsxyz_ton_boc",
                sell = SwapFixtures.tonAsset,
                buy = SwapFixtures.usdtTonAsset,
                sourceBaseAmount = "5000000000",
                to = "EQAH4MY1EbqP7Gg2wJkoZvoCG4BYPsEG5lVBXPZn0UqXNz4y",
                amount = "5280000000",
                calldataType = CrossSwapCalldataPayloadType.exact,
                dataPrefix = "te6cck",
            ),
            Case(
                name = "swapsxyz_ton_token",
                fixture = "swapsxyz_ton_token",
                sell = SwapFixtures.usdtTonAsset,
                buy = SwapFixtures.ethAsset,
                sourceBaseAmount = "50000000",
                to = "UQCCRc2Ncx6Y6O0FwLUc17XNgRj-1fci4gkO_QLOQrTAutf0",
                amount = "50000000",
                calldataType = CrossSwapCalldataPayloadType.exact,
            ),
            Case(
                name = "swapsxyz_tron",
                fixture = "swapsxyz_tron",
                sell = SwapFixtures.trxAsset,
                buy = SwapFixtures.usdtTronAsset,
                sourceBaseAmount = "1000000000",
                to = "TGLsPyvyPopF8V7VG49BdiRBEMvZTEe713",
                amount = "1000000000",
                calldataType = CrossSwapCalldataPayloadType.flex,
            ),
            Case(
                name = "swapsxyz_tron_token",
                fixture = "swapsxyz_tron_token",
                sell = SwapFixtures.usdtTronAsset,
                buy = SwapFixtures.ethAsset,
                sourceBaseAmount = "50000000",
                to = "TAzit5iKjsgSRSMdvJDJbrbwq1hdV5ffnv",
                amount = "50000000",
                calldataType = CrossSwapCalldataPayloadType.flex,
            ),
            // SwapKit: the chain's own transaction. EVM quantities are hex, and gas + gasPrice
            // become the fee floor the confirm screen shows.
            Case(
                name = "swapkit_evm",
                fixture = "swapkit_evm",
                sell = SwapFixtures.ethAsset,
                buy = SwapFixtures.btcAsset,
                sourceBaseAmount = "20000000000000000",
                to = "0x0ed055e30fec1436377e215525d653eaa045371d",
                amount = "20000000000000000",
                calldataType = CrossSwapCalldataPayloadType.flex,
                fee = Fee.Gas(
                    asset = SwapFixtures.ethAsset.value,
                    limit = BigInteger.fromInt(0xc738),
                    price = BigInteger.fromInt(0xb1d33d5),
                ),
            ),
            // The token leg carries no value of its own: both the recipient and the amount have to
            // come out of the erc20 transfer calldata, or the wallet would send 0 to the token.
            Case(
                name = "swapkit_evm_token",
                fixture = "swapkit_evm_token",
                sell = SwapFixtures.usdtEthAsset,
                buy = SwapFixtures.btcAsset,
                sourceBaseAmount = "20000000",
                to = "0x06b1258ec67880c56b34c1ec78030108efb3ba88",
                amount = "20000000",
                calldataType = CrossSwapCalldataPayloadType.flex,
                fee = Fee.Gas(
                    asset = SwapFixtures.ethAsset.value,
                    limit = BigInteger.fromInt(0x186a0),
                    price = BigInteger.fromInt(0x3223423),
                ),
            ),
            // SwapKit does not inline TON payloads, so this one arrives from /prepare — a boc
            // again, keyed the TonConnect way like every other SwapKit TVM payload.
            Case(
                name = "swapkit_ton_token",
                fixture = "swapkit_ton_token",
                sell = SwapFixtures.usdtTonAsset,
                buy = SwapFixtures.btcAsset,
                sourceBaseAmount = "100000000",
                to = "EQAmgMf3URUyDeXEsIAiNVORsLkncPhXB-copJD4RKabqVKU",
                amount = "50000000",
                calldataType = CrossSwapCalldataPayloadType.flex,
                dataPrefix = "te6cck",
                prepareFixture = "swapkit_ton_token",
            ),
            Case(
                name = "omniston_ton_token",
                fixture = "omniston_ton_token",
                sell = SwapFixtures.usdtTonAsset,
                buy = SwapFixtures.tonAsset,
                sourceBaseAmount = "100000000",
                to = "EQC9bXRVHLtdjSa4EOcLlD8pNKhNWLeRGLFF0nNTDfl13uwH",
                amount = "330000000",
                calldataType = CrossSwapCalldataPayloadType.exact,
                dataPrefix = "te6cck",
            ),
            // Tron pays for its own energy: the tx is signed as given and fee_limit rides along as
            // the floor for our own estimate.
            Case(
                name = "swapkit_tron_token",
                fixture = "swapkit_tron_token",
                sell = SwapFixtures.usdtTronAsset,
                buy = SwapFixtures.ethAsset,
                sourceBaseAmount = "50000000",
                to = "TDLz9ikU1U6UGixgwrci3GaLusFMdraZiM",
                amount = "50000000",
                calldataType = CrossSwapCalldataPayloadType.flex,
                fee = Fee.Value(SwapFixtures.trxAsset.value, BigInteger.fromInt(10000000)),
            ),
        ).map { arrayOf(it) }
    }
}
