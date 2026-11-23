package com.tonapps.perps.funding

import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.bus.generated.Events.Perps.PerpsSourceScreen
import com.tonapps.chainkit.core.chain.model.account.MnemonicWallet
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualConfirmError
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualConfirmException
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualReview
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.OTHER_LIGHTER_ACCOUNT
import com.tonapps.perps.data.PerpsLoadException
import com.tonapps.perps.data.PerpsSendReason
import com.tonapps.perps.data.formatTokenAmount
import com.tonapps.perps.data.formatUsdAmount
import com.tonapps.wallet.data.multichain.lighter.PerpetualDepositProvider
import com.tonapps.wallet.data.multichain.lighter.PerpetualDepositQuote
import com.tonapps.wallet.data.multichain.perps.PerpsDepositRoute
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.testAccount
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.infrastructure.ClientError
import io.infrastructure.ClientException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

/**
 * What the shared confirm screen gets from perps: the request each move opens it with, what a
 * withdrawal review shows, and which confirm error every withdrawal failure turns into.
 */
class PerpsConfirmDelegateTest : BaseUnitTest() {

    private val withdrawService = mockk<PerpsWithdrawService>()
    private val wallet = mockk<MnemonicWallet>()
    private val delegate = PerpsConfirmDelegate(withdrawService)

    private val withdrawRequest: ConfirmRequest =
        PerpsConfirmRequests.withdraw(WALLET_ID, WITHDRAW_UNITS, PerpsFundingAssetId.USDC_ETHEREUM)
    private val withdraw: ConfirmType.PerpetualWithdraw
        get() = withdrawRequest.type as ConfirmType.PerpetualWithdraw
    private val usdtFunding get() = PerpsFundingAssetId.parse(TestAssets.usdtEth.id) ?: error("USDT cannot fund")
    private val usdcFunding get() = PerpsFundingAssetId.parse(PerpsFundingAssetId.USDC_ETHEREUM) ?: error("USDC cannot fund")

    @Test
    fun aDepositRequestCarriesTheQuotedOperation() {
        val quote = depositQuote(PerpsDepositRoute.DIRECT_GATEWAY)

        val request = PerpsConfirmRequests.deposit(WALLET_ID, TestAssets.usdtEth.id, DEPOSIT_UNITS, BENEFICIARY, quote, PerpsSourceScreen.PerpsHome, SERVER_NOW)

        assertEquals(TestAssets.usdtEth.id, request.data.assetId)
        assertEquals(WALLET_ID, request.data.walletId)
        val deposit = request.type as ConfirmType.PerpetualDeposit
        assertSame(quote.operation, deposit.operation)
        assertEquals(DEPOSIT_UNITS.toString(), deposit.amount.toString())
        // TK-4608: a minute to sign on the server's clock from when it is quoted, not the quote's own deadline.
        assertEquals(SERVER_NOW + MINUTE_MS, request.data.expiresAt)
        // A direct deposit is credited one to one: there is no minimum to show.
        assertNull(deposit.minReceive)
        assertNull(deposit.estimatedDurationSeconds)
    }

    @Test
    fun aDepositRequestCarriesTheAmountEnteredNotTheQuotesEchoOfIt() {
        val quote = depositQuote(PerpsDepositRoute.DIRECT_GATEWAY).copy(sourceAmount = (DEPOSIT_UNITS * 2.toBigInteger()).toChain())

        val deposit = PerpsConfirmRequests.deposit(WALLET_ID, TestAssets.usdtEth.id, DEPOSIT_UNITS, BENEFICIARY, quote, PerpsSourceScreen.PerpsHome, SERVER_NOW).type
            as ConfirmType.PerpetualDeposit

        assertEquals(DEPOSIT_UNITS.toString(), deposit.amount.toString())
    }

    @Test
    fun aDepositRequestCarriesTheQuotedArrivalEstimate() {
        val quote = depositQuote(PerpsDepositRoute.CROSSCHAIN_SWAP, estimatedDurationSeconds = 115L)

        val deposit = PerpsConfirmRequests.deposit(WALLET_ID, TestAssets.usdtEth.id, DEPOSIT_UNITS, BENEFICIARY, quote, PerpsSourceScreen.PerpsHome, SERVER_NOW).type
            as ConfirmType.PerpetualDeposit

        assertEquals(115L, deposit.estimatedDurationSeconds)
    }

