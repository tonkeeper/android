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

    private companion object {
        const val GET_POSITIONS = "GET /positions"
    }
}
