package com.tonapps.tonkeeper.ui.screen.browser.main

import android.app.Application
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeper.manager.tonconnect.ITonConnectBridge
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.browser.main.list.connected.ConnectedItem
import com.tonapps.tonkeeper.ui.screen.browser.main.list.explore.list.ExploreItem
import com.tonapps.wallet.api.API
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.extensions.toUriOrNull
import com.tonapps.wallet.data.browser.BrowserRepository
import com.tonapps.wallet.data.browser.entities.BrowserAppEntity
import com.tonapps.wallet.data.browser.entities.BrowserCategoryEntity
import com.tonapps.wallet.data.browser.entities.BrowserDataEntity
import com.tonapps.wallet.data.browser.entities.filterByChain
import com.tonapps.wallet.data.dapps.DAppsRepository
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import com.tonapps.wallet.data.dapps.entities.AppConnectWithDetails
import com.tonapps.wallet.data.dapps.entities.AppEntity
import com.tonapps.wallet.data.dapps.entities.DappProvider
import com.tonapps.wallet.data.dapps.source.db.AppRow
import com.tonapps.wallet.data.dapps.wc.WcRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val DISCONNECT_SESSION_TIMEOUT_MS = 5_000L

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserMainViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val settings: SettingsRepository,
    private val api: API,
    private val tonConnectBridge: ITonConnectBridge,
    private val browserRepository: BrowserRepository,
    private val mcAccountRepository: McAccountRepository,
    private val environment: Environment,
    private val wcRepository: WcRepository,
    private val dAppsRepository: DAppsRepository,
): BaseWalletVM(app) {

    val installId: String
        get() = settings.installId

    val uiConnectedItemsFlow = dAppsRepository.connectionsWithDetailsFlow(
        walletId = wallet.id,
        accountId = wallet.accountId,
        mode = wallet.network.value,
    ).map { items ->
        items.filter { it.isBrowserConnected }
            .groupBy { it.dedupeKey() }
            .values
            .map { group -> group.toConnectedItem(wallet) }
    }

    val isDappsDisabled: Boolean
        get() = api.getConfig(wallet.network).flags.disableDApps

    private val _selectedChain = MutableStateFlow<Network.Type?>(null)
    val selectedChain: StateFlow<Network.Type?> = _selectedChain.asStateFlow()

    private val browserDataFlow = flow {
        if (!isDappsDisabled) {
            emitAll(
                browserRepository.dataFlow(
                    country = environment.deviceCountry,
                    network = wallet.network,
                    locale = settings.getLocale(),
                    walletId = wallet.multichainWalletId,
                )
            )
        }
    }

    val uiExploreItemsFlow: StateFlow<List<ExploreItem>> = combine(
        browserDataFlow,
        _selectedChain,
    ) { data, chain -> data to chain }.mapLatest { (data, chain) ->
        val isMultichain = mcAccountRepository.getWallet(wallet.id) != null
        buildExploreItems(data, isMultichain, chain?.id)
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun applyChainFromDeepLink(network: Network.Type, onResult: (applied: Boolean) -> Unit) {
        if (isDappsDisabled) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val isMultichain = withContext(Dispatchers.IO) {
                mcAccountRepository.getWallet(wallet.id) != null
            }
            if (isMultichain) {
                onChainSelected(network)
            }
            onResult(isMultichain)
        }
    }

    fun onChainSelected(network: Network.Type?) {
        _selectedChain.value = network
    }

    fun disconnect(item: ConnectedItem) {
        viewModelScope.launch {
            if (item.wcTopics.isNotEmpty()) {
                // The relay may answer neither callback, so an unbounded call would strand the
                // remaining topics.
                coroutineScope {
                    item.wcTopics.map { topic ->
                        async {
                            withTimeoutOrNull(DISCONNECT_SESSION_TIMEOUT_MS) {
                                wcRepository.disconnect(topic)
                            }
                        }
                    }.awaitAll()
                }
            } else {
                tonConnectBridge.disconnect(
                    wallet = wallet,
                    appUrl = item.app.url,
                    type = AppConnectEntity.Type.Internal,
                )
            }
        }
    }

    private fun buildExploreItems(
        data: BrowserDataEntity,
        isMultichain: Boolean,
        selectedChainId: String?,
    ): List<ExploreItem> {
        val items = mutableListOf<ExploreItem>()
        if (data.apps.isNotEmpty()) {
            items.add(
                ExploreItem.Banners(
                    data.apps,
                    api.getConfig(wallet.network).featuredPlayInterval,
                    wallet,
                    environment.deviceCountry,
                    isMultichain,
                )
            )
        }

        val categories = data.categories.filter { it.id != "featured" }
        val adsItem = resolveGlobalAds(categories, isMultichain)
        val nomadsIndex = categories.indexOfFirst { it.id == "digital_nomads" }
        val beforeNomadsEnd = if (nomadsIndex >= 0) nomadsIndex else categories.size
        val afterNomadsStart = if (nomadsIndex >= 0) nomadsIndex + 1 else categories.size

        for (category in categories.subList(0, beforeNomadsEnd)) {
            if (category.id == "ads") continue
            appendResolvedCategory(
                items = items,
                category = category,
                selectedChainId = selectedChainId,
                isMultichain = isMultichain,
            )
        }

        if (nomadsIndex >= 0) {
            appendResolvedCategory(
                items = items,
                category = categories[nomadsIndex],
                selectedChainId = selectedChainId,
                isMultichain = isMultichain,
            )
        }

        if (isMultichain) {
            if (items.lastOrNull() != ExploreItem.Space) {
                items.add(ExploreItem.Space)
            }
            items.add(ExploreItem.ChainFilter)
        }

        val hasChainCategoryContent = hasChainCategoryContent(
            categories = categories,
            selectedChainId = selectedChainId,
        )
        if (selectedChainId != null && !hasChainCategoryContent) {
            items.add(ExploreItem.ChainEmpty)
        } else {
            for (category in categories.subList(afterNomadsStart, categories.size)) {
                if (category.id == "ads") continue
                appendResolvedCategory(
                    items = items,
                    category = category,
                    selectedChainId = selectedChainId,
                    isMultichain = isMultichain,
                )
            }
        }

        adsItem?.let { ads ->
            val index = minOf(1, items.size)
            items.add(index, ads)
        }

        return items
    }

    private fun resolveGlobalAds(
        categories: List<BrowserCategoryEntity>,
        isMultichain: Boolean,
    ): ExploreItem.Ads? {
        val adsCategory = categories.find { it.id == "ads" } ?: return null
        val ads = adsCategory.apps.firstOrNull() ?: return null
        if (ads.button == null) return null
        return ExploreItem.Ads(
            app = ads,
            wallet = wallet,
            country = environment.deviceCountry,
            multichain = isMultichain,
        )
    }

    private fun hasChainCategoryContent(
        categories: List<BrowserCategoryEntity>,
        selectedChainId: String?,
    ): Boolean {
        return categories.any { category ->
            category.id !in BrowserCategoryEntity.CHAIN_INDEPENDENT_IDS &&
                category.apps.filterByChain(selectedChainId).isNotEmpty()
        }
    }

    private fun appendResolvedCategory(
        items: MutableList<ExploreItem>,
        category: BrowserCategoryEntity,
        selectedChainId: String?,
        isMultichain: Boolean,
    ) {
        val applyChainFilter = category.id !in BrowserCategoryEntity.CHAIN_INDEPENDENT_IDS
        appendCategory(
            items = items,
            category = category,
            applyChainFilter = applyChainFilter,
            selectedChainId = selectedChainId,
            isMultichain = isMultichain,
        )
    }

    private fun appendCategory(
        items: MutableList<ExploreItem>,
        category: BrowserCategoryEntity,
        applyChainFilter: Boolean,
        selectedChainId: String?,
        isMultichain: Boolean,
    ) {
        val categoryApps = if (applyChainFilter) category.apps.filterByChain(selectedChainId) else category.apps
        if (category.id == "ads" || categoryApps.isEmpty()) {
            return
        }

        val isDigitalNomads = category.id == "digital_nomads"

        if (!isDigitalNomads) {
            items.add(ExploreItem.Title(category.title, category.id))
        }

        val apps = mutableListOf<BrowserAppEntity>()
        if (categoryApps.size > 4) {
            for (chunk in categoryApps.chunked(4)) {
                if (chunk.size >= 3) {
                    apps.addAll(chunk)
                }
            }
        } else {
            apps.addAll(categoryApps)
        }

        for (app in apps.take(8)) {
            items.add(
                ExploreItem.App(
                    app = app,
                    wallet = wallet,
                    singleLine = !isDigitalNomads,
                    country = environment.deviceCountry,
                    multichain = isMultichain,
                )
            )
        }

        if (!isDigitalNomads) {
            items.add(ExploreItem.Space)
        }
    }
}