    @Test
    fun aCrosschainDepositRequestCarriesTheMinimumCredited() {
        val quote = depositQuote(PerpsDepositRoute.CROSSCHAIN_SWAP)

        val deposit = PerpsConfirmRequests.deposit(WALLET_ID, TestAssets.usdtEth.id, DEPOSIT_UNITS, BENEFICIARY, quote, PerpsSourceScreen.PerpsHome, SERVER_NOW).type
            as ConfirmType.PerpetualDeposit

        assertEquals(CREDITED_UNITS.toString(), deposit.minReceive.toString())
    }

    @Test
    fun aWithdrawRequestCarriesTheAmountAndWhatIsReceived() {
        assertEquals(WITHDRAW_UNITS.toString(), withdraw.amount.toString())
        // The request's asset is what the user receives.
        assertEquals(PerpsFundingAssetId.USDC_ETHEREUM, withdrawRequest.data.assetId)
        assertEquals(WALLET_ID, withdrawRequest.data.walletId)
    }

    @Test
    fun aWithdrawalToUsdcOnEthereumIsQuotedAndShowsWhatArrives() = runTest {
        val usdc = PerpsDepositSource(testAccount(PerpsFundingTestEnv.USDC, balance = "0", price = "1.0"), usdcFunding)
        val plan = withdrawPlan(
            source = usdc,
            quote = withdrawQuote(outputUnits = BigInteger.valueOf(8_900_000), estimatedDurationSeconds = 10L),
            cost = PerpsWithdrawCost(usd = BigDecimal("1.10"), isEstimate = true),
        )
        coEvery { withdrawService.prepare(WALLET_ID, WITHDRAW_UNITS, PerpsFundingAssetId.USDC_ETHEREUM) } returns plan

        val review = delegate.review(withdrawRequest, withdraw)

        assertSame(plan, review.plan)
        assertEquals(QUOTE_EXPIRY, review.expiresAtMs)
        val display = review.display as PerpetualReview.Withdraw
        assertEquals("USDC", display.symbol)
        assertEquals("Ethereum", display.network)
        assertEquals(PerpsFundingTestEnv.USDC, display.image)
        assertEquals(formatUsdAmount(plan.amount), display.amount)
        assertEquals(formatTokenAmount(BigDecimal("8.9"), "USDC", PerpsFundingAmount.USDC_DECIMALS), display.receive)
        assertEquals(formatUsdAmount(BigDecimal("1.10")), display.fee)
        assertEquals(1_100, display.feeShareBps)
        assertEquals(10L, display.arrivalSeconds)
        assertEquals(usdc.account.data.displayAddress, display.recipient)
        assertEquals("Relay", display.provider)
    }

    @Test
    fun aWithdrawalIntoAnotherAssetIsQuotedAndShowsWhatArrives() = runTest {
        val usdt = PerpsDepositSource(testAccount(TestAssets.usdtEth, balance = "0", price = "1.0"), usdtFunding)
        val plan = withdrawPlan(
            source = usdt,
            assetId = TestAssets.usdtEth.id,
            quote = withdrawQuote(outputUnits = BigInteger.valueOf(9_800_000), estimatedDurationSeconds = 45L),
            cost = PerpsWithdrawCost(usd = BigDecimal("0.20"), isEstimate = true),
        )
        coEvery { withdrawService.prepare(WALLET_ID, WITHDRAW_UNITS, TestAssets.usdtEth.id) } returns plan
        val request = PerpsConfirmRequests.withdraw(WALLET_ID, WITHDRAW_UNITS, TestAssets.usdtEth.id)

        val review = delegate.review(request, request.type as ConfirmType.PerpetualWithdraw)

        assertEquals(QUOTE_EXPIRY, review.expiresAtMs)
        val display = review.display as PerpetualReview.Withdraw
        assertEquals("USDT", display.symbol)
        assertEquals("Ethereum", display.network)
        assertEquals(TestAssets.usdtEth, display.image)
        assertEquals(formatTokenAmount(BigDecimal("9.8"), "USDT", 6), display.receive)
        assertEquals(formatUsdAmount(BigDecimal("0.20")), display.fee)
        assertTrue(display.feeIsEstimate)
        assertEquals(200, display.feeShareBps)
        assertEquals(45L, display.arrivalSeconds)
    }

