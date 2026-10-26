package com.tonapps.tonkeeper.ui.screen.browser.dapp.earn

import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.DappBrowser.DappAppClickFrom
import com.tonapps.tonkeeper.manager.earn.EarnUrlResolver
import com.tonapps.tonkeeper.ui.screen.browser.dapp.evm.EvmProviderHandler
import com.tonapps.tonkeeper.ui.screen.browser.dapp.evm.EvmProviderProtocol
import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.InAppBridgeContract.BaseBridges
import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.bridge.InAppBridge
import com.tonapps.tonkeeper.ui.screen.browser.dapp.nativebridge.NativeBridgeHandler
import com.tonapps.tonkeeper.ui.screen.browser.dapp.nativebridge.NativeBridgeProtocol
import com.tonapps.wallet.data.dapps.confirm.ConfirmSheetQueue
import com.tonapps.wallet.data.dapps.evm.EvmProviderRepository
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import org.json.JSONObject

internal class EarnDAppBridges(
    private val wallet: WalletEntity,
    private val accountRepository: McAccountRepository,
    private val providerRepository: EvmProviderRepository,
    private val evmProtocol: EvmProviderProtocol,
    private val analytics: AnalyticsHelper,
    private val settingsRepository: SettingsRepository,
    private val earnUrlResolver: EarnUrlResolver,
    private val confirmSheetQueue: ConfirmSheetQueue,
    openedFrom: DappAppClickFrom,
) : BaseBridges() {

    val earnAnalytics = EarnDAppAnalytics(openedFrom)

    private val nativeMethods = EarnNativeMethods(analytics::simpleTrackEvent, ::currentTheme)

    override fun createBridges(scope: CoroutineScope): List<InAppBridge<*, *>> {
        return listOf(nativeBridge(), evmBridge(scope))
    }

    private fun nativeBridge(): InAppBridge<NativeBridgeProtocol.Message, NativeBridgeProtocol.Response> {
        return InAppBridge(
            NativeBridgeProtocol(config = { nativeConfig() }),
            NativeBridgeHandler(nativeMethods),
        )
    }

    private fun evmBridge(
        scope: CoroutineScope,
    ): InAppBridge<EvmProviderProtocol.Message, EvmProviderProtocol.Response> {
        val handler = EvmProviderHandler(
            scope = scope,
            walletId = wallet.id,
            dappName = DAPP_NAME,
            providerRepository = providerRepository,
            confirmSheetQueue = confirmSheetQueue,
            signListener = earnAnalytics,
            resolveAccounts = {
                if (wallet.isMultichain) {
                    accountRepository.getCoinAccounts(wallet.id)
                } else {
                    emptyList()
                }
            },
        )
        return InAppBridge(evmProtocol, handler)
    }

    fun onThemeChanged() {
        nativeMethods.onThemeChanged()
    }

    private fun currentTheme(): String {
        return if (settingsRepository.isLightTheme) {
            "light"
        } else {
            "dark"
        }
    }

    private fun nativeConfig(): JSONObject {
        return JSONObject()
            .put("theme", nativeMethods.injectTheme())
            .put("locale", settingsRepository.getLocale().stripExtensions().toLanguageTag())
            .put("featuredVaults", earnUrlResolver.featuredVaults)
    }

    private companion object {
        private const val DAPP_NAME = "Earn"
    }
}
