package com.tonapps.perps.screens.details

import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.core.helper.EnvironmentHelper
import com.tonapps.perps.data.PerpsAccountRefresh
import com.tonapps.perps.data.PerpsCandleFeed
import com.tonapps.perps.data.PerpsLivePrices
import com.tonapps.perps.data.PerpsPortfolioStore
import com.tonapps.perps.data.PERPS_SLOW_POLL_MS
import com.tonapps.perps.data.PerpsPositionSection
import com.tonapps.perps.data.PerpsRealtimeTestEnv
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.perps.trade.PerpsPlannerAdapter
import com.tonapps.perps.trade.PerpsSignedMarkets
import com.tonapps.perps.trade.PerpsTradeExecutor
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.ConfigEntity
import com.tonapps.wallet.data.loyalty.LoyaltyEntry
import com.tonapps.wallet.data.loyalty.LoyaltyRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.multichain.perps.PerpsOperationKind
import com.tonapps.wallet.data.multichain.perps.PerpsOperationRecord
import com.tonapps.wallet.data.multichain.perps.PerpsOperationStatus
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestWallet
import io.infrastructure.ClientException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.perpsapi.apis.PositionsApi
import io.perpsapi.apis.ScreensApi
import io.perpsapi.models.Balance
import io.perpsapi.models.OpenOrdersPage
import io.perpsapi.models.OpenPosition
import io.perpsapi.models.OpenPositionDetail
import io.perpsapi.models.Order
import io.perpsapi.models.OrderCategory
import io.perpsapi.models.OrderSide
import io.perpsapi.models.OrderStatus
import io.perpsapi.models.OrderType
import io.perpsapi.models.PositionSide
import io.perpsapi.models.TradingFlags
import io.perpsapi.models.TradingScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class PerpsAssetDetailsFeatureTest : BaseUnitTest() {

    private val api = mockk<API>()
    private val positionsApi = mockk<PositionsApi>()
    private val screensApi = mockk<ScreensApi>()
    private val candleFeed = mockk<PerpsCandleFeed> {
        every { stream(any(), any()) } returns emptyFlow()
    }
    private val repository = PerpsRepository(api, candleFeed, mockk<EnvironmentHelper>(), mockk(relaxed = true))
    private val adapter = PerpsPlannerAdapter(
        repository = repository,
        accountService = mockk(),
        clientOrderIndex = mockk(),
        mediator = mockk(),
        securityConfig = PerpsSignedMarkets.listing(),
    )
    private val executor = mockk<PerpsTradeExecutor>()
    private val livePrices = mockk<PerpsLivePrices>()
    private val unifiedAccountRepository = mockk<UnifiedAccountRepository>()
    private val passcodeManager = mockk<PasscodeManager>()
    private val loyaltyRepository = mockk<LoyaltyRepository> {
        every { entryFlow(any()) } returns flowOf(LoyaltyEntry.Hidden)
    }
    private val settingsRepository = mockk<SettingsRepository> {
        every { hiddenBalances } returns false
        every { hiddenBalancesFlow } returns MutableStateFlow(false)
    }
    private val accountRefresh = PerpsAccountRefresh()
    private val portfolioStore = mockk<PerpsPortfolioStore> {
        every { snapshot } returns MutableStateFlow(null)
    }
    private val operations = MutableStateFlow(emptyList<PerpsOperationRecord>())
    private val realtimeEnv = PerpsRealtimeTestEnv(CoroutineScope(dispatchers.io))
    private var position: () -> OpenPositionDetail = { throw ClientException("Client error : 404", HTTP_NOT_FOUND) }
    private var orders = emptyList<Order>()

    private val scheduler get() = dispatchers.scheduler

    @Before
    fun setUp() {
        val wallet = mockk<WalletEntity>()
        every { wallet.type } returns WalletType.Multichain
        every { wallet.id } returns TestWallet.ID
        every { wallet.supportsPerps } returns true
        every { unifiedAccountRepository.selectedTonWalletFlow } returns flowOf(wallet)
        every { livePrices.observe(any()) } returns flowOf(emptyMap())
        every { passcodeManager.lockscreenHiddenFlow } returns MutableStateFlow(true)
        every { executor.observe(TestWallet.ID) } returns operations
        every { executor.unnotifiedFailures } returns MutableStateFlow(emptySet())
        every { executor.unnotifiedUnknowns } returns MutableStateFlow(emptySet())
        every { api.configFlow } returns emptyFlow()
        every { api.getConfig(any()) } returns mockk<ConfigEntity>(relaxed = true)
        every { api.perps.positions } returns positionsApi
        every { api.perps.screens } returns screensApi
        every { screensApi.getTradingScreen(MARKET_INDEX, TestWallet.ID) } returns TradingScreen(
            balance = Balance(collateral = "10"),
            flags = TradingFlags(openEnabled = true),
        )
        every { api.perps.orders.listOpenOrders(TestWallet.ID) } answers { OpenOrdersPage(orders = orders) }
        every { positionsApi.getOpenPosition(POSITION_ID, TestWallet.ID) } answers { position() }
    }

    @Test
    fun aMarketWithoutAPositionIsNotPolled() {
        open()

        advance(SILENT_REFRESH_MS - 1)

        verifyPositionReads(1)
    }

    @Test
    fun aMarketWithoutAPositionIsReadAgainWithTheTradingState() {
        open()

        advance(SILENT_REFRESH_MS)

        verifyPositionReads(2)
        verify(exactly = 2) { screensApi.getTradingScreen(MARKET_INDEX, TestWallet.ID) }
    }

    @Test
    fun anOpenPositionWithoutRealtimeIsReadAtTheSlowPeriod() {
        position = { detail() }
        open()

        advance(PERPS_SLOW_POLL_MS * 2)

        verifyPositionReads(3)
    }

    @Test
    fun anOpenPositionWithRealtimeIsReadOnItsMarketEventsAndAfterALongSilence() {
        subscribeRealtime()
        position = { detail() }
        open()
        advance(QUIET_MS)

        verifyPositionReads(1)

        realtimeEnv.publishMarket(TestWallet.ID, marketIndex = MARKET_INDEX + 1, seq = 1)
        realtimeEnv.publish(TestWallet.ID, "perps.deposit", seq = 2)
        advance(EVENT_SETTLE_MS)

        verifyPositionReads(1)

        realtimeEnv.publishMarket(TestWallet.ID, marketIndex = MARKET_INDEX, seq = 3)
        advance(EVENT_SETTLE_MS)

        verifyPositionReads(2)

        advance(PERPS_SLOW_POLL_MS)

        verifyPositionReads(3)
    }

    @Test
    fun aRestingOrderWithRealtimeIsReadAgainOnAccountEventsInsteadOfATimer() {
        subscribeRealtime()
        orders = listOf(restingOrder())
        open()
        advance(PERPS_SLOW_POLL_MS * 2)

        verify(exactly = 1) { screensApi.getTradingScreen(MARKET_INDEX, TestWallet.ID) }

        realtimeEnv.publish(TestWallet.ID, "perps.account", seq = 1)
        advance(EVENT_SETTLE_MS)

        verify(exactly = 2) { screensApi.getTradingScreen(MARKET_INDEX, TestWallet.ID) }
    }

    @Test
    fun aPositionClosedOnScreenStopsThePolling() {
        position = { detail() }
        open()
        advance(PERPS_SLOW_POLL_MS)

        position = { throw ClientException("Client error : 404", HTTP_NOT_FOUND) }
        advance(PERPS_SLOW_POLL_MS)
        advance(SILENT_REFRESH_MS - PERPS_SLOW_POLL_MS * 2 - 1)

        verifyPositionReads(3)
    }

    @Test
    fun aFailingReadBacksOff() {
        position = { throw IOException() }
        open()

        advance(14_000)

        verifyPositionReads(4)
    }

    @Test
    fun anOperationOnTheMarketReadsThePositionAsItProgresses() {
        val feature = open()

        operations.value = listOf(operation(PerpsOperationStatus.IN_PROGRESS))
        advance(0)

        verifyPositionReads(2)

        position = { detail() }
        operations.value = listOf(operation(PerpsOperationStatus.SUCCEEDED))
        advance(0)

        verifyPositionReads(3)
        assertTrue(feature.positionView.value.section is PerpsPositionSection.Open)
    }

    @Test
    fun anOperationLongUnknownDoesNotPollThePositionEveryTwoSeconds() {
        position = { detail() }
        operations.value = listOf(
            operation(PerpsOperationStatus.UNKNOWN, updatedAt = System.currentTimeMillis() - STALE_UPDATE_MS)
        )
        open()

        advance(QUIET_MS)

        verifyPositionReads(1)
    }

    @Test
    fun aMissingPositionWithALongUnknownOperationIsReadAgainOnAnAccountEvent() {
        subscribeRealtime()
        operations.value = listOf(
            operation(PerpsOperationStatus.UNKNOWN, updatedAt = System.currentTimeMillis() - STALE_UPDATE_MS)
        )
        open()
        advance(QUIET_MS)

        verifyPositionReads(1)

        realtimeEnv.publish(TestWallet.ID, "perps.account", seq = 1)
        advance(EVENT_SETTLE_MS)

        verifyPositionReads(2)
    }

    @Test
    fun anAccountChangeReadsThePositionAgain() {
        open()

        accountRefresh.notifyChanged()
        scheduler.runCurrent()

        verifyPositionReads(2)
    }

    @Test
    fun aFailedReadWithoutAPositionRetriesWithBackoff() {
        open()

        position = { throw IOException() }
        advance(SILENT_REFRESH_MS)
        advance(4_000)

        verifyPositionReads(3)

        position = { throw ClientException("Client error : 404", HTTP_NOT_FOUND) }
        advance(8_000)
        advance(60_000)

        verifyPositionReads(4)
    }

    @Test
    fun aFilledLimitOrderShowsThePositionWithoutWaitingForTheSilentRefresh() {
        orders = listOf(restingOrder())
        val feature = open()
        advance(PERPS_SLOW_POLL_MS)

        orders = emptyList()
        advance(PERPS_SLOW_POLL_MS)
        position = { detail() }
        advance(PERPS_SLOW_POLL_MS)

        assertTrue(feature.positionView.value.section is PerpsPositionSection.Open)
    }

    @Test
    fun aHiddenScreenDoesNotReadTheOpenPosition() {
        position = { detail() }
        val feature = open()

        feature.sendAction(PerpsAssetDetailsAction.SetVisible(false))
        advance(60_000)

        verifyPositionReads(1)
    }

    private fun open(): PerpsAssetDetailsFeature {
        val feature = PerpsAssetDetailsFeature(
            marketIndex = MARKET_INDEX,
            initialSymbol = SYMBOL,
            repository = repository,
            livePrices = livePrices,
            accountRefresh = accountRefresh,
            executor = executor,
            adapter = adapter,
            accountService = mockk(),
            unifiedAccountRepository = unifiedAccountRepository,
            passcodeManager = passcodeManager,
            settingsRepository = settingsRepository,
            api = api,
            portfolioStore = portfolioStore,
            realtime = realtimeEnv.realtime,
            loyaltyRepository = loyaltyRepository,
        )
        scheduler.runCurrent()
        return feature
    }

    private fun subscribeRealtime() {
        realtimeEnv.connect(TestWallet.ID)
        scheduler.runCurrent()
        realtimeEnv.confirmSubscription(TestWallet.ID)
        scheduler.runCurrent()
    }

    private fun advance(millis: Long) {
        scheduler.advanceTimeBy(millis)
        scheduler.runCurrent()
    }

    private fun verifyPositionReads(count: Int) {
        verify(exactly = count) { positionsApi.getOpenPosition(POSITION_ID, TestWallet.ID) }
    }

    private fun operation(
        status: PerpsOperationStatus,
        updatedAt: Long = System.currentTimeMillis(),
    ) = PerpsOperationRecord(
        operationId = "operation",
        walletId = TestWallet.ID,
        marketIndex = MARKET_INDEX,
        kind = PerpsOperationKind.OPEN,
        side = "LONG",
        marginQuote = "10",
        leverage = 5,
        reviewJson = null,
        planDigest = null,
        expectedBaseSize = null,
        baseBefore = null,
        marginBefore = null,
        status = status,
        error = null,
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )

    private fun restingOrder() = Order(
        orderIndex = 1L,
        marketIndex = MARKET_INDEX,
        symbol = SYMBOL,
        side = OrderSide.long,
        type = OrderType.limit,
        status = OrderStatus.open,
        category = OrderCategory.open,
        baseSize = "0.03",
        price = "1000",
    )

    private fun detail() = OpenPositionDetail(
        position = OpenPosition(
            id = POSITION_ID,
            marketIndex = MARKET_INDEX,
            symbol = SYMBOL,
            side = PositionSide.long,
            propertySize = "1",
            avgEntryPrice = "100",
            margin = "20",
            leverage = 5,
        ),
        autoCloseKnown = true,
    )

    private companion object {
        const val MARKET_INDEX = 90
        const val POSITION_ID = "lighter:90"
        const val SYMBOL = "ZEC"
        const val SILENT_REFRESH_MS = 120_000L
        const val HTTP_NOT_FOUND = 404
        const val EVENT_SETTLE_MS = 1_000L
        const val QUIET_MS = 10_000L
        const val STALE_UPDATE_MS = 60_000L
    }
}
