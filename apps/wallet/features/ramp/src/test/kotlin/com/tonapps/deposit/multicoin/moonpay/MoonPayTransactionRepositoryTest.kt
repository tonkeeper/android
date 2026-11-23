package com.tonapps.deposit.multicoin.moonpay

import com.tonapps.deposit.multicoin.moonpay.api.MoonPayApiException
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayEnvironment
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayPlatformApi
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayTransactionDetails
import com.tonapps.deposit.multicoin.moonpay.crypto.MoonPayCredentials
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MoonPayTransactionRepositoryTest {

    private val connection = MoonPayConnection(
        environment = MoonPayEnvironment.Production,
        credentials = MoonPayCredentials("access", "client", "2030-01-01T00:00:00Z"),
    )

    private val connections = mockk<MoonPayConnectionRepository>(relaxUnitFun = true) {
        coEvery { ensureConnected(any(), interactive = false) } returns Result.success(connection)
    }

    private val answers = ArrayDeque<Result<MoonPayTransactionDetails>>()

    private val api = mockk<MoonPayPlatformApi> {
        coEvery { transaction("access", "tx-1") } answers { answers.removeFirst() }
    }

    private val repository = MoonPayTransactionRepository(connections) { api }

    @Test
    fun `a refused token connects again once`() = runTest {
        answers += listOf(unauthorized(), Result.success(MoonPayTransactionDetails(id = "tx-1", status = "completed")))

        assertEquals("completed", repository.transaction("tx-1", theme = null).getOrThrow().status)
        verify(exactly = 1) { connections.invalidate(connection) }
    }

    @Test
    fun `a token refused after a fresh connect is final`() = runTest {
        answers += listOf(unauthorized(), unauthorized())

        val error = repository.transaction("tx-1", theme = null).exceptionOrNull()

        assertEquals(401, (error as MoonPayApiException.Http).status)
        coVerify(exactly = 2) { api.transaction(any(), any()) }
    }

    @Test
    fun `other failures are not retried`() = runTest {
        answers += Result.failure(MoonPayApiException.Http(404, null))

        repository.transaction("tx-1", theme = null)

        verify(exactly = 0) { connections.invalidate(any()) }
        coVerify(exactly = 1) { api.transaction(any(), any()) }
    }

    private fun unauthorized(): Result<MoonPayTransactionDetails> = Result.failure(MoonPayApiException.Http(401, null))
}
