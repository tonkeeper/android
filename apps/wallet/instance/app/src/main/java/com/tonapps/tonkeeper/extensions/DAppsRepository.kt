package com.tonapps.tonkeeper.extensions

import android.net.Uri
import android.util.Log
import com.tonapps.extensions.filterList
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.browser.BrowserRepository
import com.tonapps.wallet.data.dapps.DAppsRepository
import com.tonapps.wallet.data.dapps.entities.AppEntity
import com.tonapps.wallet.data.dapps.entities.AppNotificationsEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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
    if (wallet.isTonConnectSupported && !wallet.testnet) {
        val tonProof = accountRepository.requestTonProofToken(wallet)
        refreshPushes(wallet.accountId, tonProof)
    }
}

suspend fun DAppsRepository.getAppFixIcon(
    url: Uri,
    wallet: WalletEntity,
    browserRepository: BrowserRepository,
    settingsRepository: SettingsRepository,
): AppEntity = withTimeout(3_000) {
    var app = getApp(url)
    val browserApp = browserRepository.getApp(
        country = settingsRepository.country,
        testnet = wallet.testnet,
        locale = settingsRepository.getLocale(),
        uri = url
    )
    if (browserApp != null) {
        app = app.copy(
            name = browserApp.name,
            iconUrl = browserApp.icon.toString()
        )
        insertApp(app)
    } else if (app.isBadIcon) {
        app = app.copy(
            iconUrl = app.iconByFavicon
        )
        insertApp(app)
    }
    app
}