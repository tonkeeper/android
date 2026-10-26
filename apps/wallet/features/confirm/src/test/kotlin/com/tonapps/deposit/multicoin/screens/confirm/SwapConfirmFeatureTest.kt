package com.tonapps.deposit.multicoin.screens.confirm

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.chainkit.core.chain.api.models.ChainRes
import com.tonapps.chainkit.core.chain.api.models.NodeRes
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.transaction.GasReservePolicy
import com.tonapps.chainkit.transaction.GasReserveResult
import com.tonapps.deposit.multicoin.screens.confirm.SwapConfirmTestEnv.Companion.EXACT_TON
import com.tonapps.deposit.multicoin.screens.confirm.SwapConfirmTestEnv.Companion.FLEX_ETH
import com.tonapps.deposit.multicoin.screens.confirm.SwapConfirmTestEnv.Companion.OMNISTON_SPLIT
import com.tonapps.deposit.multicoin.screens.confirm.SwapConfirmTestEnv.Companion.OMNISTON_SPLIT_PREPARE
import com.tonapps.deposit.multicoin.screens.confirm.SwapConfirmTestEnv.Companion.TOKEN_ETH
import com.tonapps.deposit.multicoin.screens.confirm.SwapConfirmTestEnv.Companion.BERRY_APPROVAL
import com.tonapps.deposit.multicoin.screens.confirm.SwapConfirmTestEnv.Companion.TON_BOC
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.deposit.multicoin.screens.confirm.base.PendingTransaction
import com.tonapps.deposit.multicoin.screens.confirm.base.Signing
import com.tonapps.deposit.multicoin.screens.confirm.fee.FeeAccount
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFee
import com.tonapps.wallet.data.multichain.account.AccountBalanceEntity
import com.tonapps.wallet.data.multichain.legacy.LegacySwapWallet
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.testAccount
import io.exchangeapi.models.CrossSwapPayload
import io.exchangeapi.models.CrossSwapPrepare
import io.exchangeapi.models.CrossSwapQuote
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * [SwapConfirmFeature.prepareSwap]: how a route recorded from the backend turns into the
 * transactions the confirm screen is about to sign.
 *
 * The cases that matter are the ones where the amount the user asked for and the amount that can
 * actually be sent disagree — MAX against the gas reserve — and they behave differently per calldata
 * type: a `flex` payload may be shrunk to fit, an `exact` one carries the sell amount inside the
 * provider calldata and must reach the chain untouched. An EVM approve is sent as its own transaction
 * ahead of the swap. See [SwapConfirmTestEnv] for what is faked.
 */
class SwapConfirmFeatureTest : BaseUnitTest() {

    private lateinit var env: SwapConfirmTestEnv
    private lateinit var feature: SwapConfirmFeature

    private val scheduler get() = dispatchers.scheduler

    @Before
    fun setUp() {
        env = SwapConfirmTestEnv(dispatchers.main)
    }

    @After
    fun tearDown() {
        env.clear()
    }

    /** Coming from the swap screen, the quote it already paid for is reused, not re-requested. */
    @Test
    fun snapshotQuoteIsReusedForTheFirstPrepare() {
        val snapshot = SwapConfirmFixtures.snapshot(EXACT_TON)
        val pending = prepare(SwapConfirmFixtures.swapRequest(quote = snapshot))

        assertTrue(env.quoteRequests.isEmpty())
        assertEquals(listOf(snapshot.routeId), env.prepareRequests.toList())
        assertEquals(snapshot.routeId, pending.quote?.routeId)
        assertEquals(snapshot.buyAmount, pending.quote?.buyBaseAmount)
        assertEquals(SwapConfirmFixtures.TON_SOURCE_AMOUNT, pending.quote?.sourceBaseAmount)
        assertNotNull(pending.rateLabel)
        assertNull(feature.confirmationError.value)
    }

