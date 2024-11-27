package com.tonapps.tonkeeper.extensions

import android.util.Log
import com.tonapps.extensions.filterList
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.dapps.DAppsRepository
import com.tonapps.wallet.data.dapps.entities.AppNotificationsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

fun DAppsRepository.notificationsFlow(
    wallet: WalletEntity,
    scope: CoroutineScope
): Flow<AppNotificationsEntity> {
    if (!wallet.isTonConnectSupported || wallet.testnet) {
        return flow {
            emit(AppNotificationsEntity(wallet.accountId))
        }
    }
    return notificationsFlow.filterList {
        it.accountId == wallet.accountId
    }.mapNotNull { it.firstOrNull() }.stateIn(scope, SharingStarted.Eagerly, AppNotificationsEntity(wallet.accountId))
}

suspend fun DAppsRepository.refreshNotifications(
    wallet: WalletEntity,
    accountRepository: AccountRepository,
) = withContext(Dispatchers.IO) {
    Log.d("DAppsRepositoryLog", "refreshNotifications")
    if (wallet.isTonConnectSupported && !wallet.testnet) {
        val tonProof = accountRepository.requestTonProofToken(wallet)
        refreshPushes(wallet.accountId, tonProof)
    }
}