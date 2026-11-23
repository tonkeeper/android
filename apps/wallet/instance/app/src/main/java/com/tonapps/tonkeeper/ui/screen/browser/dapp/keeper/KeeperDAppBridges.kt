package com.tonapps.tonkeeper.ui.screen.browser.dapp.keeper

import com.tonapps.async.Async
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.core.loyalty.LOYALTY_HOST
import com.tonapps.wallet.data.loyalty.LoyaltyRepository
import com.tonapps.webview.bridge.InAppBridgeContract.BaseBridges
import com.tonapps.webview.bridge.InAppBridge
import com.tonapps.tonkeeper.ui.screen.browser.dapp.keeper.KeeperBridgeProtocol.AppSettings
import com.tonapps.tonkeeper.ui.screen.browser.dapp.keeper.KeeperBridgeProtocol.Credentials
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.multichain.device.SecureDeviceRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import com.tonapps.log.L
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class KeeperDAppBridges(
    private val wallet: WalletEntity,
    private val deviceRepository: SecureDeviceRepository,
    private val settingsRepository: SettingsRepository,
    private val accountRepository: UnifiedAccountRepository,
    private val analytics: AnalyticsHelper,
    private val loyaltyRepository: LoyaltyRepository,
) : BaseBridges() {

    private val referralCodeLock = Mutex()

    private var isReferralCodeTaken = false

    private var referralCode: String? = null

    override fun createBridges(scope: CoroutineScope): List<InAppBridge<*, *>> {
        val handler = KeeperBridgeHandler(
            scope = scope,
            loadCredentials = ::loadCredentials,
            appSettings = ::appSettings,
            track = analytics::simpleTrackEvent,
            referralCode = ::referralCode,
            walletDeactivated = walletDeactivated(),
        )
        return listOf(InAppBridge(KeeperBridgeProtocol(), handler))
    }

    private suspend fun referralCode(host: String): String? {
        if (!host.equals(LOYALTY_HOST, ignoreCase = true)) {
            return null
        }
        return referralCodeLock.withLock {
            if (!isReferralCodeTaken) {
                withContext(NonCancellable) {
                    referralCode = loyaltyRepository.takeReferralCode()
                    isReferralCodeTaken = true
                }
            }
            referralCode
        }
    }

    private suspend fun loadCredentials(expiredAccessToken: String?): Credentials? = withContext(Async.Io) {
        val walletId = wallet.multichainWalletId ?: return@withContext null
        val deviceToken = if (expiredAccessToken == null) {
            deviceRepository.loadAccessToken()
        } else {
            deviceRepository.renewAccessToken(expiredAccessToken)
        }
        if (deviceToken.isNullOrBlank()) {
            return@withContext null
        }
        val walletToken = deviceRepository.walletAuthToken(walletId, deviceToken)
        if (walletToken.isNullOrBlank()) {
            return@withContext null
        }
        Credentials(walletId = walletId, deviceToken = deviceToken, walletToken = walletToken)
    }

    private fun appSettings(): AppSettings {
        return AppSettings(
            theme = theme(),
            language = settingsRepository.getLocale().language,
        )
    }

    private fun theme(): String {
        val theme = settingsRepository.theme
        return when {
            theme.isSystem && settingsRepository.isLightTheme -> THEME_LIGHT
            theme.isSystem -> THEME_DARK
            theme.key == THEME_KEY_BLUE -> THEME_DEEP_BLUE
            theme.light -> THEME_LIGHT
            else -> THEME_DARK
        }
    }

    private fun walletDeactivated(): Flow<Unit> {
        return accountRepository.selectedTonWalletFlow
            .map { it?.id }
            .activeWalletLeaves(wallet.id)
            .catch { e -> L.w("Keeper dApp: active wallet unknown: ${e.javaClass.simpleName}") }
    }

    private companion object {
        private const val THEME_KEY_BLUE = "blue"
        private const val THEME_LIGHT = "light"
        private const val THEME_DARK = "dark"
        private const val THEME_DEEP_BLUE = "deepBlue"
    }
}

internal fun Flow<String?>.activeWalletLeaves(walletId: String): Flow<Unit> {
    return distinctUntilChanged()
        .drop(1)
        .filter { it != walletId }
        .take(1)
        .map { }
}
