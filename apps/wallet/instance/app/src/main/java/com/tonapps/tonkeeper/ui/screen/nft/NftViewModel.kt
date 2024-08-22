package com.tonapps.tonkeeper.ui.screen.nft

import android.app.Application
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.settings.entities.TokenPrefsEntity
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take

class NftViewModel(
    app: Application,
    private val nft: NftEntity,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val api: API,
): BaseWalletVM(app) {

    fun reportSpam(spam: Boolean) = accountRepository.selectedWalletFlow.take(1).onEach { wallet ->
        val state = if (spam) TokenPrefsEntity.State.SPAM else TokenPrefsEntity.State.TRUST
        settingsRepository.setTokenState(wallet.id, nft.address, state)
        api.reportNtfSpam(nft.address, spam)
    }

    fun hideCollection() = accountRepository.selectedWalletFlow.take(1).onEach { wallet ->
        val address = nft.collection?.address ?: nft.address
        settingsRepository.setTokenState(wallet.id, address, TokenPrefsEntity.State.SPAM)
    }
}