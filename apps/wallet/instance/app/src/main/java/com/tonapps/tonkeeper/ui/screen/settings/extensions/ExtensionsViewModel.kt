package com.tonapps.tonkeeper.ui.screen.settings.extensions

import android.app.Application
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.settings.extensions.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.plugins.PluginsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class ExtensionsViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val pluginsRepository: PluginsRepository,
) : BaseWalletVM(app) {

    val uiItemsFlow = pluginsRepository.updatedFlow.map { _ ->
        val plugins =
            pluginsRepository.getPlugins(wallet.accountId, wallet.network, refresh = false)
        plugins.mapIndexed { index, plugin ->
            Item.Plugin(
                plugin = plugin,
                wallet = wallet,
                position = ListCell.getPosition(plugins.size, index)
            )
        }
    }.flowOn(Dispatchers.IO)
}



