package com.tonapps.tonkeeper.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.Operation
import androidx.work.WorkerParameters
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.tonkeeper.extensions.workManager
import com.tonapps.tonkeeper.manager.push.PushManager
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PushToggleWorker(
    context: Context,
    workParam: WorkerParameters,
    private val unifiedAccountRepository: UnifiedAccountRepository,
    private val pushManager: PushManager
): CoroutineWorker(context, workParam) {

    private val state: PushManager.State by lazy {
        val code = inputData.getInt(ARG_PUSH_STATE, 0)
        PushManager.State.of(code)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val requestedIds = requestedWalletIds()
        val wallets = getWallets(requestedIds)
        if (state == PushManager.State.Enable && requestedIds.isNotEmpty() && wallets.isEmpty()) {
            return@withContext if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Result.retry()
            } else {
                FirebaseCrashlytics.getInstance().recordException(
                    IllegalStateException("Failed to resolve wallets for push enable")
                )
                Result.failure()
            }
        }
        try {
            if (wallets.isNotEmpty() && !pushManager.wallets(wallets, state)) {
                throw IllegalStateException("Failed to toggle push")
            }
            Result.success()
        } catch (e: Throwable) {
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Result.retry()
            } else {
                FirebaseCrashlytics.getInstance().recordException(e)
                Result.failure()
            }
        }
    }

    private fun requestedWalletIds(): List<String> {
        return (inputData.getStringArray(ARG_WALLET_IDS) ?: emptyArray()).toList()
    }

    private suspend fun getWallets(walletIds: List<String>): List<WalletEntity> {
        return walletIds.mapNotNull { unifiedAccountRepository.getTonWalletById(it) }
    }

    companion object {

        private const val ARG_WALLET_IDS = "wallet_ids"
        private const val ARG_PUSH_STATE = "push_state"
        private const val MAX_RETRY_ATTEMPTS = 3

        fun run(context: Context, wallet: WalletEntity, state: PushManager.State): Operation {
            return run(context, listOf(wallet), state)
        }

        fun run(context: Context, wallets: List<WalletEntity>, state: PushManager.State): Operation {
            return runByIds(context, wallets.map { it.id }, state)
        }

        fun runByIds(context: Context, walletIds: List<String>, state: PushManager.State): Operation {
            val inputData = Data.Builder()
                .putStringArray(ARG_WALLET_IDS, walletIds.toTypedArray())
                .putInt(ARG_PUSH_STATE, state.code)
                .build()
            return context.workManager.oneTime<PushToggleWorker>(inputData)
        }
    }

}