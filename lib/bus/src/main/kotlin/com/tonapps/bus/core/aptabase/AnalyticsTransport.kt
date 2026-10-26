package com.tonapps.bus.core.aptabase

import android.annotation.SuppressLint
import android.content.Context
import com.tonapps.core.flags.FeatureSource
import com.tonapps.extensions.prefs

private const val PREFS_NAME = "analytics_transport"
private const val SEQUENCE_KEY = "analytics_event_seq"

enum class AnalyticsTransport(val value: String) {
    Sdk("sdk"),
    Cache("cache"),
}

interface AnalyticsSequenceStorage {
    fun read(): Long
    fun write(value: Long)
}

class PrefsSequenceStorage(context: Context) : AnalyticsSequenceStorage {

    private val prefs = context.prefs(PREFS_NAME)

    override fun read(): Long {
        return prefs.getLong(SEQUENCE_KEY, 0L)
    }

    @SuppressLint("ApplySharedPref")
    override fun write(value: Long) {
        prefs.edit().putLong(SEQUENCE_KEY, value).commit()
    }
}

class AnalyticsEventSequence(
    private val storage: AnalyticsSequenceStorage,
) {

    private var value = storage.read()

    fun next(): Long {
        value += 1
        storage.write(value)
        return value
    }
}

object AnalyticsTransportProps {

    const val SEQUENCE = "event_seq"
    const val TRANSPORT = "analytics_transport"
    const val INSTALL_ID = "analytics_install_id"
    const val COHORT_SOURCE = "analytics_cohort_source"

    fun decorate(
        props: MutableMap<String, Any>,
        transport: AnalyticsTransport,
        cohortSource: FeatureSource,
        sequence: Long,
    ) {
        props[SEQUENCE] = sequence
        props[TRANSPORT] = transport.value
        props[COHORT_SOURCE] = cohortSource.name.lowercase()
    }
}
