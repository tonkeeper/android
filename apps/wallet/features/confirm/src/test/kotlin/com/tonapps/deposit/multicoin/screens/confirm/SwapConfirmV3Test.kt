package com.tonapps.deposit.multicoin.screens.confirm

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.DefaultEvents
import com.tonapps.chainkit.core.chain.api.models.ChainError
import com.tonapps.chainkit.core.chain.api.models.ChainRes
import com.tonapps.chainkit.core.chain.api.models.NodeRes
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.core.op.Intent
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.chainkit.transaction.GasReserveResult
import com.tonapps.core.flags.BootFlags
import com.tonapps.core.flags.WalletFeatureKey
import com.tonapps.deposit.multicoin.screens.confirm.SwapConfirmTestEnv.Companion.PAYLOAD_FEE
import com.tonapps.deposit.multicoin.screens.confirm.SwapConfirmTestEnv.Companion.V3_BTC_ETH
import com.tonapps.deposit.multicoin.screens.confirm.SwapConfirmTestEnv.Companion.V3_TON_USDT
import com.tonapps.deposit.multicoin.screens.confirm.SwapConfirmTestEnv.Companion.V3_USDT_ETH
import com.tonapps.deposit.multicoin.screens.confirm.SwapConfirmTestEnv.Companion.V3_USDT_TRON
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.deposit.multicoin.screens.confirm.base.SendingState
import com.tonapps.deposit.multicoin.screens.confirm.base.Signing
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationConfirmAction
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationShortfall
import com.tonapps.deposit.multicoin.screens.confirm.fee.FeeAccount
import com.tonapps.deposit.multicoin.screens.confirm.fee.RelayerOutcomeUnknownException
import com.tonapps.deposit.multicoin.screens.confirm.fee.RelayerRejectedException
import com.tonapps.deposit.multicoin.screens.confirm.fee.RelayerSend
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFee
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeState
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.exchange.status.SentSwap
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.testAccount
import io.Serializer
import io.exchangeapi.models.CrossSwapPrepareV3
import io.exchangeapi.models.CrossSwapProviderErrorCode
import io.infrastructure.ClientError
import io.infrastructure.ClientException
import com.tonapps.wallet.data.settings.entities.PreferredFeeMethod
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Swaps quoted on `/v3/crosschain`: the route is prepared into a Generic Payload operation, which the
 * operation confirm prices payload by payload, holds to the swap asked for through chain-kit, and signs
 * as it is. Responses are recorded for the test wallet's own addresses.
 */
class SwapConfirmV3Test : BaseUnitTest() {

    private lateinit var env: SwapConfirmTestEnv
    private lateinit var feature: SwapOperationConfirmFeature

    private val scheduler get() = dispatchers.scheduler
    private val headlessJob = Job()

    @Before
    fun setUp() {
        env = SwapConfirmTestEnv(dispatchers.main)
        features.set(WalletFeatureKey.IS_SWAP_V3_ENABLED, true)
        BootFlags.update(BootFlags.values.copy(txValidation = true))
    }

    @After
    fun tearDown() {
        headlessJob.cancel()
        env.clear()
        unmockkObject(AnalyticsHelper.Companion)
    }

    // ─── prepare ───

    @Test
    fun theSwapScreensQuoteIsPreparedIntoTheOperation() {
        open(tonUsdt())

        assertEquals(listOf(SwapConfirmFixtures.snapshotV3(V3_TON_USDT).routeId), env.prepareRequestsV3.toList())
        assertTrue("the swap screen's quote is not asked for again", env.quoteRequestsV3.isEmpty())
        assertTrue(env.quoteRequests.isEmpty() && env.prepareRequests.isEmpty())
        assertTrue(env.requestWalletIds.all { it == SwapConfirmFixtures.WALLET_ID })

        val message = feature.swap.value.operation.payloads.single().meta as PayloadMeta.Ton.Main
        assertEquals(OMNISTON_ROUTER, message.to.display)
        assertEquals(BigInteger.parseString("1280000000"), message.value)
        assertEquals(Fee.Value(TestAssets.ton.value.chain.toAsset(), PAYLOAD_FEE), feature.fee.value)
        assertEquals(BigInteger.parseString("1183456"), feature.quote.value?.buyBaseAmount)
        assertNull(feature.confirmationError.value)
    }

    // ─── intent ───

    /**
     * A DEX swap is held to what it sells. The router it calls comes from the same answer as the
     * operation, so the intent does not name it.
     */
    @Test
    fun aDexSwapIsHeldToItsAmountAlone() {
        open(tonUsdt())

        val intent = env.validatedIntents.last()
        assertEquals(Intent.Spend(TestAssets.ton.value, SwapConfirmFixtures.TON_SOURCE_AMOUNT), intent.spends?.single())
        assertNull(intent.to)
        assertEquals(Intent.Source.Wallet, intent.source)
    }

    @Test
    fun aTokenDepositNamesNoRecipient() {
        env.prepareAnswerV3 = { SwapConfirmFixtures.prepareV3(V3_USDT_TRON) }

        open(request(V3_USDT_TRON, SwapConfirmFixtures.USDT_TRON_ASSET_ID, SwapConfirmFixtures.TON_ASSET_ID, SwapConfirmFixtures.USDT_SOURCE_AMOUNT))

        val intent = env.validatedIntents.last()
        assertEquals(TestAssets.usdtTron.value, intent.spends?.single()?.asset)
        assertNull(intent.to)
    }

    /** The route's deposit address comes from the same answer as the operation: the intent does not name it. */
    @Test
    fun aDepositAddressTheRouteNamesIsNotTheRecipient() {
        env.prepareAnswerV3 = { edited(V3_BTC_ETH, summary = { put("deposit_address", JsonPrimitive(BTC_DEPOSIT)) }) }

        open(btcEth())

        assertNull(env.validatedIntents.last().to)
    }

    // ─── fee ───

    /**
     * The swap behind an approve spends the allowance it has yet to grant: it is priced at the chain's
     * default limit for a call, not simulated. The approve keeps the fee the route quotes.
     */
    @Test
    fun anEvmTokenSwapIsPricedAtTheDefaultLimitBehindItsApprove() {
        val defaultFee = Fee.Value(TestAssets.eth.value, BigInteger.parseString("180850818600000"))
        env.defaultFeeAnswer = { ChainRes.ok(defaultFee) }
        env.prepareAnswerV3 = { SwapConfirmFixtures.prepareV3(V3_USDT_ETH) }

        open(usdtEth())

        val (approve, swap) = feature.swap.value.operation.payloads
        assertEquals(defaultFee, swap.fee)
        val call = env.defaultFeeRequests.single() as Transaction.Call
        assertTrue(call.contract.display.equals(SWAPSXYZ_ROUTER, ignoreCase = true))
        assertTrue("nothing is simulated", env.payloadFeeRequests.isEmpty())
        // The approve quotes its gas: 63617 at 301418031 wei.
        assertEquals(BigInteger.parseString("19175310878127"), approve.fee?.amount)
        assertEquals(BigInteger.parseString("19175310878127") + defaultFee.amount, feature.fee.value?.amount)
        assertNull(env.validatedIntents.last().to)
    }

