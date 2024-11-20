package com.tonapps.tonkeeper.ui.screen.collectibles.manage

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.extensions.filterList
import com.tonapps.extensions.mapList
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.collectibles.manage.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.collectibles.CollectiblesRepository
import com.tonapps.wallet.data.collectibles.entities.NftCollectionEntity
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.data.core.Trust
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.settings.entities.TokenPrefsEntity.State
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class CollectiblesManageViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val collectiblesRepository: CollectiblesRepository,
    private val settingsRepository: SettingsRepository,
): BaseWalletVM(app) {

    private val safeMode: Boolean
        get() = settingsRepository.isSafeModeEnabled()

    private val _showedAllFlow = MutableStateFlow(false)
    private val showedAllFlow = _showedAllFlow.asStateFlow()

    private val _toggleFlow = MutableEffectFlow<Unit>()
    private val toggleFlow = _toggleFlow.asSharedFlow()

    private val collectiblesFlow = collectiblesRepository.getFlow(
        address = wallet.address,
        testnet = wallet.testnet,
        isOnline = true
    ).map { it.list }

    private val sortedCollectionFlow = combine(
        collectiblesFlow,
        toggleFlow,
    ) { it, _ ->
        val collectionItems = collectionItems(it)

        val visibleCollection = mutableListOf<Item.Collection>()
        val hiddenCollection = mutableListOf<Item.Collection>()
        val spamCollection = mutableListOf<Item.Collection>()

        for (item in collectionItems) {
            val pref = settingsRepository.getTokenPrefs(wallet.id, item.address)
            if (pref.state == State.TRUST) {
                if (item.visible) {
                    visibleCollection.add(item)
                } else {
                    hiddenCollection.add(item)
                }
            } else if (pref.state == State.SPAM || item.spam) {
                spamCollection.add(item)
            } else if (pref.isHidden) {
                hiddenCollection.add(item)
            } else {
                visibleCollection.add(item)
            }
        }

        Triple(visibleCollection, hiddenCollection, spamCollection)
    }

    val uiItemsFlow = combine(
        sortedCollectionFlow,
        showedAllFlow,
    ){ (visibleCollection, hiddenCollection, spamCollection), showedAll ->
        val uiItems = mutableListOf<Item>()
        if (visibleCollection.isNotEmpty()) {
            uiItems.add(Item.Title(getString(Localization.visible)))
            uiItems.add(Item.Space)
            val showAllButton = visibleCollection.size > 3 && !showedAll && (hiddenCollection.size > 0 || spamCollection.size > 0)
            val count = if (!showAllButton) visibleCollection.size else 3
            for ((index, item) in visibleCollection.withIndex()) {
                val isLast = index == count - 1
                uiItems.add(item.copy(
                    position = ListCell.getPosition(count, index),
                    visible = true,
                    spam = false
                ))
                if (isLast && !showedAll) {
                    break
                }
            }
            if (showAllButton) {
                uiItems.add(Item.All)
            }
            uiItems.add(Item.Space)
        }

        if (hiddenCollection.isNotEmpty()) {
            uiItems.add(Item.Title(getString(Localization.hidden)))
            uiItems.add(Item.Space)
            for ((index, item) in hiddenCollection.withIndex()) {
                uiItems.add(item.copy(
                    position = ListCell.getPosition(hiddenCollection.size, index),
                    visible = false,
                    spam = false
                ))
            }
            uiItems.add(Item.Space)
        }

        if (spamCollection.isNotEmpty()) {
            uiItems.add(Item.Title(getString(Localization.spam)))
            uiItems.add(Item.Space)
            for ((index, item) in spamCollection.withIndex()) {
                uiItems.add(item.copy(
                    position = ListCell.getPosition(spamCollection.size, index),
                    spam = true
                ))
            }
            uiItems.add(Item.Space)
        }

        if (safeMode) {
            uiItems.add(Item.SafeMode)
        }

        uiItems.toList()
    }

    init {
        _toggleFlow.tryEmit(Unit)
    }

    fun showAll() {
        _showedAllFlow.value = true
    }

    fun toggle(item: Item.Collection) {
        viewModelScope.launch {
            settingsRepository.setTokenHidden(wallet.id, item.address, item.visible)
            _toggleFlow.tryEmit(Unit)
            if (!item.visible) {
                showAll()
            }
        }
    }

    fun notSpam(item: Item.Collection) {
        viewModelScope.launch {
            settingsRepository.setTokenState(wallet.id, item.address, State.TRUST)
            _toggleFlow.tryEmit(Unit)
            showAll()
        }
    }

    private suspend fun collectionItems(collectibles: List<NftEntity>): List<Item.Collection> {
        val items = mutableListOf<Item.Collection>()
        for (nft in collectibles) {
            if (safeMode && !nft.verified) {
                continue
            }
            val address = nft.collection?.address ?: nft.address
            val name = nft.collection?.name ?: nft.name
            val index = items.indexOfFirst {
                it.address.equalsAddress(address)
            }
            if (index == -1) {
                val state = settingsRepository.getTokenPrefs(wallet.id, nft.address).state
                items.add(Item.Collection(
                    address = address,
                    title = name,
                    imageUri = nft.thumbUri,
                    count = 1,
                    spam = state == State.SPAM
                ))
            } else {
                items[index] = items[index].copy(
                    count = items[index].count + 1
                )
            }
        }
        return items.toList()
    }

}