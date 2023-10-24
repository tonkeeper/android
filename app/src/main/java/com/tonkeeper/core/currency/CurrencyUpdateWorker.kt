package com.tonkeeper.core

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tonkeeper.App
import java.util.concurrent.TimeUnit

class CurrencyUpdateWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {

        private const val WORKER_NAME = "CurrencyUpdateWorker"

        fun enable(context: Context = App.instance) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val hourlyWork = PeriodicWorkRequestBuilder<CurrencyUpdateWorker>(1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .addTag(WORKER_NAME)
                .build()

            WorkManager.getInstance(context).enqueue(hourlyWork)
        }

        fun disable(context: Context = App.instance) {
            WorkManager.getInstance(context).cancelAllWorkByTag(WORKER_NAME)
        }

    }

    override suspend fun doWork(): Result {
        CurrencyManager.getInstance().sync()
        return Result.success()
    }

}