    @Test
    fun aBitcoinWithdrawalShowsTheQuotedEstimate() = runTest {
        val btcFunding = PerpsFundingAssetId.parse(TestAssets.btc.id) ?: error("BTC cannot fund")
        val btc = PerpsDepositSource(testAccount(TestAssets.btc, balance = "0", price = "80000"), btcFunding)
        val plan = withdrawPlan(
            source = btc,
            assetId = TestAssets.btc.id,
            quote = withdrawQuote(outputUnits = BigInteger.valueOf(2_400), estimatedDurationSeconds = 222L),
        )
        coEvery { withdrawService.prepare(WALLET_ID, WITHDRAW_UNITS, TestAssets.btc.id) } returns plan
        val request = PerpsConfirmRequests.withdraw(WALLET_ID, WITHDRAW_UNITS, TestAssets.btc.id)

        val display = delegate.review(request, request.type as ConfirmType.PerpetualWithdraw).display

        assertEquals(222L, (display as PerpetualReview.Withdraw).arrivalSeconds)
    }

    @Test
    fun aQuotedWithdrawalWithoutAnEstimateShowsNoArrival() = runTest {
        val usdt = PerpsDepositSource(testAccount(TestAssets.usdtEth, balance = "0", price = "1.0"), usdtFunding)
        val plan = withdrawPlan(
            source = usdt,
            assetId = TestAssets.usdtEth.id,
            quote = withdrawQuote(outputUnits = BigInteger.valueOf(9_800_000)),
        )
        coEvery { withdrawService.prepare(WALLET_ID, WITHDRAW_UNITS, TestAssets.usdtEth.id) } returns plan
        val request = PerpsConfirmRequests.withdraw(WALLET_ID, WITHDRAW_UNITS, TestAssets.usdtEth.id)

        val display = delegate.review(request, request.type as ConfirmType.PerpetualWithdraw).display

        assertNull((display as PerpetualReview.Withdraw).arrivalSeconds)
    }

    @Test
    fun anExactWithdrawCostIsNotAnEstimate() = runTest {
        coEvery { withdrawService.prepare(any(), any(), any()) } returns
            withdrawPlan(cost = PerpsWithdrawCost(usd = BigDecimal("1.5"), isEstimate = false))

        val display = delegate.review(withdrawRequest, withdraw).display

        assertEquals(formatUsdAmount(BigDecimal("1.5")), (display as PerpetualReview.Withdraw).fee)
        assertEquals(false, display.feeIsEstimate)
    }

    @Test
    fun submitSendsThePlanThroughTheWithdrawService() = runTest {
        val plan = withdrawPlan()
        coEvery { withdrawService.submit(plan, wallet) } returns mockk()

        delegate.submit(review(plan), wallet)

        coVerify(exactly = 1) { withdrawService.submit(plan, wallet) }
    }

    @Test
    fun anUnknownWithdrawOutcomeCountsAsSent() = runTest {
        val plan = withdrawPlan()
        coEvery { withdrawService.submit(any(), any()) } throws PerpsWithdrawException.SendUnknown()

        // Recovery settles it from the journal; the screen moves on.
        delegate.submit(review(plan), wallet)
    }

    @Test
    fun aPlanThatIsNotAWithdrawalFails() = runTest {
        try {
            delegate.submit(review("not a plan"), wallet)
            fail("expected the submit to fail")
        } catch (e: PerpetualConfirmException) {
            assertEquals(PerpetualConfirmError.Failed, e.error)
        }
    }