    @Test
    fun preparedAmountsReplaceTheQuotedOnes() {
        val snapshot = SwapConfirmFixtures.snapshot(EXACT_TON)
        val preparedReceive = snapshot.buyAmount / BigInteger.TWO
        val preparedMinimum = preparedReceive - BigInteger.fromInt(1_000)
        env.prepareAnswer = { SwapConfirmFixtures.prepare(EXACT_TON).withPreparedAmounts(preparedReceive, preparedMinimum) }

        val pending = prepare(SwapConfirmFixtures.swapRequest(quote = snapshot))

        val quote = checkNotNull(pending.quote)
        val sell = SwapConfirmFixtures.tonAccount.asset.value
        val buy = SwapConfirmFixtures.usdtTonAccount.asset.value
        assertEquals(preparedReceive, quote.buyBaseAmount)
        assertEquals(preparedMinimum, quote.minimumBuyBaseAmount)
        assertEquals(env.swapRepo.formatRateLabel(quote, sell, buy), pending.rateLabel)
        assertNotEquals(env.swapRepo.formatRateLabel(quote.copy(buyBaseAmount = snapshot.buyAmount), sell, buy), pending.rateLabel)
    }

    @Test
    fun inlinePreparedAmountsReplaceTheQuotedOnes() {
        val quote = SwapConfirmFixtures.quote(EXACT_TON)
        val preparedReceive = BigInteger.parseString(quote.routes.first().estimatedDestinationAmount) / BigInteger.TWO
        val preparedMinimum = preparedReceive - BigInteger.fromInt(1_000)
        env.quoteAnswer = { quote.withPreparedAmounts(preparedReceive, preparedMinimum) }

        val pending = prepare(SwapConfirmFixtures.swapRequest())

        assertTrue(env.prepareRequests.isEmpty())
        assertEquals(preparedReceive, pending.quote?.buyBaseAmount)
        assertEquals(preparedMinimum, pending.quote?.minimumBuyBaseAmount)
    }

    @Test
    fun quotedAmountsStayWhenPrepareReportsNone() {
        val snapshot = SwapConfirmFixtures.snapshot(EXACT_TON)
        env.prepareAnswer = { SwapConfirmFixtures.prepare(EXACT_TON).withPreparedAmounts(receive = null, minimum = null) }

        val pending = prepare(SwapConfirmFixtures.swapRequest(quote = snapshot))

        assertEquals(snapshot.buyAmount, pending.quote?.buyBaseAmount)
        assertEquals(snapshot.minBuyAmount, pending.quote?.minimumBuyBaseAmount)
        assertEquals(snapshot.priceImpactBps, pending.quote?.priceImpactBps)
    }

    @Test
    fun valueDifferenceFollowsThePreparedReceive() {
        val snapshot = SwapConfirmFixtures.snapshot(EXACT_TON).copy(buyAmount = BigInteger.fromInt(2_000_000), priceImpactBps = -100)
        env.prepareAnswer = {
            SwapConfirmFixtures.prepare(EXACT_TON).withPreparedAmounts(receive = BigInteger.fromInt(1_000_000), minimum = null)
        }

        val pending = prepare(SwapConfirmFixtures.swapRequest(quote = snapshot))

        assertEquals(-5_050, pending.quote?.priceImpactBps)
    }

    @Test
    fun quotedMinimumFollowsThePreparedReceive() {
        val snapshot = SwapConfirmFixtures.snapshot(EXACT_TON)
            .copy(buyAmount = BigInteger.fromInt(2_000_000), minBuyAmount = BigInteger.fromInt(1_900_000))
        env.prepareAnswer = {
            SwapConfirmFixtures.prepare(EXACT_TON).withPreparedAmounts(receive = BigInteger.fromInt(1_000_000), minimum = null)
        }

        val pending = prepare(SwapConfirmFixtures.swapRequest(quote = snapshot))

        assertEquals(BigInteger.fromInt(1_000_000), pending.quote?.buyBaseAmount)
        assertEquals(BigInteger.fromInt(950_000), pending.quote?.minimumBuyBaseAmount)
    }

    @Test
    fun preparedMinimumIsCappedByThePreparedReceive() {
        val snapshot = SwapConfirmFixtures.snapshot(EXACT_TON)
        val preparedReceive = snapshot.minBuyAmount - BigInteger.fromInt(1_000)
        env.prepareAnswer = {
            SwapConfirmFixtures.prepare(EXACT_TON).withPreparedAmounts(receive = preparedReceive, minimum = snapshot.minBuyAmount)
        }

        val pending = prepare(SwapConfirmFixtures.swapRequest(quote = snapshot))

        assertEquals(preparedReceive, pending.quote?.buyBaseAmount)
        assertEquals(preparedReceive, pending.quote?.minimumBuyBaseAmount)
    }

