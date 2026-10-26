package com.tonapps.deposit.multicoin.screens.confirm

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmContext
import com.tonapps.blockchain.model.ConfirmOperations
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.DefaultEvents
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.wallet.data.multichain.exchange.SwapQuote
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ConfirmAnalyticsTest {

    private val tracked = ArrayList<Pair<String, Map<String, Any>>>()

    private val ton = account(TON_ID, TON_ADDRESS, Address.Type.TonV5R1, decimals = 9)
    private val usdtTon = account(USDT_TON_ID, TON_ADDRESS, Address.Type.TonV5R1, decimals = 6)
    private val eth = account(ETH_ID, ETH_ADDRESS, Address.Type.Default, decimals = 18)
    private val usdtEth = account(USDT_ETH_ID, ETH_ADDRESS, Address.Type.Default, decimals = 6)

    @Before
    fun setUp() {
        val recording = object : EventExecutor {
            override fun trackEvent(eventName: String, props: Map<String, Any>) {
                tracked += eventName to props
            }
        }
        val helper = mockk<AnalyticsHelper> {
            every { eventsTaggedWith(any()) } returns DefaultEvents(recording)
        }
        mockkObject(AnalyticsHelper.Companion)
        every { AnalyticsHelper.Default } returns helper
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun jettonSwapReportsTheSoldJettonsInsteadOfTheAttachedTon() {
        val request = swapRequest(from = usdtTon, sourceAmount = "1000000", to = ton)
        val swap = swap(from = usdtTon, attached = "330000000", to = ton)

        ConfirmAnalytics(request).transactionSent(swap, fee = null, quote = quote(sourceAmount = "1000000"))

        val props = transactionSent()
        assertEquals("swap", props["category"])
        assertEquals(USDT_TON_ID, props["asset"])
        assertEquals(TON_ID, props["to_asset"])
        assertEquals(1.0, props["amount"] as Double, 0.0)
    }

    @Test
    fun coinSwapReportsTheSoldCoinsWithoutTheAttachedGas() {
        val request = swapRequest(from = ton, sourceAmount = "100000000", to = usdtTon)
        val swap = swap(from = ton, attached = "380000000", to = usdtTon)

        ConfirmAnalytics(request).transactionSent(swap, fee = null, quote = quote(sourceAmount = "100000000"))

        val props = transactionSent()
        assertEquals(TON_ID, props["asset"])
        assertEquals(0.1, props["amount"] as Double, 0.0)
    }

    @Test
    fun tokenSwapReportsTheSoldTokensWhenTheCallCarriesNoValue() {
        val request = swapRequest(from = usdtEth, sourceAmount = "25000000", to = eth)
        val swap = swap(from = usdtEth, attached = "0", to = eth)

        ConfirmAnalytics(request).transactionSent(swap, fee = null, quote = quote(sourceAmount = "25000000"))

        val props = transactionSent()
        assertEquals(USDT_ETH_ID, props["asset"])
        assertEquals(25.0, props["amount"] as Double, 0.0)
    }

    @Test
    fun transferReportsTheTransferredAmount() {
        val amount = BigInteger.parseString("600000000")
        val request = ConfirmRequest(
            data = CommonTransactionData(assetId = TON_ID, walletId = WALLET_ID),
            type = ConfirmType.Transfer(
                operation = ConfirmOperations.build(
                    listOf(ConfirmOperations.payload(0, ton.chain, PayloadMeta.Ton.Main(to = ton.address, value = amount))),
                ),
            ),
        )
        val transfer = Transaction.Transfer(account = ton, amount = amount, isMax = false, to = ton.address)

        ConfirmAnalytics(request).transactionSent(transfer, fee = null, quote = null)

        val props = transactionSent()
        assertEquals("transfer", props["category"])
        assertEquals(0.6, props["amount"] as Double, 0.0)
    }

    @Test
    fun dappTransferReportsWhichProviderTheDappSignedThrough() {
        val amount = BigInteger.parseString("1000")
        val transfer = Transaction.Transfer(account = eth, amount = amount, isMax = false, to = eth.address)

        ConfirmAnalytics(dappTransfer(amount, ConfirmContext.Dapp.Source.Injected))
            .transactionSent(transfer, fee = null, quote = null)
        ConfirmAnalytics(dappTransfer(amount, ConfirmContext.Dapp.Source.WalletConnect))
            .transactionSent(transfer, fee = null, quote = null)

        val initiatedBy = tracked.filter { (name, _) -> name == "transaction_sent" }.map { it.second["initiated_by"] }
        assertEquals(listOf("evm_injected", "walletconnect"), initiatedBy)
    }

    private fun dappTransfer(amount: BigInteger, source: ConfirmContext.Dapp.Source) = ConfirmRequest(
        data = CommonTransactionData(assetId = ETH_ID, walletId = WALLET_ID),
        type = ConfirmType.Transfer(
            operation = ConfirmOperations.build(
                listOf(ConfirmOperations.payload(0, eth.chain, PayloadMeta.Evm.Main(to = eth.address, value = amount))),
            ),
        ),
        context = ConfirmContext.Dapp(
            requestId = "request",
            topic = "",
            name = "Earn",
            url = "https://earn.example",
            source = source,
        ),
    )

    private fun transactionSent(): Map<String, Any> {
        return tracked.single { (name, _) -> name == "transaction_sent" }.second
    }

    private fun swapRequest(from: Account, sourceAmount: String, to: Account) = ConfirmRequest(
        data = CommonTransactionData(assetId = from.asset.id, walletId = WALLET_ID),
        type = ConfirmType.Swap(
            sourceAmount = BigInteger.parseString(sourceAmount),
            destinationAssetId = to.asset.id,
        ),
    )

    private fun swap(from: Account, attached: String, to: Account) = Transaction.Swap(
        account = from,
        amount = BigInteger.parseString(attached),
        destination = to,
        to = from.address,
    )

    private fun quote(sourceAmount: String) = SwapQuote(
        routeId = "route",
        sourceBaseAmount = BigInteger.parseString(sourceAmount),
        buyBaseAmount = BigInteger.ONE,
        minimumBuyBaseAmount = BigInteger.ONE,
        slippageBps = null,
        priceImpactBps = null,
        provider = SwapQuote.Provider.Omniston,
    )

    private fun account(assetId: String, address: String, type: Address.Type, decimals: Int): Account {
        val asset = requireNotNull(Asset.fromString(assetId, name = "", symbol = "", decimals = decimals))
        return Account.watch(Address.unchecked(address, asset.chain, type), asset)
    }

    companion object {
        private const val WALLET_ID = "wallet"
        private const val TON_ADDRESS = "UQC1zHXDFDKegQmkRuPjT9G6mZ-9pDVp89zUWrXA3WfyqMJm"
        private const val ETH_ADDRESS = "0x0eE3CdEE4f010BF6c49aF86E9e67ef2bc80a508b"
        private const val TON_ID = "ton/mainnet/coin"
        private const val USDT_TON_ID =
            "ton/mainnet/jetton/0:b113a994b5024a16719f69139328eb759596c38a25f59028b146fecdc3621dfe"
        private const val ETH_ID = "eth/mainnet/coin"
        private const val USDT_ETH_ID = "eth/mainnet/erc20/0xdAC17F958D2ee523a2206206994597C13D831ec7"
    }
}