    /** A sale of TON itself goes to the gasless relayer as the swap states it, as any other TON message. */
    @Test
    fun aTonSaleIsHandedToTheRelayerAsItStands() {
        env.feeOptions = listOf(chargedBattery())

        open(tonUsdt())

        assertEquals(listOf(chargedBattery()), feature.feeMethods.value?.options)
        val message = env.operationBuilds.last().payloads.single().meta as PayloadMeta.Ton.Main
        assertEquals(OMNISTON_ROUTER, message.to.display)
        assertEquals(BigInteger.parseString("1280000000"), message.value)
        assertTrue(checkNotNull(message.body).startsWith("te6cc"))
        assertTrue("the message is not made into a `/v2` one as well", env.feeMethodBuilds.isEmpty())
    }

    /** A jetton sold on TON is one message with a body of its own: it goes to the gasless relayer as it stands. */
    @Test
    fun aTonJettonSaleIsHandedToTheRelayerAsItStands() {
        env.feeOptions = listOf(chargedBattery())

        open(usdtTonSale())

        assertEquals(listOf(chargedBattery()), feature.feeMethods.value?.options)
        val message = env.operationBuilds.last().payloads.single().meta as PayloadMeta.Ton.Main
        assertEquals(OMNISTON_ROUTER, message.to.display)
        assertTrue(checkNotNull(message.body).startsWith("te6cc"))
        assertTrue("the message is not made into a `/v2` one as well", env.feeMethodBuilds.isEmpty())
    }

    /** Where the gasless relayer offers nothing for a TON sale, it is paid in TON: nothing falls back to the old battery. */
    @Test
    fun aTonJettonSaleTheRelayerOffersNothingForIsPaidInTon() {
        open(usdtTonSale())

        assertTrue(env.operationBuilds.isNotEmpty())
        assertTrue(env.feeMethodBuilds.isEmpty())
        assertTrue(feature.feeMethods.value?.options.orEmpty().none { it.viaRelayer })
    }

    /** A message that deploys a contract goes to the gasless relayer with its state init, as the swap states it. */
    @Test
    fun aTonMessageThatDeploysIsHandedOverWithItsStateInit() {
        env.feeOptions = listOf(chargedBattery())
        env.prepareAnswerV3 = { edited(V3_TON_USDT, meta = { put("state_init", JsonPrimitive(STATE_INIT)) }) }

        open(usdtTonSale())

        val message = env.operationBuilds.last().payloads.single().meta as PayloadMeta.Ton.Main
        assertEquals(STATE_INIT, message.stateInit)
        assertTrue(env.feeMethodBuilds.isEmpty())
    }

    /** A TRON route calls a contract with its calldata: the relayer takes that call whole, not a transfer made of it. */
    @Test
    fun aTronCallIsHandedToTheRelayerAsItStands() {
        env.prepareAnswerV3 = { SwapConfirmFixtures.prepareV3(V3_USDT_TRON) }
        env.feeOptions = listOf(chargedBattery())

        open(usdtTron())

        assertEquals(listOf(chargedBattery()), feature.feeMethods.value?.options)
        val build = env.operationBuilds.last()
        val call = build.payloads.single().meta as PayloadMeta.Tron.Main
        assertEquals(USDT_TRON_CONTRACT, call.to.display)
        assertTrue(checkNotNull(call.data).startsWith("a9059cbb"))
        assertEquals(SwapConfirmFixtures.USDT_SOURCE_AMOUNT, build.sold)
        assertTrue("the call is not priced as a transfer as well", env.feeMethodBuilds.isEmpty())
    }

    /** A TRON `main` with no calldata goes to the gasless relayer as the swap states it, its memo with it. */
    @Test
    fun aTronDepositGoesToTheRelayerAsTheSwapStatesIt() {
        env.prepareAnswerV3 = {
            edited(
                V3_USDT_TRON,
                meta = {
                    remove("data")
                    put("memo", JsonPrimitive(DEPOSIT_MEMO))
                },
            )
        }

        open(usdtTron())

        val deposit = env.operationBuilds.last().payloads.single().meta as PayloadMeta.Tron.Main
        assertEquals(USDT_TRON_CONTRACT, deposit.to.display)
        assertNull(deposit.data)
        assertEquals(DEPOSIT_MEMO, deposit.memo)
        assertTrue("the deposit is not made into a `/v2` transfer as well", env.feeMethodBuilds.isEmpty())
    }

    /**
     * A wallet with none of the chain's coin is what a relayer is for, and the chain cannot price a
     * transaction such a wallet sends: the sale is priced at the chain's default fee and paid by the relayer.
     */
    @Test
    fun aTokenSaleTheChainCannotPriceIsPaidByTheRelayer() {
        env.prepareAnswerV3 = { SwapConfirmFixtures.prepareV3(V3_USDT_TRON) }
        env.payloadFeeAnswer = { ChainRes.err(ChainError.InternalError("rejected before execution")) }
        env.defaultFeeAnswer = { ChainRes.ok(Fee.Value(TestAssets.trx.value, PAYLOAD_FEE)) }
        env.accounts[SwapConfirmFixtures.TRX_ASSET_ID] = testAccount(TestAssets.trx, balance = "0", price = "0.3")
        env.feeOptions = listOf(chargedBattery())

        open(usdtTron())

        assertNull(feature.confirmationError.value)
        assertNotNull(feature.prepared.value)
        assertEquals(listOf(chargedBattery()), feature.feeMethods.value?.options)
        assertEquals(chargedBattery().fee, feature.fee.value)
        assertEquals(1, env.defaultFeeRequests.size)
    }

    /** The coin short of the sale is no option at a fee the chain did not price, whatever it holds. */
    @Test
    fun theCoinIsNoOptionAtAFeeTheChainDidNotPrice() {
        env.prepareAnswerV3 = { SwapConfirmFixtures.prepareV3(V3_USDT_TRON) }
        env.payloadFeeAnswer = { ChainRes.err(ChainError.InternalError("rejected before execution")) }
        env.defaultFeeAnswer = { ChainRes.ok(Fee.Value(TestAssets.trx.value, PAYLOAD_FEE)) }
        val trx = testAccount(TestAssets.trx, balance = "0", price = "0.3")
        env.accounts[SwapConfirmFixtures.TRX_ASSET_ID] = trx
        // A max sale's builder holds nothing against the coin's fee: the option comes back payable.
        val native = TxFee(account = FeeAccount.Chain(trx), fee = Fee.Value(TestAssets.trx.value, PAYLOAD_FEE))
        env.feeOptions = listOf(native, chargedBattery())

        open(usdtTron())

        assertNull(feature.confirmationError.value)
        assertEquals(chargedBattery().id, feature.feeMethod.value?.id)
        val options = checkNotNull(feature.feeMethods.value).options
        assertEquals(listOf(chargedBattery().id, native.id), options.map { it.id })
        assertFalse(options.last().available)
    }

    /** A swap the relayer sent is a swap all the same: reported pending and tracked under the relayer's transaction. */
    @Test
    fun aSwapTheRelayerSentIsReportedAndTrackedUnderItsTransaction() {
        env.prepareAnswerV3 = { SwapConfirmFixtures.prepareV3(V3_USDT_TRON) }
        relayedBy(RELAYED_HASH)
        open(usdtTron(oneClick = true))

        swipe()

        assertTrue(env.broadcastPayloads.isEmpty())
        val swap = feature.swap.value.operation.payloads.last()
        assertEquals(RELAYED_HASH, env.reportedHashes[swap.id])
        val reported = checkNotNull(env.reportedSwaps[swap.id])
        assertEquals(SwapConfirmFixtures.TON_ASSET_ID, reported.destinationAssetId)
        assertEquals(SwapConfirmFixtures.USDT_SOURCE_AMOUNT, reported.sellBaseAmount)
        val sent = slot<SentSwap>()
        verify { env.statusTracker.track(capture(sent)) }
        assertEquals(RELAYED_HASH, sent.captured.txHash)
    }