    @Test
    fun unparsablePreparedAmountsFallBackToTheQuote() {
        val snapshot = SwapConfirmFixtures.snapshot(EXACT_TON)
        env.prepareAnswer = { SwapConfirmFixtures.prepare(EXACT_TON).withPreparedSummary(receive = "1.5", minimum = "n/a") }

        val pending = prepare(SwapConfirmFixtures.swapRequest(quote = snapshot))

        assertNull(feature.confirmationError.value)
        assertEquals(snapshot.buyAmount, pending.quote?.buyBaseAmount)
        assertEquals(snapshot.minBuyAmount, pending.quote?.minimumBuyBaseAmount)
    }

    /** No snapshot (timer tick, or a fresh entry): the confirm screen quotes for itself. */
    @Test
    fun withoutASnapshotTheQuoteIsFetched() {
        val pending = prepare(SwapConfirmFixtures.swapRequest())

        val request = env.quoteRequests.single()
        assertEquals(SwapConfirmFixtures.TON_ASSET_ID, request.sourceAsset)
        assertEquals(SwapConfirmFixtures.USDT_TON_ASSET_ID, request.destinationAsset)
        assertEquals(SwapConfirmFixtures.TON_SOURCE_AMOUNT.toString(), request.sourceAmount)
        assertEquals(100, request.slippageBps)
        assertNotNull(pending.quote)
        // swapsxyz returns the signing payload inline with the quote, so there is nothing to prepare.
        assertTrue(env.prepareRequests.isEmpty())
    }

    /**
     * `flex` MAX: the provider takes whatever arrives, so the amount is shrunk to what is left after
     * gas and the transaction stays a MAX one.
     */
    @Test
    fun flexMaxShrinksTheAmountToTheGasReserve() {
        env.quoteAnswer = { SwapConfirmFixtures.quote(FLEX_ETH) }
        env.prepareAnswer = { SwapConfirmFixtures.prepare(FLEX_ETH) }
        env.reserveAnswer = { call ->
            NodeRes.ok(GasReserveResult(amount = call.amount - GAS, isAmountAdjusted = true, error = null))
        }

        val pending = prepare(
            SwapConfirmFixtures.swapRequest(
                assetId = SwapConfirmFixtures.ETH_ASSET_ID,
                destinationAssetId = SwapConfirmFixtures.USDT_ETH_ASSET_ID,
                sourceAmount = SwapConfirmFixtures.ETH_SOURCE_AMOUNT,
                isMax = true,
            ),
        )

        val call = env.reserveCalls.last()
        assertEquals(GasReservePolicy.ShrinkToFit, call.policy)
        assertTrue("a flex MAX is reserved as MAX", call.isMax)

        val transaction = pending.swapTransaction()
        assertEquals(call.amount - GAS, transaction.amount)
        assertTrue(transaction.isMax)
        assertTrue(pending.prerequisites.isEmpty())
        assertNull(feature.confirmationError.value)
    }

    /**
     * `exact` MAX: the sell amount is baked into the provider calldata, so it must survive the gas
     * reservation untouched — trimming it would desync the value from the calldata and revert.
     */
    @Test
    fun exactMaxKeepsTheCalldataAmount() {
        env.reserveAnswer = { call ->
            NodeRes.ok(GasReserveResult(amount = call.amount - GAS, isAmountAdjusted = true, error = null))
        }

        val pending = prepare(SwapConfirmFixtures.swapRequest(isMax = true))

        val call = env.reserveCalls.last()
        assertEquals(GasReservePolicy.DrainOrError, call.policy)
        assertFalse("an exact MAX must not be reserved as MAX", call.isMax)

        // Whatever the provider baked into its calldata is what has to reach the chain.
        val transaction = pending.swapTransaction()
        assertEquals(TON_PAYLOAD_AMOUNT, transaction.amount)
        assertEquals(call.amount, transaction.amount)
    }