    @Test
    fun withdrawFailuresMapToConfirmErrors() = runTest {
        val min = BigInteger.valueOf(5_000_000)
        val cases = listOf(
            PerpsWithdrawException.WithdrawInFlight("op") to PerpetualConfirmError.InFlight,
            PerpsWithdrawException.InsufficientBalance() to PerpetualConfirmError.InsufficientBalance,
            PerpsWithdrawException.BelowMinimum(min) to PerpetualConfirmError.BelowMinimum(
                PerpsFundingAmount.declaredAmount(min, PerpsFundingAmount.USDC_DECIMALS),
            ),
            PerpsWithdrawException.AccountUnavailable() to PerpetualConfirmError.AccountUnavailable,
            PerpsWithdrawException.ActivationFailed() to PerpetualConfirmError.AccountUnavailable,
            PerpsWithdrawException.Refused(PerpsSendReason.LIGHTER_REJECTED, "21") to PerpetualConfirmError.Refused,
            PerpsWithdrawException.Refused(PerpsSendReason.WITHDRAWAL_RATE_LIMITED, "withdrawal_rate_limited") to PerpetualConfirmError.WithdrawalRateLimited,
            PerpsWithdrawException.Refused(PerpsSendReason.RATE_LIMITED, "rate_limited") to PerpetualConfirmError.TooManyRequests,
            PerpsWithdrawException.SendFailed(PerpsError.Network) to PerpetualConfirmError.ServiceUnavailable,
            PerpsWithdrawException.SendFailed(PerpsError.NoAccount) to PerpetualConfirmError.AccountUnavailable,
            PerpsWithdrawException.SendFailed(PerpsError.BadRequest) to PerpetualConfirmError.PrepareFailed,
            PerpsWithdrawException.RelayUnverified() to PerpetualConfirmError.PrepareFailed,
            PerpsWithdrawQuoteException.NotToRelay(OTHER_LIGHTER_ACCOUNT) to PerpetualConfirmError.PrepareFailed,
            PerpsWithdrawException.SigningFailed("key") to PerpetualConfirmError.Failed,
            PerpsLoadException(PerpsError.ServiceUnavailable) to PerpetualConfirmError.ServiceUnavailable,
        )
        cases.forEach { (thrown, expected) ->
            coEvery { withdrawService.prepare(any(), any(), any()) } throws thrown
            val error = reviewFailure()
            assertEquals(thrown::class.simpleName, expected, error.error)
            assertSame(thrown, error.cause)
            assertEquals(false, error.requote)
        }
    }

    @Test
    fun aWithdrawalRateLimitDuringReviewShowsItsOwnError() = runTest {
        for (body in listOf(
            """{"code":"withdrawal_rate_limited","retryable":false}""",
            """{"code":"validation_error","retryable":false,"details":{"reason":"withdrawal_rate_limited"}}""",
        )) {
            coEvery { withdrawService.prepare(any(), any(), any()) } throws ClientException(
                statusCode = 429,
                response = ClientError<Any>(body = body, statusCode = 429),
            )
            assertEquals(PerpetualConfirmError.WithdrawalRateLimited, reviewFailure().error)
        }
    }

    @Test
    fun anExpiredWithdrawQuoteIsRequoted() = runTest {
        listOf(
            PerpsWithdrawException.QuoteExpired(),
            PerpsWithdrawException.FeeChanged(quoted = BigInteger.ZERO, current = BigInteger.ONE),
            PerpsWithdrawQuoteException.Expired(),
        ).forEach { thrown ->
            coEvery { withdrawService.prepare(any(), any(), any()) } throws thrown
            val error = reviewFailure()
            assertEquals(thrown::class.simpleName, PerpetualConfirmError.QuoteExpired, error.error)
            assertTrue(thrown::class.simpleName, error.requote)
            assertNull(thrown::class.simpleName, error.requoted)
        }

        coEvery { withdrawService.prepare(any(), any(), any()) } throws PerpsWithdrawQuoteException.Overspend()
        assertEquals(PerpetualConfirmError.PrepareFailed, reviewFailure().error)
    }

    @Test
    fun aRenewedQuoteThatGivesLessComesBackAsTheReviewToConfirm() = runTest {
        val usdt = PerpsDepositSource(testAccount(TestAssets.usdtEth, balance = "0", price = "1.0"), usdtFunding)
        val renewed = withdrawPlan(
            source = usdt,
            assetId = TestAssets.usdtEth.id,
            quote = withdrawQuote(outputUnits = BigInteger.valueOf(9_700_000)),
            cost = PerpsWithdrawCost(usd = BigDecimal("0.30"), isEstimate = true),
        )
        val thrown = PerpsWithdrawException.QuoteChanged(renewed)
        coEvery { withdrawService.submit(any(), any()) } throws thrown

        val failure = try {
            delegate.submit(review(withdrawPlan()), wallet)
            throw AssertionError("expected the submit to fail")
        } catch (e: PerpetualConfirmException) {
            e
        }

        assertEquals(PerpetualConfirmError.QuoteExpired, failure.error)
        assertTrue(failure.requote)
        assertSame(thrown, failure.cause)
        val requoted = checkNotNull(failure.requoted)
        assertSame(renewed, requoted.plan)
        assertEquals(QUOTE_EXPIRY, requoted.expiresAtMs)
        val display = requoted.display as PerpetualReview.Withdraw
        assertEquals(formatTokenAmount(BigDecimal("9.7"), "USDT", 6), display.receive)
        assertEquals(formatUsdAmount(BigDecimal("0.30")), display.fee)
    }

