package com.tonapps.tonkeeper.ui.screen.nft

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.settings.entities.TokenPrefsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import uikit.extensions.collectFlow

class NftViewModel(
    private val nft: NftEntity,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val api: API,
): ViewModel() {

    private val _trustFlow = MutableStateFlow<Boolean?>(null)
    val trustFlow = _trustFlow.asStateFlow().filterNotNull()

    private val prefFlow = accountRepository.selectedWalletFlow.take(1).map { wallet ->
        settingsRepository.getTokenPrefs(wallet.id, nft.address)
    }

    init {
        if (nft.isTrusted) {
            _trustFlow.value = true
        } else {
            collectFlow(prefFlow) { pref ->
                _trustFlow.value = pref.isTrust
            }
        }
    }

    fun reportSpam(spam: Boolean) = accountRepository.selectedWalletFlow.take(1).onEach { wallet ->
        val state = if (spam) TokenPrefsEntity.State.SPAM else TokenPrefsEntity.State.TRUST
        settingsRepository.setTokenState(wallet.id, nft.address, state)
        api.reportNtfSpam(nft.address, spam)
    }
}