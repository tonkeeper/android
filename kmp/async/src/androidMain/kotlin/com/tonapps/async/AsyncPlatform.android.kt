package com.tonapps.async

import android.os.Looper
import com.tonapps.async.Async.STATE_DIFF_THREAD_NAME
import com.tonapps.async.Async.STATE_THREAD_NAME
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher

actual object AsyncPlatform {

    actual val Io: CoroutineDispatcher = Dispatchers.IO

    actual fun createDispatcherPool(name: String): CoroutineDispatcher {
        return createDefaultPool(name)
            .asCoroutineDispatcher()
    }
}

actual object ThreadChecker {
    actual fun isStateThread(): Boolean {
        return Thread.currentThread().name
            .startsWith(STATE_THREAD_NAME)
    }

    actual fun isStateDiffThread(): Boolean {
        return Thread.currentThread().name
            .startsWith(STATE_DIFF_THREAD_NAME)
    }

    actual fun isMainThread(): Boolean {
        return Looper.myLooper() === Looper.getMainLooper()
    }
}