    /** `exact` cannot shrink, so a reservation that does not fit is reported instead. */
    @Test
    fun exactMaxWithoutRoomForGasReportsInsufficientBalance() {
        env.reserveAnswer = {
            NodeRes.ok(
                GasReserveResult(
                    amount = BigInteger.ZERO,
                    isAmountAdjusted = false,
                    error = GasReserveResult.Error.InsufficientBalance,
                ),
            )
        }

        val pending = prepare(SwapConfirmFixtures.swapRequest(isMax = true))

        assertEquals(ConfirmationError.InsufficientBalance, feature.confirmationError.value)
        // Still prepared, and still carrying the calldata amount: the screen shows the swap it
        // cannot afford rather than an empty sheet.
        assertEquals(TON_PAYLOAD_AMOUNT, pending.swapTransaction().amount)
    }

    /** Selling more than the wallet holds is an error even when the chain would reserve fine. */
    @Test
    fun sourceAmountOverTheBalanceReportsInsufficientBalance() {
        val pending = prepare(
            SwapConfirmFixtures.swapRequest(
                sourceAmount = BigInteger.parseString("50000000000"), // 50 TON of a 5 TON balance
            ),
        )

        assertEquals(ConfirmationError.InsufficientBalance, feature.confirmationError.value)
        assertNotNull(pending.quote)
    }

    /** A token sell pays its gas in the chain's coin, not in the token. */
    @Test
    fun tokenSourceReservesGasOnTheChainCoin() {
        env.quoteAnswer = { SwapConfirmFixtures.quote(TOKEN_ETH) }
        env.prepareAnswer = { SwapConfirmFixtures.prepare(TOKEN_ETH) }

        prepare(tokenEthRequest())

        val call = env.reserveCalls.last()
        assertEquals(SwapConfirmFixtures.USDT_ETH_ASSET_ID, call.account.asset.id)
        assertEquals(SwapConfirmFixtures.ETH_ASSET_ID, call.energy.asset.id)
    }

    /**
     * An EVM route that needs an approve: the approve is its own transaction ahead of the swap, each
     * signed with its own fee, and the screen shows what the two cost together.
     */
    @Test
    fun anApproveGoesAheadOfTheSwap() {
        env.prepareAnswer = { SwapConfirmFixtures.prepare(BERRY_APPROVAL) }

        val pending = prepare(
            SwapConfirmFixtures.swapRequest(
                assetId = SwapConfirmFixtures.BERRY_ASSET_ID,
                destinationAssetId = SwapConfirmFixtures.ETH_ASSET_ID,
                sourceAmount = SwapConfirmFixtures.BERRY_SOURCE_AMOUNT,
                quote = SwapConfirmFixtures.berrySnapshot,
            ),
        )

        assertEquals(listOf(SwapConfirmFixtures.berrySnapshot.routeId), env.prepareRequests.toList())
        val prerequisite = pending.prerequisites.single()
        val approve = prerequisite.tx as Transaction.Call
        assertEquals(SwapConfirmFixtures.BERRY_ASSET_ID, approve.account.asset.id)
        assertTrue(approve.contract.display.equals(BERRY_CONTRACT, ignoreCase = true))
        assertTrue(approve.data.startsWith(APPROVE_SELECTOR))
        assertEquals(BigInteger.ZERO, approve.amount)

        val swap = pending.swapTransaction()
        assertTrue(swap.to.display.equals(ROUTER, ignoreCase = true))
        assertNotNull(swap.data)
        assertEquals(approve.account, swap.account)

        val swapFee = checkNotNull(pending.signingFee)
        assertEquals(swapFee.amount + prerequisite.fee.amount, pending.fee.estimated?.amount)
        assertEquals(pending.fee.estimated, env.reserveCalls.last().fee)
    }

    /** Without an approve the provider's own price for the swap is used as is. */
    @Test
    fun aRouteWithoutApproveKeepsTheProviderFee() {
        env.quoteAnswer = { SwapConfirmFixtures.quote(TOKEN_ETH) }
        env.prepareAnswer = { SwapConfirmFixtures.prepare(TOKEN_ETH) }

        val pending = prepare(tokenEthRequest())

        assertTrue(pending.prerequisites.isEmpty())
    }

