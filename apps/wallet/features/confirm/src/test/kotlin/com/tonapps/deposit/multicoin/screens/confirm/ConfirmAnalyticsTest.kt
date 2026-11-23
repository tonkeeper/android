package com.tonapps.deposit.multicoin.screens.confirm

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmContext
import com.tonapps.blockchain.model.ConfirmInitiator
import com.tonapps.blockchain.model.ConfirmOperations
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.DefaultEvents
import com.tonapps.bus.generated.Events.TransactionSent.TransactionSentLedgerModel
import com.tonapps.bus.generated.Events.RedOperations.RedOperationsOperation
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerAppSetting
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.wallet.data.ledger.signing.LedgerDismissedException
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.deposit.multicoin.screens.confirm.base.PendingTransaction
import com.tonapps.wallet.data.multichain.exchange.SwapQuote
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class ConfirmAnalyticsTest {

    private val tracked = ArrayList<Pair<String, Map<String, Any>>>()

    private val ton = account(TON_ID, TON_ADDRESS, Address.Type.TonV5R1, decimals = 9)
    private val usdtTon = account(USDT_TON_ID, TON_ADDRESS, Address.Type.TonV5R1, decimals = 6, symbol = "USD₮")
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
            every { events } returns DefaultEvents(recording)
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

        ConfirmAnalytics(request).transactionSent(swap, fee = null, quote = quote(sourceAmount = "1000000"), isHardware = false, ledgerModel = null)

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

        ConfirmAnalytics(request).transactionSent(swap, fee = null, quote = quote(sourceAmount = "100000000"), isHardware = false, ledgerModel = null)

        val props = transactionSent()
        assertEquals(TON_ID, props["asset"])
        assertEquals(0.1, props["amount"] as Double, 0.0)
    }

    @Test
    fun tokenSwapReportsTheSoldTokensWhenTheCallCarriesNoValue() {
        val request = swapRequest(from = usdtEth, sourceAmount = "25000000", to = eth)
        val swap = swap(from = usdtEth, attached = "0", to = eth)

        ConfirmAnalytics(request).transactionSent(swap, fee = null, quote = quote(sourceAmount = "25000000"), isHardware = false, ledgerModel = null)

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

        ConfirmAnalytics(request).transactionSent(transfer, fee = null, quote = null, isHardware = false, ledgerModel = null)

        val props = transactionSent()
        assertEquals("transfer", props["category"])
        assertEquals(0.6, props["amount"] as Double, 0.0)
    }

    @Test
    fun dappTransferReportsWhichProviderTheDappSignedThrough() {
        val amount = BigInteger.parseString("1000")
        val transfer = Transaction.Transfer(account = eth, amount = amount, isMax = false, to = eth.address)

        ConfirmAnalytics(dappTransfer(amount, ConfirmContext.Dapp.Source.Injected))
            .transactionSent(transfer, fee = null, quote = null, isHardware = false, ledgerModel = null)
        ConfirmAnalytics(dappTransfer(amount, ConfirmContext.Dapp.Source.WalletConnect))
            .transactionSent(transfer, fee = null, quote = null, isHardware = false, ledgerModel = null)

        val initiatedBy = tracked.filter { (name, _) -> name == "transaction_sent" }.map { it.second["initiated_by"] }
        assertEquals(listOf("evm_injected", "walletconnect"), initiatedBy)
    }

    @Test
    fun ledgerSendCarriesTheModelOfItsDevice() {
        val amount = BigInteger.parseString("600000000")
        val transfer = Transaction.Transfer(account = ton, amount = amount, isMax = false, to = ton.address)

        ConfirmAnalytics(tonTransfer(amount, ConfirmInitiator.User))
            .transactionSent(transfer, fee = null, quote = null, isHardware = true, ledgerModel = TransactionSentLedgerModel.Stax)

        val props = transactionSent()
        assertEquals("ledger", props["wallet_source"])
        assertEquals("stax", props["ledger_model"])
    }

    @Test
    fun ledgerFailureEndsTheSendWithAStableLedgerErrorType() {
        val analytics = ConfirmAnalytics(tonTransfer(BigInteger.ONE, ConfirmInitiator.User))

        analytics.redOperation(analytics.sendOperation) {
            failure(LedgerDismissedException(LedgerFailure.UserRefused(USER_REFUSED)))
        }
        analytics.redOperation(analytics.sendOperation) {
            failure(LedgerFailureException(LedgerFailure.SettingRequired(LedgerAppId.Ton, LedgerAppSetting.BlindSigning)))
        }

        val terminals = tracked.filter { (name, _) -> name == "op_terminal" }.map { it.second }
        assertEquals("cancel", terminals[0]["outcome"])
        assertEquals("ledger_user_refused", terminals[0]["error_type"])
        assertEquals(USER_REFUSED, terminals[0]["error_code"])
        assertEquals("fail", terminals[1]["outcome"])
        assertEquals("ledger_setting_required", terminals[1]["error_type"])
    }

    @Test
    fun transferSendAttemptCarriesTheNativeUiSourceAndTheSendMetadata() {
        val amount = BigInteger.parseString("600000000")
        val request = tonTransfer(amount, ConfirmInitiator.DeepLink)
        val transfer = Transaction.Transfer(account = ton, amount = amount, isMax = false, to = ton.address)
        val analytics = ConfirmAnalytics(request)
        val before = System.currentTimeMillis()

        analytics.redOperation(
            operation = analytics.sendOperation,
            attemptSource = analytics.sendAttemptSource,
            otherMetadata = analytics.sendMetadata(transfer, fee = null, quote = null),
        ) { success() }

        val props = opAttempt()
        assertEquals("transfer", props["flow"])
        assertEquals("send", props["operation"])
        assertEquals("native_ui", props["attempt_source"])
        assertTrue(props["started_at_ms"] as Long >= before)
        val metadata = JSONObject(props["other_metadata"] as String)
        assertEquals("deep_link", metadata.getString("source"))
        assertEquals(TON_ID, metadata.getString("asset"))
        assertEquals(0.6, metadata.getDouble("amount"), 0.0)
        assertEquals("coin", metadata.getString("fee_paid_in"))
        assertEquals("ton", metadata.getString("asset_network"))
        assertEquals("GRAM", metadata.getString("token_symbol"))
        val terminal = tracked.single { (name, _) -> name == "op_terminal" }.second
        assertTrue(terminal["finished_at_ms"] as Long >= props["started_at_ms"] as Long)
    }

    @Test
    fun swapSendAttemptReportsTheSoldTokenAndLeavesOutTheUnknownSource() {
        val request = swapRequest(from = usdtTon, sourceAmount = "1000000", to = ton)
        val swap = swap(from = usdtTon, attached = "330000000", to = ton)
        val analytics = ConfirmAnalytics(request)

        analytics.redOperation(
            operation = analytics.sendOperation,
            attemptSource = analytics.sendAttemptSource,
            otherMetadata = analytics.sendMetadata(swap, fee = null, quote = quote(sourceAmount = "1000000")),
        ) { success() }

        val props = opAttempt()
        assertEquals("swap", props["flow"])
        assertEquals("native_ui", props["attempt_source"])
        val metadata = JSONObject(props["other_metadata"] as String)
        assertFalse(metadata.has("source"))
        assertEquals(USDT_TON_ID, metadata.getString("asset"))
        assertEquals(1.0, metadata.getDouble("amount"), 0.0)
        assertEquals("USD₮", metadata.getString("token_symbol"))
    }

    @Test
    fun feePaidInNamesWhatPaysTheFee() {
        val analytics = ConfirmAnalytics(tonTransfer(BigInteger.ONE, ConfirmInitiator.User))

        fun feePaidIn(paidIn: Asset?): String {
            return JSONObject(analytics.sendMetadata(usdtTon.asset, amount = 1.0, paidIn = paidIn)).getString("fee_paid_in")
        }

        assertEquals("coin", feePaidIn(null))
        assertEquals("coin", feePaidIn(ton.asset))
        assertEquals("battery_charges", feePaidIn(Asset.Battery(ton.chain)))
        assertEquals("gasless", feePaidIn(usdtTon.asset))
    }

    @Test
    fun dappRequestIsNotASendAndCarriesNoSendMetadata() {
        val amount = BigInteger.parseString("1000")
        val transfer = Transaction.Transfer(account = eth, amount = amount, isMax = false, to = eth.address)
        val analytics = ConfirmAnalytics(dappTransfer(amount, ConfirmContext.Dapp.Source.WalletConnect))

        assertNull(analytics.sendAttemptSource)
        assertNull(analytics.sendMetadata(transfer, fee = null, quote = null))
    }

    @Test
    fun swapEmulateShortOnBalanceFailsAtValidationWithTheSwapMetadata() {
        val analytics = ConfirmAnalytics(swapRequest(from = usdtTon, sourceAmount = "1000000", to = ton))

        analytics.redOperation(RedOperationsOperation.Emulate, otherMetadata = analytics.emulateMetadata()) {
            failure(ConfirmationError.InsufficientBalance, analytics.emulateMetadata(quote = quote(sourceAmount = "1000000")))
        }

        val terminal = opTerminal()
        assertEquals("fail", terminal["outcome"])
        assertEquals("validation", terminal["stage"])
        assertEquals("insufficient_balance", terminal["error_type"])
        for (props in listOf(opAttempt(), terminal)) {
            val metadata = JSONObject(props["other_metadata"] as String)
            assertEquals("omniston", metadata.getString("provider_name"))
            assertEquals(USDT_TON_ID, metadata.getString("asset_from"))
            assertEquals(TON_ID, metadata.getString("asset_to"))
        }
    }

    @Test
    fun swapEmulateWithATechnicalErrorFailsAtTheEmulateStage() {
        val analytics = ConfirmAnalytics(swapRequest(from = usdtTon, sourceAmount = "1000000", to = ton))

        analytics.redOperation(RedOperationsOperation.Emulate) { failure(ConfirmationError.NetworkError) }

        val terminal = opTerminal()
        assertEquals("fail", terminal["outcome"])
        assertEquals("emulate", terminal["stage"])
        assertEquals("network_error", terminal["error_type"])
    }

    @Test
    fun sendFailsAtTheSendStageUnlessTheUserCanFixIt() {
        val analytics = ConfirmAnalytics(swapRequest(from = usdtTon, sourceAmount = "1000000", to = ton))

        analytics.redOperation(analytics.sendOperation) { failure(ConfirmationError.Unknown) }
        analytics.redOperation(analytics.sendOperation) { failure(ConfirmationError.DustAmount) }

        val terminals = tracked.filter { (name, _) -> name == "op_terminal" }.map { it.second }
        assertEquals(listOf("send", "validation"), terminals.map { it["stage"] })
        assertEquals(listOf("unknown", "dust_amount"), terminals.map { it["error_type"] })
    }

    @Test
    fun transferEmulateCarriesNoSwapMetadata() {
        val analytics = ConfirmAnalytics(tonTransfer(BigInteger.ONE, ConfirmInitiator.User))

        assertNull(analytics.emulateMetadata())
        analytics.redOperation(RedOperationsOperation.Emulate, otherMetadata = analytics.emulateMetadata()) {
            failure(ConfirmationError.Unknown, analytics.emulateMetadata(quote = null))
        }

        assertFalse(opAttempt().containsKey("other_metadata"))
        val terminal = opTerminal()
        assertEquals("emulate", terminal["stage"])
        assertFalse(terminal.containsKey("other_metadata"))
    }

    @Test
    fun emulateMetadataNamesTheQuotedProviderOverTheOneTheScreenOpenedWith() {
        val opened = swapRequest(from = usdtTon, sourceAmount = "1000000", to = ton, provider = "swapkit")
        val analytics = ConfirmAnalytics(opened)

        val snapshot = JSONObject(analytics.emulateMetadata())
        val quoted = JSONObject(analytics.emulateMetadata(quote = quote(sourceAmount = "1000000")))

        assertEquals("swapkit", snapshot.getString("provider_name"))
        assertEquals("omniston", quoted.getString("provider_name"))
    }

    @Test
    fun swapFailedReportsTheErrorKeyWhenNothingElseDescribesIt() {
        val request = swapRequest(from = usdtTon, sourceAmount = "1000000", to = ton)
        val pendingTx = mockk<PendingTransaction> { every { quote } returns null }

        ConfirmAnalytics(request).swapFailed(pendingTx, ConfirmationError.Expired)

        val props = tracked.single { (name, _) -> name == "swap_failed" }.second
        assertEquals("expired", props["error_message"])
    }

    @Test
    fun aFailureWithoutAMessageOfItsOwnReportsWhatThrewIt() {
        val analytics = ConfirmAnalytics(swapRequest(from = usdtTon, sourceAmount = "1000000", to = ton))

        analytics.redOperation(analytics.sendOperation) {
            failure(ConfirmationError.Unknown, cause = IllegalStateException("relayer is down"))
        }
        analytics.redOperation(analytics.sendOperation) {
            failure(ConfirmationError.RelayerRejected("too slow"), cause = IllegalStateException("ignored"))
        }
        analytics.redOperation(analytics.sendOperation) {
            failure(ConfirmationError.RelayerRejected(""), cause = IllegalStateException("relayer said nothing"))
        }

        val terminals = tracked.filter { (name, _) -> name == "op_terminal" }.map { it.second }
        assertEquals(listOf("relayer is down", "too slow", "relayer said nothing"), terminals.map { it["error_message"] })
        assertEquals(listOf("unknown", "relayer_rejected", "relayer_rejected"), terminals.map { it["error_type"] })
    }

    @Test
    fun anOperationGivenUpIsACancelAtItsOwnStageWithWhatStoppedIt() {
        val analytics = ConfirmAnalytics(swapRequest(from = usdtTon, sourceAmount = "1000000", to = ton))

        analytics.redOperation(analytics.sendOperation, otherMetadata = analytics.emulateMetadata()) {
            cancel("quote_changed", "quote changed: fee")
        }

        val terminal = opTerminal()
        assertEquals("cancel", terminal["outcome"])
        assertEquals("send", terminal["stage"])
        assertEquals("quote_changed", terminal["error_type"])
        assertEquals("quote changed: fee", terminal["error_message"])
        assertEquals(opAttempt()["other_metadata"], terminal["other_metadata"])
    }

    @Test
    fun aSendStoppedAsTheRequestIsGoneIsACancelForTheGoneRequest() {
        val analytics = ConfirmAnalytics(dappTransfer(BigInteger.ONE, ConfirmContext.Dapp.Source.WalletConnect))
        analytics.markRequestGone()

        runCatching {
            analytics.redOperation(analytics.sendOperation) { throw CancellationException("sheet closed") }
        }

        val terminal = opTerminal()
        assertEquals("cancel", terminal["outcome"])
        assertEquals("request_gone", terminal["error_type"])
        assertEquals("send", terminal["stage"])
    }

    @Test
    fun aSendCancelledWhileTheRequestStandsIsReportedAsWhatCancelledIt() {
        val analytics = ConfirmAnalytics(dappTransfer(BigInteger.ONE, ConfirmContext.Dapp.Source.WalletConnect))

        runCatching {
            analytics.redOperation(analytics.sendOperation) { throw CancellationException("Passcode dismissed") }
        }

        val terminal = opTerminal()
        assertEquals("CancellationException", terminal["error_type"])
        assertEquals("Passcode dismissed", terminal["error_message"])
    }

    private fun opTerminal(): Map<String, Any> {
        return tracked.single { (name, _) -> name == "op_terminal" }.second
    }

    private fun tonTransfer(amount: BigInteger, initiator: ConfirmInitiator) = ConfirmRequest(
        data = CommonTransactionData(assetId = TON_ID, walletId = WALLET_ID),
        type = ConfirmType.Transfer(
            operation = ConfirmOperations.build(
                listOf(ConfirmOperations.payload(0, ton.chain, PayloadMeta.Ton.Main(to = ton.address, value = amount))),
            ),
        ),
        initiator = initiator,
    )

    private fun opAttempt(): Map<String, Any> {
        return tracked.single { (name, _) -> name == "op_attempt" }.second
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

    private fun swapRequest(
        from: Account,
        sourceAmount: String,
        to: Account,
        provider: String = "omniston",
    ) = ConfirmRequest(
        data = CommonTransactionData(assetId = from.asset.id, walletId = WALLET_ID),
        type = ConfirmType.Swap(
            sourceAmount = BigInteger.parseString(sourceAmount),
            destinationAssetId = to.asset.id,
            quote = ConfirmType.Swap.Quote(
                routeId = "route",
                buyAmount = BigInteger.ONE,
                minBuyAmount = BigInteger.ONE,
                provider = provider,
            ),
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

    private fun account(
        assetId: String,
        address: String,
        type: Address.Type,
        decimals: Int,
        symbol: String = "",
    ): Account {
        val asset = requireNotNull(Asset.fromString(assetId, name = "", symbol = symbol, decimals = decimals))
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
        private const val USER_REFUSED = 0x6985
    }
}
