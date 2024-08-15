package com.tonapps.tonkeeper.ui.screen.send.contacts

import android.util.Log
import androidx.lifecycle.ViewModel
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.tonkeeper.ui.screen.send.contacts.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.events.EventsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SendContactsViewModel(
    private val accountRepository: AccountRepository,
    private val eventsRepository: EventsRepository,
): ViewModel() {

    val uiItemsFlow = accountRepository.selectedWalletFlow.map { currentWallet ->
        val myWallets = getMyWallets(currentWallet)
        val latestContacts = getLatestContacts(currentWallet)
        myWallets + Item.Space + latestContacts
    }.flowOn(Dispatchers.IO)

    private suspend fun getMyWallets(currentWallet: WalletEntity): List<Item.MyWallet> {
        val wallets = accountRepository.getWallets().filter {
            it.address != currentWallet.address
        }

        return wallets.mapIndexed { index, wallet ->
            val position = ListCell.getPosition(wallets.size, index)
            Item.MyWallet(position, wallet)
        }
    }

    private suspend fun getLatestContacts(currentWallet: WalletEntity): List<Item.LatestContact> {
        val accountEvents = eventsRepository.get(currentWallet.accountId, currentWallet.testnet) ?: return emptyList()
        val actions = accountEvents.events.map { it.actions }.flatten()
        val accounts = actions.map { it.simplePreview }
            .map { it.accounts }
            .flatten()
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