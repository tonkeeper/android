package com.tonapps.tonkeeper.worker

import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.google.firebase.crashlytics.internal.common.ExecutorUtils
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

inline fun <reified T : CoroutineWorker> WorkManager.oneTime(
    inputData: Data = Data.EMPTY
): Operation {
    val builder = OneTimeWorkRequestBuilder<T>()
    builder.setInputData(inputData)
    builder.requiredNetwork()
    return enqueue(builder.build())
}

inline fun <reified T : CoroutineWorker> WorkManager.periodic(
    repeatInterval: Long,
    repeatIntervalTimeUnit: TimeUnit,
    flexTimeInterval: Long,
    flexTimeIntervalUnit: TimeUnit,
    inputData: Data = Data.EMPTY
): Operation {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    val builder = PeriodicWorkRequestBuilder<T>(repeatInterval, repeatIntervalTimeUnit, flexTimeInterval, flexTimeIntervalUnit)
    builder.setInputData(inputData)
    builder.setConstraints(constraints)
    return enqueue(builder.build())
}

fun WorkRequest.Builder<*, *>.requiredNetwork(networkType: NetworkType = NetworkType.CONNECTED) {
    setConstraints(Constraints.Builder().setRequiredNetworkType(networkType).build())
}