    /** Changing slippage here re-quotes: the snapshot was priced at the old value. */
    @Test
    fun slippageChangeRefetchesTheQuote() {
        val snapshot = SwapConfirmFixtures.snapshot(EXACT_TON)
        feature = env.feature(SwapConfirmFixtures.swapRequest(quote = snapshot))
        val pending = env.collectPending(feature)
        scheduler.runCurrent()
        assertNotNull(pending.lastOrNull())
        assertTrue(env.quoteRequests.isEmpty())

        feature.selectSlippage(500)
        scheduler.runCurrent()

        val request = env.quoteRequests.single()
        assertEquals(500, request.slippageBps)
        assertEquals(SwapConfirmFixtures.TON_SOURCE_AMOUNT.toString(), request.sourceAmount)
    }

    /** Both the first attempt and the retry failing is a network error, not a blank screen. */
    @Test
    fun routeFailureReportsNetworkError() {
        env.quoteAnswer = { throw IOException("offline") }

        feature = env.feature(SwapConfirmFixtures.swapRequest())
        val pending = env.collectPending(feature)
        scheduler.runCurrent()

        assertEquals(ConfirmationError.NetworkError, feature.confirmationError.value)
        assertEquals(2, env.quoteRequests.size)
        assertTrue(pending.all { it == null })
    }

    /**
     * The shipped app asks as `Tonkeeper/26.09.x`, and for a TON source the backend answers with a
     * BOC payload keyed `address`/`amount` and no deposit address. The pinned chainkit used to read
     * only `to`/`value` for TVM and failed to prepare it; the current one reads both spellings.
     */
    @Test
    fun tonBocPayloadOfTheShippedClientPrepares() {
        env.quoteAnswer = { SwapConfirmFixtures.quote(TON_BOC) }
        env.prepareAnswer = { SwapConfirmFixtures.prepare(TON_BOC) }

        val pending = prepare(SwapConfirmFixtures.swapRequest())

        assertNull(feature.confirmationError.value)
        assertNotNull(pending.swapTransaction().data)
    }

    /**
     * Omniston splits a TON swap across pools: one `ton_boc` payload of four messages, each to its
     * own pool. All of them are signed, in order, as one batch — the first alone would swap only its
     * part of the amount — and the balance is checked against what they send together.
     */
    @Test
    fun splitTonRouteSignsEveryMessage() {
        env.accounts[SwapConfirmFixtures.TON_ASSET_ID] = tonAccount(balance = "200000000000")
        env.quoteAnswer = { SwapConfirmFixtures.quote(OMNISTON_SPLIT) }

        val pending = prepare(splitRequest(SPLIT_SOURCE_AMOUNT))

        assertTrue(env.prepareRequests.isEmpty())
        val payload = SwapConfirmFixtures.quote(OMNISTON_SPLIT).routes.single().payloads.orEmpty().single()
        assertSignsEveryMessage(pending, payload, sent = SPLIT_SENT_AMOUNT)
        assertNull(feature.confirmationError.value)
    }

    /** The same split, quoted without payloads and built by prepare. */
    @Test
    fun preparedSplitTonRouteSignsEveryMessage() {
        env.accounts[SwapConfirmFixtures.TON_ASSET_ID] = tonAccount(balance = "200000000000")
        env.quoteAnswer = { SwapConfirmFixtures.quote(OMNISTON_SPLIT_PREPARE) }
        env.prepareAnswer = { SwapConfirmFixtures.prepare(OMNISTON_SPLIT) }

        val pending = prepare(splitRequest(PREPARED_SPLIT_SOURCE_AMOUNT))

        assertEquals(1, env.prepareRequests.size)
        val payload = SwapConfirmFixtures.prepare(OMNISTON_SPLIT).payloads.single()
        assertSignsEveryMessage(pending, payload, sent = PREPARED_SPLIT_SENT_AMOUNT)
        assertNull(feature.confirmationError.value)
    }

