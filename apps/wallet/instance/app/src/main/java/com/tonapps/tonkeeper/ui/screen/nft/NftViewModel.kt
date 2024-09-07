package com.tonapps.tonkeeper.ui.screen.nft

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.settings.entities.TokenPrefsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NftViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val nft: NftEntity,
    private val settingsRepository: SettingsRepository,
    private val api: API,
): BaseWalletVM(app) {

    fun reportSpam(spam: Boolean, callback: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val state = if (spam) TokenPrefsEntity.State.SPAM else TokenPrefsEntity.State.TRUST
            settingsRepository.setTokenState(wallet.id, nft.address, state)
            api.reportNtfSpam(nft.address, spam)
            withContext(Dispatchers.Main) {
                callback()
            }
        }
    }

    fun hideCollection(callback: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val address = nft.collection?.address ?: nft.address
            settingsRepository.setTokenState(wallet.id, address, TokenPrefsEntity.State.SPAM)
            withContext(Dispatchers.Main) {
                callback()
            }
        }
    }
}