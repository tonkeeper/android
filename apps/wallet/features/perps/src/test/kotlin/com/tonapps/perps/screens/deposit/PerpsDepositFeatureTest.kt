package com.tonapps.perps.screens.deposit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.ionspin.kotlin.bignum.integer.BigInteger as ChainInteger
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.DefaultEvents
import com.tonapps.bus.generated.Events.Perps.PerpsSourceScreen
import com.tonapps.perps.funding.PerpsDepositMinimum
import com.tonapps.perps.funding.PerpsDepositQuote
import com.tonapps.perps.funding.PerpsDepositQuoteError
import com.tonapps.perps.funding.PerpsDepositException
import com.tonapps.perps.funding.PerpsDepositService
import com.tonapps.perps.funding.PerpsDepositSource
import com.tonapps.perps.funding.PerpsDepositSources
import com.tonapps.perps.funding.PerpsFundingAssetId
import com.tonapps.perps.trade.PerpsAccountService
import com.tonapps.network.NetworkMonitor
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.data.multichain.asset.AssetRateEntity
import com.tonapps.wallet.data.multichain.perps.PerpsFundingRecord
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.FakeTimeProvider
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import com.tonapps.wallet.testing.testAsset
import io.infrastructure.ClientError
import io.infrastructure.ClientException
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger

@OptIn(ExperimentalCoroutinesApi::class)
class PerpsDepositFeatureTest : BaseUnitTest() {

    private val service = mockk<PerpsDepositService>()
    private val accountService = mockk<PerpsAccountService>()
    private val unifiedAccountRepository = mockk<UnifiedAccountRepository>()
    private val mcAccountRepository = mockk<McAccountRepository>()
    private val settingsRepository = mockk<SettingsRepository> {
        every { hiddenBalances } returns false
        every { hiddenBalancesFlow } returns MutableStateFlow(false)
    }
    private val deposit = mockk<ConfirmType.PerpetualDeposit> {
        every { quote } returns mockk { every { depositAmount } returns ChainInteger.fromLong(4_900_000L) }
        every { providerFees } returns null
        every { activatesAccount } returns false
    }
    private val serverTime = FakeTimeProvider()
    private val quoteExpiry = MutableStateFlow<Long?>(null)
    private val request = mockk<ConfirmRequest> {
        every { type } returns deposit
        every { data.expiresAt } answers { quoteExpiry.value }
    }
    private val source = PerpsDepositSource(
        account = testAccount(TestAssets.usdtEth, balance = "100000000"),
        fundingAsset = checkNotNull(PerpsFundingAssetId.parse(TestAssets.usdtEth.id)),
    )

    private val online = MutableStateFlow(true)
    private val networkMonitor = mockk<NetworkMonitor> {
        every { isOnlineFlow } returns online
    }

    private val scheduler get() = dispatchers.scheduler

    private val tracked = mutableListOf<Pair<String, Map<String, Any>>>()

