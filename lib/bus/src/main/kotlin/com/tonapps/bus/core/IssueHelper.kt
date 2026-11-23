package com.tonapps.bus.core

import com.google.firebase.crashlytics.CustomKeysAndValues
import com.google.firebase.crashlytics.FirebaseCrashlytics

object IssueHelper {
    fun recordException(e: Throwable) {
        FirebaseCrashlytics.getInstance().recordException(e)
    }

    fun recordException(e: Throwable, keys: Map<String, String>) {
        val values = CustomKeysAndValues.Builder()
        keys.forEach { (key, value) -> values.putString(key, value) }
        FirebaseCrashlytics.getInstance().recordException(e, values.build())
    }
}
