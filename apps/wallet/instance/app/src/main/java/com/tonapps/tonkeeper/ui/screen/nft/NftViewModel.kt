package com.tonapps.tonkeeper.ui.screen.nft

import android.app.Application
import android.net.Uri
import com.tonapps.log.L
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.extensions.currentTimeSeconds
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.dns.renew.DNSRenewViewModel
import com.tonapps.tonkeeper.ui.screen.send.transaction.SendTransactionScreen
import com.tonapps.wallet.api.API
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.collectibles.CollectiblesRepository
import com.tonapps.wallet.data.collectibles.entities.DnsExpiringEntity
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.data.core.entity.SignRequestEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.settings.entities.TokenPrefsEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uikit.extensions.activity
import uikit.navigation.Navigation.Companion.navigation

class NftViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val nft: NftEntity,
    private val settingsRepository: SettingsRepository,
    private val api: API,
    private val collectiblesRepository: CollectiblesRepository,
): BaseWalletVM(app) {

    val burnAddress: String by lazy {
        api.getBurnAddress()
    }

    val expiresFlow = flow {
        if (nft.isDomain && !nft.isTelegramUsername && !wallet.isWatchOnly) {
            collectiblesRepository.getDnsNftExpiring(
                accountId = wallet.accountId,
                network = wallet.network,
                nftAddress = nft.address
            )?.let { emit(it) }
        }
    }

    fun renewDomain() {
        val request = SignRequestEntity.Builder()
            .setValidUntil(currentTimeSeconds() + 10 * 60)
            .setTestnet(wallet.testnet)
            .addMessage(DNSRenewViewModel.createMessage(nft.address))
            .setFrom(wallet.contract.address)
            .build(Uri.EMPTY)

        viewModelScope.launch {
            try {
                SendTransactionScreen.run(context, wallet, request)
                toast(Localization.renew_dns_done)
                getNft()?.let {
                    context.activity?.addScreenDelay(NftScreen.newInstance(wallet, it))
                }
                finish()
            } catch (ignored: Throwable) { }
        }
    }

    private fun getNft() = collectiblesRepository.getNft(wallet.id, wallet.network, nft.address)

    fun reportSpam(spam: Boolean, callback: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val state = if (spam) TokenPrefsEntity.State.SPAM else TokenPrefsEntity.State.TRUST
            val address = nft.collectionAddressOrNFTAddress
            settingsRepository.setTokenState(wallet.id, address, state)
            try {
                api.reportNtfSpam(nft.address, spam)
            } catch (ignored: Throwable) {}
            withContext(Dispatchers.Main) {
                callback()
            }
        }
    }

    fun hideCollection(callback: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val address = nft.collection?.address ?: nft.address
            settingsRepository.setTokenHidden(wallet.id, address, true)
            withContext(Dispatchers.Main) {
                callback()
            }
        }
    }
}