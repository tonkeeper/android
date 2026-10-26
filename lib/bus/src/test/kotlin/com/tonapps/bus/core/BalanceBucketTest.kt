package com.tonapps.bus.core

import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowBalanceBucket
import org.junit.Assert.assertEquals
import org.junit.Test

class BalanceBucketTest {

    @Test
    fun anEmptyWalletIsZero() {
        assertEquals(WalletFlowBalanceBucket._0, balanceBucketOf(0.0))
        assertEquals(WalletFlowBalanceBucket._0, balanceBucketOf(-5.0))
    }

    @Test
    fun eachBucketTakesItsLowerBoundAndStopsBeforeTheUpperOne() {
        assertEquals(WalletFlowBalanceBucket._01, balanceBucketOf(0.004))
        assertEquals(WalletFlowBalanceBucket._01, balanceBucketOf(0.99))
        assertEquals(WalletFlowBalanceBucket._110, balanceBucketOf(1.0))
        assertEquals(WalletFlowBalanceBucket._110, balanceBucketOf(9.99))
        assertEquals(WalletFlowBalanceBucket._10100, balanceBucketOf(10.0))
        assertEquals(WalletFlowBalanceBucket._10100, balanceBucketOf(99.99))
        assertEquals(WalletFlowBalanceBucket._1001k, balanceBucketOf(100.0))
        assertEquals(WalletFlowBalanceBucket._1001k, balanceBucketOf(999.99))
        assertEquals(WalletFlowBalanceBucket._1k10k, balanceBucketOf(1_000.0))
        assertEquals(WalletFlowBalanceBucket._1k10k, balanceBucketOf(9_999.99))
        assertEquals(WalletFlowBalanceBucket._10k100k, balanceBucketOf(10_000.0))
        assertEquals(WalletFlowBalanceBucket._10k100k, balanceBucketOf(99_999.99))
        assertEquals(WalletFlowBalanceBucket._100k, balanceBucketOf(100_000.0))
        assertEquals(WalletFlowBalanceBucket._100k, balanceBucketOf(5_000_000.0))
    }

    @Test
    fun theEventCarriesTheSchemaValues() {
        assertEquals(
            listOf("0", "0-1", "1-10", "10-100", "100-1k", "1k-10k", "10k-100k", "100k+"),
            WalletFlowBalanceBucket.entries.map { it.key },
        )
    }
}