    /** A swap the relayer may have sent is reported under the transaction it named, though its outcome is unknown. */
    @Test
    fun aSwapOfUnknownOutcomeIsReportedUnderTheTransactionTheRelayerNamed() {
        env.prepareAnswerV3 = { SwapConfirmFixtures.prepareV3(V3_USDT_TRON) }
        relayedBy { throw RelayerOutcomeUnknownException(IllegalStateException("unknown"), txHash = RELAYED_HASH) }
        open(usdtTron())

        swipe()

        val swap = feature.swap.value.operation.payloads.last()
        assertEquals(RELAYED_HASH, env.reportedHashes[swap.id])
        assertNotNull(env.reportedSwaps[swap.id])
    }

    /** A one-click swap of unknown outcome is tracked under the transaction the relayer named: its status tells what became of it. */
    @Test
    fun aOneClickSwapOfUnknownOutcomeIsTrackedUnderTheRelayersTransaction() {
        env.prepareAnswerV3 = { SwapConfirmFixtures.prepareV3(V3_USDT_TRON) }
        relayedBy { throw RelayerOutcomeUnknownException(IllegalStateException("unknown"), txHash = RELAYED_HASH) }
        open(usdtTron(oneClick = true))

        swipe()

        val sent = slot<SentSwap>()
        verify { env.statusTracker.track(capture(sent)) }
        assertEquals(RELAYED_HASH, sent.captured.txHash)
    }

    /** The screen closed while the relayer answered an unknown outcome: the swap is reported and tracked all the same. */
    @Test
    fun aSwapOfUnknownOutcomeIsReportedThoughTheScreenClosed() {
        env.prepareAnswerV3 = { SwapConfirmFixtures.prepareV3(V3_USDT_TRON) }
        relayedBy {
            env.clear()
            throw RelayerOutcomeUnknownException(IllegalStateException("unknown"), txHash = RELAYED_HASH)
        }
        open(usdtTron(oneClick = true))

        swipe()

        val swap = feature.swap.value.operation.payloads.last()
        assertEquals(RELAYED_HASH, env.reportedHashes[swap.id])
        val sent = slot<SentSwap>()
        verify { env.statusTracker.track(capture(sent)) }
        assertEquals(RELAYED_HASH, sent.captured.txHash)
    }

    @Test
    fun aSwapTheRelayerRefusedWithNothingSentIsNotReported() {
        env.prepareAnswerV3 = { SwapConfirmFixtures.prepareV3(V3_USDT_TRON) }
        relayedBy { throw RelayerRejectedException(reason = "refused", nothingSent = true, txHash = RELAYED_HASH) }
        open(usdtTron())

        swipe()

        assertTrue(env.reportedSwaps.isEmpty())
    }

    @Test
    fun aRelayerThatNamesNoTransactionLeavesNothingToReport() {
        env.prepareAnswerV3 = { SwapConfirmFixtures.prepareV3(V3_USDT_TRON) }
        relayedBy(null)
        open(usdtTron())

        swipe()

        assertTrue(env.reportedSwaps.isEmpty())
    }

    /** The coin is never signed with a fee the chain did not price: a wallet that holds the coin is told the fee failed. */
    @Test
    fun aSaleTheChainCannotPriceStaysUnpricedWhereTheCoinWouldPay() {
        env.prepareAnswerV3 = { SwapConfirmFixtures.prepareV3(V3_USDT_TRON) }
        env.payloadFeeAnswer = { ChainRes.err(ChainError.InternalError("rejected before execution")) }
        env.defaultFeeAnswer = { ChainRes.ok(Fee.Value(TestAssets.trx.value, PAYLOAD_FEE)) }
        env.feeOptions = listOf(chargedBattery())

        open(usdtTron())

        assertNull(feature.prepared.value)
        assertNotNull(feature.confirmationError.value)
        assertTrue(feature.feeUnavailable.value)
    }

    @Test
    fun aSaleTheChainCannotPriceStaysUnpricedWithNoRelayerToPay() {
        env.prepareAnswerV3 = { SwapConfirmFixtures.prepareV3(V3_USDT_TRON) }
        env.payloadFeeAnswer = { ChainRes.err(ChainError.InternalError("rejected before execution")) }
        env.defaultFeeAnswer = { ChainRes.ok(Fee.Value(TestAssets.trx.value, PAYLOAD_FEE)) }
        env.accounts[SwapConfirmFixtures.TRX_ASSET_ID] = testAccount(TestAssets.trx, balance = "0", price = "0.3")

        open(usdtTron())

        assertNull(feature.prepared.value)
        assertNotNull(feature.confirmationError.value)
        assertTrue(feature.feeUnavailable.value)
    }

    /** The operation is sent whole: the relayer is handed its payloads as they are, the approve ahead of the swap. */
    @Test
    fun theApproveIsHandedToTheRelayerAheadOfTheSwap() {
        env.prepareAnswerV3 = { SwapConfirmFixtures.prepareV3(V3_USDT_ETH) }

        open(usdtEth())

        val payloads = env.operationBuilds.last().payloads
        assertEquals(feature.swap.value.operation.payloads, payloads)
        val (approve, swap) = payloads.map { it.meta as PayloadMeta.Evm.Main }
        assertTrue(checkNotNull(approve.data).startsWith("0x095ea7b3"))
        assertTrue(swap.to.display.equals(SWAPSXYZ_ROUTER, ignoreCase = true))
        assertTrue("an EVM route is not made into messages", env.feeMethodBuilds.isEmpty())
    }

    @Test
    fun aPickedBatteryPaysTheFee() {
        env.feeOptions = listOf(chargedBattery())
        open(tonUsdt())

        feature.selectFee(chargedBattery())
        scheduler.runCurrent()

        assertEquals(chargedBattery().id, feature.feeMethod.value?.id)
        verify { env.feeMethodSettings.setPreferredFeeMethod(any(), PreferredFeeMethod.BATTERY) }
    }

    @Test
    fun aFeeSeededBeforeTheFeesLoadIsTheOneSentWith() {
        val token = TxFee(
            account = FeeAccount.Keeper(balance = BigInteger.fromInt(1000), rate = null),
            fee = Fee.Value(TestAssets.usdtTon.value, BigInteger.fromInt(10)),
        )
        env.feeOptions = listOf(chargedBattery(), token)

        // As the swap screen's headless confirm seeds the fee it kept, before anything is priced.
        feature = env.operationFeature(tonUsdt(), SWAP_QUOTE_INTERVAL_MS)
        feature.pickedFeeId.value = token.id
        scheduler.runCurrent()

        assertEquals(token.id, feature.feeMethod.value?.id)
        // Kept from an earlier attempt, not chosen here: the wallet's preference is left as it was.
        verify(exactly = 0) { env.feeMethodSettings.setPreferredFeeMethod(any(), any()) }
    }

    // ─── send ───

