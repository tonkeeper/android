package com.tonapps.perps.trade

import com.tonapps.perps.data.PerpsOrderSide
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.wallet.data.multichain.perps.PerpsOperationStatus
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.perpsapi.models.ClientMetricPositionOpen
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal
import kotlin.time.TimeSource

class PerpsRunningOperationsTest {

    private val repository = mockk<PerpsRepository> {
        coEvery { recordClientMetric(any(), any()) } just Runs
    }

    @Test
    fun `a market open that succeeds sends its wait once`() = runTest {
        val operations = operations()
        operations.start(OPERATION_ID, marketOpen(), TimeSource.Monotonic.markNow())

        operations.reportSettled(OPERATION_ID, PerpsOperationStatus.SUCCEEDED)
        advanceUntilIdle()
        coVerify(exactly = 1) {
            repository.recordClientMetric(WALLET_ID, match { it is ClientMetricPositionOpen && it.type == "position_open" })
        }

        operations.reportSettled(OPERATION_ID, PerpsOperationStatus.SUCCEEDED)
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.recordClientMetric(any(), any()) }
    }

    @Test
    fun `a partial fill, a failure and a finished operation send nothing`() = runTest {
        val operations = operations()
        operations.start(OPERATION_ID, marketOpen(), TimeSource.Monotonic.markNow())

        operations.reportSettled(OPERATION_ID, PerpsOperationStatus.PARTIALLY_FILLED)
        operations.reportSettled(OPERATION_ID, PerpsOperationStatus.FAILED)
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.recordClientMetric(any(), any()) }

        operations.finish(OPERATION_ID)
        operations.reportSettled(OPERATION_ID, PerpsOperationStatus.SUCCEEDED)
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.recordClientMetric(any(), any()) }
    }

    @Test
    fun `an operation this process never started sends nothing`() = runTest {
        val operations = operations()

        operations.reportSettled(OPERATION_ID, PerpsOperationStatus.SUCCEEDED)
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.recordClientMetric(any(), any()) }
    }

    private fun TestScope.operations() = PerpsRunningOperations(repository, this)

    private fun marketOpen() = PerpsTradeRequest.Open(
        walletId = WALLET_ID,
        marketIndex = 1,
        side = PerpsOrderSide.LONG,
        marginQuote = BigDecimal.TEN,
        leverage = 5,
        autoClose = null,
        limitPrice = null,
    )

    private companion object {
        const val WALLET_ID = "wallet"
        const val OPERATION_ID = "operation"
    }
}
