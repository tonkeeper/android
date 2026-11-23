package com.tonapps.deposit.multicoin.screens.confirm.swap

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.deposit.multicoin.screens.confirm.SwapFeeSummary
import com.tonapps.deposit.multicoin.screens.confirm.SwapPriceChange
import com.tonapps.deposit.multicoin.screens.confirm.base.PendingTransaction
import com.tonapps.deposit.multicoin.screens.confirm.base.Signing
import com.tonapps.deposit.multicoin.screens.confirm.fee.FeeAccount
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFee
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeState
import com.tonapps.deposit.multicoin.screens.confirm.feeSummary
import com.tonapps.deposit.multicoin.screens.confirm.isRouteStale
import com.tonapps.deposit.multicoin.screens.confirm.priceChange
import com.tonapps.deposit.multicoin.screens.confirm.summarize
import com.tonapps.deposit.multicoin.screens.confirm.swapSpentAmount
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.exchange.SwapQuote
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.testAccount
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwapFeeSummaryTest : BaseUnitTest() {

    private val tonChain get() = TestAssets.ton.value.chain

    @Test
    fun aPayableBatteryCostsItsChargesLessTheReserve() {
        val tx = tx(
            account = testAccount(TestAssets.usdtTon, balance = "1000"),
            options = listOf(battery(charges = 28, balance = 100, reserve = 21)),
        )

        assertEquals(SwapFeeSummary.Charges(7), summarize(tx, pickedId = null, amountBlocked = false))
    }

    @Test
    fun aBatteryWithoutAReserveCostsAllItsCharges() {
        val tx = tx(
            account = testAccount(TestAssets.usdtTon, balance = "1000"),
            options = listOf(battery(charges = 7, balance = 100)),
        )

        assertEquals(SwapFeeSummary.Charges(7), summarize(tx, pickedId = null, amountBlocked = false))
    }

    @Test
    fun aPayableCoinShowsItsAmount() {
        val ton = testAccount(TestAssets.ton, balance = "1000000000")
        val tx = tx(
            account = testAccount(TestAssets.usdtTon, balance = "1000"),
            energy = ton,
            options = listOf(battery(charges = 7, balance = 0), coin(ton, amount = 30_000_000)),
        )

        assertEquals(amount(30_000_000), summarize(tx, pickedId = null, amountBlocked = false))
    }

    @Test
    fun anEmptyBatteryWithNothingElseIsBatteryEmpty() {
        val tx = tx(
            account = testAccount(TestAssets.usdtTon, balance = "1000"),
            energy = testAccount(TestAssets.ton),
            estimated = tonFee(30_000_000),
            options = listOf(battery(charges = 7, balance = 0)),
        )

        assertEquals(SwapFeeSummary.BatteryEmpty, summarize(tx, pickedId = null, amountBlocked = false))
    }

    @Test
    fun aShortBatteryWithNothingElseIsLowBattery() {
        val tx = tx(
            account = testAccount(TestAssets.usdtTon, balance = "1000"),
            energy = testAccount(TestAssets.ton),
            estimated = tonFee(30_000_000),
            options = listOf(battery(charges = 7, balance = 3)),
        )

        assertEquals(SwapFeeSummary.LowBattery, summarize(tx, pickedId = null, amountBlocked = false))
    }

    @Test
    fun aCoinThatCannotPayWithNoBatteryIsNoFunds() {
        val tx = tx(
            account = testAccount(TestAssets.usdtTon, balance = "1000"),
            energy = testAccount(TestAssets.ton, balance = "1000"),
            estimated = tonFee(30_000_000),
        )

        assertEquals(SwapFeeSummary.NoFunds("TON"), summarize(tx, pickedId = null, amountBlocked = false))
    }

    @Test
    fun aCoinThatPaysWithNoBatteryShowsTheEstimate() {
        val tx = tx(
            account = testAccount(TestAssets.usdtTon, balance = "1000"),
            energy = testAccount(TestAssets.ton, balance = "1000000000"),
            estimated = tonFee(30_000_000),
        )

        assertEquals(amount(30_000_000), summarize(tx, pickedId = null, amountBlocked = false))
    }

    @Test
    fun theCoinSoldMustCoverTheAmountAndTheFee() {
        val ton = testAccount(TestAssets.ton, balance = "1000000000")
        val tx = tx(account = ton, energy = ton, estimated = tonFee(30_000_000), spent = BigInteger.fromInt(990_000_000))

        assertEquals(SwapFeeSummary.NoFunds("TON"), summarize(tx, pickedId = null, amountBlocked = false))
    }

    @Test
    fun aCoinShortOfAmountPlusFeeWithNoOptionsIsNoFunds() {
        val ton = testAccount(TestAssets.ton, balance = "1000000000")
        val tx = tx(
            account = ton,
            energy = ton,
            estimated = tonFee(30_000_000),
            signing = Signing.Tx(swapTx(amount = 990_000_000)),
        )

        assertNull(tx.nonce)
        assertEquals(SwapFeeSummary.NoFunds("TON"), summarize(tx, pickedId = null, amountBlocked = false))
    }

    @Test
    fun anAmountAboveTheBalanceIsNotAFeeShortageOfTheCoinSold() {
        val ton = testAccount(TestAssets.ton, balance = "0")
        val tx = tx(account = ton, energy = ton, estimated = tonFee(30_000_000), spent = BigInteger.fromInt(990_000_000))

        assertEquals(amount(30_000_000), summarize(tx, pickedId = null, amountBlocked = true))
    }

    @Test
    fun anAmountAboveTheBalanceShowsTheCoinEstimateOverAnEmptyBattery() {
        val ton = testAccount(TestAssets.ton, balance = "0")
        val tx = tx(
            account = ton,
            energy = ton,
            estimated = tonFee(30_000_000),
            options = listOf(battery(charges = 7, balance = 0)),
        )

        assertEquals(amount(30_000_000), summarize(tx, pickedId = null, amountBlocked = true))
    }

    @Test
    fun aBlockedAmountOfATokenStillNamesTheCoinShortage() {
        val tx = tx(
            account = testAccount(TestAssets.usdtTon, balance = "0"),
            energy = testAccount(TestAssets.ton, balance = "0"),
            estimated = tonFee(30_000_000),
        )

        assertEquals(SwapFeeSummary.NoFunds("TON"), summarize(tx, pickedId = null, amountBlocked = true))
    }

    @Test
    fun theCoinSoldNearItsWholeBalanceCannotAlsoPayTheFee() {
        val ton = testAccount(TestAssets.ton, balance = "1000000000")
        val tx = tx(
            account = ton,
            energy = ton,
            estimated = tonFee(30_000_000),
            options = listOf(coin(ton, amount = 30_000_000), battery(charges = 7, balance = 100)),
            spent = BigInteger.fromInt(990_000_000),
        )

        assertEquals(SwapFeeSummary.NoFunds("TON"), summarize(tx, pickedId = TestAssets.ton.value.id, amountBlocked = false))
    }

    @Test
    fun aMaxSwapPaidInTheCoinSoldSpendsWhatTheWalletSigns() {
        val ton = testAccount(TestAssets.ton, balance = "1000000000")
        val tx = tx(
            account = ton,
            energy = ton,
            estimated = tonFee(30_000_000),
            options = listOf(battery(charges = 7, balance = 100), coin(ton, amount = 30_000_000)),
            signing = Signing.Tx(
                value = swapTx(amount = 1_000_000_000),
                walletPaid = swapTx(amount = 970_000_000),
            ),
        )

        assertEquals(amount(30_000_000), summarize(tx, pickedId = TestAssets.ton.value.id, amountBlocked = false))
    }

    @Test
    fun aBatteryStillPaysWhenTheCoinSoldTakesTheWholeBalance() {
        val ton = testAccount(TestAssets.ton, balance = "1000000000")
        val tx = tx(
            account = ton,
            energy = ton,
            estimated = tonFee(30_000_000),
            options = listOf(battery(charges = 7, balance = 100), coin(ton, amount = 30_000_000)),
            spent = BigInteger.fromInt(990_000_000),
        )

        assertEquals(SwapFeeSummary.Charges(7), summarize(tx, pickedId = null, amountBlocked = false))
    }

    @Test
    fun aBalanceErrorOutranksAPayableSummary() {
        val noFunds = SwapFeeSummary.NoFunds("TON")

        assertEquals(noFunds, feeSummary(SwapFeeSummary.Charges(7), noFunds, sendShortage = false, balanceShort = true))
        assertEquals(noFunds, feeSummary(SwapFeeSummary.Amount("1 TON"), noFunds, sendShortage = true, balanceShort = false))
        assertEquals(
            SwapFeeSummary.BatteryEmpty,
            feeSummary(SwapFeeSummary.BatteryEmpty, noFunds, sendShortage = false, balanceShort = true),
        )
        assertEquals(
            SwapFeeSummary.Charges(7),
            feeSummary(SwapFeeSummary.Charges(7), noFunds, sendShortage = false, balanceShort = false),
        )
    }

    @Test
    fun aRouteIsStaleFiveSecondsBeforeItsLifetimeEnds() {
        assertFalse(isRouteStale(elapsedNowMillis = 1_054_999, receivedAt = 1_000_000, lifetimeMillis = 60_000, quoteAgeMillis = 0))
        assertTrue(isRouteStale(elapsedNowMillis = 1_055_000, receivedAt = 1_000_000, lifetimeMillis = 60_000, quoteAgeMillis = 0))
    }

    @Test
    fun aClockBehindTheServerCannotStretchARoutePastAMinute() {
        assertFalse(isRouteStale(elapsedNowMillis = 1_059_999, receivedAt = 1_000_000, lifetimeMillis = 70_000, quoteAgeMillis = 0))
        assertTrue(isRouteStale(elapsedNowMillis = 1_060_000, receivedAt = 1_000_000, lifetimeMillis = 70_000, quoteAgeMillis = 0))
    }

    @Test
    fun aSevenSecondRouteStillLeavesFiveSeconds() {
        assertFalse(isRouteStale(elapsedNowMillis = 1_004_999, receivedAt = 1_000_000, lifetimeMillis = 7_000, quoteAgeMillis = 0))
        assertTrue(isRouteStale(elapsedNowMillis = 1_005_000, receivedAt = 1_000_000, lifetimeMillis = 7_000, quoteAgeMillis = 0))
    }

    @Test
    fun aRouteWithoutALifetimeIsStaleAfterAMinute() {
        assertFalse(isRouteStale(elapsedNowMillis = 1_059_999, receivedAt = 1_000_000, lifetimeMillis = null, quoteAgeMillis = 0))
        assertTrue(isRouteStale(elapsedNowMillis = 1_060_000, receivedAt = 1_000_000, lifetimeMillis = null, quoteAgeMillis = 0))
    }

    @Test
    fun aClockAheadOfTheServerStillLeavesFiveSeconds() {
        assertFalse(isRouteStale(elapsedNowMillis = 1_004_999, receivedAt = 1_000_000, lifetimeMillis = 5_000, quoteAgeMillis = 0))
        assertTrue(isRouteStale(elapsedNowMillis = 1_005_000, receivedAt = 1_000_000, lifetimeMillis = 5_000, quoteAgeMillis = 0))
        assertFalse(isRouteStale(elapsedNowMillis = 1_004_999, receivedAt = 1_000_000, lifetimeMillis = -30_000, quoteAgeMillis = 0))
        assertTrue(isRouteStale(elapsedNowMillis = 1_005_000, receivedAt = 1_000_000, lifetimeMillis = -30_000, quoteAgeMillis = 0))
    }

    @Test
    fun aRouteWithoutAnArrivalTimeAgesFromTheQuote() {
        assertFalse(isRouteStale(elapsedNowMillis = 0, receivedAt = null, lifetimeMillis = 30_000, quoteAgeMillis = 24_999))
        assertTrue(isRouteStale(elapsedNowMillis = 0, receivedAt = null, lifetimeMillis = 30_000, quoteAgeMillis = 25_000))
    }

    @Test
    fun anUnavailableFeeIsUnknown() {
        val tx = tx(account = testAccount(TestAssets.usdtTon, balance = "1000"))

        assertEquals(SwapFeeSummary.Unknown, summarize(tx, pickedId = null, amountBlocked = false))
    }

    @Test
    fun onlyShortagesAreUnpaid() {
        assertTrue(SwapFeeSummary.BatteryEmpty.isUnpaid)
        assertTrue(SwapFeeSummary.LowBattery.isUnpaid)
        assertTrue(SwapFeeSummary.NoFunds("TON").isUnpaid)
        assertEquals(false, SwapFeeSummary.Charges(1).isUnpaid)
        assertEquals(false, SwapFeeSummary.Amount("1 TON").isUnpaid)
        assertEquals(false, SwapFeeSummary.Unknown.isUnpaid)
    }

    @Test
    fun theSameRouteAtTheQuotedAmountsIsNoPriceChange() {
        assertNull(priceChange(snapshot(buy = 100), BigInteger.fromInt(10), quote(route = "a", source = 10, buy = 100)))
    }

    @Test
    fun theSameRoutePreparedToReceiveLessIsAPriceChange() {
        val change = priceChange(snapshot(buy = 100), BigInteger.fromInt(10), quote(route = "a", source = 10, buy = 90))

        assertEquals(
            SwapPriceChange(SwapPriceChange.Side.Receive, previous = BigInteger.fromInt(100), current = BigInteger.fromInt(90)),
            change,
        )
    }

    @Test
    fun aNewRouteReceivingLessIsAPriceChange() {
        val change = priceChange(snapshot(buy = 100), BigInteger.fromInt(10), quote(route = "b", source = 10, buy = 90))

        assertEquals(
            SwapPriceChange(SwapPriceChange.Side.Receive, previous = BigInteger.fromInt(100), current = BigInteger.fromInt(90)),
            change,
        )
    }

    @Test
    fun aNewRouteSendingMoreIsAPriceChange() {
        val change = priceChange(snapshot(buy = 100), BigInteger.fromInt(10), quote(route = "b", source = 12, buy = 100))

        assertEquals(
            SwapPriceChange(SwapPriceChange.Side.Send, previous = BigInteger.fromInt(10), current = BigInteger.fromInt(12)),
            change,
        )
    }

    @Test
    fun aNewRouteThatIsNoWorseIsNoPriceChange() {
        assertNull(priceChange(snapshot(buy = 100), BigInteger.fromInt(10), quote(route = "b", source = 10, buy = 101)))
    }

    @Test
    fun aSoldTokenSpendsItsSourceAmountNotTheAttachedCoin() {
        val spent = swapSpentAmount(
            sellsCoin = false,
            messagesAmount = BigInteger.fromInt(330_000_000),
            sourceAmount = BigInteger.fromInt(1_000_000),
        )

        assertEquals(BigInteger.fromInt(1_000_000), spent)
    }

    @Test
    fun aSoldCoinSpendsItsMessagesWhenTheyExceedTheSourceAmount() {
        val spent = swapSpentAmount(
            sellsCoin = true,
            messagesAmount = BigInteger.fromInt(1_050_000_000),
            sourceAmount = BigInteger.fromInt(1_000_000_000),
        )

        assertEquals(BigInteger.fromInt(1_050_000_000), spent)
    }

    @Test
    fun aSoldCoinWhoseMessagesStayWithinTheSourceAmountHasNoSeparateSpend() {
        val spent = swapSpentAmount(
            sellsCoin = true,
            messagesAmount = BigInteger.fromInt(1_000_000_000),
            sourceAmount = BigInteger.fromInt(1_000_000_000),
        )

        assertNull(spent)
    }

    private fun tx(
        account: AccountWithDetails,
        energy: AccountWithDetails? = null,
        estimated: Fee? = null,
        options: List<TxFee> = emptyList(),
        spent: BigInteger? = null,
        signing: Signing = mockk<Signing.Tx>(relaxed = true),
    ) = PendingTransaction(
        request = mockk(relaxed = true),
        signing = signing,
        wallet = mockk(relaxed = true),
        account = account,
        fee = TxFeeState(energy = energy, estimated = estimated, options = options),
        nonce = null,
        spentAmount = spent,
    )

    private fun swapTx(amount: Int): Transaction = mockk<Transaction.Swap> {
        every { this@mockk.amount } returns BigInteger.fromInt(amount)
    }

    private fun tonFee(amount: Int): Fee = Fee.Value(tonChain.toAsset(), BigInteger.fromInt(amount))

    private fun amount(units: Int): SwapFeeSummary.Amount {
        val asset = tonChain.toAsset()
        return SwapFeeSummary.Amount(
            Formatter.formatShort(value = asset.decimals.toDisplayUnit(BigInteger.fromInt(units)), asset = asset)
        )
    }

    private fun coin(account: AccountWithDetails, amount: Int): TxFee =
        TxFee(FeeAccount.Chain(account), Fee.Value(account.asset.value, BigInteger.fromInt(amount)))

    private fun battery(charges: Int, balance: Int, reserve: Int? = null): TxFee = TxFee(
        account = FeeAccount.Keeper(balance = BigInteger.fromInt(balance), rate = null),
        fee = Fee.Value(Asset.Battery(tonChain), BigInteger.fromInt(charges)),
        reserve = reserve?.let { BigInteger.fromInt(it) },
    )

    private fun snapshot(buy: Int) = ConfirmType.Swap.Quote(
        routeId = "a",
        buyAmount = BigInteger.fromInt(buy),
        minBuyAmount = BigInteger.fromInt(buy),
        provider = SwapQuote.Provider.Omniston.id,
    )

    private fun quote(route: String, source: Int, buy: Int) = SwapQuote(
        routeId = route,
        sourceBaseAmount = BigInteger.fromInt(source),
        buyBaseAmount = BigInteger.fromInt(buy),
        minimumBuyBaseAmount = BigInteger.fromInt(buy),
        slippageBps = null,
        priceImpactBps = null,
        provider = SwapQuote.Provider.Omniston,
    )
}
