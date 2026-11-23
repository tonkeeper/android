package com.tonapps.perps.screens.margin

import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.perps.data.PerpsLivePrices
import com.tonapps.perps.data.PerpsOrderSide
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.perps.data.formatUsdAmount
import com.tonapps.perps.data.perpsTicker
import com.tonapps.perps.trade.PerpsPlannerAdapter
import com.tonapps.perps.trade.PerpsSignedMarkets
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestWallet
import io.infrastructure.Success
import io.mockk.every
import io.mockk.mockk
import io.perpsapi.models.Balance
import io.perpsapi.models.Integrator
import io.perpsapi.models.Market
import io.perpsapi.models.OpenPosition
import io.perpsapi.models.OpenPositionDetail
import io.perpsapi.models.Position
import io.perpsapi.models.PositionSide
import io.perpsapi.models.TradingFlags
import io.perpsapi.models.TradingScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

class PerpsMarginChangeFeatureTest : BaseUnitTest() {

    private val api = mockk<API>()
    private val repository = PerpsRepository(api, mockk(), mockk(), mockk(relaxed = true))
    private val adapter = PerpsPlannerAdapter(
        repository = repository,
        accountService = mockk(),
        clientOrderIndex = mockk(),
        mediator = mockk(),
        securityConfig = PerpsSignedMarkets.listing(litMarket),
    )
    private val livePrices = mockk<PerpsLivePrices>()
    private val unifiedAccountRepository = mockk<UnifiedAccountRepository>()
    private val settingsRepository = mockk<SettingsRepository> {
        every { hiddenBalances } returns false
        every { hiddenBalancesFlow } returns MutableStateFlow(false)
    }

    private val scheduler get() = dispatchers.scheduler

    @Before
    fun setUp() {
        val wallet = mockk<WalletEntity>()
        every { wallet.type } returns WalletType.Multichain
        every { wallet.id } returns TestWallet.ID
        every { wallet.supportsPerps } returns true
        every { unifiedAccountRepository.selectedTonWalletFlow } returns flowOf(wallet)
        every { livePrices.observe(any()) } returns MutableStateFlow(mapOf(perpsTicker(SYMBOL) to BigDecimal(MARK)))
        every { api.perps.integrator.getIntegrator(xWalletId = any()) } returns Integrator(
            integratorAccountIndex = 0,
            integratorTakerFee = 0,
            integratorMakerFee = 0,
            maxPerpsTakerFee = 0,
            maxPerpsMakerFee = 0,
            maxSpotTakerFee = 0,
            maxSpotMakerFee = 0,
            approvalTtlMs = 0,
        )
        every { api.perps.positions.getOpenPosition(xWalletId = TestWallet.ID, id = POSITION_ID) } returns
            OpenPositionDetail(position = position, autoCloseKnown = true)
    }

    @Test
    fun balanceBelowTheMinimumIsShownAsAvailableInsteadOfTheZeroCeiling() {
        val feature = open(balance = "0.215")

        assertEquals(formatUsdAmount(BigDecimal("0.215")), feature.availableFormat.value)

        enter(feature, "0.2")
        assertEquals(PerpsMarginChangeIssue.MinAmount(BigDecimal("1.000000")), feature.issue.value)

        enter(feature, "2")
        assertEquals(PerpsMarginChangeIssue.InsufficientBalance, feature.issue.value)
        assertFalse(feature.canReview.value)
    }

    @Test
    fun addingMarginIsBoundByTheFreeBalanceNotTheOneXCeiling() {
        val feature = open(balance = "50")

        assertEquals(formatUsdAmount(BigDecimal("50")), feature.availableFormat.value)

        enter(feature, "20")
        assertNull(feature.issue.value)

        enter(feature, "60")
        assertEquals(PerpsMarginChangeIssue.InsufficientBalance, feature.issue.value)
        assertFalse(feature.canReview.value)
    }

    private fun open(balance: String): PerpsMarginChangeFeature {
        val screen = TradingScreen(
            market = litMarket,
            balance = Balance(collateral = balance, availableBalance = balance),
            flags = TradingFlags(addMarginEnabled = true, removeMarginEnabled = true),
            position = screenPosition,
        )
        every { api.perps.screens.getTradingScreen(MARKET_INDEX, TestWallet.ID) } returns screen
        every { api.perps.screens.getTradingScreenWithHttpInfo(MARKET_INDEX, TestWallet.ID) } returns Success(screen)
        val feature = PerpsMarginChangeFeature(
            marketIndex = MARKET_INDEX,
            symbol = SYMBOL,
            side = PerpsOrderSide.SHORT,
            add = true,
            repository = repository,
            adapter = adapter,
            livePrices = livePrices,
            unifiedAccountRepository = unifiedAccountRepository,
            settingsRepository = settingsRepository,
        )
        scheduler.runCurrent()
        return feature
    }

    private fun enter(feature: PerpsMarginChangeFeature, amount: String) {
        feature.sendAction(PerpsMarginChangeAction.SetAmount(amount))
        scheduler.runCurrent()
    }

    private companion object {
        const val MARKET_INDEX = 120
        const val SYMBOL = "LIT"
        const val POSITION_ID = "lighter:120"
        const val MARK = "3.62"

        val position = OpenPosition(
            id = POSITION_ID,
            marketIndex = MARKET_INDEX,
            symbol = SYMBOL,
            side = PositionSide.short,
            propertySize = "5.5",
            positionValue = "19.79",
            avgEntryPrice = "3.60",
            markPrice = MARK,
            liquidationPrice = "3.89",
            margin = "3.96",
            leverage = 5,
        )

        val screenPosition = Position(
            marketIndex = MARKET_INDEX,
            symbol = SYMBOL,
            side = PositionSide.short,
            propertySize = "5.5",
            avgEntryPrice = "3.60",
            markPrice = MARK,
            positionValue = "19.79",
            liquidationPrice = "3.89",
            allocatedMargin = "3.96",
            leverage = 5,
        )

        val litMarket = Market(
            marketIndex = MARKET_INDEX,
            symbol = SYMBOL,
            status = "active",
            priceDecimals = 5,
            sizeDecimals = 1,
            tickSize = "0.0001",
            stepSize = "0.1",
            minSizeBase = "0.1",
            minSizeQuote = "1",
            maxLeverage = 10,
            makerFeePct = "0",
            takerFeePct = "0.05",
            initialMarginFraction = 1000,
            maintenanceMarginFraction = 500,
            markPrice = MARK,
        )
    }
}