    @Test
    fun theApproveGoesOutAheadOfTheSwapAndOnlyTheSwapIsReportedAsOne() {
        env.prepareAnswerV3 = { SwapConfirmFixtures.prepareV3(V3_USDT_ETH) }
        open(usdtEth())

        swipe()

        val (approve, swap) = feature.swap.value.operation.payloads
        assertEquals(listOf(approve.id to BigInteger.ONE, swap.id to BigInteger.TWO), env.signedPayloads.map { (payload, nonce) -> payload.id to nonce })
        assertEquals(listOf(approve.id, swap.id), env.broadcastPayloads.toList())
        assertNull(env.reportedSwaps[approve.id])
        val reported = checkNotNull(env.reportedSwaps[swap.id])
        assertEquals(SwapConfirmFixtures.ETH_ASSET_ID, reported.destinationAssetId)
        assertEquals("swapsxyz", reported.service)
        assertEquals(SwapConfirmFixtures.snapshotV3(V3_USDT_ETH).routeId, reported.routeId)
        assertEquals(SwapConfirmFixtures.USDT_SOURCE_AMOUNT, reported.sellBaseAmount)
        assertEquals(SendingState.Done, feature.sendingState.value)
    }

    @Test
    fun aSentSwapIsTrackedUnderItsSwapsHash() {
        env.prepareAnswerV3 = { SwapConfirmFixtures.prepareV3(V3_USDT_ETH) }
        open(usdtEth(oneClick = true))

        swipe()

        val sent = slot<SentSwap>()
        verify { env.statusTracker.track(capture(sent)) }
        val swap = feature.swap.value.operation.payloads.last()
        assertEquals("hash-${swap.id}", sent.captured.txHash)
        assertEquals(SwapConfirmFixtures.USDT_SOURCE_AMOUNT, sent.captured.sendAmount)
        assertEquals(SwapConfirmFixtures.ETH_ASSET_ID, sent.captured.buy.id)
    }

    // The swap screen decided the flow when it was built; a boot flag that turned on since does not make this swap one-click.
    @Test
    fun aSwapTheScreenSentClassicIsNotTrackedWhateverTheFlagSaysNow() {
        BootFlags.update(BootFlags.values.copy(oneClickSwap = true))
        env.prepareAnswerV3 = { SwapConfirmFixtures.prepareV3(V3_USDT_ETH) }
        open(usdtEth())

        swipe()

        verify(exactly = 0) { env.statusTracker.track(any()) }
    }

    @Test
    fun aRouteNoLongerSignableIsNotSigned() {
        open(tonUsdt())
        env.serverTime = SwapConfirmFixtures.prepareV3(V3_TON_USDT).dateExpire.toInstant().toEpochMilli()

        swipe()

        assertEquals(ConfirmationError.Expired, feature.confirmationError.value)
        assertTrue(env.signedPayloads.isEmpty())
    }

    /** A max sale is signed with the amount it was prepared with, even where the route lets the wallet change it. */
    @Test
    fun aMaxSaleIsSignedAsPrepared() {
        env.prepareAnswerV3 = { SwapConfirmFixtures.prepareV3(V3_BTC_ETH) }
        env.reserveAnswer = { NodeRes.ok(GasReserveResult(amount = BigInteger.parseString("98500"), isAmountAdjusted = true, error = null)) }

        open(btcEth(isMax = true))

        val deposit = feature.swap.value.operation.payloads.single().meta as PayloadMeta.Bitcoin.Main
        assertEquals(SwapConfirmFixtures.BTC_SOURCE_AMOUNT, deposit.value)
        assertTrue("nothing is trimmed to fit the fee", env.reserveCalls.isEmpty())
    }

    // ─── a max sale and its fee ───

    /** A max sale the fee, paid in the coin it sells, leaves no room for sells what is left, quoted for that. */
    @Test
    fun aMaxSaleShortOfItsFeeSellsWhatTheFeeLeaves() {
        holdTon(TON_SENT + PAYLOAD_FEE - BigInteger.ONE)
        env.prepareAnswerV3 = ::tonRouteAsQuoted

        open(tonUsdt(isMax = true))

        // What is left past what the route sends on top of the amount, less the fee and a fifth of it.
        val fitted = SwapConfirmFixtures.TON_SOURCE_AMOUNT - BigInteger.ONE - PAYLOAD_FEE / BigInteger.fromInt(5)
        assertEquals(fitted.toString(), env.quoteRequestsV3.single().sourceAmount)
        assertEquals(2, env.prepareRequestsV3.size)
        assertEquals(fitted, feature.swap.value.swap.sourceAmount)
        assertEquals(Intent.Spend(TestAssets.ton.value, fitted), env.validatedIntents.last().spends?.single())
        assertNull(feature.confirmationError.value)
    }

    @Test
    fun aMaxSaleTheBalanceCoversToTheUnitIsLeftAsAsked() {
        holdTon(TON_SENT + PAYLOAD_FEE)

        open(tonUsdt(isMax = true))

        assertTrue(env.quoteRequestsV3.isEmpty())
        assertEquals(1, env.prepareRequestsV3.size)
        assertEquals(SwapConfirmFixtures.TON_SOURCE_AMOUNT, feature.swap.value.swap.sourceAmount)
        assertNull(feature.confirmationError.value)
    }

    @Test
    fun anAmountTypedInIsShortRatherThanFitted() {
        holdTon(TON_SENT + PAYLOAD_FEE - BigInteger.ONE)

        open(tonUsdt())

        assertTrue(env.quoteRequestsV3.isEmpty())
        assertTrue(feature.confirmationError.value is ConfirmationError.InsufficientNetworkFee)
    }

    /** One fit per prepare: a route that still does not leave the fee its room is short. */
    @Test
    fun aRouteStillShortOnceFittedIsNotFittedAgain() {
        holdTon(TON_SENT + PAYLOAD_FEE - BigInteger.ONE)

        // The recorded route sends the same whatever it is quoted for.
        open(tonUsdt(isMax = true))

        assertEquals(1, env.quoteRequestsV3.size)
        assertEquals(2, env.prepareRequestsV3.size)
        assertTrue(feature.confirmationError.value is ConfirmationError.InsufficientNetworkFee)
        // What is short is told of what the fitted route sends, which the recorded one does not lower.
        val shortfall = feature.shortfall.value as OperationShortfall.Balance
        assertEquals(TON_SENT + PAYLOAD_FEE, shortfall.required)
    }

    @Test
    fun aMaxSaleTheBatteryPaysForIsLeftAsAsked() {
        holdTon(TON_SENT + PAYLOAD_FEE - BigInteger.ONE)
        env.feeOptions = listOf(chargedBattery())

        open(tonUsdt(isMax = true))

        assertTrue(env.quoteRequestsV3.isEmpty())
        assertEquals(chargedBattery().id, feature.feeMethod.value?.id)
        assertNull(feature.confirmationError.value)
    }

    /** The coin picked over the battery once the route is prepared is a fee the max sale has to leave room for. */
    @Test
    fun pickingTheCoinForAMaxSaleSellsWhatItsFeeLeaves() {
        val coin = holdTon(TON_SENT + PAYLOAD_FEE - BigInteger.ONE)
        val native = TxFee(account = FeeAccount.Chain(coin), fee = Fee.Value(TestAssets.ton.value, PAYLOAD_FEE))
        env.feeOptions = listOf(chargedBattery(), native)
        env.prepareAnswerV3 = ::tonRouteAsQuoted
        open(tonUsdt(isMax = true))
        assertTrue(env.quoteRequestsV3.isEmpty())

        feature.selectFee(native)
        scheduler.runCurrent()

        val fitted = SwapConfirmFixtures.TON_SOURCE_AMOUNT - BigInteger.ONE - PAYLOAD_FEE / BigInteger.fromInt(5)
        assertEquals(listOf(fitted.toString()), env.quoteRequestsV3.map { it.sourceAmount })
        assertEquals(fitted, feature.swap.value.swap.sourceAmount)
        assertEquals(native.id, feature.feeMethod.value?.id)
    }

