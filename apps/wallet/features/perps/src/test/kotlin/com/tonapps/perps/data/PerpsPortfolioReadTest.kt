package com.tonapps.perps.data

import com.tonapps.perps.funding.PerpsFundingTestEnv
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.ACCOUNT_INDEX
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.GET_ACCOUNT
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.GET_PORTFOLIO
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.HTTP_BAD_REQUEST
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.WALLET_ID
import com.tonapps.perps.trade.readPortfolio
import com.tonapps.wallet.testing.BaseUnitTest
import io.mockk.coEvery
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

class PerpsPortfolioReadTest : BaseUnitTest() {

    private lateinit var env: PerpsFundingTestEnv

    @Before
    fun setUp() {
        env = PerpsFundingTestEnv()
    }

    @Test
    fun aPortfolioReadWithoutTheReadOnlyTokenTakesItsBalanceFromTheAccount() = runTest {
        coEvery { env.accountService.isActivated(WALLET_ID) } returns false
        env.answer(GET_PORTFOLIO) { env.refused(HTTP_BAD_REQUEST, code = "readonly_token_unavailable", reason = null) }
        env.answer(GET_POSITIONS) { env.ok(buildJsonObject { putJsonArray("positions") {} }) }
        env.answer(GET_ACCOUNT) {
            env.ok(
                buildJsonObject {
                    put("account_index", ACCOUNT_INDEX)
                    put("collateral", "150")
                    put("available_balance", "120")
                },
            )
        }

        val portfolio = checkNotNull(env.accountService.readPortfolio(env.repository, WALLET_ID).getOrNull())

        assertEquals(BigDecimal("120"), portfolio.balance?.availableBalance)
        assertEquals(BigDecimal("150"), portfolio.balance?.collateral)
        assertNull(portfolio.openOrders)
        assertEquals(listOf(GET_ACCOUNT, GET_PORTFOLIO, GET_PORTFOLIO, GET_POSITIONS), env.endpoints.sorted())
    }

    @Test
    fun duplicatePositionsAndOrdersFromTheBackendCollapseToOneRowEach() = runTest {
        env.answer(GET_PORTFOLIO) {
            env.ok(
                buildJsonObject {
                    putJsonArray("positions") {
                        add(position(marketIndex = 1, side = "long", margin = "10"))
                        add(position(marketIndex = 1, side = "long", margin = "11"))
                        add(position(marketIndex = 1, side = "short", margin = "12"))
                    }
                    putJsonArray("open_orders") {
                        add(order(marketIndex = 1, orderIndex = 7, price = "100"))
                        add(order(marketIndex = 1, orderIndex = 7, price = "101"))
                        add(order(marketIndex = 2, orderIndex = 7, price = "102"))
                    }
                },
            )
        }
        env.answer(GET_POSITIONS) {
            env.ok(
                buildJsonObject {
                    putJsonArray("positions") {
                        add(position(marketIndex = 3, side = "short", margin = "5"))
                        add(position(marketIndex = 3, side = "short", margin = "6"))
                    }
                },
            )
        }

        val portfolio = checkNotNull(env.repository.getPortfolio(WALLET_ID).getOrNull())
        val positions = checkNotNull(env.repository.getOpenPositions(WALLET_ID).getOrNull())

        assertEquals(
            listOf(
                Triple(1, PerpsPositionSide.LONG, BigDecimal("10")),
                Triple(1, PerpsPositionSide.SHORT, BigDecimal("12")),
            ),
            portfolio.positions.map { Triple(it.marketIndex, it.side, it.margin) },
        )
        assertEquals(
            listOf(Triple(1, 7L, BigDecimal("100")), Triple(2, 7L, BigDecimal("102"))),
            portfolio.openOrders.orEmpty().map { Triple(it.marketIndex, it.orderIndex, it.limitPrice) },
        )
        assertEquals(listOf(BigDecimal("5")), positions.map { it.margin })
    }

    @Test
    fun aRestingOrderCarriesTheTakeProfitAndStopLossPlacedWithIt() = runTest {
        env.answer(GET_PORTFOLIO) {
            env.ok(
                buildJsonObject {
                    putJsonArray("positions") {}
                    putJsonArray("open_orders") {
                        add(
                            buildJsonObject {
                                put("order_index", 7)
                                put("market_index", 3)
                                put("symbol", "DOGE")
                                put("side", "short")
                                put("type", "limit")
                                put("status", "open")
                                put("category", "open")
                                put("base_size", "164")
                                put("price", "0.09")
                                put("leverage", 15)
                                put("margin", "0.98")
                                putJsonObject("auto_close") {
                                    putJsonObject("take_profit") {
                                        put("order_index", 8)
                                        put("trigger_price", "0.082")
                                    }
                                    putJsonObject("stop_loss") {
                                        put("order_index", 9)
                                        put("trigger_price", "0.000")
                                    }
                                }
                            },
                        )
                    }
                },
            )
        }

        val order = checkNotNull(env.repository.getPortfolio(WALLET_ID).getOrNull()?.openOrders?.single())

        assertEquals(PerpsLimitOrderLeg(orderIndex = 8, triggerPrice = BigDecimal("0.082")), order.takeProfit)
        assertNull(order.stopLoss)
    }

    private fun position(marketIndex: Int, side: String, margin: String) = buildJsonObject {
        put("market_index", marketIndex)
        put("symbol", "ETH")
        put("side", side)
        put("margin", margin)
    }

    private fun order(marketIndex: Int, orderIndex: Long, price: String) = buildJsonObject {
        put("order_index", orderIndex)
        put("market_index", marketIndex)
        put("symbol", "ETH")
        put("side", "long")
        put("type", "limit")
        put("status", "open")
        put("category", "open")
        put("base_size", "1")
        put("price", price)
    }

    private companion object {
        const val GET_POSITIONS = "GET /positions"
    }
}