    @Before
    fun setUp() {
        val recording = object : EventExecutor {
            override fun trackEvent(eventName: String, props: Map<String, Any>) {
                tracked += eventName to props
            }
        }
        val helper = mockk<AnalyticsHelper> {
            every { events } returns DefaultEvents(recording)
        }
        mockkObject(AnalyticsHelper.Companion)
        every { AnalyticsHelper.Default } returns helper
        val wallet = mockk<WalletEntity>()
        every { wallet.supportsPerps } returns true
        every { wallet.id } returns TestWallet.ID
        every { unifiedAccountRepository.selectedTonWalletFlow } returns flowOf(wallet)
        coEvery { unifiedAccountRepository.getTonWallets() } returns listOf(wallet, otherWallet(supportsPerps = true))
        every { mcAccountRepository.refreshTrigger } returns MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }
        coEvery { accountService.keepTradingKey(any()) } returns true
        coEvery { service.sources(TestWallet.ID) } returns fresh(source)
        coEvery { service.pendingPayment(TestWallet.ID) } returns null
        coEvery { service.loadNetworkFees() } just Runs
        coEvery { service.minimum(TestWallet.ID, any()) } returns null
        coEvery { service.quote(TestWallet.ID, source, any(), any()) } coAnswers {
            delay(QUOTE_MS)
            PerpsDepositQuote(request, networkFeeUsd = BigDecimal("0.4"))
        }
    }

    @After
    fun tearDown() {
        unmockkObject(AnalyticsHelper.Companion)
    }

    @Test
    fun aTypedAmountIsPricedOnTheScreenAndReviewOpensTheConfirmScreenAtOnce() {
        val feature = open()
        val events = collectEvents(feature)
        enter(feature, "5")

        assertEquals(PerpsDepositQuotePreview.Loading, feature.preview.value)

        settle()

        val ready = feature.preview.value as PerpsDepositQuotePreview.Ready
        assertEquals(BigDecimal("4.9"), ready.youGetUsd.stripTrailingZeros())
        assertEquals(BigDecimal("0.4"), ready.feeUsd)
        assertFalse(ready.activatesAccount)

        review(feature)

        assertEquals(listOf<PerpsDepositEvent>(PerpsDepositEvent.Reviewed(request)), events)
        assertFalse(feature.quoting.value)
        coVerify(exactly = 1) { service.quote(TestWallet.ID, source, FIVE_USDT, PerpsSourceScreen.PerpsHome) }
    }

    @Test
    fun aQuoteThatActivatesTheAccountSaysSoOnTheScreen() {
        every { deposit.activatesAccount } returns true
        val feature = open()
        enter(feature, "5")
        settle()

        val ready = feature.preview.value as PerpsDepositQuotePreview.Ready
        assertTrue(ready.activatesAccount)
    }

    @Test
    fun aQuotePaidInBatteryChargesShowsNoDollarFee() {
        coEvery { service.quote(TestWallet.ID, source, any(), any()) } returns
            PerpsDepositQuote(request, networkFeeUsd = BigDecimal("0.4"), paysFeeInCharges = true)
        val feature = open()
        enter(feature, "5")
        settle()

        val ready = feature.preview.value as PerpsDepositQuotePreview.Ready
        assertNull(ready.feeUsd)
        assertEquals(BigDecimal("4.9"), ready.youGetUsd.stripTrailingZeros())
    }

    @Test
    fun aFailedQuoteSaysWhyAndStaysOnTheScreenUntilTheAmountChanges() {
        coEvery { service.quote(any(), any(), any(), any()) } throws PerpsDepositException.InsufficientBalance()
        val feature = open()
        val events = collectEvents(feature)
        enter(feature, "5")
        settle()

        assertEquals(PerpsDepositQuotePreview.Failed(PerpsDepositQuoteError.InsufficientBalance), feature.preview.value)

        review(feature)

        assertTrue(events.isEmpty())

        enter(feature, "6")

        assertEquals(PerpsDepositQuotePreview.Loading, feature.preview.value)
    }

    @Test
    fun anAmountRefusedForItsNetworkFeeIsQuotedAgainOnceTheBalanceGrows() {
        val toppedUp = PerpsDepositSource(
            account = testAccount(TestAssets.usdtEth, balance = "200000000"),
            fundingAsset = source.fundingAsset,
        )
        val refusal = PerpsDepositQuoteError.InsufficientNetworkFee("ETH", BigDecimal("0.01"))
        var listings = 0
        coEvery { service.sources(TestWallet.ID) } coAnswers {
            listings += 1
            if (listings == 1) {
                fresh(source)
            } else {
                delay(SLOW_SOURCES_MS)
                fresh(toppedUp)
            }
        }
        coEvery { service.quote(TestWallet.ID, source, any(), any()) } throws
            PerpsDepositException.InsufficientNetworkFee("ETH", BigDecimal("0.01"))
        coEvery { service.quote(TestWallet.ID, toppedUp, any(), any()) } coAnswers {
            delay(QUOTE_MS)
            PerpsDepositQuote(request, networkFeeUsd = BigDecimal("0.4"))
        }
        val feature = open()
        enter(feature, "5")
        settle()

        assertEquals(PerpsDepositQuotePreview.Failed(refusal), feature.preview.value)

        reload(feature)
        scheduler.advanceTimeBy(SLOW_SOURCES_MS)
        scheduler.runCurrent()
        settle()

        assertTrue(feature.preview.value is PerpsDepositQuotePreview.Ready)
        assertEquals("5", feature.amountInput.value)
    }

    @Test
    fun aRefusalForTheFeeCoinIsQuotedAgainWhenTheWalletRefreshes() {
        var quotes = 0
        coEvery { service.quote(TestWallet.ID, source, any(), any()) } coAnswers {
            delay(QUOTE_MS)
            quotes += 1
            if (quotes == 1) {
                throw PerpsDepositException.InsufficientNetworkFee("ETH", BigDecimal("0.01"))
            }
            PerpsDepositQuote(request, networkFeeUsd = BigDecimal("0.4"))
        }
        val feature = open()
        enter(feature, "5")
        settle()

        assertTrue(feature.preview.value is PerpsDepositQuotePreview.Failed)

        reload(feature)
        settle()

        assertTrue(feature.preview.value is PerpsDepositQuotePreview.Ready)
    }

    @Test
    fun aPricedDepositIsNotQuotedAgainWhenTheBalanceChanges() {
        val toppedUp = PerpsDepositSource(
            account = testAccount(TestAssets.usdtEth, balance = "200000000"),
            fundingAsset = source.fundingAsset,
        )
        coEvery { service.sources(TestWallet.ID) } returnsMany listOf(fresh(source), fresh(toppedUp))
        coEvery { service.quote(TestWallet.ID, any(), any(), any()) } coAnswers {
            delay(QUOTE_MS)
            PerpsDepositQuote(request, networkFeeUsd = BigDecimal("0.4"))
        }
        val feature = open()
        enter(feature, "5")
        settle()

        reload(feature)

        assertTrue(feature.preview.value is PerpsDepositQuotePreview.Ready)
        settle()
        coVerify(exactly = 1) { service.quote(any(), any(), any(), any()) }
    }

    @Test
    fun closingThePasscodePromptForTheTradingKeyClosesTheDeposit() {
        coEvery { accountService.keepTradingKey(TestWallet.ID) } returns false
        val feature = open()
        val events = collectEvents(feature)
        enter(feature, "5")
        settle()

        assertEquals(listOf<PerpsDepositEvent>(PerpsDepositEvent.Closed), events)
        assertEquals(PerpsDepositQuotePreview.None, feature.preview.value)
        coVerify(exactly = 0) { service.quote(any(), any(), any(), any()) }
    }

    @Test
    fun aTradingKeyThatFailsToKeepClosesTheDeposit() {
        coEvery { accountService.keepTradingKey(TestWallet.ID) } throws IllegalStateException("keystore")
        val feature = open()
        val events = collectEvents(feature)
        enter(feature, "5")
        settle()

        assertEquals(listOf<PerpsDepositEvent>(PerpsDepositEvent.Closed), events)
        assertFalse(feature.tradingKeyKept.value)
        coVerify(exactly = 0) { service.quote(any(), any(), any(), any()) }
    }

    @Test
    fun aDepositIsPricedOnlyOnceTheTradingKeyIsKept() {
        val kept = CompletableDeferred<Boolean>()
        coEvery { accountService.keepTradingKey(TestWallet.ID) } coAnswers { kept.await() }
        val feature = open()
        enter(feature, "5")
        settle()

        assertFalse(feature.tradingKeyKept.value)
        assertEquals(PerpsDepositQuotePreview.None, feature.preview.value)
        coVerify(exactly = 0) { service.quote(any(), any(), any(), any()) }

        kept.complete(true)
        scheduler.runCurrent()
        settle()

        assertTrue(feature.tradingKeyKept.value)
        assertTrue(feature.preview.value is PerpsDepositQuotePreview.Ready)
        coVerify(exactly = 1) { service.quote(TestWallet.ID, source, FIVE_USDT, PerpsSourceScreen.PerpsHome) }
    }

    @Test
    fun aQuoteTheServiceFailedIsTakenAgainFromTheButtonWithoutOpeningTheConfirmScreen() {
        coEvery { service.quote(any(), any(), any(), any()) } throws IOException("timeout")
        val feature = open()
        val events = collectEvents(feature)
        enter(feature, "5")
        settle()
        assertEquals(PerpsDepositQuotePreview.Failed(PerpsDepositQuoteError.ServiceUnavailable), feature.preview.value)

        coEvery { service.quote(TestWallet.ID, source, any(), any()) } coAnswers {
            delay(QUOTE_MS)
            PerpsDepositQuote(request, networkFeeUsd = null)
        }
        review(feature)

        assertEquals(PerpsDepositQuotePreview.Loading, feature.preview.value)

        settle()

        assertEquals(PerpsDepositQuotePreview.Ready::class, feature.preview.value::class)
        assertTrue(events.isEmpty())
        assertFalse(feature.quoting.value)
    }

    @Test
    fun aQuoteTheBackendRefusedIsNotRetriedWithTheSameAmount() {
        coEvery { service.quote(any(), any(), any(), any()) } throws PerpsDepositException.QuoteRejected(mockk())
        val feature = open()
        enter(feature, "5")
        settle()
        assertEquals(PerpsDepositQuotePreview.Failed(PerpsDepositQuoteError.QuoteFailed), feature.preview.value)

        review(feature)
        settle()

        coVerify(exactly = 1) { service.quote(any(), any(), any(), any()) }
    }

    @Test
    fun aQuoteThatFailedOfflineIsTakenAgainWhenTheNetworkIsBack() {
        coEvery { service.quote(any(), any(), any(), any()) } throws PerpsDepositException.InsufficientBalance()
        val feature = open()
        enter(feature, "5")
        online.value = false
        settle()
        assertEquals(PerpsDepositQuotePreview.Failed(PerpsDepositQuoteError.InsufficientBalance), feature.preview.value)

        coEvery { service.quote(TestWallet.ID, source, any(), any()) } coAnswers {
            delay(QUOTE_MS)
            PerpsDepositQuote(request, networkFeeUsd = null)
        }
        online.value = true
        settle()

        assertEquals(PerpsDepositQuotePreview.Ready::class, feature.preview.value::class)
    }

    @Test
    fun editingTheAmountDropsTheQuoteInFlight() {
        val feature = open()
        val events = collectEvents(feature)
        enter(feature, "5")
        scheduler.advanceTimeBy(DEBOUNCE_MS + QUOTE_MS / 2)
        scheduler.runCurrent()

        enter(feature, "6")
        settle()

        assertEquals(PerpsDepositQuotePreview.Ready::class, feature.preview.value::class)
        assertTrue(events.isEmpty())
        coVerify(exactly = 1) { service.quote(TestWallet.ID, source, BigInteger.valueOf(6_000_000L), PerpsSourceScreen.PerpsHome) }
    }

    @Test
    fun aTapWhileThePriceLoadsOpensTheConfirmScreenOnceItArrives() {
        val feature = open()
        val events = collectEvents(feature)
        enter(feature, "5")

        review(feature)
        review(feature)

        assertTrue(feature.quoting.value)
        assertTrue(events.isEmpty())

        settle()

        assertEquals(listOf<PerpsDepositEvent>(PerpsDepositEvent.Reviewed(request)), events)
        assertFalse(feature.quoting.value)
        coVerify(exactly = 1) { service.quote(any(), any(), any(), any()) }
    }

    @Test
    fun comingBackFromTheConfirmScreenPricesTheDepositAgain() {
        val feature = open()
        val events = collectEvents(feature)
        enter(feature, "5")
        settle()
        review(feature)
        assertEquals(1, events.size)

        review(feature)

        assertEquals(1, events.size)
        assertTrue(feature.quoting.value)

        settle()

        assertEquals(2, events.size)
        coVerify(exactly = 2) { service.quote(TestWallet.ID, source, FIVE_USDT, PerpsSourceScreen.PerpsHome) }
    }

    @Test
    fun theFormShownOnOpeningIsNotPricedAgain() {
        val feature = open()
        show(feature)
        enter(feature, "5")
        settle()
        show(feature)
        settle()

        coVerify(exactly = 1) { service.quote(TestWallet.ID, source, FIVE_USDT, PerpsSourceScreen.PerpsHome) }
    }

    @Test
    fun theFormShownAgainAfterTheConfirmScreenPricesTheDepositAtOnce() {
        val feature = open()
        val events = collectEvents(feature)
        enter(feature, "5")
        settle()
        review(feature)
        assertEquals(1, events.size)

        show(feature)

        assertEquals(PerpsDepositQuotePreview.Loading, feature.preview.value)

        settle()

        assertEquals(PerpsDepositQuotePreview.Ready::class, feature.preview.value::class)
        assertEquals(1, events.size)
        coVerify(exactly = 2) { service.quote(TestWallet.ID, source, FIVE_USDT, PerpsSourceScreen.PerpsHome) }
    }

    @Test
    fun anAmountTheBackendRefusedBelowTheMinimumOnTheConfirmScreenIsShownSoOnTheForm() {
        val feature = open()
        enter(feature, "5")
        settle()
        review(feature)

        coEvery { service.quote(any(), any(), any(), any()) } throws ClientException(
            statusCode = 400,
            response = ClientError<Any>(body = AMOUNT_TOO_LOW_BODY, statusCode = 400),
        )
        show(feature)
        settle()

        assertEquals(PerpsDepositQuotePreview.Failed(PerpsDepositQuoteError.BelowMinimum(BigDecimal("3.51"))), feature.preview.value)
        assertFalse(feature.reviewEnabled.value)
    }

    @Test
    fun aTapWhileThePriceLoadsIsKeptWhenTheFormIsShownAgain() {
        val feature = open()
        val events = collectEvents(feature)
        enter(feature, "5")
        review(feature)

        show(feature)

        assertTrue(feature.quoting.value)

        settle()

        assertEquals(listOf<PerpsDepositEvent>(PerpsDepositEvent.Reviewed(request)), events)
    }

    @Test
    fun anExpiringPriceIsTakenAgainBeforeTheConfirmScreenOpens() {
        val feature = open()
        val events = collectEvents(feature)
        enter(feature, "5")
        settle()
        quoteExpiry.value = serverTime.currentTimeMillis() + 30_000L

        review(feature)

        assertTrue(events.isEmpty())
        assertTrue(feature.quoting.value)

        quoteExpiry.value = null
        settle()

        assertEquals(listOf<PerpsDepositEvent>(PerpsDepositEvent.Reviewed(request)), events)
        coVerify(exactly = 2) { service.quote(TestWallet.ID, source, FIVE_USDT, PerpsSourceScreen.PerpsHome) }
    }

    @Test
    fun aPriceStillFreshOnTheServerOpensTheConfirmScreenWhileTheDeviceClockRunsAhead() {
        serverTime.offsetMs = -DEVICE_CLOCK_SKEW_MS
        val feature = open()
        val events = collectEvents(feature)
        enter(feature, "5")
        settle()
        quoteExpiry.value = serverTime.currentTimeMillis() + 60_000L

        review(feature)

        assertEquals(listOf<PerpsDepositEvent>(PerpsDepositEvent.Reviewed(request)), events)
        coVerify(exactly = 1) { service.quote(TestWallet.ID, source, FIVE_USDT, PerpsSourceScreen.PerpsHome) }
    }

    @Test
    fun aPriceExpiringOnTheServerIsTakenAgainWhileTheDeviceClockRunsBehind() {
        serverTime.offsetMs = DEVICE_CLOCK_SKEW_MS
        val feature = open()
        val events = collectEvents(feature)
        enter(feature, "5")
        settle()
        quoteExpiry.value = serverTime.currentTimeMillis() + 30_000L

        review(feature)

        assertTrue(events.isEmpty())
        assertTrue(feature.quoting.value)
    }

    /** MAX finds the network fees in the cache: the screen loads them as it opens. */
    @Test
    fun openingTheDepositLoadsTheNetworkFees() {
        open()

        coVerify(exactly = 1) { service.loadNetworkFees() }
    }

    /** MAX refreshes a stale or failed load for the next tap. */
    @Test
    fun maxRefreshesTheNetworkFees() {
        coEvery { service.maxAmount(TestWallet.ID, source) } returns BigInteger.valueOf(1_000_000)
        val feature = open()

        feature.sendAction(PerpsDepositAction.Max)
        scheduler.runCurrent()

        coVerify(exactly = 2) { service.loadNetworkFees() }
        assertEquals("1", feature.amountInput.value)
    }

    @Test
    fun theSourceWorthMostInFiatIsSelectedFirst() {
        coEvery { service.sources(TestWallet.ID) } returns fresh(usdc(balance = "2000000"), source)

        val feature = open()

        assertEquals(source, feature.selected.value?.source)
    }

    @Test
    fun usdcAtZeroDoesNotTakeTheSelectionFromAnUnpricedBalance() {
        val unpriced = PerpsDepositSource(
            account = testAccount(TestAssets.usdtEth, balance = "5000000", price = null),
            fundingAsset = source.fundingAsset,
        )
        coEvery { service.sources(TestWallet.ID) } returns fresh(usdc(balance = "0"), unpriced)

        val feature = open()

        assertEquals(unpriced, feature.selected.value?.source)
    }

    @Test
    fun aReloadKeepsTheShownSourceWhileItIsStillListed() {
        coEvery { service.sources(TestWallet.ID) } returnsMany listOf(
            fresh(usdc(balance = "2000000"), source),
            fresh(usdc(balance = "500000000"), source),
        )
        val feature = open()
        enter(feature, "5")

        reload(feature)

        assertEquals(source, feature.selected.value?.source)
        assertEquals("5", feature.amountInput.value)
    }

    @Test
    fun togglingBackRestoresTheTypedAmount() {
        val priced = PerpsDepositSource(
            account = testAccount(TestAssets.usdtEth, balance = "100000000", price = "0.9987"),
            fundingAsset = source.fundingAsset,
        )
        coEvery { service.sources(TestWallet.ID) } returns fresh(priced)
        val feature = open()
        enter(feature, "1.23")

        toggle(feature)
        assertTrue(feature.inputInFiat.value)
        assertEquals("1.22", feature.amountInput.value)

        toggle(feature)
        assertFalse(feature.inputInFiat.value)
        assertEquals("1.23", feature.amountInput.value)

        toggle(feature)
        assertEquals("1.22", feature.amountInput.value)
    }

    @Test
    fun anAmountEditedAfterTheToggleIsConvertedAgain() {
        val priced = PerpsDepositSource(
            account = testAccount(TestAssets.usdtEth, balance = "100000000", price = "0.9987"),
            fundingAsset = source.fundingAsset,
        )
        coEvery { service.sources(TestWallet.ID) } returns fresh(priced)
        val feature = open()
        enter(feature, "1.23")
        toggle(feature)
        enter(feature, "2.5")

        toggle(feature)

        assertFalse(feature.inputInFiat.value)
        assertEquals("2.503254", feature.amountInput.value)
    }

    @Test
    fun maxAfterAToggleIsConvertedAgain() {
        val priced = PerpsDepositSource(
            account = testAccount(TestAssets.usdtEth, balance = "100000000", price = "0.9987"),
            fundingAsset = source.fundingAsset,
        )
        coEvery { service.sources(TestWallet.ID) } returns fresh(priced)
        coEvery { service.maxAmount(TestWallet.ID, priced) } returns BigInteger.valueOf(1_222_000)
        val feature = open()
        enter(feature, "1.23")
        toggle(feature)
        feature.sendAction(PerpsDepositAction.Max)
        scheduler.runCurrent()
        assertEquals("1.22", feature.amountInput.value)

        toggle(feature)

        assertFalse(feature.inputInFiat.value)
        assertEquals("1.221588", feature.amountInput.value)
    }

    @Test
    fun maxOnTonFillsAtMostEightDecimals() {
        val ton = coinSource(TestAssets.ton, balance = "6926710002")
        coEvery { service.sources(TestWallet.ID) } returns fresh(ton)
        coEvery { service.maxAmount(TestWallet.ID, ton) } returns BigInteger.valueOf(6_926_710_002L)
        val feature = open()

        feature.sendAction(PerpsDepositAction.Max)
        scheduler.runCurrent()

        assertEquals("6.92671", feature.amountInput.value)
    }

    @Test
    fun maxOnAnEighteenDecimalCoinRoundsDownToEightDecimals() {
        val eth = coinSource(TestAssets.eth, balance = "5186490539327960")
        coEvery { service.sources(TestWallet.ID) } returns fresh(eth)
        coEvery { service.maxAmount(TestWallet.ID, eth) } returns BigInteger("5186490539327960")
        val feature = open()

        feature.sendAction(PerpsDepositAction.Max)
        scheduler.runCurrent()

        assertEquals("0.00518649", feature.amountInput.value)
        assertEquals(BigInteger("5186490000000000"), feature.summary.value?.amountUnits)
    }

    @Test
    fun aFiatAmountToggledIntoAnEighteenDecimalCoinKeepsEightDecimals() {
        val eth = coinSource(TestAssets.eth, balance = "1000000000000000000", price = "3000")
        coEvery { service.sources(TestWallet.ID) } returns fresh(eth)
        val feature = open()
        toggle(feature)
        enter(feature, "10")

        toggle(feature)

        assertFalse(feature.inputInFiat.value)
        assertEquals("0.00333333", feature.amountInput.value)
    }

    @Test
    fun theLargestSourceTakesOverFromAnEmptyShownOne() {
        val usdcAtZero = usdc(balance = "0")
        val received = usdc(balance = "3000000")
        coEvery { service.sources(TestWallet.ID) } returnsMany listOf(
            fresh(usdcAtZero),
            fresh(usdcAtZero, source),
        )
        val feature = open()
        assertEquals(usdcAtZero, feature.selected.value?.source)

        reload(feature)

        assertEquals(source, feature.selected.value?.source)

        coEvery { service.sources(TestWallet.ID) } returns fresh(received, source)
        feature.sendAction(PerpsDepositAction.SelectSource(received.assetId))
        reload(feature)

        assertEquals(received, feature.selected.value?.source)
    }

    @Test
    fun anotherWalletOpensOnItsOwnLargestSource() {
        val selectedWallet = MutableStateFlow<WalletEntity?>(multichainWallet(TestWallet.ID))
        every { unifiedAccountRepository.selectedTonWalletFlow } returns selectedWallet
        val usdtOfOther = PerpsDepositSource(
            account = testAccount(TestAssets.usdtEth, balance = "1000000", walletId = OTHER_WALLET_ID),
            fundingAsset = source.fundingAsset,
        )
        val usdcOfOther = usdc(balance = "50000000", walletId = OTHER_WALLET_ID)
        coEvery { service.sources(OTHER_WALLET_ID) } returns fresh(usdcOfOther, usdtOfOther)
        coEvery { service.pendingPayment(OTHER_WALLET_ID) } returns null
        val feature = open()
        assertEquals(source, feature.selected.value?.source)

        selectedWallet.value = multichainWallet(OTHER_WALLET_ID)
        scheduler.runCurrent()

        assertEquals(usdcOfOther, feature.selected.value?.source)
    }

    @Test
    fun anotherWalletWithTheSameAssetIsProbedForItsOwnMinimum() {
        val selectedWallet = MutableStateFlow<WalletEntity?>(multichainWallet(TestWallet.ID))
        every { unifiedAccountRepository.selectedTonWalletFlow } returns selectedWallet
        val usdtOfOther = PerpsDepositSource(
            account = testAccount(TestAssets.usdtEth, balance = "1000000", walletId = OTHER_WALLET_ID),
            fundingAsset = source.fundingAsset,
        )
        coEvery { service.sources(OTHER_WALLET_ID) } coAnswers {
            delay(SLOW_SOURCES_MS)
            fresh(usdtOfOther)
        }
        coEvery { service.pendingPayment(OTHER_WALLET_ID) } returns null
        coEvery { service.minimum(OTHER_WALLET_ID, any()) } returns null
        open()
        settle()

        selectedWallet.value = multichainWallet(OTHER_WALLET_ID)
        scheduler.advanceTimeBy(SLOW_SOURCES_MS)
        scheduler.runCurrent()

        coVerify(exactly = 0) { service.minimum(OTHER_WALLET_ID, source) }
        coVerify(exactly = 1) { service.minimum(OTHER_WALLET_ID, usdtOfOther) }
    }

    @Test
    fun aFailedWalletLookupDoesNotOfferAnotherWallet() {
        coEvery { unifiedAccountRepository.getTonWallets() } throws IOException("disk")
        val feature = open()
        settle()

        assertFalse(feature.anotherPerpsWalletAvailable.value)
    }

    @Test
    fun aSourcePickedInOneWalletDoesNotCarryOverToAnother() {
        val selectedWallet = MutableStateFlow<WalletEntity?>(multichainWallet(TestWallet.ID))
        every { unifiedAccountRepository.selectedTonWalletFlow } returns selectedWallet
        val usdcOfThis = usdc(balance = "2000000")
        coEvery { service.sources(TestWallet.ID) } returns fresh(usdcOfThis, source)
        val usdcOfOther = usdc(balance = "10000", walletId = OTHER_WALLET_ID)
        val usdtOfOther = PerpsDepositSource(
            account = testAccount(TestAssets.usdtEth, balance = "50000000", walletId = OTHER_WALLET_ID),
            fundingAsset = source.fundingAsset,
        )
        coEvery { service.sources(OTHER_WALLET_ID) } returns fresh(usdcOfOther, usdtOfOther)
        coEvery { service.pendingPayment(OTHER_WALLET_ID) } returns null
        val feature = open()
        feature.sendAction(PerpsDepositAction.SelectSource(usdcOfThis.assetId))
        scheduler.runCurrent()
        assertEquals(usdcOfThis, feature.selected.value?.source)

        selectedWallet.value = multichainWallet(OTHER_WALLET_ID)
        scheduler.runCurrent()

        assertEquals(usdtOfOther, feature.selected.value?.source)
    }

    @Test
    fun anAmountTheSourceCannotCoverIsNotQuoted() {
        val feature = open()
        enter(feature, "500")

        settle()

        assertEquals(PerpsDepositQuotePreview.None, feature.preview.value)
        coVerify(exactly = 0) { service.quote(any(), any(), any(), any()) }
    }

    @Test
    fun anAmountBelowTheSourcesMinimumIsFlaggedAndNotQuoted() {
        coEvery { service.minimum(TestWallet.ID, source) } returns
            PerpsDepositMinimum(usd = BigDecimal("10"), units = BigInteger.valueOf(10_000_000L))
        val feature = open()
        enter(feature, "5")

        settle()

        assertEquals(true, feature.summary.value?.belowMinimum)
        assertEquals(PerpsDepositQuotePreview.None, feature.preview.value)
        coVerify(exactly = 0) { service.quote(any(), any(), any(), any()) }

        enter(feature, "10")

        assertEquals(false, feature.summary.value?.belowMinimum)
        assertEquals(true, feature.summary.value?.canReview)
    }

    @Test
    fun withoutAKnownMinimumAnyAmountGoesToReview() {
        val feature = open()
        enter(feature, "0.5")

        assertEquals(false, feature.summary.value?.belowMinimum)
        assertEquals(true, feature.summary.value?.canReview)
    }

    @Test
    fun theStartCountsTheListedSourcesWorthADollarOnceTheListOfThisOpeningLoads() {
        val listed = CompletableDeferred<PerpsDepositSources>()
        coEvery { service.sources(TestWallet.ID) } coAnswers { listed.await() }
        val unpriced = PerpsDepositSource(
            account = testAccount(TestAssets.usdtEth, balance = "5000000", price = null),
            fundingAsset = source.fundingAsset,
        )
        val feature = open()

        assertTrue(sent(DEPOSIT_STARTED).isEmpty())

        listed.complete(fresh(usdc(balance = "999999"), source, unpriced))
        scheduler.runCurrent()
        reload(feature)

        assertEquals(listOf(mapOf("wallet_mode" to "multi", "eligible_assets_count" to 1)), sent(DEPOSIT_STARTED))
    }

    @Test
    fun aListPricedInAnotherCurrencyCountsADollarAtThePriceOfUsdc() {
        val usdcInEuro = PerpsDepositSource(
            account = inEuro(testAccount(USDC, balance = "0"), price = "0.9"),
            fundingAsset = checkNotNull(PerpsFundingAssetId.parse(PerpsFundingAssetId.USDC_ETHEREUM)),
        )
        val worthADollar = PerpsDepositSource(
            account = inEuro(testAccount(TestAssets.usdtEth, balance = "1000000"), price = "0.9"),
            fundingAsset = source.fundingAsset,
        )
        val underADollar = PerpsDepositSource(
            account = inEuro(testAccount(TestAssets.usdtEth, balance = "999999"), price = "0.9"),
            fundingAsset = source.fundingAsset,
        )
        coEvery { service.sources(TestWallet.ID) } returns fresh(usdcInEuro, worthADollar, underADollar)

        open()

        assertEquals(listOf(mapOf("wallet_mode" to "multi", "eligible_assets_count" to 1)), sent(DEPOSIT_STARTED))
    }

    @Test
    fun aListThatFailsToLoadStartsWithoutACount() {
        coEvery { service.sources(TestWallet.ID) } throws IOException("offline")

        open()

        assertEquals(listOf(mapOf<String, Any>("wallet_mode" to "multi")), sent(DEPOSIT_STARTED))
    }

    @Test
    fun aListKeptFromAnEarlierLoadStartsWithoutACount() {
        coEvery { service.sources(TestWallet.ID) } returns PerpsDepositSources(listOf(source), fresh = false)

        open()

        assertEquals(listOf(mapOf<String, Any>("wallet_mode" to "multi")), sent(DEPOSIT_STARTED))
    }

    @Test
    fun closingBeforeTheListLoadsStartsWithoutACount() {
        coEvery { service.sources(TestWallet.ID) } coAnswers { CompletableDeferred<PerpsDepositSources>().await() }
        val feature = open()
        val store = ViewModelStore()
        store.put("perps-deposit", feature)

        store.clear()
        scheduler.runCurrent()

        assertEquals(listOf(mapOf<String, Any>("wallet_mode" to "multi")), sent(DEPOSIT_STARTED))
    }

    @Test
    fun eachTapTheButtonTakesSendsTheAssetAndTheAmountAsADollarRange() {
        val feature = open()
        enter(feature, "5")
        settle()
        review(feature)

        enter(feature, "500")
        settle()
        review(feature)

        assertEquals(
            listOf(mapOf("asset" to TestAssets.usdtEth.id, "amount_bucket" to "1-10")),
            sent(CONTINUE_CLICKED),
        )
    }

    @Test
    fun anAmountTheFormRefusesIsReportedOnceItStaysRefusedForThePause() {
        coEvery { service.minimum(TestWallet.ID, source) } returns
            PerpsDepositMinimum(usd = BigDecimal("10"), units = BigInteger.valueOf(10_000_000L))
        val feature = open()
        enter(feature, "500")
        scheduler.advanceTimeBy(DEBOUNCE_MS / 2)
        scheduler.runCurrent()

        assertTrue(sent(DEPOSIT_FAILED).isEmpty())

        settle()
        enter(feature, "5")
        settle()
        settle()

        assertEquals(listOf(failedOnAmount("insufficient_balance"), failedOnAmount("below_minimum")), sent(DEPOSIT_FAILED))
    }

    @Test
    fun eachQuoteThatFailsIsReportedAgainWhenTheButtonRetriesIt() {
        coEvery { service.quote(any(), any(), any(), any()) } throws IOException("timeout")
        val feature = open()
        enter(feature, "5")
        settle()

        review(feature)
        settle()

        assertEquals(listOf(failedOnAmount("service_unavailable"), failedOnAmount("service_unavailable")), sent(DEPOSIT_FAILED))
        assertEquals(1, sent(CONTINUE_CLICKED).size)
    }

    @Test
    fun aQuoteTheBackendRefusedIsReportedAsNoQuoteAndItsButtonIsOff() {
        coEvery { service.quote(any(), any(), any(), any()) } throws PerpsDepositException.QuoteRejected(mockk())
        val feature = open()
        enter(feature, "5")
        settle()

        review(feature)
        settle()

        assertEquals(listOf(failedOnAmount("no_quote")), sent(DEPOSIT_FAILED))
        assertTrue(sent(CONTINUE_CLICKED).isEmpty())
    }

    @Test
    fun aSenderBlockedOnOpeningIsReportedOnceAndShownAsSoonAsAnAmountIsTyped() {
        coEvery { service.minimum(TestWallet.ID, source) } throws PerpsDepositException.AddressBlocked()
        val feature = open()
        settle()

        assertEquals(false, feature.summary.value?.addressBlocked)
        assertEquals(listOf(failedInBackground()), sent(DEPOSIT_FAILED))

        enter(feature, "5")

        assertEquals(true, feature.summary.value?.addressBlocked)
        assertFalse(feature.reviewEnabled.value)
        assertEquals(PerpsDepositQuotePreview.None, feature.preview.value)

        settle()
        reload(feature)
        settle()

        assertEquals(listOf(failedInBackground(), failedOnAmount("address_blocked")), sent(DEPOSIT_FAILED))
        coVerify(exactly = 0) { service.quote(any(), any(), any(), any()) }
    }

    @Test
    fun anotherSourceOfABlockedSenderIsPricedAsUsual() {
        val usdc = usdc(balance = "2000000")
        coEvery { service.sources(TestWallet.ID) } returns fresh(usdc, source)
        coEvery { service.minimum(TestWallet.ID, source) } throws PerpsDepositException.AddressBlocked()
        coEvery { service.quote(TestWallet.ID, usdc, any(), any()) } coAnswers {
            delay(QUOTE_MS)
            PerpsDepositQuote(request, networkFeeUsd = null)
        }
        val feature = open()
        feature.sendAction(PerpsDepositAction.SelectSource(usdc.assetId))
        scheduler.runCurrent()
        enter(feature, "1.5")
        settle()

        assertEquals(false, feature.summary.value?.addressBlocked)
        assertTrue(feature.preview.value is PerpsDepositQuotePreview.Ready)
        assertTrue(feature.reviewEnabled.value)
    }

    @Test
    fun aTypedAmountWhoseQuoteFindsTheSenderBlockedSaysSoAndKeepsReviewOff() {
        blockSenderOnQuotes()
        val feature = open()
        enter(feature, "5")
        settle()

        assertEquals(true, feature.summary.value?.addressBlocked)
        assertFalse(feature.reviewEnabled.value)

        review(feature)
        settle()

        assertEquals(listOf(failedOnAmount("address_blocked")), sent(DEPOSIT_FAILED))
        assertTrue(sent(CONTINUE_CLICKED).isEmpty())
    }

    @Test
    fun aSenderBlockedOnItsOwnQuoteIsShownAtOnceForTheNextAmountWithoutAnotherQuote() {
        blockSenderOnQuotes()
        val feature = open()
        enter(feature, "5")
        settle()

        enter(feature, "6")

        assertEquals(true, feature.summary.value?.addressBlocked)
        assertEquals(PerpsDepositQuotePreview.None, feature.preview.value)

        settle()

        assertEquals(listOf(failedOnAmount("address_blocked"), failedOnAmount("address_blocked")), sent(DEPOSIT_FAILED))
        coVerify(exactly = 1) { service.quote(any(), any(), any(), any()) }
    }

    @Test
    fun aSenderBlockedOnItsOwnQuoteIsNotQuotedAgainWhenTheWalletRefreshes() {
        blockSenderOnQuotes()
        val feature = open()
        enter(feature, "5")
        settle()

        reload(feature)
        settle()

        assertEquals(true, feature.summary.value?.addressBlocked)
        coVerify(exactly = 1) { service.quote(any(), any(), any(), any()) }
        assertEquals(listOf(failedOnAmount("address_blocked")), sent(DEPOSIT_FAILED))
    }

    @Test
    fun aSenderBlockedOnItsOwnQuoteIsNotQuotedAgainWhenTheNetworkIsBack() {
        blockSenderOnQuotes()
        val feature = open()
        enter(feature, "5")
        settle()

        online.value = false
        scheduler.runCurrent()
        online.value = true
        settle()

        assertEquals(true, feature.summary.value?.addressBlocked)
        assertFalse(feature.reviewEnabled.value)
        coVerify(exactly = 1) { service.quote(any(), any(), any(), any()) }
        assertEquals(listOf(failedOnAmount("address_blocked")), sent(DEPOSIT_FAILED))
    }

    @Test
    fun aBlockTheTypedQuoteFoundBeforeTheOpeningProbeIsNotReportedAsBackground() {
        blockSenderOnQuotes()
        coEvery { service.minimum(TestWallet.ID, source) } coAnswers {
            delay(SLOW_PROBE_MS)
            throw PerpsDepositException.AddressBlocked()
        }
        val feature = open()
        enter(feature, "5")
        settle()
        scheduler.advanceTimeBy(SLOW_PROBE_MS)
        settle()

        assertEquals(listOf(failedOnAmount("address_blocked")), sent(DEPOSIT_FAILED))
    }

    @Test
    fun maxOnASenderKnownToBeBlockedTakesTheBalanceWithoutAskingTheProvider() {
        coEvery { service.minimum(TestWallet.ID, source) } throws PerpsDepositException.AddressBlocked()
        val feature = open()
        settle()

        feature.sendAction(PerpsDepositAction.Max)
        settle()

        assertEquals("100", feature.amountInput.value)
        assertEquals(true, feature.summary.value?.addressBlocked)
        coVerify(exactly = 0) { service.maxAmount(any(), any()) }
    }

    @Test
    fun aBlockedSenderIsOfferedAnotherWalletThatCanDepositToPerps() {
        coEvery { service.minimum(TestWallet.ID, source) } throws PerpsDepositException.AddressBlocked()
        val feature = open()
        settle()
        enter(feature, "5")

        assertEquals(true, feature.summary.value?.addressBlocked)
        assertTrue(feature.anotherPerpsWalletAvailable.value)
    }

    @Test
    fun aBlockedSenderIsNotOfferedAnotherWalletWhenNoOtherWalletCanDepositToPerps() {
        coEvery { unifiedAccountRepository.getTonWallets() } returns listOf(otherWallet(supportsPerps = false))
        coEvery { service.minimum(TestWallet.ID, source) } throws PerpsDepositException.AddressBlocked()
        val feature = open()
        settle()
        enter(feature, "5")

        assertEquals(true, feature.summary.value?.addressBlocked)
        assertFalse(feature.anotherPerpsWalletAvailable.value)
        assertFalse(feature.reviewEnabled.value)
    }

    @Test
    fun aMinimumProbeRefusedForTheRegionLeavesForTheRegionSheetWithoutAnError() {
        coEvery { service.minimum(TestWallet.ID, source) } throws regionRestricted()
        val feature = open()
        val events = collectEvents(feature)
        settle()

        assertEquals(listOf<PerpsDepositEvent>(PerpsDepositEvent.RegionRestricted), events)
        assertTrue(sent(DEPOSIT_FAILED).isEmpty())
    }

    @Test
    fun aTypedAmountRefusedForTheRegionLeavesForTheRegionSheetWithoutAnError() {
        coEvery { service.quote(any(), any(), any(), any()) } throws regionRestricted()
        val feature = open()
        val events = collectEvents(feature)
        enter(feature, "5")
        settle()

        assertEquals(listOf<PerpsDepositEvent>(PerpsDepositEvent.RegionRestricted), events)
        assertEquals(PerpsDepositQuotePreview.None, feature.preview.value)
        assertTrue(sent(DEPOSIT_FAILED).isEmpty())
    }

    @Test
    fun aTapWhileThePriceLoadsLeavesForTheRegionSheetWhenTheQuoteIsRefusedForTheRegion() {
        coEvery { service.quote(any(), any(), any(), any()) } coAnswers {
            delay(QUOTE_MS)
            throw regionRestricted()
        }
        val feature = open()
        val events = collectEvents(feature)
        enter(feature, "5")
        review(feature)

        assertTrue(feature.quoting.value)

        settle()

        assertEquals(listOf<PerpsDepositEvent>(PerpsDepositEvent.RegionRestricted), events)
        assertFalse(feature.quoting.value)
    }

    @Test
    fun aRegionRefusedOnBothTheProbeAndTheQuoteLeavesOnce() {
        coEvery { service.minimum(TestWallet.ID, source) } throws regionRestricted()
        coEvery { service.quote(any(), any(), any(), any()) } throws regionRestricted()
        val feature = open()
        val events = collectEvents(feature)
        settle()
        enter(feature, "5")
        settle()

        assertEquals(listOf<PerpsDepositEvent>(PerpsDepositEvent.RegionRestricted), events)
    }

    private fun otherWallet(supportsPerps: Boolean): WalletEntity = mockk {
        every { id } returns OTHER_WALLET_ID
        every { this@mockk.supportsPerps } returns supportsPerps
    }

    private fun blockSenderOnQuotes() {
        coEvery { service.quote(any(), any(), any(), any()) } throws ClientException(
            statusCode = 400,
            response = ClientError<Any>(body = SENDER_BLOCKED_BODY, statusCode = 400),
        )
    }

    private fun regionRestricted() = ClientException(
        statusCode = 400,
        response = ClientError<Any>(body = REGION_RESTRICTED_BODY, statusCode = 400),
    )

    @Test
    fun aPaymentStillInFlightIsReportedOncePerOpening() {
        coEvery { service.pendingPayment(TestWallet.ID) } returns mockk<PerpsFundingRecord>()
        val feature = open()
        enter(feature, "500")
        settle()
        reload(feature)
        settle()

        assertEquals(listOf(failedOnAmount("deposit_in_progress")), sent(DEPOSIT_FAILED))
    }

    private fun open(): PerpsDepositFeature {
        val feature = PerpsDepositFeature(
            sourceScreen = PerpsSourceScreen.PerpsHome,
            service = service,
            accountService = accountService,
            unifiedAccountRepository = unifiedAccountRepository,
            mcAccountRepository = mcAccountRepository,
            settingsRepository = settingsRepository,
            timeProvider = serverTime,
            networkMonitor = networkMonitor,
            savedStateHandle = SavedStateHandle(),
        )
        scheduler.runCurrent()
        return feature
    }

    private fun enter(feature: PerpsDepositFeature, amount: String) {
        feature.sendAction(PerpsDepositAction.SetAmount(amount))
        scheduler.runCurrent()
    }

    private fun toggle(feature: PerpsDepositFeature) {
        feature.sendAction(PerpsDepositAction.ToggleInput)
        scheduler.runCurrent()
    }

    private fun settle() {
        scheduler.advanceTimeBy(DEBOUNCE_MS + QUOTE_MS)
        scheduler.runCurrent()
    }

    private fun review(feature: PerpsDepositFeature) {
        feature.sendAction(PerpsDepositAction.Review)
        scheduler.runCurrent()
    }

    private fun show(feature: PerpsDepositFeature) {
        feature.sendAction(PerpsDepositAction.Shown)
        scheduler.runCurrent()
    }

    private fun multichainWallet(id: String): WalletEntity {
        val wallet = mockk<WalletEntity>()
        every { wallet.supportsPerps } returns true
        every { wallet.id } returns id
        return wallet
    }

    private fun reload(feature: PerpsDepositFeature) {
        feature.sendAction(PerpsDepositAction.Reload)
        scheduler.runCurrent()
    }

    private fun fresh(vararg items: PerpsDepositSource) = PerpsDepositSources(items.toList(), fresh = true)

    private fun usdc(balance: String, walletId: String = TestWallet.ID): PerpsDepositSource {
        return PerpsDepositSource(
            account = testAccount(USDC, balance = balance, walletId = walletId),
            fundingAsset = checkNotNull(PerpsFundingAssetId.parse(PerpsFundingAssetId.USDC_ETHEREUM)),
        )
    }

    private fun coinSource(asset: AssetEntity, balance: String, price: String? = "1"): PerpsDepositSource {
        return PerpsDepositSource(
            account = testAccount(asset, balance = balance, price = price),
            fundingAsset = checkNotNull(PerpsFundingAssetId.parse(asset.id)),
        )
    }

    private fun sent(eventName: String): List<Map<String, Any>> = tracked.filter { it.first == eventName }.map { it.second }

    private fun failedOnAmount(errorType: String): Map<String, Any> = mapOf("screen" to "amount", "error_type" to errorType)

    private fun failedInBackground(): Map<String, Any> = mapOf("screen" to "background", "error_type" to "address_blocked")

    private fun inEuro(account: AccountWithDetails, price: String): AccountWithDetails =
        account.copy(rate = AssetRateEntity(price = price, percentChange24h = "0", currencyCode = "EUR"))

    private fun collectEvents(feature: PerpsDepositFeature): List<PerpsDepositEvent> {
        val events = mutableListOf<PerpsDepositEvent>()
        CoroutineScope(dispatchers.main).launch { feature.events.collect { events += it } }
        return events
    }

    private companion object {
        const val QUOTE_MS = 1_000L
        const val DEBOUNCE_MS = 500L
        const val SLOW_SOURCES_MS = 2_000L
        const val DEVICE_CLOCK_SKEW_MS = 60L * 60L * 1_000L
        val FIVE_USDT: BigInteger = BigInteger.valueOf(5_000_000L)
        const val OTHER_WALLET_ID = "other-wallet"
        const val DEPOSIT_STARTED = "perps_deposit_started"
        const val CONTINUE_CLICKED = "perps_deposit_continue_clicked"
        const val DEPOSIT_FAILED = "perps_deposit_failed"
        const val SLOW_PROBE_MS = 3_000L
        const val SENDER_BLOCKED_BODY = """{"code":"validation_error","retryable":false,"details":{"reason":"invalid_sender_address"}}"""
        const val AMOUNT_TOO_LOW_BODY =
            """{"code":"validation_error","retryable":false,"details":{"reason":"amount_too_low","min_usd_amount":"3.51"}}"""
        const val REGION_RESTRICTED_BODY =
            """{"code":"region_restricted","message":"not available in this region","retryable":false,"details":null}"""
        val USDC = testAsset(PerpsFundingAssetId.USDC_ETHEREUM, symbol = "USDC", name = "USD Coin", decimals = 6)
    }
}