    /** A fee in the token sold is held to the balance as the coin's is: USDT pays for selling all of it. */
    @Test
    fun aMaxTokenSalePaidInTheTokenSellsWhatTheFeeLeaves() {
        val balance = SwapConfirmFixtures.USDT_SOURCE_AMOUNT + BigInteger.fromInt(500_000)
        val usdt = testAccount(TestAssets.usdtEth, balance = balance.toString())
        env.accounts[SwapConfirmFixtures.USDT_ETH_ASSET_ID] = usdt
        env.feeOptions = listOf(TxFee(account = FeeAccount.Chain(usdt), fee = Fee.Value(TestAssets.usdtEth.value, BigInteger.fromInt(1_000_000))))
        env.prepareAnswerV3 = { SwapConfirmFixtures.prepareV3(V3_USDT_ETH) }

        open(usdtEth(isMax = true))

        val fitted = balance - BigInteger.fromInt(1_200_000)
        assertEquals(listOf(fitted.toString()), env.quoteRequestsV3.map { it.sourceAmount })
        assertEquals(fitted, feature.swap.value.swap.sourceAmount)
        // A max sale's fee methods are not held to what it sells: one in the token sold is what it is fitted to.
        assertTrue(env.operationBuilds.all { it.shrinkToFit })
        assertNull(feature.confirmationError.value)
        // The swap screen reads what is spent off the route as fitted.
        val tx = checkNotNull(feature.prepared.value)
        assertEquals(fitted, tx.spentAmount)
        assertEquals(fitted, (tx.signing as Signing.Tx).value.amount)
    }

    @Test
    fun theFeeMethodsAreToldWhatATypedAmountSells() {
        env.prepareAnswerV3 = { SwapConfirmFixtures.prepareV3(V3_USDT_ETH) }

        open(usdtEth())

        assertEquals(listOf(SwapConfirmFixtures.USDT_SOURCE_AMOUNT), env.operationBuilds.map { it.sold })
        assertTrue(env.operationBuilds.none { it.shrinkToFit })
    }

    /** The swap screen quotes its own route: it is told what to sell, and nothing is quoted here. */
    @Test
    fun aOneClickMaxSaleTellsTheSwapScreenWhatToSell() {
        holdTon(TON_SENT + PAYLOAD_FEE - BigInteger.ONE)

        open(tonUsdt(isMax = true, oneClick = true))

        val fitted = SwapConfirmFixtures.TON_SOURCE_AMOUNT - BigInteger.ONE - PAYLOAD_FEE / BigInteger.fromInt(5)
        assertEquals(SwapMaxFit.Sell(fitted), feature.maxFit.value)
        assertTrue(env.quoteRequestsV3.isEmpty())
        assertEquals(1, env.prepareRequestsV3.size)
    }

    /** The fee the swap screen kept is what the first prepare fits by, not the head of the list. */
    @Test
    fun aMaxSaleWhoseKeptFeeIsInAnotherAssetIsLeftAsAsked() {
        val coin = holdTon(TON_SENT + PAYLOAD_FEE - BigInteger.ONE)
        val native = TxFee(account = FeeAccount.Chain(coin), fee = Fee.Value(TestAssets.ton.value, PAYLOAD_FEE))
        val token = TxFee(
            account = FeeAccount.Keeper(balance = BigInteger.fromInt(1000), rate = null),
            fee = Fee.Value(TestAssets.usdtTon.value, BigInteger.fromInt(10)),
        )
        env.feeOptions = listOf(native, token)

        feature = env.operationFeature(tonUsdt(isMax = true, oneClick = true), feeId = token.id)
        scheduler.runCurrent()

        assertEquals(token.id, feature.feeMethod.value?.id)
        assertNull("a fee in another asset leaves the max as it is", feature.maxFit.value)
        assertTrue(env.quoteRequestsV3.isEmpty())
    }

    /** A fit the swap screen has not acted on leaves the attempt short of its fee, not preparing for good. */
    @Test
    fun aFitTheSwapScreenDoesNotTakeReadsAsShortOfTheFee() {
        val coin = holdTon(TON_SENT + PAYLOAD_FEE - BigInteger.ONE)
        val native = TxFee(account = FeeAccount.Chain(coin), fee = Fee.Value(TestAssets.ton.value, PAYLOAD_FEE))
        val token = TxFee(
            account = FeeAccount.Keeper(balance = BigInteger.fromInt(1000), rate = null),
            fee = Fee.Value(TestAssets.usdtTon.value, BigInteger.fromInt(10)),
        )
        env.feeOptions = listOf(native, token)
        open(tonUsdt(isMax = true, oneClick = true))
        val headless = HeadlessSwapConfirm(feature, CoroutineScope(dispatchers.main), review = true, feeId = null)
        scheduler.runCurrent()

        assertTrue(feature.maxFit.value is SwapMaxFit.Sell)
        assertEquals(SwapFeePlan.Preparing, headless.feePlan.value)
        assertTrue(!headless.canConfirm.value)

        scheduler.advanceTimeBy(6_000)
        scheduler.runCurrent()

        val plan = headless.feePlan.value as SwapFeePlan.Ready
        assertTrue("the fee reads as unpaid: ${plan.summary}", plan.summary.isUnpaid)
        assertTrue(!headless.canConfirm.value)
    }

    /** A fee that leaves nothing to sell is a shortage, not a fit: the attempt stays short and quotes nothing. */
    @Test
    fun aMaxSaleItsFeeLeavesNothingOfIsShort() {
        // The balance covers the fee alone, but the fee is more than what is left past the forward gas
        // the route sends on top of the amount.
        val fee = BigInteger.parseString("1400000000")
        val coin = holdTon(BigInteger.parseString("1500000000"))
        val native = TxFee(account = FeeAccount.Chain(coin), fee = Fee.Value(TestAssets.ton.value, fee))
        env.payloadFeeAnswer = { ChainRes.ok(Fee.Value(it.chain.toAsset(), fee)) }
        env.feeOptions = listOf(native, chargedBattery())
        open(tonUsdt(isMax = true, oneClick = true))
        val headless = HeadlessSwapConfirm(feature, CoroutineScope(dispatchers.main), review = true, feeId = null)
        scheduler.runCurrent()

        assertEquals(SwapMaxFit.Short, feature.maxFit.value)
        assertNull(headless.refitAmount.value)
        assertTrue(!headless.canConfirm.value)

        // Another same-asset fee that still leaves nothing does not read as one that fits.
        feature.selectFee(native)
        scheduler.runCurrent()

        assertEquals(SwapMaxFit.Short, feature.maxFit.value)
        assertTrue(!headless.canConfirm.value)
    }

    /** A Continue on a max being fitted hands the attempt over to the quote of what fits, without a shortage. */
    @Test
    fun aContinueOnAMaxBeingFittedGivesWayToTheFittedQuote() {
        val coin = holdTon(TON_SENT + PAYLOAD_FEE - BigInteger.ONE)
        val native = TxFee(account = FeeAccount.Chain(coin), fee = Fee.Value(TestAssets.ton.value, PAYLOAD_FEE))
        env.feeOptions = listOf(native, chargedBattery())
        open(tonUsdt(isMax = true, oneClick = true))
        val headless = HeadlessSwapConfirm(feature, CoroutineScope(dispatchers.main), review = true, feeId = null)
        scheduler.runCurrent()

        headless.confirmReview()
        scheduler.runCurrent()

        val fitted = SwapConfirmFixtures.TON_SOURCE_AMOUNT - BigInteger.ONE - PAYLOAD_FEE / BigInteger.fromInt(5)
        assertEquals(HeadlessSwapPhase.Refit(fitted), headless.updates.value.phase)
        assertTrue(env.signedPayloads.isEmpty())
    }

