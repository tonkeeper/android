package com.tonapps.tonkeeper.ui.screen.browser.base

import android.app.Application
import androidx.core.view.WindowInsetsCompat
import com.tonapps.bus.generated.Events.DappBrowser.DappBrowserType
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.browser.analytics.DappBrowserAnalytics
import com.tonapps.wallet.api.API
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.wallet.data.browser.BrowserRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

class BrowserBaseViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val browserRepository: BrowserRepository,
    private val settingsRepository: SettingsRepository,
    private val api: API,
    private val environment: Environment,
): BaseWalletVM(app) {

    private val _childBottomScrolled = MutableEffectFlow<Boolean>()
    val childBottomScrolled = _childBottomScrolled.asSharedFlow()

    private val _insetsRootFlow = MutableEffectFlow<WindowInsetsCompat>()
    val insetsRootFlow = _insetsRootFlow.asSharedFlow()

    private var activeTab: DappBrowserType? = null
    private val defaultTab: DappBrowserType
        get() = if (api.getConfig(wallet.network).flags.disableDApps) {
            DappBrowserType.Connected
        } else {
            DappBrowserType.Explore
        }

    val pendingChainFlow: StateFlow<Network.Type?> field = MutableStateFlow(null)

    fun setPendingChain(network: String) {
        Network.Type.find(network)?.let { pendingChainFlow.value = it }
    }

    fun consumePendingChain() {
        pendingChainFlow.value = null
    }

    fun setActiveTab(type: DappBrowserType) {
        activeTab = type
    }

    fun trackBrowserOpen(from: String) {
        DappBrowserAnalytics.browserOpen(
            from = from,
            type = activeTab ?: defaultTab,
            country = environment.deviceCountry
        )
    }

    fun trackTabClick(type: DappBrowserType) {
        activeTab = type
        DappBrowserAnalytics.tabClick(
            type = type,
            country = environment.deviceCountry
        )
    }

    fun setInsetsRoot(value: WindowInsetsCompat) {
        _insetsRootFlow.tryEmit(value)
    }

    fun setBottomScrolled(value: Boolean) {
        _childBottomScrolled.tryEmit(value)
    }

    suspend fun hasCategory(category: String): Boolean = withContext(Dispatchers.IO) {
        val categories = browserRepository.loadCategories(
            country = environment.deviceCountry,
            network = wallet.network,
            locale = settingsRepository.getLocale(),
            walletId = wallet.multichainWalletId,
        )
        categories.any { it == category }
    }

}