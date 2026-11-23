package com.tonapps.deposit.multicoin.moonpay

import com.tonapps.deposit.multicoin.moonpay.api.MoonPayAssetSource
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayAssetsPage
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayEnvironment
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayPlatformApi
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayPlatformAsset
import com.tonapps.deposit.multicoin.moonpay.crypto.MoonPayCredentials
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource

class MoonPayAssetRepositoryTest {

    private val connection = MoonPayConnection(
        environment = MoonPayEnvironment.Production,
        credentials = MoonPayCredentials("access", "client", "2030-01-01T00:00:00Z"),
    )

    private val eth = MoonPayPlatformAsset(code = "eth", network = "ethereum")
    private val token = MoonPayPlatformAsset(
        caip19 = "eip155:1/erc20:0xabc",
        code = "abc",
        network = "ethereum",
        source = MoonPayAssetSource.Defi,
        contractAddress = "0xabc",
    )

    private val api = mockk<MoonPayPlatformApi> {
        coEvery { assets("access", MoonPayAssetSource.Cefi, null, null) } returns
            Result.success(MoonPayAssetsPage(listOf(eth), nextCursor = "next"))
        coEvery { assets("access", MoonPayAssetSource.Cefi, null, "next") } returns
            Result.success(MoonPayAssetsPage(emptyList(), nextCursor = null))
        coEvery { assets("access", MoonPayAssetSource.Defi, "ABC", null) } returns
            Result.success(MoonPayAssetsPage(listOf(token), nextCursor = null))
    }

    private val clock = TestTimeSource()
    private val repository = MoonPayAssetRepository({ api }, clock)

    @Test
    fun `an asset sold on the exchange is found without a token lookup, and the list is kept`() = runTest {
        assertEquals(eth, repository.find(connection, "eth/mainnet/coin", "ETH").getOrThrow())
        assertEquals(eth, repository.find(connection, "eth/mainnet/coin", "ETH").getOrThrow())

        coVerify(exactly = 2) { api.assets(any(), MoonPayAssetSource.Cefi, any(), any()) }
        coVerify(exactly = 0) { api.assets(any(), MoonPayAssetSource.Defi, any(), any()) }
    }

    @Test
    fun `a token the exchange does not list is looked up by its ticker`() = runTest {
        assertEquals(token, repository.find(connection, "eth/mainnet/erc20/0xABC", "ABC").getOrThrow())
    }

    @Test
    fun `an asset MoonPay does not sell is none`() = runTest {
        coEvery { api.assets("access", MoonPayAssetSource.Defi, "XYZ", null) } returns
            Result.success(MoonPayAssetsPage(emptyList(), nextCursor = null))

        assertNull(repository.find(connection, "eth/mainnet/erc20/0xdef", "XYZ").getOrThrow())
    }

    @Test
    fun `a token lookup still loading keeps no other ticker waiting`() = runTest {
        val slow = CompletableDeferred<Result<MoonPayAssetsPage>>()
        coEvery { api.assets("access", MoonPayAssetSource.Defi, "SLOW", null) } coAnswers { slow.await() }
        repository.find(connection, "eth/mainnet/coin", "ETH")
        val waiting = async { repository.find(connection, "eth/mainnet/erc20/0xdef", "SLOW") }
        runCurrent()

        assertEquals(token, repository.find(connection, "eth/mainnet/erc20/0xABC", "ABC").getOrThrow())
        assertFalse(waiting.isCompleted)
        slow.complete(Result.success(MoonPayAssetsPage(emptyList(), nextCursor = null)))
        assertNull(waiting.await().getOrThrow())
    }

    @Test
    fun `the lists are loaded again after a quarter of an hour`() = runTest {
        repository.find(connection, "eth/mainnet/coin", "ETH")
        clock += 14.minutes
        repository.find(connection, "eth/mainnet/coin", "ETH")
        coVerify(exactly = 1) { api.assets(any(), MoonPayAssetSource.Cefi, null, null) }

        clock += 2.minutes
        repository.find(connection, "eth/mainnet/coin", "ETH")

        coVerify(exactly = 2) { api.assets(any(), MoonPayAssetSource.Cefi, null, null) }
    }

    @Test
    fun `a failed load is a failure and is tried again next time`() = runTest {
        coEvery { api.assets("access", MoonPayAssetSource.Cefi, null, null) } returns Result.failure(IOException("offline"))

        assertTrue(repository.find(connection, "eth/mainnet/coin", "ETH").isFailure)
        assertTrue(repository.find(connection, "eth/mainnet/coin", "ETH").isFailure)

        coVerify(exactly = 2) { api.assets(any(), MoonPayAssetSource.Cefi, null, null) }
    }
}
