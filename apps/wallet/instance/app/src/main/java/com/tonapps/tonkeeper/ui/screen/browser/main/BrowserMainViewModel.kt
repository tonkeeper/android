package com.tonapps.tonkeeper.ui.screen.browser.main

import android.app.Application
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.view.View
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.extensions.mapList
import com.tonapps.tonkeeper.extensions.getFixedCountryCode
import com.tonapps.tonkeeper.extensions.getLocaleCountryFlow
import com.tonapps.tonkeeper.koin.remoteConfig
import com.tonapps.tonkeeper.manager.tonconnect.TonConnectManager
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.browser.main.list.connected.ConnectedItem
import com.tonapps.tonkeeper.ui.screen.browser.main.list.explore.list.ExploreItem
import com.tonapps.tonkeeperx.BuildConfig
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.browser.BrowserRepository
import com.tonapps.wallet.data.browser.entities.BrowserAppEntity
import com.tonapps.wallet.data.browser.entities.BrowserDataEntity
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import com.tonapps.wallet.data.dapps.entities.AppEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class BrowserMainViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val settings: SettingsRepository,
    private val api: API,
    private val tonConnectManager: TonConnectManager,
    private val browserRepository: BrowserRepository,
    private val settingsRepository: SettingsRepository
): BaseWalletVM(app) {

    val countryFlow = settings.getLocaleCountryFlow(api)

    val installId: String
        get() = settings.installId

    val uiConnectedItemsFlow = tonConnectManager.walletAppsFlow(wallet).mapList {
        ConnectedItem(wallet, it)
    }

    private val _uiExploreItemsFlow = MutableStateFlow<List<ExploreItem>>(emptyList())
    val uiExploreItemsFlow = _uiExploreItemsFlow.asStateFlow()

    init {
        val isDappsDisable = context.remoteConfig?.isDappsDisable == true

        if (!isDappsDisable) {
            viewModelScope.launch(Dispatchers.IO) {
                val code = settingsRepository.getFixedCountryCode(api)
                val locale = settingsRepository.getLocale()
                _uiExploreItemsFlow.value = emptyList()
                browserRepository.load(code, wallet.testnet, locale)?.let { setData(it) }
                browserRepository.loadRemote(code, wallet.testnet, locale)?.let { setData(it) }
            }
        }
    }

    fun showDisconnect(app: AppEntity) {
        viewModelScope.launch {
            tonConnectManager.showLogoutAppBar(wallet, context, app.url)
        }
    }

    private fun getDebugApps(): List<BrowserAppEntity> {
        val apps = mutableListOf<BrowserAppEntity>()
        apps.add(BrowserAppEntity(
            name = "Mariabit",
            description = "fdsfsd",
            icon = Uri.EMPTY,
            poster = null,
            url = "https://mariabit.github.io/".toUri(),
            textColor = Color.WHITE,
        ))

        return apps.toList()
    }

    private fun setData(data: BrowserDataEntity) {
        val items = mutableListOf<ExploreItem>()
        if (data.apps.isNotEmpty()) {
            items.add(ExploreItem.Banners(data.apps, api.config.featuredPlayInterval, wallet, settingsRepository.country))
        }

        var adsItem: ExploreItem.Ads? = null
        for (category in data.categories) {
            if (category.id == "featured") {
                continue
            } else if (category.id == "ads" && category.apps.isNotEmpty()) {
                val ads = category.apps.first()
                if (ads.button != null) {
                    adsItem = ExploreItem.Ads(category.apps.first(), wallet)
                }
                continue
            }

            val isDigitalNomads = category.id == "digital_nomads"
            if (!isDigitalNomads) {
                items.add(ExploreItem.Title(category.title, category.id))
            }

            val apps = mutableListOf<BrowserAppEntity>()
            for (chunk in category.apps.chunked(4)) {
                if (chunk.size >= 3) {
                    apps.addAll(chunk)
                }
            }

            for (app in apps.take(8)) {
                items.add(ExploreItem.App(
                    app = app,
                    wallet = wallet,
                    singleLine = !isDigitalNomads,
                    country = settingsRepository.country
                ))
            }
        }

        adsItem?.let {
            items.add(1, it)
        }

        if (BuildConfig.DEBUG) {
            val debugItems = mutableListOf<ExploreItem>()
            debugItems.add(ExploreItem.Title("Testing"))
            for (app in getDebugApps()) {
                debugItems.add(ExploreItem.App(
                    app = app,
                    wallet = wallet,
                    singleLine = false,
                    country = settingsRepository.country
                ))
            }
            items.addAll(5, debugItems)
        }

        _uiExploreItemsFlow.value = items.toList()
    }
}