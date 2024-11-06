package com.tonapps.tonkeeper.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.Operation
import androidx.work.WorkerParameters
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.extensions.toUriOrNull
import com.tonapps.tonkeeper.extensions.workManager
import com.tonapps.tonkeeper.manager.push.PushManager
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.dapps.DAppsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DAppPushToggleWorker(
    context: Context,
    workParam: WorkerParameters,
    private val accountRepository: AccountRepository,
    private val dAppsRepository: DAppsRepository,
    private val pushManager: PushManager
): CoroutineWorker(context, workParam) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val wallet = getWallet() ?: throw IllegalArgumentException("Wallet not found")
            val appUrl = getAppUrl() ?: throw IllegalArgumentException("App URL not found")
            val enabled = inputData.getBoolean(ARG_ENABLE, false)
            val connections = dAppsRepository.setPushEnabled(wallet.accountId, wallet.testnet, appUrl, enabled)
            if (!pushManager.dAppPush(wallet, connections, enabled)) {
                dAppsRepository.setPushEnabled(wallet.accountId, wallet.testnet, appUrl, !enabled)
                throw IllegalStateException("Failed to toggle push")
            }
            Result.success()
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            Result.failure()
        }
    }

    private suspend fun getWallet(): WalletEntity? {
        val walletId = inputData.getString(ARG_WALLET_ID) ?: return null
        return accountRepository.getWalletById(walletId)
    }

    private fun getAppUrl(): Uri? {
        return inputData.getString(ARG_APP_URL)?.toUriOrNull()
    }

    companion object {

        private const val ARG_WALLET_ID = "wallet_id"
        private const val ARG_APP_URL = "app_url"
        private const val ARG_ENABLE = "enable"

        fun run(context: Context, wallet: WalletEntity, appUrl: Uri, enable: Boolean): Operation {
            val inputData = Data.Builder()
                .putString(ARG_WALLET_ID, wallet.id)
                .putString(ARG_APP_URL, appUrl.toString())
                .putBoolean(ARG_ENABLE, enable)
                .build()
            return context.workManager.oneTime<DAppPushToggleWorker>(inputData)
        }
    }
}