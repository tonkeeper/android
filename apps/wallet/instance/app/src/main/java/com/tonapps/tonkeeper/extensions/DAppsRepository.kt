package com.tonapps.tonkeeper.extensions

import android.net.Uri
import com.tonapps.extensions.filterList
import com.tonapps.log.L
import com.tonapps.tonkeeper.ui.screen.browser.dapp.keeper.isKeeperDApp
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.blockchain.model.legacy.WalletEntity
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
import kotlinx.coroutines.withTimeoutOrNull

private const val APP_LOOKUP_TIMEOUT_MS = 3_000L

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
): AppEntity {
    if (url.isKeeperDApp()) {
        return hostOnlyApp(url)
    }
    return withTimeoutOrNull(APP_LOOKUP_TIMEOUT_MS) {
        var app = getApp(url)
        val browserApp = browserRepository.getApp(
            country = settingsRepository.country,
            network = wallet.network,
            locale = settingsRepository.getLocale(),
            uri = url,
            walletId = wallet.multichainWalletId,
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
    } ?: lookupTimedOutApp(url)
}

private fun lookupTimedOutApp(url: Uri): AppEntity {
    L.w("App lookup timed out: host=${url.host}")
    return hostOnlyApp(url)
}

private fun hostOnlyApp(url: Uri): AppEntity {
    val app = AppEntity(
        url = url,
        name = url.host ?: url.toString(),
        iconUrl = "",
        empty = true,
    )
    return app.copy(iconUrl = app.iconByFavicon)
}