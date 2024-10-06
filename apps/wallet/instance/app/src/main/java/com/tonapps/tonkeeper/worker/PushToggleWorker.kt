package com.tonapps.tonkeeper.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.Operation
import androidx.work.WorkerParameters
import com.tonapps.tonkeeper.extensions.workManager
import com.tonapps.tonkeeper.manager.push.PushManager
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity

class PushToggleWorker(
    context: Context,
    workParam: WorkerParameters,
    private val accountRepository: AccountRepository,
    private val pushManager: PushManager
): CoroutineWorker(context, workParam) {

    private val state: PushManager.State by lazy {
        val code = inputData.getInt(ARG_PUSH_STATE, 0)
        PushManager.State.of(code)
    }

    override suspend fun doWork(): Result {
        val wallets = getWallets()
        if (pushManager.wallets(wallets, state)) {
            return Result.success()
        }
        return Result.failure()
    }

    private suspend fun getWallets(): List<WalletEntity> {
        val walletIds = inputData.getStringArray(ARG_WALLET_IDS) ?: emptyArray()
        return accountRepository.getWallets().filter {
            it.id in walletIds
        }
    }

    companion object {

        private const val ARG_WALLET_IDS = "wallet_ids"
        private const val ARG_PUSH_STATE = "push_state"

        fun run(context: Context, wallet: WalletEntity, state: PushManager.State): Operation {
            return run(context, listOf(wallet), state)
        }

        fun run(context: Context, wallets: List<WalletEntity>, state: PushManager.State): Operation {
            val inputData = Data.Builder()
                .putStringArray(ARG_WALLET_IDS, wallets.map { it.id }.toTypedArray())
                .putInt(ARG_PUSH_STATE, state.code)
                .build()
            return context.workManager.oneTime<PushToggleWorker>(inputData)
        }
    }

}