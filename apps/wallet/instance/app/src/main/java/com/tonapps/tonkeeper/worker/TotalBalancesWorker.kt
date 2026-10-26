package com.tonapps.tonkeeper.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkerParameters
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.tonkeeper.extensions.workManager
import com.tonapps.legacy.assets.AssetsManager
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException

class TotalBalancesWorker(
    context: Context,
    workParam: WorkerParameters,
    private val accountRepository: AccountRepository,
    private val assetsManager: AssetsManager,
    private val settingsRepository: SettingsRepository,
): CoroutineWorker(context, workParam) {

    override suspend fun doWork(): Result {
        try {
            val wallets = accountRepository.getWallets()
            for (wallet in wallets) {
                assetsManager.requestTotalBalance(
                    wallet = wallet,
                    currency = settingsRepository.currency,
                    sorted = true,
                    refresh = true,
                )
            }
            return Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            return Result.failure()
        }
    }

    companion object {

        private const val NAME = "TotalBalancesWorker"

        fun run(context: Context): Operation {
            val builder = OneTimeWorkRequestBuilder<TotalBalancesWorker>()
            builder.requiredNetwork()
            return context.workManager.enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, builder.build())
        }

    }
}