private val AppConnectWithDetails.isBrowserConnected: Boolean
    get() = when (connect.provider) {
        DappProvider.TonConnect -> connect.type == AppConnectEntity.Type.Internal.value
        DappProvider.WalletConnect -> connect.source?.isDapp == true
    }

// Revocation matches on host plus path for TonConnect, so grouping must not be coarser than that.
private fun AppConnectWithDetails.dedupeKey(): Pair<DappProvider, String> {
    val uri = app.url.toUriOrNull()
    val host = uri?.host?.lowercase()
        ?: return connect.provider to app.url.ifBlank { connect.id }.lowercase()
    val path = when (connect.provider) {
        DappProvider.TonConnect -> uri.path.orEmpty().trimEnd('/')
        DappProvider.WalletConnect -> ""
    }
    return connect.provider to "$host$path"
}

private fun List<AppConnectWithDetails>.toConnectedItem(wallet: WalletEntity): ConnectedItem {
    val details = first()
    return ConnectedItem(
        wallet = wallet,
        app = details.app.toAppEntity(),
        wcTopics = mapNotNull { it.connect.topic },
        chain = details.chains.singleOrNull()?.id,
    )
}

private fun AppRow.toAppEntity(): AppEntity = AppEntity(
    url = url.toUri(),
    name = name.orEmpty(),
    iconUrl = iconUrl.orEmpty(),
    empty = false,
)
