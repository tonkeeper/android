package com.tonapps.tonkeeper.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Operation
import androidx.work.WorkerParameters
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.tonkeeper.extensions.workManager
import com.tonapps.tonkeeper.manager.assets.AssetsManager
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository

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
                assetsManager.getRemoteTotalBalance(
                    wallet = wallet,
                    currency = settingsRepository.currency,
                    sorted = true
                )
            }
            return Result.success()
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            return Result.failure()
        }
    }

    companion object {

        fun run(context: Context): Operation {
            return context.workManager.oneTime<TotalBalancesWorker>()
        }

    }
}