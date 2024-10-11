package com.tonapps.tonkeeper.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tonapps.wallet.data.account.AccountRepository

class WidgetRateWorker(
    context: Context,
    workParam: WorkerParameters,
    accountRepository: AccountRepository
): CoroutineWorker(context, workParam) {
    override suspend fun doWork(): Result {
        TODO("Not yet implemented")
    }

}