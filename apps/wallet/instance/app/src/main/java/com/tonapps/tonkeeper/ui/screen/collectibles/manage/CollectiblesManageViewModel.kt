package com.tonapps.tonkeeper.ui.screen.collectibles.manage

import android.app.Application
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.tonkeeper.extensions.isSafeModeEnabled
import com.tonapps.tonkeeper.ui.screen.collectibles.manage.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.API
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.collectibles.CollectiblesRepository
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.settings.entities.TokenPrefsEntity.State
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class CollectiblesManageArgs(
    val spamOnly: Boolean
)

class CollectiblesManageViewModel(
    private val app: Application,
    private val wallet: WalletEntity,
    private val args: CollectiblesManageArgs,
    private val collectiblesRepository: CollectiblesRepository,
    private val settingsRepository: SettingsRepository,
) : AsyncViewModel() {

    private val spamOnly: Boolean
        get() = args.spamOnly

    private val safeMode: Boolean
        get() = settingsRepository.isSafeModeEnabled(wallet.network)

    private var showedAll = false
    private var collectibles: List<NftEntity> = emptyList()

    private val _uiItemsFlow = MutableStateFlow<List<Item>>(emptyList())
    val uiItemsFlow = _uiItemsFlow.asStateFlow()

    init {
        bgScope.launch {
            collectiblesRepository.getFlow(
                address = wallet.address,
                network = wallet.network,
                isOnline = true
            )
                .map { it.list }
                .collect { list ->
                    stateScope.launch {
                        collectibles = list
                        buildUiItems()
                    }
                }
        }
    }

    fun showAll() {
        stateScope.launch {
            showedAll = true
            buildUiItems()
        }
    }

    fun toggle(item: Item.Collection) {
        stateScope.launch {
            settingsRepository.setTokenHidden(wallet.id, item.address, item.visible)
            if (!item.visible) showedAll = true
            buildUiItems()
        }
    }

    fun notSpam(item: Item.Collection) {
        stateScope.launch {
            settingsRepository.setTokenState(wallet.id, item.address, State.TRUST)
            showedAll = true
            buildUiItems()
        }
    }

    private suspend fun buildUiItems() {
        val collectionItems = collectionItems(collectibles)

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

        val uiItems = mutableListOf<Item>()

        if (!spamOnly) {
            if (visibleCollection.isNotEmpty()) {
                uiItems.add(Item.Title(app.getString(Localization.visible)))
                uiItems.add(Item.Space)

                val showAllButton =
                    visibleCollection.size > 3 && !showedAll && hiddenCollection.isNotEmpty()
                val count = if (!showAllButton) visibleCollection.size else 3
                for ((index, item) in visibleCollection.withIndex()) {
                    val isLast = index == count - 1
                    uiItems.add(
                        item.copy(
                            position = ListCell.getPosition(count, index),
                            visible = true,
                            spam = false
                        )
                    )

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
                uiItems.add(Item.Title(app.getString(Localization.hidden)))
                uiItems.add(Item.Space)

                for ((index, item) in hiddenCollection.withIndex()) {
                    uiItems.add(
                        item.copy(
                            position = ListCell.getPosition(hiddenCollection.size, index),
                            visible = false,
                            spam = false
                        )
                    )
                }

                uiItems.add(Item.Space)
            }
        }

        if (spamCollection.isNotEmpty()) {
            if (!spamOnly) {
                uiItems.add(Item.Title(app.getString(Localization.spam)))
                uiItems.add(Item.Space)
            }

            for ((index, item) in spamCollection.withIndex()) {
                uiItems.add(item.copy(
                    position = ListCell.getPosition(spamCollection.size, index),
                    spam = true
                ))
            }

            uiItems.add(Item.Space)
        }

        if (safeMode) {
            uiItems.add(Item.SafeMode(wallet))
        }

        _uiItemsFlow.value = uiItems
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
                items.add(Item.Collection(
                    address = address,
                    title = name,
                    imageUri = nft.thumbUri,
                    count = 1,
                    spam = isLocalSpam(nft),
                ))
            } else {
                items[index] = items[index].copy(
                    count = items[index].count + 1
                )
            }
        }

        return items
    }

    private suspend fun isLocalSpam(nft: NftEntity): Boolean {
        return getStates(nft).count {
            it == State.SPAM
        } > 0
    }

    private suspend fun getStates(nft: NftEntity): List<State> {
        val states = mutableListOf(
            settingsRepository.getTokenPrefs(wallet.id, nft.address).state
        )

        nft.collection?.let {
            states.add(settingsRepository.getTokenPrefs(wallet.id, it.address).state)
        }

        return states
    }
}
