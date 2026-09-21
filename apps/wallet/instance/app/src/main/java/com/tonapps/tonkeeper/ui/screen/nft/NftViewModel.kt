package com.tonapps.tonkeeper.ui.screen.nft

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.bus.generated.Events
import com.tonapps.extensions.currentTimeSeconds
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.dns.renew.DNSRenewViewModel
import com.tonapps.tonkeeper.ui.screen.send.transaction.SendTransactionScreen
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.collectibles.CollectiblesRepository
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.data.core.entity.SignRequestEntity
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.settings.entities.TokenPrefsEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uikit.extensions.activity
import uikit.navigation.Navigation.Companion.navigation

@OptIn(ExperimentalCoroutinesApi::class)
class NftViewModel(
    app: Application,
    private val nftAddress: String,
    private val prefetchedNft: NftEntity?,
    private val unifiedAccountRepository: UnifiedAccountRepository,
    private val mcAccountRepository: McAccountRepository,
    private val settingsRepository: SettingsRepository,
    private val api: API,
    private val collectiblesRepository: CollectiblesRepository,
) : BaseWalletVM(app) {

    val walletFlow: StateFlow<WalletEntity?> = combine(
        unifiedAccountRepository.selectedTonWalletFlow,
        settingsRepository.walletPrefsChangedFlow,
        mcAccountRepository.refreshTrigger,
    ) { wallet, _, _ ->
        wallet
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    private val fetchedNftFlow = walletFlow.filterNotNull().flatMapLatest { wallet ->
        flow {
            emit(
                collectiblesRepository.getNft(
                    accountId = wallet.address,
                    network = wallet.network,
                    address = nftAddress,
                ),
            )
        }
    }

    val nftFlow: StateFlow<NftEntity?> = (
        if (prefetchedNft != null) {
            flowOf(prefetchedNft)
        } else {
            fetchedNftFlow
        }
        ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = prefetchedNft,
    )

    val burnAddress: String by lazy {
        api.getBurnAddress()
    }

    val expiresFlow = walletFlow.filterNotNull().flatMapLatest { wallet ->
        flow {
            val nft = nftFlow.value
            if (nft != null && nft.isDomain && !nft.isTelegramUsername && !wallet.isWatchOnly) {
                collectiblesRepository.getDnsNftExpiring(
                    accountId = wallet.accountId,
                    network = wallet.network,
                    nftAddress = nft.address,
                )?.let { emit(it) }
            }
        }
    }

    fun renewDomain() {
        val wallet = walletFlow.value ?: return
        val nft = nftFlow.value ?: return
        val request = SignRequestEntity.Builder()
            .setValidUntil(currentTimeSeconds() + 10 * 60)
            .setTestnet(wallet.testnet)
            .addMessage(DNSRenewViewModel.createMessage(nft.address))
            .setFrom(wallet.contract.address)
            .build(Uri.EMPTY)

        viewModelScope.launch {
            try {
                SendTransactionScreen.run(
                    context, wallet, request,
                    transactionSentDetail = Events.TransactionSent.TransactionSentCategoryDetail.DomainRenew
                )
                toast(Localization.renew_dns_done)
                getNft()?.let {
                    context.activity?.addScreenDelay(NftScreen.newInstance(it))
                }
                finish()
            } catch (_: Throwable) {
            }
        }
    }

    private fun getNft(): NftEntity? {
        val wallet = walletFlow.value ?: return null
        return collectiblesRepository.getNft(wallet.address, wallet.network, nftAddress)
    }

    fun reportSpam(spam: Boolean, callback: () -> Unit) {
        val wallet = walletFlow.value ?: return
        val nft = nftFlow.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val state = if (spam) TokenPrefsEntity.State.SPAM else TokenPrefsEntity.State.TRUST
            val address = nft.collectionAddressOrNFTAddress
            settingsRepository.setTokenState(wallet.id, address, state)
            try {
                api.reportNtfSpam(nft.address, spam)
            } catch (_: Throwable) {
            }
            withContext(Dispatchers.Main) {
                callback()
            }
        }
    }

    fun hideCollection(callback: () -> Unit) {
        val wallet = walletFlow.value ?: return
        val nft = nftFlow.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val address = nft.collection?.address ?: nft.address
            settingsRepository.setTokenHidden(wallet.id, address, true)
            withContext(Dispatchers.Main) {
                callback()
            }
        }
    }
}