    /** A prepare that fails for a reason of its own ends the attempt with it, whatever fit was pending. */
    @Test
    fun aFailedPrepareEndsTheAttemptDespiteAPendingFit() {
        val coin = holdTon(TON_SENT + PAYLOAD_FEE - BigInteger.ONE)
        val native = TxFee(account = FeeAccount.Chain(coin), fee = Fee.Value(TestAssets.ton.value, PAYLOAD_FEE))
        env.feeOptions = listOf(native, chargedBattery())
        open(tonUsdt(isMax = true, oneClick = true))
        val headless = HeadlessSwapConfirm(feature, CoroutineScope(dispatchers.main), review = true, feeId = null)
        scheduler.runCurrent()
        assertTrue(feature.maxFit.value is SwapMaxFit.Sell)

        env.prepareAnswerV3 = { throw IOException("offline") }
        headless.retryFees()
        scheduler.runCurrent()
        headless.confirmReview()
        scheduler.runCurrent()

        assertEquals(HeadlessSwapPhase.Ended(ConfirmationError.NetworkError), headless.updates.value.phase)
    }

    /** A fee picked in another asset needs no room in what is sold: the fit asked for is taken back. */
    @Test
    fun pickingAFeeInAnotherAssetTakesThePendingFitBack() {
        val coin = holdTon(TON_SENT + PAYLOAD_FEE - BigInteger.ONE)
        val native = TxFee(account = FeeAccount.Chain(coin), fee = Fee.Value(TestAssets.ton.value, PAYLOAD_FEE))
        env.feeOptions = listOf(native, chargedBattery())
        open(tonUsdt(isMax = true, oneClick = true))
        val headless = HeadlessSwapConfirm(feature, CoroutineScope(dispatchers.main), review = true, feeId = null)
        scheduler.runCurrent()
        assertTrue(feature.maxFit.value is SwapMaxFit.Sell)

        feature.selectFee(chargedBattery())
        scheduler.runCurrent()

        assertNull(feature.maxFit.value)
        assertTrue(headless.canConfirm.value)
        assertTrue(env.quoteRequestsV3.isEmpty())
    }

    // ─── quoting again ───

    @Test
    fun pickingASlippageQuotesAndPreparesAgainWithIt() {
        open(tonUsdt())

        feature.selectSlippage(300)
        scheduler.runCurrent()

        assertEquals(300, env.quoteRequestsV3.single().slippageBps)
        assertEquals(2, env.prepareRequestsV3.size)
        assertEquals(300, feature.swap.value.swap.slippageBps)
    }

    @Test
    fun anAgedQuoteIsQuotedAndPreparedAgain() {
        open(tonUsdt(), quoteIntervalMillis = 1_000)

        scheduler.advanceTimeBy(1_300)
        scheduler.runCurrent()

        assertEquals(1, env.quoteRequestsV3.size)
        assertEquals(2, env.prepareRequestsV3.size)
    }

    // ─── refusals ───

    @Test
    fun aRouteValidationRejectedIsUnsupported() {
        env.prepareAnswerV3 = { edited(V3_TON_USDT, root = { put("validation_status", JsonPrimitive("rejected")) }) }

        open(tonUsdt())

        assertEquals(ConfirmationError.UnsupportedTransaction, feature.confirmationError.value)
        assertTrue(env.signedPayloads.isEmpty())
    }

    @Test
    fun anOperationThatDoesNotParseIsUnsupported() {
        env.prepareAnswerV3 = { edited(V3_TON_USDT, payload = { put("chain_id", JsonPrimitive("nowhere/mainnet")) }) }

        open(tonUsdt())

        assertEquals(ConfirmationError.UnsupportedTransaction, feature.confirmationError.value)
    }

    /** A route still being validated may pass on the next prepare: a retry quotes and prepares it again. */
    @Test
    fun aRouteStillBeingValidatedIsRetried() {
        var prepares = 0
        env.prepareAnswerV3 = {
            prepares++
            if (prepares == 1) {
                edited(V3_TON_USDT, root = { put("validation_status", JsonPrimitive("pending")) })
            } else {
                SwapConfirmFixtures.prepareV3(V3_TON_USDT)
            }
        }
        open(tonUsdt())
        assertEquals(ConfirmationError.Unknown, feature.confirmationError.value)

        feature.sendAction(OperationConfirmAction.Retry)
        scheduler.runCurrent()

        assertEquals(1, env.quoteRequestsV3.size)
        assertEquals(2, env.prepareRequestsV3.size)
        assertNull(feature.confirmationError.value)
    }

    @Test
    fun aScreenedWalletIsToldSo() {
        val events = recordAnalytics()
        env.prepareAnswerV3 = { throw walletScreened() }

        open(tonUsdt())

        assertEquals(ConfirmationError.WalletScreened, feature.confirmationError.value)
        // The RED emulate ends as the user-state failure the screen shows, with the swap it was for.
        val terminal = events.single { (event, props) -> event == "op_terminal" && props["operation"] == "emulate" }.second
        assertEquals("fail", terminal["outcome"])
        assertEquals("validation", terminal["stage"])
        assertEquals("wallet_screened", terminal["error_type"])
        val metadata = JSONObject(terminal["other_metadata"] as String)
        assertEquals("omniston", metadata.getString("provider_name"))
        assertEquals(SwapConfirmFixtures.TON_ASSET_ID, metadata.getString("asset_from"))
        assertEquals(SwapConfirmFixtures.USDT_TON_ASSET_ID, metadata.getString("asset_to"))
    }

    // ─── headless ───

    /** The swap screen reads a `/v3` route as it reads a `/v2` one, and sends it the same way. */
    @Test
    fun aHeadlessSwapShowsTheRouteAndSendsIt() {
        env.prepareAnswerV3 = { SwapConfirmFixtures.prepareV3(V3_USDT_ETH) }
        open(usdtEth())
        feature.runHeadless()

        val tx = checkNotNull(feature.prepared.value)
        assertEquals(SwapConfirmFixtures.snapshotV3(V3_USDT_ETH).routeId, tx.quote?.routeId)
        assertEquals(SwapConfirmFixtures.USDT_ETH_ASSET_ID, tx.account.asset.id)
        assertEquals(SwapConfirmFixtures.ETH_ASSET_ID, tx.destination?.asset?.id)
        assertEquals(feature.fee.value, tx.fee.estimated)
        // The swap screen compares its snapshot with what was prepared.
        assertEquals(SwapConfirmFixtures.snapshotV3(V3_USDT_ETH), (tx.request.type as ConfirmType.Swap).quote)
        assertTrue(feature.unsignedSend != null && !feature.hasSendData)

        val job = checkNotNull(feature.sendTransaction(tx))
        scheduler.runCurrent()
        scheduler.advanceTimeBy(5_000)
        scheduler.runCurrent()

        assertTrue(job.isCompleted)
        assertEquals(SendingState.Done, feature.sendingState.value)
        assertEquals(2, env.broadcastPayloads.size)
    }

