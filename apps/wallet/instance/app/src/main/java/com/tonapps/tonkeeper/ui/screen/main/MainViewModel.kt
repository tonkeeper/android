package com.tonapps.tonkeeper.ui.screen.main

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.browser.BrowserRepository
import com.tonapps.wallet.data.collectibles.CollectiblesRepository
import com.tonapps.wallet.data.events.EventsRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainViewModel(
    app: Application,
    private val accountRepository: AccountRepository,
    private val api: API,
    private val browserRepository: BrowserRepository,
    private val settingsRepository: SettingsRepository,
    private val collectiblesRepository: CollectiblesRepository,
    private val eventsRepository: EventsRepository,
    private val environment: Environment,
) : BaseWalletVM(app) {

    private var currentWallet: WalletEntity? = null

    private val _childBottomScrolled = MutableEffectFlow<Boolean>()
    val childBottomScrolled = _childBottomScrolled.asSharedFlow()

    val selectedWalletFlow = accountRepository.selectedWalletFlow

    val disbleNftsFlow = combine(selectedWalletFlow, api.configFlow) { wallet, _ ->
        api.getConfig(wallet.network).flags.disableNfts
    }

    fun setBottomScrolled(value: Boolean) {
        _childBottomScrolled.tryEmit(value)
    }

    fun setData(wallet: WalletEntity, itemId: Int) {
        if (currentWallet == null) {
            prefetchTabs(wallet, itemId)
        } else if (currentWallet != wallet) {
            prefetchTabs(wallet, itemId)
        }
        currentWallet = wallet
    }

    private fun prefetchTabs(wallet: WalletEntity, itemId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            if (itemId != R.id.activity) {
                async { eventsRepository.get(wallet.accountId, wallet.network) }
            }
            if (itemId != R.id.browser) {
                async { prefetchBrowser(wallet) }
            }
            if (itemId != R.id.collectibles) {
                async { collectiblesRepository.get(wallet.address, wallet.network) }
            }
        }
    }

    private suspend fun prefetchBrowser(wallet: WalletEntity) {
        val country = environment.deviceCountry

        browserRepository.load(
            country = country,
            network = wallet.network,
            locale = settingsRepository.getLocale()
        )
    }
}