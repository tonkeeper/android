package com.tonapps.bus.core.aptabase

import com.tonapps.core.flags.FeatureSource
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsEventSequenceTest {

    private class MemoryStorage : AnalyticsSequenceStorage {
        var value = 0L

        override fun read(): Long = value

        override fun write(value: Long) {
            this.value = value
        }
    }

    @Test
    fun sequenceIsMonotonicAndPersisted() {
        val storage = MemoryStorage()
        val sequence = AnalyticsEventSequence(storage)

        assertEquals(1L, sequence.next())
        assertEquals(2L, sequence.next())
        assertEquals(2L, storage.value)
    }

    @Test
    fun sequenceContinuesFromStorage() {
        val storage = MemoryStorage().apply { value = 41L }

        assertEquals(42L, AnalyticsEventSequence(storage).next())
    }

    @Test
    fun decorateAddsTransportProps() {
        val props = hashMapOf<String, Any>("own" to "value")

        AnalyticsTransportProps.decorate(
            props = props,
            transport = AnalyticsTransport.Cache,
            cohortSource = FeatureSource.Remote,
            sequence = 7L,
        )

        assertEquals(
            mapOf(
                "own" to "value",
                "event_seq" to 7L,
                "analytics_transport" to "cache",
                "analytics_cohort_source" to "remote",
            ),
            props,
        )
    }
}
