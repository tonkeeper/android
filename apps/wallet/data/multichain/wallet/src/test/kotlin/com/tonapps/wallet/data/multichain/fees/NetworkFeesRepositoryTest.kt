package com.tonapps.wallet.data.multichain.fees

import com.tonapps.extensions.DateTimeProvider
import com.tonapps.wallet.api.API
import io.batteryapi.models.NetworkFee
import io.batteryapi.models.NetworkFeeAmounts
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicInteger
import io.batteryapi.models.NetworkFees as NetworkFeesResponse

/**
 * MAX keeps `safe` of Battery's `/network-fees` only for an asset whose fee is paid in itself, and the
 * response is asked for once per five minutes for the whole app.
 */
class NetworkFeesRepositoryTest {

    private var now = 0L
    private var answer: NetworkFeesResponse? = response(ethCoin, usdtEth)
    private val requests = AtomicInteger()
    private val api = mockk<API> {
        every { getBatteryNetworkFees() } answers {
            requests.incrementAndGet()
            answer
        }
    }
    private val repository = NetworkFeesRepository(api, DateTimeProvider { now })

    @Test
    fun aCoinPayingItsOwnFeeKeepsItsSafeFee() = runTest {
        val fees = repository.load()!!

        assertEquals(BigInteger("54600000000000"), fees.swapReserve(ETH))
        assertEquals(BigInteger("1843400000000000"), fees.perpsReserve(ETH))
    }

    @Test
    fun aTokenWhoseFeeIsPaidInTheCoinKeepsNothing() = runTest {
        val fees = repository.load()!!

        assertNull(fees.swapReserve(USDT_ETH))
        assertNull(fees.perpsReserve(USDT_ETH))
    }

    @Test
    fun anAssetTheResponseDoesNotListKeepsNothing() = runTest {
        assertNull(repository.load()!!.swapReserve("ton/mainnet/coin"))
    }

    @Test
    fun aCoinWithoutPerpsKeepsItsSwapFeeOnly() = runTest {
        answer = response(solCoin)

        val fees = repository.load()!!

        assertEquals(BigInteger("1950000"), fees.swapReserve(SOL))
        assertNull(fees.perpsReserve(SOL))
    }

    @Test
    fun anAmountThatIsNotAnIntegerDropsItsEntry() = runTest {
        answer = response(ethCoin.copy(swap = amounts(safe = "0.5")), solCoin)

        val fees = repository.load()!!

        assertNull(fees.swapReserve(ETH))
        assertEquals(BigInteger("1950000"), fees.swapReserve(SOL))
    }

    @Test
    fun aSecondLoadWithinFiveMinutesAsksNothing() = runTest {
        val first = repository.load()
        now += NetworkFeesRepository.TTL - 1
        val second = repository.load()

        assertSame(first, second)
        assertEquals(1, requests.get())
    }

    @Test
    fun aLoadAfterFiveMinutesAsksAgain() = runTest {
        repository.load()
        now += NetworkFeesRepository.TTL
        answer = response(solCoin)
        val fees = repository.load()!!

        assertEquals(2, requests.get())
        assertNull(fees.swapReserve(ETH))
        assertEquals(fees, repository.fees.value)
    }

    @Test
    fun concurrentLoadsWaitForOneRequest() = runTest {
        every { api.getBatteryNetworkFees() } answers {
            requests.incrementAndGet()
            Thread.sleep(SLOW_RESPONSE_MS)
            answer
        }

        val loaded = List(LOADS) { async { repository.load() } }.awaitAll()

        assertEquals(1, requests.get())
        assertEquals(1, loaded.distinct().size)
    }

    @Test
    fun aFailedLoadFallsBackToTheLastResponse() = runTest {
        val last = repository.load()
        now += NetworkFeesRepository.TTL
        answer = null

        assertSame(last, repository.load())
        assertEquals(2, requests.get())
    }

    @Test
    fun aFailedLoadOfAThrowingClientFallsBackToTheLastResponse() = runTest {
        val last = repository.load()
        now += NetworkFeesRepository.TTL
        every { api.getBatteryNetworkFees() } throws IllegalStateException("403")

        assertSame(last, repository.load())
    }

    @Test
    fun aFailedFirstLoadHasNothingAndTriesAgainNextTime() = runTest {
        answer = null
        assertNull(repository.load())
        assertNull(repository.fees.value)

        answer = response(ethCoin)
        repository.load()

        verify(exactly = 2) { api.getBatteryNetworkFees() }
        assertEquals(BigInteger("54600000000000"), repository.fees.value?.swapReserve(ETH))
    }

    private companion object {
        const val ETH = "eth/mainnet/coin"
        const val USDT_ETH = "eth/mainnet/erc20/0xdac17f958d2ee523a2206206994597c13d831ec7"
        const val SOL = "sol/mainnet/coin"
        const val LOADS = 5
        const val SLOW_RESPONSE_MS = 100L

        val ethCoin = NetworkFee(
            assetId = ETH,
            feeAssetId = ETH,
            feeDecimals = 18,
            swap = amounts(safe = "54600000000000"),
            perps = amounts(safe = "1843400000000000"),
        )
        val usdtEth = NetworkFee(
            assetId = USDT_ETH,
            feeAssetId = ETH,
            feeDecimals = 18,
            swap = amounts(safe = "902200000000000"),
            perps = amounts(safe = "1177800000000000"),
        )
        val solCoin = NetworkFee(
            assetId = SOL,
            feeAssetId = SOL,
            feeDecimals = 9,
            swap = amounts(safe = "1950000"),
            perps = null,
        )

        fun response(vararg fees: NetworkFee) = NetworkFeesResponse(assets = fees.toList())

        fun amounts(safe: String) = NetworkFeeAmounts(typical = "1", high = "1", safe = safe)
    }
}