    /**
     * A swap refused before it was signed ends: nothing is signed for a retry to send. Why it was refused
     * is the check's own text, for analytics and the logs: the swap screen says only that it was not sent.
     */
    @Test
    fun aHeadlessSwapRefusedBeforeItWasSignedEndsAndReportsWhy() {
        val events = recordAnalytics()
        env.prepareAnswerV3 = { SwapConfirmFixtures.prepareV3(V3_USDT_TRON) }
        relayedBy { throw RelayerRejectedException(reason = OVERSPEND_REASON, nothingSent = true) }
        open(usdtTron())
        val headless = headless()

        headless.start()
        scheduler.runCurrent()
        scheduler.advanceTimeBy(5_000)
        scheduler.runCurrent()

        assertEquals(HeadlessSwapPhase.Ended(ConfirmationError.RelayerRejected(OVERSPEND_REASON)), headless.updates.value.phase)
        val terminal = events.single { (event, props) -> event == "op_terminal" && props["error_type"] == "relayer_rejected" }.second
        assertEquals(OVERSPEND_REASON, terminal["error_message"])
    }

    @Test
    fun aCoinShortOfItsFeeIsShownWithThePricedFee() {
        openShortBtcEth()

        val error = feature.confirmationError.value as ConfirmationError.InsufficientNetworkFee
        assertEquals("BTC", error.symbol)
        val tx = checkNotNull(feature.prepared.value)
        assertNotNull(feature.fee.value)
        assertEquals(feature.fee.value, tx.fee.estimated)
        assertNull(tx.nonce)
        assertEquals(SwapFeeSummary.NoFunds("BTC"), summarize(tx, null, false))
        assertFalse(feature.hasSendData)
    }

    @Test
    fun aShortSwapIsNeverSent() {
        openShortBtcEth()
        val tx = checkNotNull(feature.prepared.value)

        val job = feature.sendTransaction(tx)
        scheduler.runCurrent()

        assertNull(job)
        assertTrue(env.broadcastPayloads.isEmpty())
        assertTrue(env.signedPayloads.isEmpty())
    }

    @Test
    fun aTonSwapShortOfItsMessagesSpendsWhatTheMessagesCarry() {
        env.feeOptions = listOf(emptyBattery())
        env.accounts[SwapConfirmFixtures.TON_ASSET_ID] = testAccount(TestAssets.ton, balance = "1200000000", price = "1.43")

        open(tonUsdt())

        assertTrue(feature.confirmationError.value is ConfirmationError.InsufficientNetworkFee)
        val tx = checkNotNull(feature.prepared.value)
        assertEquals(BigInteger.parseString("1280000000"), tx.spentAmount)
        assertFalse(tx.isShortOnFeeOnly())
        assertFalse(feature.shortfall.value is OperationShortfall.Fee)
    }

    @Test
    fun aTokenSellShortOfTheCoinItAttachesIsNotChargedInTheToken() {
        val attachedWei = "300000000000000"
        env.prepareAnswerV3 = { edited(V3_USDT_ETH, meta = { put("value", JsonPrimitive(attachedWei)) }) }
        env.accounts[SwapConfirmFixtures.ETH_ASSET_ID] = testAccount(TestAssets.eth, balance = attachedWei, price = "2500")

        open(usdtEth())

        assertTrue(feature.confirmationError.value is ConfirmationError.InsufficientNetworkFee)
        val approveGasWei = BigInteger.parseString("19175310878127")
        assertEquals(approveGasWei, feature.fee.value?.amount)
        val required = approveGasWei + BigInteger.parseString(attachedWei)
        val shortfall = feature.shortfall.value as OperationShortfall.Balance
        assertEquals(SwapConfirmFixtures.ETH_ASSET_ID, shortfall.account.asset.id)
        assertTrue(shortfall.forFees)
        assertEquals(required, shortfall.required)
        assertEquals(required, checkNotNull(feature.prepared.value).fee.requiredEnergy)
    }

    @Test
    fun aShortCoinUsesTheRefreshedBalance() {
        env.prepareAnswerV3 = { SwapConfirmFixtures.prepareV3(V3_BTC_ETH) }
        open(btcEth())
        val refreshedBalance = "100500"
        env.accounts[SwapConfirmFixtures.BTC_ASSET_ID] = testAccount(TestAssets.btc, balance = refreshedBalance, price = "100000")

        feature.retry()
        scheduler.runCurrent()

        assertTrue(feature.confirmationError.value is ConfirmationError.InsufficientNetworkFee)
        assertEquals(SwapConfirmFixtures.btcAccount.balance.available, feature.account.value?.balance?.available)
        val tx = checkNotNull(feature.prepared.value)
        assertEquals(refreshedBalance, tx.account.balance.available)
        assertEquals(refreshedBalance, tx.fee.energy?.balance?.available)
    }

    @Test
    fun aCoinWhoseFeeCannotBePricedStaysUnprepared() {
        env.payloadFeeAnswer = { ChainRes.err(ChainError.InsufficientFunds("no inputs")) }

        openShortBtcEth()

        assertNull(feature.prepared.value)
        assertTrue(feature.confirmationError.value is ConfirmationError.InsufficientNetworkFee)
    }

    @Test
    fun aHeadlessSwapShortOfItsCoinFeePlansNoFunds() {
        openShortBtcEth()

        val headless = headless()
        scheduler.runCurrent()

        val tx = checkNotNull(headless.prepared.value)
        val receivedAt = (tx.request.type as ConfirmType.Swap).quote?.receivedAt
        assertEquals(SwapFeePlan.Ready(SwapFeeSummary.NoFunds("BTC"), receivedAt), headless.feePlan.value)
        assertFalse(headless.canConfirm.value)
    }

    @Test
    fun aPickedNativeFeeThatCannotPayPlansNoFunds() {
        val unpaidTon = TxFee(
            account = FeeAccount.Chain(testAccount(TestAssets.ton, balance = "0")),
            fee = Fee.Value(TestAssets.ton.value, PAYLOAD_FEE),
        )
        env.feeOptions = listOf(emptyBattery(), unpaidTon)
        open(tonUsdt())
        feature.selectFee(unpaidTon)
        scheduler.runCurrent()

        val headless = headless()
        scheduler.runCurrent()

        val plan = headless.feePlan.value as SwapFeePlan.Ready
        assertTrue(plan.summary.isUnpaid)
        assertFalse(headless.canConfirm.value)
        assertTrue(env.broadcastPayloads.isEmpty())
    }

    // ─── v2 ───

    /** The transaction swap confirm quotes again on `/v2`, whatever the flag says. */
    @Test
    fun theTransactionSwapConfirmQuotesOnV2() {
        val v2 = env.feature(SwapConfirmFixtures.swapRequest())
        env.collectPending(v2)
        scheduler.runCurrent()

        assertEquals(1, env.quoteRequests.size)
        assertTrue(env.quoteRequestsV3.isEmpty())
        assertTrue(env.prepareRequestsV3.isEmpty())
    }

    private fun open(request: ConfirmRequest, quoteIntervalMillis: Long = SWAP_QUOTE_INTERVAL_MS): SwapOperationConfirmFeature {
        feature = env.operationFeature(request, quoteIntervalMillis)
        scheduler.runCurrent()
        return feature
    }

    private fun openShortBtcEth() {
        env.prepareAnswerV3 = { SwapConfirmFixtures.prepareV3(V3_BTC_ETH) }
        env.accounts[SwapConfirmFixtures.BTC_ASSET_ID] = testAccount(TestAssets.btc, balance = "100500", price = "100000")
        open(btcEth())
    }

    private fun headless() = HeadlessSwapConfirm(feature, CoroutineScope(dispatchers.main + headlessJob), review = false, feeId = null)

    private fun swipe() {
        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()
        scheduler.advanceTimeBy(5_000)
        scheduler.runCurrent()
    }