    /**
     * The forward gas the parts carry leaves the balance too: 10 TON typed sends 11.14, so a wallet
     * holding 10.5 is short on balance. A charged battery pays the fee, not that, so it does not
     * make the swap affordable and the dialog does not offer it.
     */
    @Test
    fun splitTonRouteShortOnForwardGasReportsInsufficientBalance() {
        env.accounts[SwapConfirmFixtures.TON_ASSET_ID] = tonAccount(balance = "10500000000")
        env.quoteAnswer = { SwapConfirmFixtures.quote(OMNISTON_SPLIT) }
        env.feeOptions = listOf(chargedBattery())

        val pending = prepare(splitRequest(SPLIT_SOURCE_AMOUNT))

        assertTrue(pending.fee.hasBatteryOption)
        assertEquals(ConfirmationError.InsufficientBalance, feature.confirmationError.value)
        assertEquals(SPLIT_SENT_AMOUNT, pending.spentAmount)
        assertFalse("the battery cannot pay what the messages send", pending.isShortOnFeeOnly())
    }

    /**
     * A balance that covers every message leaves only the fee to find, which a battery the relayer
     * priced pays once recharged.
     */
    @Test
    fun splitTonRouteCoveredByTheBalanceIsShortOnTheFeeOnly() {
        env.accounts[SwapConfirmFixtures.TON_ASSET_ID] = tonAccount(balance = SPLIT_SENT_AMOUNT.toString())
        env.quoteAnswer = { SwapConfirmFixtures.quote(OMNISTON_SPLIT) }
        env.feeAnswer = { ChainRes.ok(Fee.Value(it.account.chain.toAsset(), GAS)) }
        env.feeOptions = listOf(emptyBattery())

        val pending = prepare(splitRequest(SPLIT_SOURCE_AMOUNT))

        assertEquals(
            "nothing can pay, so the shortage is shown though the battery is selected",
            ConfirmationError.InsufficientBalance,
            feature.confirmationError.value,
        )
        assertTrue(pending.isShortOnFeeOnly())
    }

    /**
     * Without a battery the relayer priced — its estimate failed, or it does not pay this swap —
     * recharging it would not help, so the dialog does not offer it whatever the charges.
     */
    @Test
    fun aShortFeeWithoutAPricedBatteryIsNotOfferedARecharge() {
        env.accounts[SwapConfirmFixtures.TON_ASSET_ID] = tonAccount(balance = SPLIT_SENT_AMOUNT.toString())
        env.quoteAnswer = { SwapConfirmFixtures.quote(OMNISTON_SPLIT) }
        env.feeAnswer = { ChainRes.ok(Fee.Value(it.account.chain.toAsset(), GAS)) }

        val pending = prepare(splitRequest(SPLIT_SOURCE_AMOUNT))

        assertFalse(pending.isShortOnFeeOnly())
    }

    /** A battery the charges already cover is not the shortage: recharging it changes nothing. */
    @Test
    fun aChargedBatteryIsNotOfferedARecharge() {
        env.accounts[SwapConfirmFixtures.TON_ASSET_ID] = tonAccount(balance = SPLIT_SENT_AMOUNT.toString())
        env.quoteAnswer = { SwapConfirmFixtures.quote(OMNISTON_SPLIT) }
        env.feeAnswer = { ChainRes.ok(Fee.Value(it.account.chain.toAsset(), GAS)) }
        env.feeOptions = listOf(chargedBattery())

        val pending = prepare(splitRequest(SPLIT_SOURCE_AMOUNT))

        assertNull("the battery pays, so nothing is short", feature.confirmationError.value)
        assertFalse(pending.isShortOnFeeOnly())
    }

    // ---- harness -----------------------------------------------------------------------------

