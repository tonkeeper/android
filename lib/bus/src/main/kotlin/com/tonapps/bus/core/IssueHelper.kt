package com.tonapps.bus.core

import com.google.firebase.crashlytics.FirebaseCrashlytics

object IssueHelper {
    fun recordException(e: Throwable) {
        FirebaseCrashlytics.getInstance().recordException(e)
    }
}