    private fun chargedBattery() = TxFee(
        account = FeeAccount.Keeper(balance = BigInteger.fromInt(1000), rate = null),
        fee = Fee.Value(Asset.Battery(Chain.Ton.Mainnet), BigInteger.fromInt(10)),
    )

    private fun emptyBattery() = TxFee(
        account = FeeAccount.Keeper(balance = BigInteger.ZERO, rate = null),
        fee = Fee.Value(Asset.Battery(Chain.Ton.Mainnet), BigInteger.fromInt(10)),
    )

    private fun tonUsdt(isMax: Boolean = false, oneClick: Boolean = false) = request(
        name = V3_TON_USDT,
        assetId = SwapConfirmFixtures.TON_ASSET_ID,
        destinationAssetId = SwapConfirmFixtures.USDT_TON_ASSET_ID,
        amount = SwapConfirmFixtures.TON_SOURCE_AMOUNT,
        isMax = isMax,
        oneClick = oneClick,
    )

    // The recorded TON route's message, sold from the wallet's jetton: the body is not read here.
    private fun usdtTonSale() = request(
        name = V3_TON_USDT,
        assetId = SwapConfirmFixtures.USDT_TON_ASSET_ID,
        destinationAssetId = SwapConfirmFixtures.TON_ASSET_ID,
        amount = SwapConfirmFixtures.USDT_SOURCE_AMOUNT,
    )

    private fun usdtTron(oneClick: Boolean = false) = request(
        name = V3_USDT_TRON,
        assetId = SwapConfirmFixtures.USDT_TRON_ASSET_ID,
        destinationAssetId = SwapConfirmFixtures.TON_ASSET_ID,
        amount = SwapConfirmFixtures.USDT_SOURCE_AMOUNT,
        oneClick = oneClick,
    )

    // The battery with a relayer behind it that answers [txHash] for what it sent.
    private fun relayedBy(txHash: String?) {
        relayedBy { txHash }
    }

    private fun relayedBy(send: () -> String?) {
        env.feeOptions = listOf(chargedBattery())
        env.feeStateAnswer = { energy, estimated ->
            TxFeeState(
                energy = energy,
                estimated = estimated,
                options = env.feeOptions,
                relayer = RelayerSend { _, _ -> send() },
            )
        }
    }

    private fun usdtEth(isMax: Boolean = false, oneClick: Boolean = false) = request(
        name = V3_USDT_ETH,
        assetId = SwapConfirmFixtures.USDT_ETH_ASSET_ID,
        destinationAssetId = SwapConfirmFixtures.ETH_ASSET_ID,
        amount = SwapConfirmFixtures.USDT_SOURCE_AMOUNT,
        isMax = isMax,
        oneClick = oneClick,
    )

    // The wallet's TON, [balance] of it.
    private fun holdTon(balance: BigInteger): AccountWithDetails {
        return testAccount(TestAssets.ton, balance = balance.toString(), price = "1.43")
            .also { env.accounts[SwapConfirmFixtures.TON_ASSET_ID] = it }
    }

    // The recorded TON route, sending the forward gas it carries on top of what it was last quoted for.
    private fun tonRouteAsQuoted(routeId: String): CrossSwapPrepareV3 {
        val quoted = env.quoteRequestsV3.lastOrNull()?.sourceAmount ?: return SwapConfirmFixtures.prepareV3(V3_TON_USDT)
        val sent = BigInteger.parseString(quoted) + (TON_SENT - SwapConfirmFixtures.TON_SOURCE_AMOUNT)
        return edited(V3_TON_USDT, meta = { put("value", JsonPrimitive(sent.toString())) })
    }

    private fun btcEth(isMax: Boolean = false) =
        request(V3_BTC_ETH, SwapConfirmFixtures.BTC_ASSET_ID, SwapConfirmFixtures.ETH_ASSET_ID, SwapConfirmFixtures.BTC_SOURCE_AMOUNT, isMax)

    private fun request(
        name: String,
        assetId: String,
        destinationAssetId: String,
        amount: BigInteger,
        isMax: Boolean = false,
        oneClick: Boolean = false,
    ) = SwapConfirmFixtures.swapRequest(
        assetId = assetId,
        destinationAssetId = destinationAssetId,
        sourceAmount = amount,
        isMax = isMax,
        quote = SwapConfirmFixtures.snapshotV3(name),
        oneClick = oneClick,
    )

    /**
     * The recorded prepare of [name] with its root, its human summary, its last payload and that
     * payload's meta edited in place.
     */
    private fun edited(
        name: String,
        root: MutableMap<String, JsonElement>.() -> Unit = {},
        summary: MutableMap<String, JsonElement>.() -> Unit = {},
        payload: MutableMap<String, JsonElement>.() -> Unit = {},
        meta: MutableMap<String, JsonElement>.() -> Unit = {},
    ): CrossSwapPrepareV3 {
        val json = requireNotNull(javaClass.classLoader?.getResourceAsStream("confirm_v3_prepare_$name.json")) {
            "missing test resource confirm_v3_prepare_$name.json"
        }.bufferedReader().use { it.readText() }
        val prepared = Serializer.JSON.parseToJsonElement(json).jsonObject.toMutableMap()

        val operation = prepared.getValue("operation").jsonObject.toMutableMap()
        val payloads = (operation.getValue("payloads") as JsonArray).toMutableList()
        val last = payloads.last().jsonObject.toMutableMap()
        last["meta"] = JsonObject(last.getValue("meta").jsonObject.toMutableMap().apply(meta))
        payloads[payloads.lastIndex] = JsonObject(last.apply(payload))
        operation["payloads"] = JsonArray(payloads)
        prepared["operation"] = JsonObject(operation)
        prepared["human_summary"] = JsonObject(prepared.getValue("human_summary").jsonObject.toMutableMap().apply(summary))
        prepared.root()

        return Serializer.JSON.decodeFromJsonElement(CrossSwapPrepareV3.serializer(), JsonObject(prepared))
    }

    private fun recordAnalytics(): List<Pair<String, Map<String, Any>>> {
        val tracked = CopyOnWriteArrayList<Pair<String, Map<String, Any>>>()
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
        return tracked
    }

    private fun walletScreened(): ClientException {
        val body = """{"error":"wallet screened","code":"${CrossSwapProviderErrorCode.wallet_screened.value}"}"""
        return ClientException(message = "Client error : 400", statusCode = 400, response = ClientError<Any>(body = body, statusCode = 400))
    }

    private companion object {
        const val OMNISTON_ROUTER = "EQAH4MY1EbqP7Gg2wJkoZvoCG4BYPsEG5lVBXPZn0UqXNz4y"
        const val SWAPSXYZ_ROUTER = "0x1b6257CAE4192e62B629eFCa21771be3D759183D"
        const val BTC_DEPOSIT = "3FHycwXWPjdpMubBrsugoFdwLGiSNBR4WJ"
        const val USDT_TRON_CONTRACT = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
        const val DEPOSIT_MEMO = "71756f74652d3031"
        const val STATE_INIT = "te6ccgEBAQEAAgAAAA=="
        const val RELAYED_HASH = "39c3edffd4ea4fea4b077ab35a32f9af51bd81792ccfc074babdaf7b87d5fbe6"
        const val OVERSPEND_REASON = "Payload 0 spends 80000 of sol/mainnet/coin, past 0"

        // What the recorded TON route sends: the amount sold and the forward gas on top of it.
        val TON_SENT: BigInteger = BigInteger.parseString("1280000000")
    }
}
