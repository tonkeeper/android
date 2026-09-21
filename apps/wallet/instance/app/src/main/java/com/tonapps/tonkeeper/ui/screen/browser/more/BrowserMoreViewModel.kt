package com.tonapps.tonkeeper.ui.screen.browser.more

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.browser.more.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.browser.BrowserRepository
import com.tonapps.wallet.data.browser.entities.BrowserCategoryEntity
import com.tonapps.wallet.data.browser.entities.filterByChain
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BrowserMoreViewModel(
    application: Application,
    private val wallet: WalletEntity,
    private val id: String,
    private val chain: String?,
    private val browserRepository: BrowserRepository,
    private val settingsRepository: SettingsRepository,
    private val mcAccountRepository: McAccountRepository,
): BaseWalletVM(application) {

    private val _selectedChain = MutableStateFlow(networkTypeFor(chain))
    val selectedChain: StateFlow<Network.Type?> = _selectedChain.asStateFlow()

    private val isMultichain = MutableStateFlow<Boolean?>(null)

    private val isChainDependent = id !in BrowserCategoryEntity.CHAIN_INDEPENDENT_IDS

    private val dataFlow = browserRepository.dataFlow(
        country = settingsRepository.country,
        network = wallet.network,
        locale = settingsRepository.getLocale(),
        walletId = wallet.multichainWalletId,
    ).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val titleFlow = dataFlow.filterNotNull()
        .map { data -> data.categories.firstOrNull { it.id == id } }
        .filterNotNull()
        .map { it.title }

    val uiItemsFlow = combine(
        dataFlow.filterNotNull(),
        _selectedChain,
        isMultichain.filterNotNull(),
    ) { data, chain, multichain ->
        val category = data.categories.firstOrNull { it.id == id }
        val apps = if (multichain && isChainDependent) {
            (category?.apps ?: emptyList()).filterByChain(chain?.id)
        } else {
            category?.apps ?: emptyList()
        }
        buildList<Item> {
            if (multichain && isChainDependent) {
                add(Item.ChainFilter)
            }
            if (multichain && isChainDependent && chain != null && apps.isEmpty()) {
                add(Item.ChainEmpty)
            } else {
                for ((index, app) in apps.withIndex()) {
                    add(
                        Item.App(
                            wallet = wallet,
                            app = app,
                            position = ListCell.getPosition(apps.size, index),
                            country = settingsRepository.country,
                            multichain = multichain,
                        )
                    )
                }
            }
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            isMultichain.value = mcAccountRepository.getWallet(wallet.id) != null
        }
    }

    fun onChainSelected(network: Network.Type?) {
        if (_selectedChain.value == network) {
            return
        }
        _selectedChain.value = network
    }

    private fun networkTypeFor(chain: String?): Network.Type? {
        return chain?.let { value ->
            Network.Type.entries.firstOrNull { it.id.equals(value, ignoreCase = true) }
        }
    }
}
