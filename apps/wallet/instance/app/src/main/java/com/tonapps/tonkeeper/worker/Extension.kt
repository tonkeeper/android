package com.tonapps.tonkeeper.worker

import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.WorkRequest

inline fun <reified T : CoroutineWorker> WorkManager.oneTime(
    inputData: Data = Data.EMPTY
): Operation {
    val builder = OneTimeWorkRequestBuilder<T>()
    builder.setInputData(inputData)
    builder.requiredNetwork()
    return enqueue(builder.build())
}

fun WorkRequest.Builder<*, *>.requiredNetwork(networkType: NetworkType = NetworkType.CONNECTED) {
    setConstraints(Constraints.Builder().setRequiredNetworkType(networkType).build())
}