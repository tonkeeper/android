package com.tonapps.tonkeeper.ui.screen.send.contacts

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.tonkeeper.core.entities.WalletExtendedEntity
import com.tonapps.tonkeeper.core.history.recipient
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.send.contacts.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.events.EventsRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SendContactsViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val accountRepository: AccountRepository,
    private val eventsRepository: EventsRepository,
    private val settingsRepository: SettingsRepository,
): BaseWalletVM(app) {

    private val _uiItemsFlow = MutableStateFlow<List<Item>?>(null)
    val uiItemsFlow = _uiItemsFlow.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val myWallets = getMyWallets(wallet)
            val latestContacts = getLatestContacts(wallet)
            _uiItemsFlow.value = myWallets + Item.Space + latestContacts
        }
    }

    private suspend fun getMyWallets(
        currentWallet: WalletEntity
    ): List<Item.MyWallet> {
        val wallets = accountRepository.getWallets().filter {
            it.type != Wallet.Type.Watch && it.testnet == currentWallet.testnet && it.address != currentWallet.address
        }.map {
            WalletExtendedEntity( it, settingsRepository.getWalletPrefs(it.id))
        }.sortedBy { it.index }.map { it.raw }.toList()

        return wallets.mapIndexed { index, wallet ->
            val position = ListCell.getPosition(wallets.size, index)
            Item.MyWallet(position, wallet)
        }
    }

    private suspend fun getLatestContacts(
        currentWallet: WalletEntity
    ): List<Item.LatestContact> {
        val accountEvents = eventsRepository.get(currentWallet.accountId, currentWallet.testnet) ?: return emptyList()
        val actions = accountEvents.events.map { it.actions }.flatten()
        val accounts = actions.mapNotNull { it.recipient }
            .filter { it.isWallet && currentWallet.accountId.equals(it.address.toRawAddress(), ignoreCase = true) }
            .distinctBy { it.address }

        val latestAccounts = accounts.filter {
            it.isWallet && it.address.toRawAddress() != currentWallet.accountId
        }.take(6)

        return latestAccounts.mapIndexed { index, account ->
            val position = ListCell.getPosition(latestAccounts.size, index)
            Item.LatestContact(position, account, currentWallet.testnet)
        }
    }
}