    /**
     * A legacy wallet the backend never registered prepares on its own accounts and asks the swap
     * API without a wallet id.
     */
    @Test
    fun legacyWalletPreparesOnItsOwnAccountsWithoutAWalletId() {
        val legacyTon = SwapConfirmFixtures.tonAccount.copy(balance = AccountBalanceEntity(available = "7000000000"))
        env.legacyWallet = LegacySwapWallet(
            id = SwapConfirmFixtures.WALLET_ID,
            accountId = "0:legacy",
            name = "legacy_wallet",
            emoji = "",
            tonAddress = SwapConfirmFixtures.tonAccount.data.displayAddress,
            tonPublicKey = "0x00",
            tonAddressType = Address.Type.TonV5R1,
            tronAddress = "TPu5KzZ1dyKx22gyUiwVNAXUk2r6vRbRRP",
        )
        env.legacyAccountsByAsset[SwapConfirmFixtures.TON_ASSET_ID] = legacyTon
        env.legacyAccountsByAsset[SwapConfirmFixtures.USDT_TON_ASSET_ID] = SwapConfirmFixtures.usdtTonAccount

        val pending = prepare(SwapConfirmFixtures.swapRequest())

        assertEquals("7000000000", pending.account.balance.available)
        assertEquals(SwapConfirmFixtures.WALLET_ID, pending.wallet.id)
        assertTrue(env.requestWalletIds.isNotEmpty())
        assertTrue(env.requestWalletIds.all { it == null })
    }

    /** A dismissed passcode leaves the legacy keys unread and sends nothing. */
    @Test
    fun legacyWalletWithThePasscodeDismissedReadsNoKeys() {
        env.passcodeConfirmed = false
        val pending = prepareLegacy()

        feature.sendTransaction(context, pending)
        scheduler.runCurrent()

        assertTrue(env.legacyKeyReads.isEmpty())
        assertTrue(env.sentWalletIds.isEmpty())
    }

    /**
     * A legacy swap goes out with no wallet id, since the backend has none to track it under, and
     * refreshes the legacy balances rather than the multichain ones.
     */
    @Test
    fun legacyWalletSendsWithoutAWalletIdAndRefreshesItsOwnBalances() {
        val pending = prepareLegacy()

        feature.sendTransaction(context, pending)
        scheduler.runCurrent()

        assertEquals(listOf(SwapConfirmFixtures.WALLET_ID), env.legacyKeyReads.toList())
        assertEquals(listOf<String?>(null), env.sentWalletIds.toList())
        assertEquals(listOf(SwapConfirmFixtures.WALLET_ID), env.legacySendsNotified.toList())
    }

    @Test
    fun multichainWalletAsksWithItsWalletId() {
        prepare(SwapConfirmFixtures.swapRequest())

        assertTrue(env.requestWalletIds.isNotEmpty())
        assertTrue(env.requestWalletIds.all { it == SwapConfirmFixtures.WALLET_ID })
    }

    private fun prepareLegacy(): PendingTransaction {
        env.legacyWallet = LegacySwapWallet(
            id = SwapConfirmFixtures.WALLET_ID,
            accountId = "0:legacy",
            name = "legacy_wallet",
            emoji = "",
            tonAddress = SwapConfirmFixtures.tonAccount.data.displayAddress,
            tonPublicKey = "0x00",
            tonAddressType = Address.Type.TonV5R1,
            tronAddress = "TPu5KzZ1dyKx22gyUiwVNAXUk2r6vRbRRP",
        )
        env.legacyAccountsByAsset[SwapConfirmFixtures.TON_ASSET_ID] = SwapConfirmFixtures.tonAccount
        env.legacyAccountsByAsset[SwapConfirmFixtures.USDT_TON_ASSET_ID] = SwapConfirmFixtures.usdtTonAccount
        return prepare(SwapConfirmFixtures.swapRequest())
    }

    private fun prepare(request: ConfirmRequest): PendingTransaction {
        feature = env.feature(request)
        val collected = env.collectPending(feature)
        scheduler.runCurrent()
        return requireNotNull(collected.lastOrNull { it != null }) {
            "nothing was prepared; error: ${feature.confirmationError.value}"
        }
    }

    private fun tokenEthRequest(quote: com.tonapps.blockchain.model.ConfirmType.Swap.Quote? = null) =
        SwapConfirmFixtures.swapRequest(
            assetId = SwapConfirmFixtures.USDT_ETH_ASSET_ID,
            destinationAssetId = SwapConfirmFixtures.ETH_ASSET_ID,
            sourceAmount = SwapConfirmFixtures.USDT_SOURCE_AMOUNT,
            quote = quote,
        )

    private fun tonAccount(balance: String) = testAccount(TestAssets.ton, balance = balance, price = "1.4")