    @Test
    fun cancellationIsNotAFailure() = runTest {
        coEvery { withdrawService.prepare(any(), any(), any()) } throws CancellationException("left the screen")

        try {
            delegate.review(withdrawRequest, withdraw)
            fail("expected the cancellation to pass through")
        } catch (e: CancellationException) {
            assertEquals("left the screen", e.message)
        }
    }

    private suspend fun reviewFailure(): PerpetualConfirmException {
        return try {
            delegate.review(withdrawRequest, withdraw)
            throw AssertionError("expected the review to fail")
        } catch (e: PerpetualConfirmException) {
            e
        }
    }

    private fun review(plan: Any) = PerpetualReview(
        walletId = WALLET_ID,
        expiresAtMs = null,
        display = PerpetualReview.Withdraw(
            symbol = "USDC",
            network = null,
            image = null,
            amount = "",
            receive = "",
            fee = null,
            feeIsEstimate = false,
            arrivalSeconds = null,
            recipient = "",
            provider = "Relay",
        ),
        plan = plan,
    )

    private fun depositQuote(route: PerpsDepositRoute, estimatedDurationSeconds: Long? = null) = PerpetualDepositQuote(
        quoteId = "quote-1",
        executionId = "execution-1",
        provider = when (route) {
            PerpsDepositRoute.DIRECT_GATEWAY -> PerpetualDepositProvider.Direct
            PerpsDepositRoute.CROSSCHAIN_SWAP -> PerpetualDepositProvider.Relay
        },
        expiresAtMs = QUOTE_EXPIRY,
        sourceAmount = DEPOSIT_UNITS.toChain(),
        depositAmount = CREDITED_UNITS.toChain(),
        operation = Operation.build("operation-1", emptyList()),
        estimatedDurationSeconds = estimatedDurationSeconds,
    )

    private fun withdrawPlan(
        source: PerpsDepositSource? = null,
        assetId: String = PerpsFundingAssetId.USDC_ETHEREUM,
        quote: PerpsWithdrawQuote = withdrawQuote(outputUnits = WITHDRAW_UNITS),
        cost: PerpsWithdrawCost = PerpsWithdrawCost(usd = BigDecimal.ONE, isEstimate = true),
    ) = PerpsWithdrawPlan(
        walletId = WALLET_ID,
        amountUnits = WITHDRAW_UNITS,
        destination = PerpsWithdrawDestination(
            assetId = assetId,
            recipient = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8",
            source = source,
            transferFeeUnits = null,
        ),
        limits = PerpsWithdrawLimits(minTransferUnits = BigInteger.ONE),
        quote = quote,
        cost = cost,
    )

    private fun withdrawQuote(outputUnits: BigInteger, estimatedDurationSeconds: Long? = null) = PerpsWithdrawQuote(
        quoteId = "withdraw-quote-1",
        expiresAtMs = QUOTE_EXPIRY,
        executionId = "execution-1",
        outputUnits = outputUnits,
        transfer = PerpsWithdrawTransfer(
            toAccountIndex = 1L,
            amountUnits = WITHDRAW_UNITS,
            usdcFeeUnits = BigInteger.ZERO,
            memoHex = "",
        ),
        estimatedDurationSeconds = estimatedDurationSeconds,
    )

    private companion object {
        const val WALLET_ID = "wallet-1"
        const val BENEFICIARY = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8"
        val DEPOSIT_UNITS: BigInteger = BigInteger.valueOf(5_000_000)
        val CREDITED_UNITS: BigInteger = BigInteger.valueOf(4_900_000)
        val WITHDRAW_UNITS: BigInteger = BigInteger.valueOf(10_000_000)
        const val QUOTE_EXPIRY = 1_700_000_000_000L
        const val MINUTE_MS = 60_000L

        // Years behind the device clock: the request must not read the device's time.
        const val SERVER_NOW = 1_699_999_000_000L
    }
}