    private fun splitRequest(sourceAmount: BigInteger) = SwapConfirmFixtures.swapRequest(
        destinationAssetId = SwapConfirmFixtures.HMSTR_ASSET_ID,
        sourceAmount = sourceAmount,
    )

    private fun chargedBattery() = TxFee(
        account = FeeAccount.Keeper(balance = BigInteger.fromInt(1000), rate = null),
        fee = Fee.Value(Asset.Battery(Chain.Ton.Mainnet), BigInteger.fromInt(10)),
    )

    private fun emptyBattery() = chargedBattery().copy(
        account = FeeAccount.Keeper(balance = BigInteger.ZERO, rate = null),
    )

    private fun assertSignsEveryMessage(pending: PendingTransaction, payload: CrossSwapPayload, sent: BigInteger) {
        val envelope = Json.parseToJsonElement(payload.payload).jsonArray.map { it.jsonObject }
        val signing = pending.signing as Signing.Tx
        val batch = signing.batchToSign.orEmpty().map { it as Transaction.Swap }

        assertEquals(4, envelope.size)
        assertEquals(envelope.size, batch.size)
        envelope.zip(batch).forEachIndexed { index, (message, tx) ->
            assertEquals("to #$index", message.getValue("address").jsonPrimitive.content, tx.to.display)
            assertEquals("amount #$index", BigInteger.parseString(message.getValue("amount").jsonPrimitive.content), tx.amount)
            assertEquals("body #$index", message.getValue("payload").jsonPrimitive.content, tx.data)
        }
        assertEquals(sent, batch.fold(BigInteger.ZERO) { total, tx -> total + tx.amount })
        assertEquals(sent, env.reserveCalls.last().amount)
        assertEquals(GasReservePolicy.DrainOrError, env.reserveCalls.last().policy)
    }

    private fun CrossSwapPrepare.withPreparedAmounts(receive: BigInteger?, minimum: BigInteger?) =
        withPreparedSummary(receive?.toString(), minimum?.toString())

    private fun CrossSwapPrepare.withPreparedSummary(receive: String?, minimum: String?) = copy(
        payloads = payloads.withPreparedSummary(receive, minimum),
    )

    private fun CrossSwapQuote.withPreparedAmounts(receive: BigInteger?, minimum: BigInteger?) = copy(
        routes = routes.map { route ->
            route.copy(payloads = route.payloads?.withPreparedSummary(receive?.toString(), minimum?.toString()))
        },
    )

    private fun List<CrossSwapPayload>.withPreparedSummary(receive: String?, minimum: String?) = map { payload ->
        val summary = payload.humanSummary.copy(
            receiveAmount = receive,
            minimumReceiveAmount = minimum,
        )
        payload.copy(humanSummary = summary)
    }

    private fun PendingTransaction.swapTransaction(): Transaction.Swap {
        val signing = signing
        assertTrue("expected a transaction to sign", signing is Signing.Tx)
        val transaction = (signing as Signing.Tx).value
        assertTrue("expected a swap transaction", transaction is Transaction.Swap)
        return transaction as Transaction.Swap
    }

    private companion object {
        val GAS: BigInteger = BigInteger.parseString("2000000")

        // `confirm_prepare_exact_ton.json`: the deposit payload spends exactly the quoted amount.
        val TON_PAYLOAD_AMOUNT: BigInteger = BigInteger.parseString("1000000000")

        // `confirm_quote_omniston_ton_hmstr_split.json`: what is typed, and what its four messages send.
        val SPLIT_SOURCE_AMOUNT: BigInteger = BigInteger.parseString("10000000000")
        val SPLIT_SENT_AMOUNT: BigInteger = BigInteger.parseString("11140000000")

        // `confirm_prepare_omniston_ton_hmstr_split.json`.
        val PREPARED_SPLIT_SOURCE_AMOUNT: BigInteger = BigInteger.parseString("100000000000")
        val PREPARED_SPLIT_SENT_AMOUNT: BigInteger = BigInteger.parseString("101150000000")

        const val BERRY_CONTRACT = "0xcb76314c2540199f4b844d4ebbc7998c604880ca"
        const val ROUTER = "0x1b6257CAE4192e62B629eFCa21771be3D759183D"
        const val APPROVE_SELECTOR = "0x095ea7b3"
    }
}